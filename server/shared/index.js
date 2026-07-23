// shared/index.js — 统一导出

// 轻量 .env 加载（仓库根目录 .env，无第三方依赖；已存在的环境变量不覆盖，
// pm2 ecosystem 注入的 env 优先级保持最高）
try {
  const fs = require('fs');
  const path = require('path');
  const envFile = path.join(__dirname, '../../.env');
  if (fs.existsSync(envFile)) {
    for (const line of fs.readFileSync(envFile, 'utf8').split('\n')) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
      if (m && !line.trim().startsWith('#') && process.env[m[1]] === undefined) {
        process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
      }
    }
  }
} catch (e) {
  console.warn('[Shared] .env load skipped:', e.message);
}

const db = require('./db');
const constants = require('./constants');
const mailer = require('./mailer');

module.exports = {
  ...db,        // pool, query, getClient
  ...constants, // TOURNAMENT_STATUS, PLAYER_STATUS, etc.
  mailer,       // sendCode
  db,           // 兼容 db.pool / db.query
  constants,    // 兼容 constants.TOURNAMENT_STATUS
};
