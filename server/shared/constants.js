// shared/constants.js — 全局常量

// 赛事状态
const TOURNAMENT_STATUS = {
  REGISTERING: 'registering',
  STARTED: 'started',
  FINISHED: 'finished',
  CANCELLED: 'cancelled',
};

// 玩家座位状态
const PLAYER_STATUS = {
  WAITING: 'waiting',
  PLAYING: 'playing',
  FOLDED: 'folded',
  ALLIN: 'allin',
  ELIMINATED: 'eliminated',
  SITOUT: 'sitout',
};

// 订单状态
const ORDER_STATUS = {
  PENDING: 'pending',
  PAID: 'paid',
  REFUNDED: 'refunded',
  CANCELLED: 'cancelled',
};

// 牌桌状态
const TABLE_STATUS = {
  IDLE: 'idle',
  WAITING: 'waiting',
  PLAYING: 'playing',
  FINISHED: 'finished',
};

// 操作类型
const ACTIONS = {
  FOLD: 'fold',
  CHECK: 'check',
  CALL: 'call',
  RAISE: 'raise',
  ALLIN: 'allin',
};

// 分账比例
const FEE_SPLIT = {
  PLATFORM: 30,
  VENUE: 70,
};

// SNG 默认参数
const SNG_DEFAULTS = {
  START_CHIPS: 1000,
  START_BLIND_SB: 10,
  START_BLIND_BB: 20,
  BLIND_INTERVAL: 600,    // 10 分钟
  WAIT_COUNTDOWN: 300,    // 5 分钟
  ACTION_TIMEOUT: 30,     // 30 秒
  MIN_PLAYERS: 2,
  MAX_PLAYERS: 6,
};

// 陪玩 Bot 系统：
// 等待倒计时到期时若真人不足 max_players，自动用 Bot 补足开赛（0 真人仍取消）。
// Bot 决策基于蒙特卡洛实时胜率（poker-engine/bot-ai.js），3 秒内行动。
const BOT_COMPANION = {
  ENABLED: process.env.BOT_FILL_ENABLED !== '0',          // BOT_FILL_ENABLED=0 关闭补人
  ACTION_MS: Number(process.env.BOT_ACTION_MS) || 3000,   // Bot 行动节奏
  MC_SCALE: Number(process.env.BOT_MC_SCALE) || 1,        // 蒙特卡洛迭代数倍率（调精度/性能）
};

// 盲注升级表
function getBlindLevel(level) {
  const sb = SNG_DEFAULTS.START_BLIND_SB * Math.pow(2, level - 1);
  const bb = sb * 2;
  return { level, sb, bb };
}

module.exports = {
  TOURNAMENT_STATUS,
  PLAYER_STATUS,
  ORDER_STATUS,
  TABLE_STATUS,
  ACTIONS,
  FEE_SPLIT,
  SNG_DEFAULTS,
  BOT_COMPANION,
  getBlindLevel,
};
