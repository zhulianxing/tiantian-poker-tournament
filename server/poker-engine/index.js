// poker-engine/index.js — 统一导出
module.exports = {
  ...require('./deck'),
  ...require('./hand-evaluator'),
  SNGManager: require('./sng-manager').SNGManager,
};
