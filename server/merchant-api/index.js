// merchant-api/index.js — 商户后台 API (Port 3003)
'use strict';

const path = require('path');
const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { query } = require('@poker-night/shared');

const app = express();
const PORT = process.env.MERCHANT_PORT || 3003;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

app.use(cors());
app.use(express.json());

// 中间件：商户鉴权
function merchantAuth(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'no token' });
  try {
    req.merchant = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'invalid token' });
  }
}

app.get('/health', (req, res) => res.json({ ok: true, service: 'merchant-api' }));

// ============================================================
// 商户登录（兼容 username / phone 两种字段名）
// ============================================================
app.post('/api/v1/merchant/login', async (req, res) => {
  const login = req.body.username || req.body.phone;
  const { password } = req.body;
  if (!login || !password) return res.status(400).json({ error: 'missing credentials' });
  try {
    const result = await query(
      `SELECT v.*, v.contact as name FROM venues v WHERE v.phone = $1 OR v.contact = $1`, [login]
    );
    const venue = result.rows[0];
    if (!venue) return res.status(404).json({ error: 'venue not found' });

    // 简化：用 phone 后 4 位做初始密码，首次登录强制修改
    const defaultPwd = (venue.phone || '').slice(-4);
    if (!bcrypt.compareSync(password, venue.password_hash || bcrypt.hashSync(defaultPwd, 10))) {
      return res.status(401).json({ error: 'wrong password' });
    }

    const token = jwt.sign({ venueId: venue.id, name: venue.name }, JWT_SECRET, { expiresIn: '1d' });
    res.json({
      token,
      merchant: { id: venue.id, name: venue.name },
      venue: { id: venue.id, name: venue.name },
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 数据看板
// ============================================================
app.get('/api/v1/dashboard/summary', merchantAuth, async (req, res) => {
  try {
    const venueId = req.merchant.venueId;

    const ordersResult = await query(
      `SELECT
         COUNT(*) FILTER (WHERE created_at::date = CURRENT_DATE AND status = 'paid') AS today_orders,
         COALESCE(SUM(amount) FILTER (WHERE created_at::date = CURRENT_DATE AND status = 'paid'), 0) AS today_amount,
         COUNT(*) FILTER (WHERE created_at::date = CURRENT_DATE - 1 AND status = 'paid') AS y_orders,
         COALESCE(SUM(amount) FILTER (WHERE created_at::date = CURRENT_DATE - 1 AND status = 'paid'), 0) AS y_amount
       FROM orders WHERE venue_id = $1`,
      [venueId]
    );
    const o = ordersResult.rows[0];

    const pendingResult = await query(
      `SELECT COALESCE(SUM(venue_share), 0) AS pending FROM settlements WHERE venue_id = $1 AND status = 'pending'`,
      [venueId]
    );

    const weekResult = await query(
      `SELECT COUNT(*) AS matches, COALESCE(SUM(t.player_count), 0) AS traffic
       FROM tournaments t JOIN tables tb ON t.table_id = tb.id
       WHERE tb.venue_id = $1 AND t.created_at >= date_trunc('week', CURRENT_DATE)`,
      [venueId]
    );

    const deviceResult = await query(
      'SELECT COUNT(*) AS cnt FROM device_bindings WHERE venue_id = $1',
      [venueId]
    );

    // 最近 7 天趋势
    const chartResult = await query(
      `SELECT to_char(created_at::date, 'MM/DD') AS d,
              COUNT(*) AS orders,
              COALESCE(SUM(amount), 0) AS revenue
       FROM orders
       WHERE venue_id = $1 AND status = 'paid' AND created_at::date >= CURRENT_DATE - 6
       GROUP BY created_at::date ORDER BY created_at::date`,
      [venueId]
    );
    // 补齐缺失日期
    const byDate = {};
    chartResult.rows.forEach(r => { byDate[r.d] = r; });
    const chartData = [];
    for (let i = 6; i >= 0; i--) {
      const dt = new Date();
      dt.setDate(dt.getDate() - i);
      const key = `${String(dt.getMonth() + 1).padStart(2, '0')}/${String(dt.getDate()).padStart(2, '0')}`;
      const row = byDate[key];
      chartData.push({
        date: key,
        orders: row ? parseInt(row.orders) : 0,
        revenue: row ? row.revenue / 100 : 0,
      });
    }

    const pct = (cur, prev) => {
      cur = Number(cur); prev = Number(prev);
      if (prev === 0) return cur > 0 ? '+100%' : '+0%';
      const v = ((cur - prev) / prev * 100).toFixed(1);
      return (v >= 0 ? '+' : '') + v + '%';
    };

    res.json({
      todayOrders: parseInt(o.today_orders),
      ordersChange: pct(o.today_orders, o.y_orders),
      todayRevenue: o.today_amount / 100,
      revenueChange: pct(o.today_amount, o.y_amount),
      pendingSettlement: pendingResult.rows[0].pending / 100,
      weekMatches: parseInt(weekResult.rows[0].matches),
      weekTraffic: parseInt(weekResult.rows[0].traffic),
      onlineDevices: parseInt(deviceResult.rows[0].cnt),
      chartData,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 订单管理（前端契约：{ list, total }，金额单位元）
// ============================================================
app.get('/api/v1/orders', merchantAuth, async (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const pageSize = Math.min(parseInt(req.query.pageSize) || 15, 10000);
  const offset = (page - 1) * pageSize;
  const { dateStart, dateEnd, status, search } = req.query;

  try {
    let where = 'WHERE o.venue_id = $1';
    const params = [req.merchant.venueId];
    let idx = 2;
    if (dateStart) { where += ` AND o.created_at >= $${idx++}`; params.push(dateStart); }
    if (dateEnd) { where += ` AND o.created_at < ($${idx++})::date + 1`; params.push(dateEnd); }
    if (status) { where += ` AND o.status = $${idx++}`; params.push(status); }
    if (search) {
      where += ` AND (o.xunhupay_order_id ILIKE $${idx} OR o.payer_identifier ILIKE $${idx} OR o.id::text ILIKE $${idx})`;
      idx++;
      params.push(`%${search}%`);
    }

    const result = await query(
      `SELECT o.*, t.display_code, tb.device_sn
       FROM orders o
       LEFT JOIN tournaments t ON o.tournament_id = t.id
       LEFT JOIN tables tb ON o.table_id = tb.id
       ${where}
       ORDER BY o.created_at DESC LIMIT $${idx} OFFSET $${idx + 1}`,
      [...params, pageSize, offset]
    );

    const countResult = await query(
      `SELECT COUNT(*) AS total FROM orders o ${where}`,
      params
    );

    const fmt = (d) => {
      const dt = new Date(d);
      const p = (n) => String(n).padStart(2, '0');
      return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())} ${p(dt.getHours())}:${p(dt.getMinutes())}`;
    };

    res.json({
      list: result.rows.map(row => ({
        id: row.id,
        orderNo: row.xunhupay_order_id || row.id,
        time: fmt(row.created_at),
        account: row.payer_identifier || '扫码用户',
        amount: row.amount / 100,
        matchId: row.display_code || null,
        deviceSN: row.device_sn || null,
        status: row.status,
      })),
      total: parseInt(countResult.rows[0].total),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 订单导出（CSV）
// ============================================================
app.get('/api/v1/merchant/orders/export', merchantAuth, async (req, res) => {
  try {
    const { startDate, endDate, status } = req.query;
    let whereClause = 'WHERE o.venue_id = $1';
    const params = [req.merchant.venueId];
    let paramIdx = 2;

    if (startDate) { whereClause += ` AND o.created_at >= $${paramIdx++}`; params.push(startDate); }
    if (endDate) { whereClause += ` AND o.created_at <= $${paramIdx++}`; params.push(endDate); }
    if (status) { whereClause += ` AND o.status = $${paramIdx++}`; params.push(status); }

    const result = await query(
      `SELECT o.*, t.display_code, v.name as venue_name
       FROM orders o
       LEFT JOIN tournaments t ON o.tournament_id = t.id
       LEFT JOIN venues v ON o.venue_id = v.id
       ${whereClause}
       ORDER BY o.created_at DESC`,
      params
    );

    // 生成 CSV
    const headers = ['订单ID', '赛事编号', '金额(元)', '平台分成(元)', '商户收入(元)', '状态', '支付时间', '创建时间'];
    const rows = result.rows.map(o => [
      o.id,
      o.display_code || '-',
      (o.amount / 100).toFixed(2),
      (o.platform_fee / 100).toFixed(2),
      (o.venue_income / 100).toFixed(2),
      o.status,
      o.paid_at ? new Date(o.paid_at).toLocaleString('zh-CN') : '-',
      new Date(o.created_at).toLocaleString('zh-CN'),
    ]);

    const csv = [headers, ...rows].map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n');

    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="orders-${new Date().toISOString().slice(0,10)}.csv"`);
    res.send('﻿' + csv); // BOM for Excel
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 结算管理
// ============================================================

// 结算记录列表
app.get(['/api/v1/settlements', '/api/v1/merchant/settlements'], merchantAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT * FROM settlements WHERE venue_id = $1 ORDER BY period_end DESC LIMIT 50`,
      [req.merchant.venueId]
    );
    const statusMap = { pending: 'pending', confirmed: 'processing', paid: 'completed' };
    res.json({
      list: result.rows.map(s => ({
        id: s.id,
        period: `${s.period_start} ~ ${s.period_end}`,
        orderCount: s.total_orders,
        totalAmount: s.total_amount / 100,
        merchantShare: s.venue_share / 100,
        status: statusMap[s.status] || s.status,
        voucherUrl: s.transfer_proof || null,
      })),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 申请提现（将所有待结算周期标记为已申请）
app.post('/api/v1/settlements/withdraw', merchantAuth, async (req, res) => {
  const { target, note } = req.body;
  if (!target) return res.status(400).json({ error: 'missing withdraw target' });
  try {
    const result = await query(
      `UPDATE settlements SET status = 'confirmed'
       WHERE venue_id = $1 AND status = 'pending'
       RETURNING id`,
      [req.merchant.venueId]
    );
    if (result.rows.length === 0) {
      return res.status(400).json({ error: '没有待结算的周期' });
    }
    console.log(`[Withdraw] venue=${req.merchant.venueId} target=${target} note=${note || '-'} count=${result.rows.length}`);
    res.json({ success: true, count: result.rows.length, message: '提现已申请，平台将在 T+7 内对公转账' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 单条结算申请提现
app.post('/api/v1/merchant/settlements/:id/withdraw', merchantAuth, async (req, res) => {
  try {
    const result = await query(
      'SELECT * FROM settlements WHERE id = $1 AND venue_id = $2',
      [req.params.id, req.merchant.venueId]
    );
    const settlement = result.rows[0];
    if (!settlement) return res.status(404).json({ error: 'settlement not found' });
    if (settlement.status !== 'pending') return res.status(400).json({ error: 'settlement not in pending state' });

    await query(
      'UPDATE settlements SET status = $1 WHERE id = $2',
      ['confirmed', req.params.id]
    );

    res.json({ success: true, message: '提现已申请，平台将在 T+7 内对公转账' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 退款管理
// ============================================================

// 退款记录列表（已退款订单）
app.get('/api/v1/refunds', merchantAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT o.*, t.display_code
       FROM orders o
       LEFT JOIN tournaments t ON o.tournament_id = t.id
       WHERE o.venue_id = $1 AND o.status = 'refunded'
       ORDER BY o.refunded_at DESC NULLS LAST LIMIT 100`,
      [req.merchant.venueId]
    );
    const fmt = (d) => d ? new Date(d).toISOString().slice(0, 19).replace('T', ' ') : '--';
    res.json({
      list: result.rows.map(o => ({
        refundNo: 'RF' + o.id.replace(/-/g, '').slice(0, 12).toUpperCase(),
        orderNo: o.xunhupay_order_id || o.id,
        amount: o.amount / 100,
        reason: o.refund_reason || '--',
        applyTime: fmt(o.refunded_at),
        status: 'completed',
      })),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 发起退款（按订单号）
app.post('/api/v1/refunds/apply', merchantAuth, async (req, res) => {
  const { orderNo, reason } = req.body;
  if (!orderNo) return res.status(400).json({ error: 'missing orderNo' });
  try {
    const orderResult = await query(
      `SELECT * FROM orders
       WHERE (xunhupay_order_id = $1 OR id::text = $1) AND venue_id = $2`,
      [orderNo, req.merchant.venueId]
    );
    const order = orderResult.rows[0];
    if (!order) return res.status(404).json({ error: 'order not found' });
    if (order.status !== 'paid') return res.status(400).json({ error: '只有已支付订单可以退款' });

    // 调用支付服务退款
    const axios = require('axios');
    const refundResult = await axios.post(
      `http://localhost:${process.env.PAYMENT_PORT || 3002}/api/v1/refund`,
      { orderId: order.id, reason: reason || 'merchant refund' }
    );

    if (refundResult.data.success) {
      await query(
        `UPDATE orders SET refund_reason = $1, refund_initiated_by = 'merchant' WHERE id = $2`,
        [reason || null, order.id]
      );
      res.json({ success: true });
    } else {
      res.status(500).json(refundResult.data);
    }
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 设备管理（统一路径，前端契约 { list }）
// ============================================================

// 获取设备列表
app.get(['/api/v1/devices', '/api/v1/merchant/devices'], merchantAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT d.*, t.code as table_code, t.label as table_label, t.status as table_status
       FROM device_bindings d
       LEFT JOIN tables t ON t.device_sn = d.device_sn
       WHERE d.venue_id = $1 ORDER BY d.bound_at DESC`,
      [req.merchant.venueId]
    );
    const fmt = (d) => d ? new Date(d).toISOString().slice(0, 19).replace('T', ' ') : '--';
    res.json({
      list: result.rows.map(d => ({
        sn: d.device_sn,
        venue: req.merchant.name || '--',
        tableNo: d.table_label || d.table_code || '--',
        status: d.table_status === 'idle' || !d.table_status ? 'online' : 'online',
        bindTime: fmt(d.bound_at),
      })),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 绑定设备 SN 到场馆（兼容 sn/tableNo 与 deviceSn/tableLabel）
app.post(['/api/v1/devices/bind', '/api/v1/merchant/devices/bind'], merchantAuth, async (req, res) => {
  const deviceSn = req.body.sn || req.body.deviceSn;
  const tableLabel = req.body.tableNo || req.body.tableLabel;
  if (!deviceSn) return res.status(400).json({ error: 'missing deviceSn' });

  try {
    // 检查设备是否已被其他场馆绑定
    const existResult = await query(
      'SELECT * FROM device_bindings WHERE device_sn = $1', [deviceSn]
    );
    if (existResult.rows.length > 0 && existResult.rows[0].venue_id !== req.merchant.venueId) {
      return res.status(409).json({ error: 'device already bound to another venue' });
    }

    await query(
      `INSERT INTO device_bindings (device_sn, venue_id, table_label)
       VALUES ($1, $2, $3)
       ON CONFLICT (device_sn) DO UPDATE SET venue_id = $2, table_label = $3`,
      [deviceSn, req.merchant.venueId, tableLabel || null]
    );

    // 自动创建牌桌
    const tableCode = generateTableCode();
    const tableResult = await query(
      `INSERT INTO tables (venue_id, device_sn, code, label)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (device_sn) DO UPDATE SET label = $4
       RETURNING *`,
      [req.merchant.venueId, deviceSn, tableCode, tableLabel || '桌台1']
    );

    res.json({
      success: true,
      device: { deviceSn, tableLabel: tableLabel || null },
      table: tableResult.rows[0],
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 解绑设备（POST，前端契约）
app.post('/api/v1/devices/unbind', merchantAuth, async (req, res) => {
  const sn = req.body.sn;
  if (!sn) return res.status(400).json({ error: 'missing sn' });
  try {
    const existResult = await query(
      'SELECT * FROM device_bindings WHERE device_sn = $1 AND venue_id = $2',
      [sn, req.merchant.venueId]
    );
    if (existResult.rows.length === 0) {
      return res.status(404).json({ error: 'device not found' });
    }

    await query('DELETE FROM device_bindings WHERE device_sn = $1 AND venue_id = $2',
      [sn, req.merchant.venueId]);

    // 清除牌桌的设备关联
    await query('UPDATE tables SET device_sn = NULL WHERE device_sn = $1', [sn]);

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 解绑设备（DELETE，旧版兼容）
app.delete(['/api/v1/devices/:sn', '/api/v1/merchant/devices/:sn'], merchantAuth, async (req, res) => {
  try {
    const existResult = await query(
      'SELECT * FROM device_bindings WHERE device_sn = $1 AND venue_id = $2',
      [req.params.sn, req.merchant.venueId]
    );
    if (existResult.rows.length === 0) {
      return res.status(404).json({ error: 'device not found' });
    }

    await query('DELETE FROM device_bindings WHERE device_sn = $1 AND venue_id = $2',
      [req.params.sn, req.merchant.venueId]);

    // 清除牌桌的设备关联
    await query('UPDATE tables SET device_sn = NULL WHERE device_sn = $1', [req.params.sn]);

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 旧版兼容接口（/api/v1/merchant/*）
// ============================================================

// 收入统计
app.get('/api/v1/merchant/stats', merchantAuth, async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    const start = startDate || '1970-01-01';
    const end = endDate || '2099-12-31';

    const statsResult = await query(
      `SELECT
         COUNT(*) as total_orders,
         COALESCE(SUM(amount), 0) as total_amount,
         COALESCE(SUM(venue_income), 0) as venue_income,
         COALESCE(SUM(platform_fee), 0) as platform_fee,
         COUNT(*) FILTER (WHERE status = 'paid') as paid_orders,
         COUNT(*) FILTER (WHERE status = 'refunded') as refunded_orders
       FROM orders
       WHERE venue_id = $1 AND created_at >= $2 AND created_at <= $3`,
      [req.merchant.venueId, start, end]
    );

    // 今日数据
    const todayResult = await query(
      `SELECT
         COUNT(*) as today_orders,
         COALESCE(SUM(amount), 0) as today_amount,
         COALESCE(SUM(venue_income), 0) as today_venue_income
       FROM orders
       WHERE venue_id = $1 AND DATE(created_at) = CURRENT_DATE AND status = 'paid'`,
      [req.merchant.venueId]
    );

    res.json({
      total: statsResult.rows[0],
      today: todayResult.rows[0],
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 订单列表（旧版）
app.get('/api/v1/merchant/orders', merchantAuth, async (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = parseInt(req.query.limit) || 20;
  const offset = (page - 1) * limit;

  try {
    const result = await query(
      `SELECT o.*, t.display_code, t.status as tournament_status
       FROM orders o
       LEFT JOIN tournaments t ON o.tournament_id = t.id
       WHERE o.venue_id = $1
       ORDER BY o.created_at DESC LIMIT $2 OFFSET $3`,
      [req.merchant.venueId, limit, offset]
    );

    const countResult = await query(
      'SELECT COUNT(*) as total FROM orders WHERE venue_id = $1',
      [req.merchant.venueId]
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

// 发起退款（旧版，按订单 ID）
app.post('/api/v1/merchant/orders/:id/refund', merchantAuth, async (req, res) => {
  const { reason } = req.body;
  try {
    // 验证订单属于该商户
    const orderResult = await query('SELECT * FROM orders WHERE id = $1 AND venue_id = $2',
      [req.params.id, req.merchant.venueId]);
    if (orderResult.rows.length === 0) return res.status(404).json({ error: 'order not found' });

    // 调用支付服务退款
    const axios = require('axios');
    const refundResult = await axios.post(
      `http://localhost:${process.env.PAYMENT_PORT || 3002}/api/v1/refund`,
      { orderId: req.params.id, reason: reason || 'merchant refund' }
    );

    if (refundResult.data.success) {
      await query('UPDATE orders SET refund_reason = $1 WHERE id = $2', [reason, req.params.id]);
      res.json({ success: true });
    } else {
      res.status(500).json(refundResult.data);
    }
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 辅助
// ============================================================
function generateTableCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

// ============================================================
// 静态托管商户后台前端（merchant-dashboard/）
// Nginx /merchant/ → 3003/ 后直接可用
// ============================================================
app.use(express.static(path.join(__dirname, '../../merchant-dashboard')));

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Merchant API] running on port ${PORT}`);
});

module.exports = app;
