// merchant-api/index.js — 商户后台 API (Port 3003)
'use strict';

const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { query } = require('@poker-night/shared');

const app = express();
const PORT = process.env.MERCHANT_PORT || 3013;
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
// 商户登录
// ============================================================
app.post('/api/v1/merchant/login', async (req, res) => {
  const { phone, password } = req.body;
  try {
    const result = await query(
      `SELECT v.*, v.contact as name FROM venues v WHERE v.phone = $1`, [phone]
    );
    const venue = result.rows[0];
    if (!venue) return res.status(404).json({ error: 'venue not found' });

    // 简化：用 phone 后 4 位做初始密码，首次登录强制修改
    const defaultPwd = phone.slice(-4);
    if (!bcrypt.compareSync(password, venue.password_hash || bcrypt.hashSync(defaultPwd, 10))) {
      return res.status(401).json({ error: 'wrong password' });
    }

    const token = jwt.sign({ venueId: venue.id, name: venue.name }, JWT_SECRET, { expiresIn: '1d' });
    res.json({ venue: { id: venue.id, name: venue.name }, token });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 设备管理
// ============================================================

// 获取设备列表
app.get('/api/v1/merchant/devices', merchantAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT d.*, t.code as table_code, t.label as table_label
       FROM device_bindings d
       LEFT JOIN tables t ON t.device_sn = d.device_sn
       WHERE d.venue_id = $1 ORDER BY d.bound_at`,
      [req.merchant.venueId]
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 绑定设备（输入设备 SN）
app.post('/api/v1/merchant/devices/bind', merchantAuth, async (req, res) => {
  const { deviceSn, tableLabel } = req.body;
  if (!deviceSn) return res.status(400).json({ error: 'missing deviceSn' });

  try {
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

    res.json({ device: { deviceSn, tableLabel }, table: tableResult.rows[0] });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 解绑设备
app.delete('/api/v1/merchant/devices/:sn', merchantAuth, async (req, res) => {
  try {
    await query('DELETE FROM device_bindings WHERE device_sn = $1 AND venue_id = $2',
      [req.params.sn, req.merchant.venueId]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 订单与收入
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

// 订单列表
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

// 发起退款
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
    res.send('\uFEFF' + csv); // BOM for Excel
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 结算管理
// ============================================================

// 结算记录列表
app.get('/api/v1/merchant/settlements', merchantAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT * FROM settlements WHERE venue_id = $1 ORDER BY period_end DESC LIMIT 50`,
      [req.merchant.venueId]
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 申请提现
app.post('/api/v1/merchant/settlements/:id/withdraw', merchantAuth, async (req, res) => {
  try {
    // 检查结算记录是否存在且属于该商户
    const result = await query(
      'SELECT * FROM settlements WHERE id = $1 AND venue_id = $2',
      [req.params.id, req.merchant.venueId]
    );
    const settlement = result.rows[0];
    if (!settlement) return res.status(404).json({ error: 'settlement not found' });
    if (settlement.status !== 'pending') return res.status(400).json({ error: 'settlement not in pending state' });

    // 标记为已申请提现（实际转账由平台线下操作）
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
// 设备管理（统一路径）
// ============================================================

// 获取设备列表
app.get('/api/v1/devices', merchantAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT d.*, t.code as table_code, t.label as table_label
       FROM device_bindings d
       LEFT JOIN tables t ON t.device_sn = d.device_sn
       WHERE d.venue_id = $1 ORDER BY d.bound_at DESC`,
      [req.merchant.venueId]
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 绑定设备 SN 到场馆
app.post('/api/v1/devices/bind', merchantAuth, async (req, res) => {
  const { deviceSn, tableLabel } = req.body;
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
      device: { deviceSn, tableLabel: tableLabel || null },
      table: tableResult.rows[0],
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 解绑设备
app.delete('/api/v1/devices/:sn', merchantAuth, async (req, res) => {
  try {
    // 检查设备是否属于该场馆
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

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Merchant API] running on port ${PORT}`);
});

module.exports = app;
