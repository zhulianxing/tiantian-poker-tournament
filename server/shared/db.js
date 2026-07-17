// shared/db.js — PostgreSQL 连接池
const { Pool } = require('pg');

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 5432,
  database: process.env.DB_NAME || 'poker_night',
  user: process.env.DB_USER || 'poker',
  password: process.env.DB_PASSWORD || 'poker123',
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
});

pool.on('error', (err) => {
  console.error('[DB] Unexpected error on idle client', err);
});

/**
 * 执行查询，返回 rows
 */
async function query(text, params) {
  const start = Date.now();
  const res = await pool.query(text, params);
  const duration = Date.now() - start;
  if (duration > 200) {
    console.warn(`[DB] Slow query (${duration}ms):`, text.substring(0, 80));
  }
  return res;
}

/**
 * 获取客户端（事务用）
 */
async function getClient() {
  return pool.connect();
}

module.exports = { pool, query, getClient };
