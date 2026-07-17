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
const PORT = process.env.PAYMENT_PORT || 3012;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

// 虎皮椒配置
const XUNHU_API = process.env.XUNHU_API || 'https://api.xunhupay.com';
const XUNHU_APP_ID = process.env.XUNHU_APP_ID || '';
const XUNHU_APP_SECRET = process.env.XUNHU_APP_SECRET || '';
const XUNHU_WX_URL = process.env.XUNHU_WX_URL || '';  // 微信支付通道
const XUNHU_ALIPAY_URL = process.env.XUNHU_ALIPAY_URL || ''; // 支付宝通道

app.use(cors());
app.use(express.json());

// ============================================================
// 中间件：JWT 鉴权（商户或管理员）
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

    // 生成 display_code（赛事编号）
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
    const notifyUrl = `${process.env.PUBLIC_BASE_URL || 'http://43.164.130.145:3002'}/api/v1/payment/notify`;
    const returnUrl = `${process.env.PUBLIC_RETURN_URL || 'http://43.164.130.145'}/pay-result?order=${orderId}`;

    // 构造虎皮椒请求
    const params = {
      version: '1.1',
      app_id: XUNHU_APP_ID,
      trade_order_id: xunhuOrderId,
      total_fee: amount,
      title: `德州扑克之夜 - ${table.label || table.code}`,
      time: Math.floor(Date.now() / 1000),
      notify_url: notifyUrl,
      return_url: returnUrl,
      nonce_str: crypto.randomBytes(16).toString('hex'),
    };

    // 签名
    params.hash = signParams(params, XUNHU_APP_SECRET);

    // 发起支付
    const payUrl = paymentMethod === 'alipay' ? XUNHU_ALIPAY_URL : XUNHU_WX_URL;
    const payResult = await axios.post(payUrl, params);
    const payData = payResult.data;

    if (payData.errcode !== 0) {
      // 支付创建失败，回滚
      await query('UPDATE orders SET status = $1 WHERE id = $2', [ORDER_STATUS.CANCELLED, orderId]);
      await query('UPDATE tournaments SET status = $1 WHERE id = $2', ['cancelled', tournamentId]);
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
      payData,
    });
  } catch (err) {
    console.error('[Payment] Create error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 虎皮椒回调
// ============================================================
app.post('/api/v1/payment/notify', async (req, res) => {
  const data = req.body;

  // 验签
  const hash = data.hash;
  delete data.hash;
  const expectedHash = signParams(data, XUNHU_APP_SECRET);

  if (hash !== expectedHash) {
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

    // 触发赛事激活（通过 require poker-socket）
    const { activateTournament } = require('../poker-socket');
    await activateTournament(order.tournament_id);

    res.send('success');
  } catch (err) {
    console.error('[Payment] Notify error:', err.message);
    res.send('fail');
  }
});

// ============================================================
// 退款（增强版）
// ============================================================
app.post('/api/v1/refund', auth, async (req, res) => {
  const { orderId, reason } = req.body;

  if (!orderId) return res.status(400).json({ error: 'missing orderId' });
  if (!reason) return res.status(400).json({ error: 'missing reason' });

  try {
    const orderResult = await query('SELECT * FROM orders WHERE id = $1', [orderId]);
    const order = orderResult.rows[0];
    if (!order) return res.status(404).json({ error: 'order not found' });
    if (order.status !== ORDER_STATUS.PAID) return res.status(400).json({ error: 'order not in paid state' });

    // 调用虎皮椒退款 API
    const params = {
      version: '1.1',
      app_id: XUNHU_APP_ID,
      trade_order_id: order.xunhupay_order_id,
      refund_amount: order.amount,
      nonce_str: crypto.randomBytes(16).toString('hex'),
    };
    params.hash = signParams(params, XUNHU_APP_SECRET);

    const refundResult = await axios.post(`${XUNHU_API}/refund.do`, params);

    if (refundResult.data.errcode === 0) {
      const client = await db.connect();
      try {
        await client.query('BEGIN');

        // 更新订单状态
        await client.query(
          'UPDATE orders SET status = $1, refunded_at = NOW(), refund_reason = $2, refund_initiated_by = $3 WHERE id = $4',
          [ORDER_STATUS.REFUNDED, reason, req.user.name || req.user.id || 'system', orderId]
        );

        // 冲销分账记录
        await client.query(
          `UPDATE orders SET platform_fee = 0, venue_income = 0 WHERE id = $1`,
          [orderId]
        );

        // 如果有关联赛事，取消赛事
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

      // 通知相关方
      // 通知 Socket.IO 服务
      try {
        const { io } = require('../poker-socket');
        if (io) {
          io.emit('order_refunded', { orderId, tournamentId: order.tournament_id, reason });
        }
      } catch (e) {
        // poker-socket 可能未启动，忽略
      }

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

    // 如果是商户身份，只能看自己的订单
    if (req.user.venueId && !req.user.isAdmin) {
      whereClause += ` AND o.venue_id = $${paramIdx++}`;
      params.push(req.user.venueId);
    } else if (venueId) {
      whereClause += ` AND o.venue_id = $${paramIdx++}`;
      params.push(venueId);
    }

    if (startDate) {
      whereClause += ` AND o.created_at >= $${paramIdx++}`;
      params.push(startDate);
    }
    if (endDate) {
      whereClause += ` AND o.created_at <= $${paramIdx++}`;
      params.push(endDate);
    }
    if (status) {
      whereClause += ` AND o.status = $${paramIdx++}`;
      params.push(status);
    }

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
      page,
      limit,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 辅助函数
// ============================================================

function signParams(params, secret) {
  const sorted = Object.keys(params).sort().filter(k => params[k] !== '' && k !== 'hash');
  const str = sorted.map(k => `${k}=${params[k]}`).join('&') + `&key=${secret}`;
  return crypto.createHash('md5').update(str).digest('hex').toUpperCase();
}

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
});

module.exports = app;
