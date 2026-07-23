// agent-api/index.js — 代理后台 API (Port 3004)
'use strict';

const path = require('path');
const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { query } = require('@poker-night/shared');

const app = express();
const PORT = process.env.AGENT_PORT || 3004;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

app.use(cors());
app.use(express.json());

// 中间件：代理鉴权
function agentAuth(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'no token' });
  try {
    req.agent = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'invalid token' });
  }
}

app.get('/health', (req, res) => res.json({ ok: true, service: 'agent-api' }));

// ============================================================
// 代理登录（手机号 + 密码）
// ============================================================
app.post('/api/v1/agent/login', async (req, res) => {
  const { phone, password } = req.body;
  if (!phone || !password) return res.status(400).json({ error: 'missing credentials' });
  try {
    const result = await query(
      `SELECT * FROM agents WHERE phone = $1 AND status = 'active'`, [phone]
    );
    const agent = result.rows[0];
    if (!agent) return res.status(404).json({ error: 'agent not found' });

    // 与 merchant-api 同一套密码哈希（bcryptjs，rounds=10）
    if (!bcrypt.compareSync(password, agent.password_hash || '')) {
      return res.status(401).json({ error: 'wrong password' });
    }

    const token = jwt.sign({ agentId: agent.id, name: agent.name }, JWT_SECRET, { expiresIn: '1d' });
    res.json({
      token,
      agent: {
        id: agent.id,
        name: agent.name,
        code: agent.code,
        rate: agent.rate,
        parentId: agent.parent_id,
      },
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 下级代理注册（无鉴权，凭邀请码）
// ============================================================
app.post('/api/v1/agent/register', async (req, res) => {
  const { name, phone, password, inviteCode } = req.body;
  if (!name || !phone || !password || !inviteCode) {
    return res.status(400).json({ error: 'missing fields' });
  }
  try {
    // 邀请码必须属于某个 active 代理（作为直属上级 parent_id）
    const parentResult = await query(
      `SELECT id FROM agents WHERE code = $1 AND status = 'active'`, [inviteCode]
    );
    const parent = parentResult.rows[0];
    if (!parent) return res.status(400).json({ error: 'invalid invite code' });

    // 手机号唯一
    const exist = await query('SELECT id FROM agents WHERE phone = $1', [phone]);
    if (exist.rows.length > 0) return res.status(409).json({ error: 'phone already registered' });

    const passwordHash = bcrypt.hashSync(password, 10);

    // 自动生成 6 位邀请码（[A-Z0-9]），唯一冲突重试
    let code = null;
    for (let i = 0; i < 5 && !code; i++) {
      const candidate = generateAgentCode();
      try {
        await query(
          `INSERT INTO agents (code, parent_id, name, phone, password_hash, rate)
           VALUES ($1, $2, $3, $4, $5, 20)`,
          [candidate, parent.id, name, phone, passwordHash]
        );
        code = candidate;
      } catch (err) {
        if (err.code === '23505') {
          if (err.constraint && err.constraint.includes('phone')) {
            return res.status(409).json({ error: 'phone already registered' });
          }
          continue; // code 冲突，换码重试
        }
        throw err;
      }
    }
    if (!code) return res.status(500).json({ error: 'failed to generate unique code' });

    res.json({ ok: true, code });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 数据看板（金额单位：分）
// 统计口径照抄 merchant-api：只计 status='paid' 的订单，退款单不算
// ============================================================
app.get('/api/v1/agent/summary', agentAuth, async (req, res) => {
  try {
    const agentId = req.agent.agentId;

    const agentResult = await query('SELECT * FROM agents WHERE id = $1', [agentId]);
    const agent = agentResult.rows[0];
    if (!agent) return res.status(404).json({ error: 'agent not found' });

    const venueResult = await query(
      'SELECT COUNT(*) AS cnt FROM venues WHERE agent_id = $1', [agentId]
    );
    const subResult = await query(
      'SELECT COUNT(*) AS cnt FROM agents WHERE parent_id = $1', [agentId]
    );

    // 与我相关的已付订单：agent_id=我（门店直属于我）或 master_agent_id=我（我是总代）
    // 我的抽成：agent_id=我取 agent_income，master_agent_id=我取 master_agent_income，二者可叠加
    const orderResult = await query(
      `SELECT COUNT(*) AS total_orders,
              COALESCE(SUM(
                (CASE WHEN agent_id = $1 THEN agent_income ELSE 0 END) +
                (CASE WHEN master_agent_id = $1 THEN master_agent_income ELSE 0 END)
              ), 0) AS total_income
       FROM orders
       WHERE status = 'paid' AND (agent_id = $1 OR master_agent_id = $1)`,
      [agentId]
    );

    // 已提现 + 提现中（pending 占用可提余额）
    const withdrawnResult = await query(
      `SELECT COALESCE(SUM(amount), 0) AS withdrawn
       FROM agent_withdrawals WHERE agent_id = $1 AND status IN ('pending', 'paid')`,
      [agentId]
    );

    const totalIncome = parseInt(orderResult.rows[0].total_income);
    const withdrawn = parseInt(withdrawnResult.rows[0].withdrawn);

    res.json({
      agent: { name: agent.name, code: agent.code, rate: agent.rate, parentId: agent.parent_id },
      venueCount: parseInt(venueResult.rows[0].cnt),
      subAgentCount: parseInt(subResult.rows[0].cnt),
      totalOrders: parseInt(orderResult.rows[0].total_orders),
      totalIncome,
      withdrawn,
      available: totalIncome - withdrawn,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 我的门店（金额单位：分）
// ============================================================
app.get('/api/v1/agent/venues', agentAuth, async (req, res) => {
  try {
    const result = await query(
      `SELECT v.id, v.name, v.contact, v.phone, v.created_at,
              COUNT(o.id) FILTER (WHERE o.status = 'paid') AS paid_orders,
              COALESCE(SUM(o.amount) FILTER (WHERE o.status = 'paid'), 0) AS total_amount,
              COALESCE(SUM(o.agent_income) FILTER (WHERE o.status = 'paid'), 0) AS my_income
       FROM venues v
       LEFT JOIN orders o ON o.venue_id = v.id
       WHERE v.agent_id = $1
       GROUP BY v.id
       ORDER BY v.created_at DESC`,
      [req.agent.agentId]
    );

    res.json({
      list: result.rows.map(r => ({
        id: r.id,
        name: r.name,
        contact: r.contact || '--',
        phone: r.phone || '--',
        bindTime: fmt(r.created_at),
        paidOrders: parseInt(r.paid_orders),
        totalAmount: parseInt(r.total_amount),
        myIncome: parseInt(r.my_income),
      })),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 我的下级代理（总代专用；普通代理返回空数组，金额单位：分）
// ============================================================
app.get('/api/v1/agent/subagents', agentAuth, async (req, res) => {
  try {
    const agentId = req.agent.agentId;

    const meResult = await query('SELECT parent_id FROM agents WHERE id = $1', [agentId]);
    const me = meResult.rows[0];
    if (!me) return res.status(404).json({ error: 'agent not found' });

    // parent_id 不为 NULL 的是普通代理，没有下级
    if (me.parent_id) return res.json({ list: [] });

    const result = await query(
      `SELECT a.id, a.name, a.code, a.phone, a.rate, a.created_at,
              (SELECT COUNT(*) FROM venues v WHERE v.agent_id = a.id) AS venue_count,
              (SELECT COUNT(*) FROM orders o WHERE o.agent_id = a.id AND o.status = 'paid') AS paid_orders,
              (SELECT COALESCE(SUM(o.master_agent_income), 0) FROM orders o
               WHERE o.master_agent_id = $1 AND o.agent_id = a.id AND o.status = 'paid') AS contributed_income
       FROM agents a
       WHERE a.parent_id = $1
       ORDER BY a.created_at DESC`,
      [agentId]
    );

    res.json({
      list: result.rows.map(r => ({
        id: r.id,
        name: r.name,
        code: r.code,
        phone: r.phone,
        rate: r.rate,
        joinTime: fmt(r.created_at),
        venueCount: parseInt(r.venue_count),
        paidOrders: parseInt(r.paid_orders),
        contributedIncome: parseInt(r.contributed_income),
      })),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 与我相关的订单（分页，最新在前，金额单位：分）
// ============================================================
app.get('/api/v1/agent/orders', agentAuth, async (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = Math.min(parseInt(req.query.limit) || 20, 100);
  const offset = (page - 1) * limit;

  try {
    const agentId = req.agent.agentId;

    const result = await query(
      `SELECT o.*, v.name AS venue_name,
              (CASE WHEN o.agent_id = $1 THEN o.agent_income ELSE 0 END) +
              (CASE WHEN o.master_agent_id = $1 THEN o.master_agent_income ELSE 0 END) AS my_income
       FROM orders o
       LEFT JOIN venues v ON o.venue_id = v.id
       WHERE o.agent_id = $1 OR o.master_agent_id = $1
       ORDER BY o.created_at DESC LIMIT $2 OFFSET $3`,
      [agentId, limit, offset]
    );

    const countResult = await query(
      'SELECT COUNT(*) AS total FROM orders o WHERE o.agent_id = $1 OR o.master_agent_id = $1',
      [agentId]
    );

    res.json({
      list: result.rows.map(row => ({
        id: row.id,
        orderNo: row.xunhupay_order_id || row.id,
        time: fmt(row.created_at),
        amount: row.amount,
        venueName: row.venue_name || '--',
        myIncome: parseInt(row.my_income),
        status: row.status,
      })),
      total: parseInt(countResult.rows[0].total),
      page,
      limit,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 提现记录（金额单位：分）
// ============================================================
app.get('/api/v1/agent/withdrawals', agentAuth, async (req, res) => {
  try {
    const result = await query(
      'SELECT * FROM agent_withdrawals WHERE agent_id = $1 ORDER BY created_at DESC LIMIT 100',
      [req.agent.agentId]
    );

    res.json({
      list: result.rows.map(w => ({
        id: w.id,
        amount: w.amount,
        status: w.status,
        note: w.note || '--',
        time: fmt(w.created_at),
        paidAt: w.paid_at ? fmt(w.paid_at) : '--',
      })),
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 申请提现（amount 单位：分）
// ============================================================
app.post('/api/v1/agent/withdraw', agentAuth, async (req, res) => {
  const { amount } = req.body;
  if (!Number.isInteger(amount) || amount <= 0) {
    return res.status(400).json({ error: 'amount must be a positive integer (单位:分)' });
  }
  try {
    const agentId = req.agent.agentId;

    // 可提余额 = 已付订单抽成合计 − 提现中/已提现合计（口径同 /summary）
    const incomeResult = await query(
      `SELECT COALESCE(SUM(
                (CASE WHEN agent_id = $1 THEN agent_income ELSE 0 END) +
                (CASE WHEN master_agent_id = $1 THEN master_agent_income ELSE 0 END)
              ), 0) AS total_income
       FROM orders
       WHERE status = 'paid' AND (agent_id = $1 OR master_agent_id = $1)`,
      [agentId]
    );
    const withdrawnResult = await query(
      `SELECT COALESCE(SUM(amount), 0) AS withdrawn
       FROM agent_withdrawals WHERE agent_id = $1 AND status IN ('pending', 'paid')`,
      [agentId]
    );
    const available = parseInt(incomeResult.rows[0].total_income) - parseInt(withdrawnResult.rows[0].withdrawn);

    if (amount > available) {
      return res.status(400).json({ error: 'amount exceeds available balance' });
    }

    const result = await query(
      `INSERT INTO agent_withdrawals (agent_id, amount, status) VALUES ($1, $2, 'pending') RETURNING id`,
      [agentId, amount]
    );

    res.json({ success: true, id: result.rows[0].id, message: '提现申请已提交' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 辅助
// ============================================================
function generateAgentCode() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

function fmt(d) {
  const dt = new Date(d);
  const p = (n) => String(n).padStart(2, '0');
  return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())} ${p(dt.getHours())}:${p(dt.getMinutes())}`;
}

// ============================================================
// 静态托管代理后台前端（agent-dashboard/）
// Nginx /agents/ → 3004/ 后直接可用
// ============================================================
app.use(express.static(path.join(__dirname, '../../agent-dashboard')));

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Agent API] running on port ${PORT}`);
});

module.exports = app;
