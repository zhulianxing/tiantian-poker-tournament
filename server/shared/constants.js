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
  getBlindLevel,
};
