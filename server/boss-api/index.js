// boss-api/index.js — BOSS 平台管理端 API (Port 3005)
// 平台超管视角：代理商管理（列表/详情/rate/冻结）、提现审核、门店换绑、全局概览
// 账号不走玩家/代理体系：BOSS_USER / BOSS_PASS 环境变量单账号，JWT 1 天有效
'use strict';

const path = require('path');
const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const { query } = require('@poker-night/shared');

const app = express();
const PORT = process.env.BOSS_PORT || 3005;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';
const BOSS_USER = process.env.BOSS_USER || '';
const BOSS_PASS = process.env.BOSS_PASS || '';

app.use(cors());
app.use(express.json());

// 中间件：BOSS 鉴权
function bossAuth(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'no token' });
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (payload.boss !== true) return res.status(401).json({ error: 'not a boss token' });
    req.boss = payload;
    next();
  } catch {
    res.status(401).json({ error: 'invalid token' });
  }
}

app.get('/health', (req, res) => res.json({ ok: true, service: 'boss-api' }));

// ============================================================
// 登录（环境变量单账号；未配置则关闭登录）
// ============================================================
app.post('/api/v1/boss/login', async (req, res) => {
  if (!BOSS_USER || !BOSS_PASS) return res.status(503).json({ error: 'boss account not configured' });
  const { username, password } = req.body || {};
  if (username !== BOSS_USER || password !== BOSS_PASS) {
    return res.status(401).json({ error: 'invalid credentials' });
  }
  const token = jwt.sign({ boss: true, name: username }, JWT_SECRET, { expiresIn: '1d' });
  res.json({ token, name: username });
});

