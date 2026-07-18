// payment-svc/index.js — 支付服务 (Port 3002)
// 虎皮椒支付集成 — 创建订单 + 回调确认 + 退款
'use strict';

const express = require('express');
const cors = require('cors');
const axios = require('axios');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { query, db } = require('@poker-night/shared');
const { ORDER_STATUS, FEE_SPLIT } = require('@poker-night/shared');

const app = express();
const PORT = process.env.PAYMENT_PORT || 3002;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

// 虎皮椒配置
const XUNHU_API = process.env.XUNHU_API || 'https://api.xunhupay.com/payment/do.html';
const XUNHU_APPID = process.env.XUNHU_APPID || '201906182246';
const XUNHU_APPKEY = process.env.XUNHU_APPKEY || '5995adfd45da21ea5a70c086df023c22';
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || 'https://pokernight.cc';

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true })); // 虎皮椒回调是 form-urlencoded

// 静态文件
const path = require('path');
app.use(express.static(path.join(__dirname, 'public')));

// ============================================================
// 中间件：JWT 鉴权
// ============================================================
function auth(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'no token' });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'invalid token' });
  }
}

// ============================================================
// 健康检查
// ============================================================
app.get('/health', (req, res) => res.json({ ok: true, service: 'payment-svc' }));

// ============================================================
// 虎皮椒签名（按 key ASCII 升序，末尾追加 appKey，MD5 小写）
// ============================================================
function sign(params, appKey) {
  const sorted = Object.keys(params)
    .filter(k => params[k] !== '' && params[k] !== undefined && params[k] !== null && k !== 'hash')
    .sort();
  const str = sorted.map(k => `${k}=${params[k]}`).join('&') + appKey;
  return crypto.createHash('md5').update(str, 'utf8').digest('hex');
}

