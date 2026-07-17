// shared/index.js — 统一导出
const db = require('./db');
const constants = require('./constants');

module.exports = {
  ...db,       // pool, query, getClient
  ...constants, // TOURNAMENT_STATUS, PLAYER_STATUS, etc.
  db,          // 兼容 db.pool / db.query
  constants,   // 兼容 constants.TOURNAMENT_STATUS
};