// ============================================================
// 全局概览（金额单位：分；只计 paid 订单，与代理/商户口径一致）
// ============================================================
app.get('/api/v1/boss/overview', bossAuth, async (req, res) => {
  try {
    const agents = await query(
      `SELECT COUNT(*) AS total,
              COUNT(*) FILTER (WHERE parent_id IS NULL) AS masters,
              COUNT(*) FILTER (WHERE status = 'active') AS active
       FROM agents`, []
    );
    const venues = await query('SELECT COUNT(*) AS total FROM venues', []);
    const orders = await query(
      `SELECT COUNT(*) AS paid_count,
              COALESCE(SUM(amount), 0) AS gmv,
              COALESCE(SUM(platform_fee), 0) AS platform_income,
              COALESCE(SUM(venue_income), 0) AS venue_income,
              COALESCE(SUM(agent_income + master_agent_income), 0) AS agent_commission
       FROM orders WHERE status = 'paid'`, []
    );
    const today = await query(
      `SELECT COUNT(*) AS paid_count, COALESCE(SUM(amount), 0) AS gmv
       FROM orders WHERE status = 'paid' AND COALESCE(paid_at, created_at) >= CURRENT_DATE`, []
    );
    const wd = await query(
      `SELECT COUNT(*) AS pending_count, COALESCE(SUM(amount), 0) AS pending_amount
       FROM agent_withdrawals WHERE status = 'pending'`, []
    );
    res.json({
      agents: agents.rows[0],
      venues: venues.rows[0],
      orders: orders.rows[0],
      today: today.rows[0],
      withdrawals: wd.rows[0],
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 代理商列表（两级扁平返回，前端按 parent_id 分组）
// ============================================================
app.get('/api/v1/boss/agents', bossAuth, async (req, res) => {
  try {
    const r = await query(
      `SELECT a.id, a.code, a.name, a.email, a.rate, a.status, a.parent_id, a.created_at,
              p.code AS parent_code, p.name AS parent_name,
              (SELECT COUNT(*) FROM agents s WHERE s.parent_id = a.id) AS sub_count,
              (SELECT COUNT(*) FROM venues v WHERE v.agent_id = a.id) AS venue_count,
              (SELECT COALESCE(SUM(o.agent_income), 0) FROM orders o
                WHERE o.agent_id = a.id AND o.status = 'paid')
            + (SELECT COALESCE(SUM(o2.master_agent_income), 0) FROM orders o2
                WHERE o2.master_agent_id = a.id AND o2.status = 'paid') AS commission_total
       FROM agents a LEFT JOIN agents p ON p.id = a.parent_id
       ORDER BY a.parent_id NULLS FIRST, a.created_at`, []
    );
    res.json({ agents: r.rows });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 代理商详情：档案 + 下级 + 名下门店 + 佣金 + 近期提现/订单
// ============================================================
app.get('/api/v1/boss/agents/:id', bossAuth, async (req, res) => {
  try {
    const a = await query(
      `SELECT a.id, a.code, a.name, a.email, a.rate, a.status, a.parent_id, a.created_at,
              p.code AS parent_code, p.name AS parent_name
       FROM agents a LEFT JOIN agents p ON p.id = a.parent_id WHERE a.id = $1`,
      [req.params.id]
    );
    if (a.rows.length === 0) return res.status(404).json({ error: 'agent not found' });

    const subs = await query(
      `SELECT id, code, name, email, rate, status, created_at FROM agents WHERE parent_id = $1 ORDER BY created_at`,
      [req.params.id]
    );
    const venues = await query(
      `SELECT v.id, v.name,
              (SELECT COUNT(*) FROM tables t WHERE t.venue_id = v.id) AS table_count,
              (SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.venue_id = v.id AND o.status = 'paid') AS gmv_total
       FROM venues v WHERE v.agent_id = $1 ORDER BY v.created_at`,
      [req.params.id]
    );
    const commission = await query(
      `SELECT (SELECT COALESCE(SUM(o.agent_income), 0) FROM orders o WHERE o.agent_id = $1 AND o.status = 'paid')
            + (SELECT COALESCE(SUM(o2.master_agent_income), 0) FROM orders o2 WHERE o2.master_agent_id = $1 AND o2.status = 'paid') AS total`,
      [req.params.id]
    );
    const withdrawals = await query(
      `SELECT id, amount, status, note, created_at, paid_at FROM agent_withdrawals
       WHERE agent_id = $1 ORDER BY created_at DESC LIMIT 20`,
      [req.params.id]
    );
    const orders = await query(
      `SELECT o.id, o.amount, o.agent_income, o.master_agent_income, o.status, o.payment_method,
              COALESCE(o.paid_at, o.created_at) AS at, v.name AS venue_name
       FROM orders o LEFT JOIN venues v ON v.id = o.venue_id
       WHERE (o.agent_id = $1 OR o.master_agent_id = $1) AND o.status = 'paid'
       ORDER BY at DESC LIMIT 20`,
      [req.params.id]
    );
    res.json({
      agent: a.rows[0],
      subagents: subs.rows,
      venues: venues.rows,
      commission_total: commission.rows[0].total,
      withdrawals: withdrawals.rows,
      orders: orders.rows,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 调整代理商：rate（0-70）/ status（active|frozen）
// ============================================================
app.patch('/api/v1/boss/agents/:id', bossAuth, async (req, res) => {
  const { rate, status } = req.body || {};
  const sets = [];
  const vals = [];
  if (rate !== undefined) {
    const r = Number(rate);
    if (!Number.isInteger(r) || r < 0 || r > 70) return res.status(400).json({ error: 'rate must be integer 0-70' });
    vals.push(r); sets.push(`rate = $${vals.length}`);
  }
  if (status !== undefined) {
    if (!['active', 'frozen'].includes(status)) return res.status(400).json({ error: 'status must be active|frozen' });
    vals.push(status); sets.push(`status = $${vals.length}`);
  }
  if (sets.length === 0) return res.status(400).json({ error: 'nothing to update' });
  try {
    vals.push(req.params.id);
    const r = await query(
      `UPDATE agents SET ${sets.join(', ')} WHERE id = $${vals.length}
       RETURNING id, code, name, rate, status`,
      vals
    );
    if (r.rows.length === 0) return res.status(404).json({ error: 'agent not found' });
    console.log(`[BOSS] agent ${r.rows[0].code} updated: ${sets.join(', ')}`);
    res.json({ agent: r.rows[0] });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 提现审核列表（默认 pending 优先）
// ============================================================
app.get('/api/v1/boss/withdrawals', bossAuth, async (req, res) => {
  try {
    const status = req.query.status;
    const where = status ? 'WHERE w.status = $1' : '';
    const vals = status ? [status] : [];
    const r = await query(
      `SELECT w.id, w.amount, w.status, w.note, w.created_at, w.paid_at,
              a.id AS agent_id, a.code AS agent_code, a.name AS agent_name, a.email AS agent_email
       FROM agent_withdrawals w JOIN agents a ON a.id = w.agent_id
       ${where}
       ORDER BY (w.status = 'pending') DESC, w.created_at DESC LIMIT 200`,
      vals
    );
    res.json({ withdrawals: r.rows });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 提现审核：paid（已线下打款）/ reject（驳回）
// ============================================================
app.post('/api/v1/boss/withdrawals/:id/review', bossAuth, async (req, res) => {
  const { action, note } = req.body || {};
  if (!['paid', 'reject'].includes(action)) return res.status(400).json({ error: 'action must be paid|reject' });
  try {
    const cur = await query('SELECT * FROM agent_withdrawals WHERE id = $1', [req.params.id]);
    const w = cur.rows[0];
    if (!w) return res.status(404).json({ error: 'withdrawal not found' });
    if (w.status !== 'pending') return res.status(409).json({ error: `already ${w.status}` });

    const newStatus = action === 'paid' ? 'paid' : 'rejected';
    const r = await query(
      `UPDATE agent_withdrawals SET status = $1::varchar, note = COALESCE($2::text, note),
              paid_at = CASE WHEN $1::varchar = 'paid' THEN NOW() ELSE paid_at END
       WHERE id = $3::uuid RETURNING *`,
      [newStatus, note || null, req.params.id]
    );
    console.log(`[BOSS] withdrawal ${req.params.id} reviewed: ${newStatus}${note ? ' (' + note + ')' : ''}`);
    res.json({ withdrawal: r.rows[0] });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 门店列表（含代理绑定与累计 GMV）
// ============================================================
app.get('/api/v1/boss/venues', bossAuth, async (req, res) => {
  try {
    const r = await query(
      `SELECT v.id, v.name, v.agent_id, v.created_at,
              a.code AS agent_code, a.name AS agent_name,
              (SELECT COUNT(*) FROM tables t WHERE t.venue_id = v.id) AS table_count,
              (SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.venue_id = v.id AND o.status = 'paid') AS gmv_total
       FROM venues v LEFT JOIN agents a ON a.id = v.agent_id
       ORDER BY v.created_at`, []
    );
    res.json({ venues: r.rows });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 门店换绑/解绑代理：{ agentId: uuid | null }
// ============================================================
app.patch('/api/v1/boss/venues/:id', bossAuth, async (req, res) => {
  const { agentId } = req.body || {};
  if (agentId !== null && agentId !== undefined && typeof agentId !== 'string') {
    return res.status(400).json({ error: 'agentId must be uuid or null' });
  }
  try {
    if (agentId) {
      const a = await query(`SELECT id FROM agents WHERE id = $1 AND status = 'active'`, [agentId]);
      if (a.rows.length === 0) return res.status(404).json({ error: 'agent not found or not active' });
    }
    const r = await query(
      `UPDATE venues SET agent_id = $1 WHERE id = $2 RETURNING id, name, agent_id`,
      [agentId || null, req.params.id]
    );
    if (r.rows.length === 0) return res.status(404).json({ error: 'venue not found' });
    console.log(`[BOSS] venue ${r.rows[0].id} agent → ${agentId || '(unbound)'}`);
    res.json({ venue: r.rows[0] });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 静态托管 BOSS 前端（boss-dashboard/）
// Nginx /boss/ → 3005/ 后直接可用
// ============================================================
app.use(express.static(path.join(__dirname, '../../boss-dashboard')));

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[BOSS API] running on port ${PORT}`);
});

module.exports = app;