// ============================================================
// 创建订单（扫码支付发起）
// ============================================================
app.post('/api/v1/payment/create', async (req, res) => {
  const { tableId, paymentMethod } = req.body; // 'wechat' | 'alipay'

  if (!tableId) return res.status(400).json({ error: 'missing tableId' });

  try {
    // 获取牌桌信息
    const tableResult = await query(
      `SELECT t.*, v.name as venue_name, v.rate_plan
       FROM tables t JOIN venues v ON t.venue_id = v.id
       WHERE t.id = $1`, [tableId]
    );
    const table = tableResult.rows[0];
    if (!table) return res.status(404).json({ error: 'table not found' });

    const amount = table.launch_fee;
    const ratePlan = table.rate_plan || { platform: FEE_SPLIT.PLATFORM, venue: FEE_SPLIT.VENUE };
    const platformFee = Math.floor(amount * ratePlan.platform / 100);
    const venueIncome = amount - platformFee;

    // 生成 display_code
    const displayCode = generateDisplayCode();

    // 创建赛事记录
    const tournamentResult = await query(
      `INSERT INTO tournaments (display_code, table_id, launch_fee, max_players, start_chips,
        start_blind, blind_interval, wait_countdown, action_timeout, status)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'pending')
       RETURNING id`,
      [displayCode, tableId, amount, table.max_players || 6,
       1000, 10, 600, 300, 30]
    );
    const tournamentId = tournamentResult.rows[0].id;

    // 创建订单
    const orderResult = await query(
      `INSERT INTO orders (tournament_id, table_id, venue_id, amount, platform_fee, venue_income, status)
       VALUES ($1, $2, $3, $4, $5, $6, 'pending') RETURNING id`,
      [tournamentId, tableId, table.venue_id, amount, platformFee, venueIncome]
    );
    const orderId = orderResult.rows[0].id;

    // 调用虎皮椒创建支付
    const xunhuOrderId = `PN-${orderId}-${Date.now()}`;
    const notifyUrl = `${PUBLIC_BASE_URL}/pay/api/v1/payment/notify`;
    const returnUrl = `${PUBLIC_BASE_URL}/pay/pay-result.html?order=${orderId}`;

    const params = {
      version: '1.1',
      appid: XUNHU_APPID,
      trade_order_id: xunhuOrderId,
      total_fee: (amount / 100).toFixed(2), // 虎皮椒用元为单位
      title: `德州扑克之夜 - ${table.label || table.code}`,
      time: String(Math.floor(Date.now() / 1000)),
      notify_url: notifyUrl,
      return_url: returnUrl,
      nonce: crypto.randomBytes(16).toString('hex'),
      type: 'WAP',
      wap_url: PUBLIC_BASE_URL,
      wap_name: 'PokerNight',
    };

    // 微信或支付宝
    if (paymentMethod === 'alipay') {
      params.type = 'WAP';
      params.wap_url = PUBLIC_BASE_URL;
      params.wap_name = 'PokerNight';
    }

    params.hash = sign(params, XUNHU_APPKEY);

    console.log('[Payment] Creating xunhupay order:', xunhuOrderId, 'amount:', params.total_fee);

    const payResult = await axios.post(XUNHU_API, new URLSearchParams(params).toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      timeout: 15000,
    });
    const payData = payResult.data;

    if (payData.errcode !== 0 || !payData.url) {
      // 支付创建失败，回滚
      await query('UPDATE orders SET status = $1 WHERE id = $2', [ORDER_STATUS.CANCELLED, orderId]);
      await query('UPDATE tournaments SET status = $1 WHERE id = $2', ['cancelled', tournamentId]);
      console.error('[Payment] Xunhupay error:', payData);
      return res.status(500).json({ error: 'payment creation failed', detail: payData.errmsg });
    }

    // 更新订单
    await query('UPDATE orders SET xunhupay_order_id = $1 WHERE id = $2', [xunhuOrderId, orderId]);

    res.json({
      orderId,
      tournamentId,
      displayCode,
      amount,
      payUrl: payData.url,
    });
  } catch (err) {
    console.error('[Payment] Create error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 虎皮椒回调（form-urlencoded POST）
// ============================================================
app.post('/api/v1/payment/notify', async (req, res) => {
  const data = req.body;

  // 验签
  const hash = data.hash;
  if (!hash) return res.send('fail');

  const expectedHash = sign(data, XUNHU_APPKEY);
  if (hash !== expectedHash) {
    console.error('[Payment] Notify signature mismatch');
    return res.send('fail');
  }

  if (data.status !== 'OD') {
    return res.send('success'); // 非已支付状态，确认收到但不处理
  }

  const xunhuOrderId = data.trade_order_id;
  const orderId = parseInt(xunhuOrderId.split('-')[1]);

  try {
    const client = await db.connect();
    await client.query('BEGIN');

    // 更新订单状态
    const orderResult = await client.query(
      'UPDATE orders SET status = $1, paid_at = NOW() WHERE id = $2 AND status = $3 RETURNING *',
      [ORDER_STATUS.PAID, orderId, ORDER_STATUS.PENDING]
    );

    if (orderResult.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.send('success'); // 已处理过
    }

    const order = orderResult.rows[0];

    // 更新赛事状态
    await client.query(
      'UPDATE tournaments SET status = $1 WHERE id = $2',
      ['registering', order.tournament_id]
    );

    await client.query('COMMIT');

    // 触发赛事激活
    try {
      const { activateTournament } = require('../poker-socket');
      await activateTournament(order.tournament_id);
    } catch (e) {
      console.error('[Payment] Failed to activate tournament:', e.message);
    }

    console.log('[Payment] Order', orderId, 'paid successfully');
    res.send('success');
  } catch (err) {
    console.error('[Payment] Notify error:', err.message);
    res.send('fail');
  }
});

// ============================================================
// 退款
// ============================================================
app.post('/api/v1/refund', auth, async (req, res) => {
  const { orderId, reason } = req.body;

  if (!orderId) return res.status(400).json({ error: 'missing orderId' });

  try {
    const orderResult = await query('SELECT * FROM orders WHERE id = $1', [orderId]);
    const order = orderResult.rows[0];
    if (!order) return res.status(404).json({ error: 'order not found' });
    if (order.status !== ORDER_STATUS.PAID) return res.status(400).json({ error: 'order not in paid state' });

    // 调用虎皮椒退款 API
    const params = {
      version: '1.1',
      appid: XUNHU_APPID,
      trade_order_id: order.xunhupay_order_id,
      refund_amount: (order.amount / 100).toFixed(2),
      nonce: crypto.randomBytes(16).toString('hex'),
    };
    params.hash = sign(params, XUNHU_APPKEY);

    const refundResult = await axios.post(
      'https://api.xunhupay.com/payment/refund.html',
      new URLSearchParams(params).toString(),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, timeout: 15000 }
    );

    if (refundResult.data.errcode === 0) {
      const client = await db.connect();
      try {
        await client.query('BEGIN');

        await client.query(
          'UPDATE orders SET status = $1, refunded_at = NOW(), refund_reason = $2 WHERE id = $3',
          [ORDER_STATUS.REFUNDED, reason || 'merchant refund', orderId]
        );

        if (order.tournament_id) {
          await client.query(
            'UPDATE tournaments SET status = $1 WHERE id = $2 AND status != $3',
            ['cancelled', order.tournament_id, 'finished']
          );
        }

        await client.query('COMMIT');
      } catch (err) {
        await client.query('ROLLBACK');
        throw err;
      } finally {
        client.release();
      }

      try {
        const { io } = require('../poker-socket');
        if (io) io.emit('order_refunded', { orderId, tournamentId: order.tournament_id });
      } catch (e) { /* ignore */ }

      res.json({ success: true, orderId, status: 'refunded' });
    } else {
      res.status(500).json({ error: 'refund failed', detail: refundResult.data.errmsg });
    }
  } catch (err) {
    console.error('[Payment] Refund error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 订单查询
// ============================================================
app.get('/api/v1/orders/:id', async (req, res) => {
  try {
    const result = await query('SELECT * FROM orders WHERE id = $1', [req.params.id]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'order not found' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/v1/payment/order/:id', async (req, res) => {
  try {
    const result = await query(
      `SELECT o.*, t.display_code FROM orders o LEFT JOIN tournaments t ON o.tournament_id = t.id WHERE o.id = $1`,
      [req.params.id]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'order not found' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 订单列表（商户/管理员查看）
// ============================================================
app.get('/api/v1/orders', auth, async (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = Math.min(parseInt(req.query.limit) || 20, 100);
  const offset = (page - 1) * limit;
  const { startDate, endDate, status, venueId } = req.query;

  try {
    let whereClause = 'WHERE 1=1';
    const params = [];
    let paramIdx = 1;

    if (req.user.venueId && !req.user.isAdmin) {
      whereClause += ` AND o.venue_id = $${paramIdx++}`;
      params.push(req.user.venueId);
    } else if (venueId) {
      whereClause += ` AND o.venue_id = $${paramIdx++}`;
      params.push(venueId);
    }

    if (startDate) { whereClause += ` AND o.created_at >= $${paramIdx++}`; params.push(startDate); }
    if (endDate) { whereClause += ` AND o.created_at <= $${paramIdx++}`; params.push(endDate); }
    if (status) { whereClause += ` AND o.status = $${paramIdx++}`; params.push(status); }

    const result = await query(
      `SELECT o.*, t.display_code, t.status as tournament_status, v.name as venue_name
       FROM orders o
       LEFT JOIN tournaments t ON o.tournament_id = t.id
       LEFT JOIN venues v ON o.venue_id = v.id
       ${whereClause}
       ORDER BY o.created_at DESC LIMIT $${paramIdx++} OFFSET $${paramIdx++}`,
      [...params, limit, offset]
    );

    const countResult = await query(
      `SELECT COUNT(*) as total FROM orders o ${whereClause}`,
      params
    );

    res.json({
      orders: result.rows,
      total: parseInt(countResult.rows[0].total),
      page, limit,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 辅助
// ============================================================
function generateDisplayCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Payment] running on port ${PORT}`);
  console.log(`[Payment] Xunhupay APPID: ${XUNHU_APPID}`);
});

module.exports = app;
