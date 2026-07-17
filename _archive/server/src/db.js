const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'poker.db');
let db;

function initDB() {
  const fs = require('fs');
  const dir = path.dirname(DB_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

  db = new Database(DB_PATH);
  db.pragma('journal_mode = WAL');

  db.exec(`
    CREATE TABLE IF NOT EXISTS players (
      id TEXT PRIMARY KEY,
      email TEXT UNIQUE NOT NULL,
      nickname TEXT,
      avatar TEXT,
      chip_balance INTEGER DEFAULT 2000,
      created_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS tables (
      id TEXT PRIMARY KEY,
      code TEXT UNIQUE NOT NULL,
      name TEXT NOT NULL,
      status TEXT DEFAULT 'waiting',
      max_players INTEGER DEFAULT 6,
      small_blind INTEGER DEFAULT 10,
      big_blind INTEGER DEFAULT 20,
      starting_chips INTEGER DEFAULT 2000,
      blind_interval INTEGER DEFAULT 600,
      created_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS game_history (
      id TEXT PRIMARY KEY,
      table_id TEXT NOT NULL,
      winner_id TEXT,
      winner_hand TEXT,
      pot INTEGER,
      finished_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS verification_codes (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT NOT NULL,
      code TEXT NOT NULL,
      used INTEGER DEFAULT 0,
      created_at TEXT DEFAULT (datetime('now'))
    );
  `);

  return db;
}

function getDB() {
  if (!db) initDB();
  return db;
}

// --- Auth ---
function saveVerificationCode(email, code) {
  const db = getDB();
  db.prepare('INSERT INTO verification_codes (email, code) VALUES (?, ?)').run(email, code);
}

function verifyCode(email, code) {
  const db = getDB();
  // 5分钟内有效
  const row = db.prepare(`
    SELECT id FROM verification_codes 
    WHERE email = ? AND code = ? AND used = 0 
    AND datetime(created_at) > datetime('now', '-5 minutes')
    ORDER BY created_at DESC LIMIT 1
  `).get(email, code);
  if (row) {
    db.prepare('UPDATE verification_codes SET used = 1 WHERE id = ?').run(row.id);
    return true;
  }
  return false;
}

function findOrCreatePlayer(email, nickname) {
  const db = getDB();
  let player = db.prepare('SELECT * FROM players WHERE email = ?').get(email);
  if (!player) {
    const { v4: uuid } = require('uuid');
    const id = uuid();
    db.prepare('INSERT INTO players (id, email, nickname) VALUES (?, ?, ?)').run(id, email, nickname || email.split('@')[0]);
    player = db.prepare('SELECT * FROM players WHERE id = ?').get(id);
  }
  return player;
}

function getPlayer(id) {
  return getDB().prepare('SELECT * FROM players WHERE id = ?').get(id);
}

// --- Tables ---
function createTable(code, name, params = {}) {
  const db = getDB();
  const { v4: uuid } = require('uuid');
  const id = uuid();
  db.prepare(`
    INSERT INTO tables (id, code, name, max_players, small_blind, big_blind, starting_chips, blind_interval)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(id, code, name, params.maxPlayers || 6, params.smallBlind || 10, params.bigBlind || 20, params.startingChips || 2000, params.blindInterval || 600);
  return db.prepare('SELECT * FROM tables WHERE id = ?').get(id);
}

function getTableByCode(code) {
  return getDB().prepare('SELECT * FROM tables WHERE code = ?').get(code);
}

function updateTableStatus(tableId, status) {
  getDB().prepare('UPDATE tables SET status = ? WHERE id = ?').run(status, tableId);
}

module.exports = {
  initDB,
  saveVerificationCode,
  verifyCode,
  findOrCreatePlayer,
  getPlayer,
  createTable,
  getTableByCode,
  updateTableStatus,
};
