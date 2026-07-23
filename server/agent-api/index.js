// agent-api/index.js — 代理后台 API (Port 3004)
'use strict';

const path = require('path');
const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const { query, mailer } = require('@poker-night/shared');
const { sendCode } = mailer;

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
// 发送邮箱验证码（purpose: 'login' | 'register'，与玩家端同一套 email_codes）
// ============================================================
app.post('/api/v1/agent/send-code', async (req, res) => {
  const { email, purpose } = req.body;
  if (!email || !purpose) return res.status(400).json({ error: 'missing fields' });
  if (!['login', 'register'].includes(purpose)) return res.status(400).json({ error: 'invalid purpose' });

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) return res.status(400).json({ error: 'invalid email format' });

  try {
    // 注册：邮箱不能已被代理占用；登录：邮箱必须属于某个 active 代理
    if (purpose === 'register') {
      const exist = await query('SELECT id FROM agents WHERE email = $1', [email]);
      if (exist.rows.length > 0) return res.status(409).json({ error: 'email already registered' });
    }
    if (purpose === 'login') {
      const exist = await query(`SELECT id FROM agents WHERE email = $1 AND status = 'active'`, [email]);
      if (exist.rows.length === 0) return res.status(404).json({ error: 'email not found' });
    }

    // 防暴力：同一邮箱60秒内只能发一次
    const recent = await query(
      `SELECT created_at FROM email_codes WHERE email = $1 ORDER BY created_at DESC LIMIT 1`,
      [email]
    );
    if (recent.rows.length > 0) {
      const elapsed = Date.now() - new Date(recent.rows[0].created_at).getTime();
      if (elapsed < 60000) {
        const waitSec = Math.ceil((60000 - elapsed) / 1000);
        return res.status(429).json({ error: `too many requests, try again in ${waitSec}s` });
      }
    }

    // 生成6位验证码，存库并发送
    const code = String(Math.floor(100000 + Math.random() * 900000));
    await query('INSERT INTO email_codes (email, code, purpose) VALUES ($1, $2, $3)', [email, code, purpose]);
    await sendCode(email, code, purpose);

    res.json({ success: true });
  } catch (err) {
    console.error('[Agent] send-code error:', err);
    res.status(500).json({ error: 'failed to send code' });
  }
});

// ============================================================
// 代理登录（邮箱 + 验证码，免密码）
// 注册不开放免费通道：统一走 payment-svc agent-signup（付费开通）
// ============================================================
app.post('/api/v1/agent/login', async (req, res) => {
  const { email, code } = req.body;
  if (!email || !code) return res.status(400).json({ error: 'missing fields' });
  try {
    const codeResult = await query(
      `SELECT * FROM email_codes
       WHERE email = $1 AND code = $2 AND purpose = 'login'
         AND used = FALSE AND expires_at > NOW()
       ORDER BY created_at DESC LIMIT 1`,
      [email, code]
    );
    if (codeResult.rows.length === 0) return res.status(400).json({ error: 'invalid or expired code' });

    const result = await query(`SELECT * FROM agents WHERE email = $1 AND status = 'active'`, [email]);
    const agent = result.rows[0];
    if (!agent) return res.status(404).json({ error: 'agent not found' });

    await query('UPDATE email_codes SET used = TRUE WHERE id = $1', [codeResult.rows[0].id]);

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
      `SELECT a.id, a.name, a.code, a.email, a.rate, a.created_at,
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
        email: r.email,
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
