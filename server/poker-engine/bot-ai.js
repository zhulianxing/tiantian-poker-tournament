// poker-engine/bot-ai.js — 陪玩 Bot 决策大脑
// 蒙特卡洛胜率（equity）实时计算 + 底池赔率决策，单次决策远低于 3 秒预算
'use strict';

const { createDeck } = require('./deck');
const { evaluateBest, compareHands } = require('./hand-evaluator');
const { ACTIONS, BOT_COMPANION } = require('../shared/constants');

// 各阶段模拟次数（翻牌后需要模拟的未知牌更少，可加大次数提高精度）
// 全局倍率：BOT_MC_SCALE（shared/constants.js BOT_COMPANION）
const _scale = BOT_COMPANION.MC_SCALE;
const MC_ITERATIONS = {
  preflop: Math.round(400 * _scale),
  flop: Math.round(500 * _scale),
  turn: Math.round(600 * _scale),
  river: Math.round(800 * _scale),
};

function cardKey(c) { return c.suit + c.rank; }

/**
 * 蒙特卡洛胜率：固定我的底牌 + 已公开公共牌，
 * 从剩余牌堆随机采样「对手底牌 + 未发出的公共牌」，统计赢/平概率。
 * 只使用公开信息（自己的牌 + 已翻公共牌），不读取引擎未公开数据。
 *
 * @param myHole    我的 2 张底牌 [{suit,rank,value}]
 * @param revealed  已公开公共牌（0/3/4/5 张）
 * @param numOpponents 未弃牌对手数（含 allin，不含自己）
 * @param stage     preflop/flop/turn/river
 * @returns 0~1 的胜率（平局按 0.5 计）
 */
function monteCarloEquity(myHole, revealed, numOpponents, stage) {
  const iterations = MC_ITERATIONS[stage] || 500;
  const known = new Set([...myHole, ...revealed].map(cardKey));
  const pool = createDeck().filter(c => !known.has(cardKey(c)));
  const boardMissing = Math.max(0, 5 - revealed.length);
  const need = numOpponents * 2 + boardMissing;
  let win = 0, tie = 0;

  for (let i = 0; i < iterations; i++) {
    // 部分 Fisher-Yates：从 pool 无放回抽 need 张
    const idx = pool.map((_, j) => j);
    for (let j = 0; j < need; j++) {
      const k = j + Math.floor(Math.random() * (idx.length - j));
      const t = idx[j]; idx[j] = idx[k]; idx[k] = t;
    }
    const sample = idx.slice(0, need).map(j => pool[j]);
    const board = [...revealed, ...sample.slice(numOpponents * 2)];
    const myHand = evaluateBest(myHole, board);

    let beaten = false, allTied = true;
    for (let o = 0; o < numOpponents; o++) {
      const oppHand = evaluateBest([sample[o * 2], sample[o * 2 + 1]], board);
      const cmp = compareHands(myHand, oppHand);
      if (cmp < 0) { beaten = true; break; }
      if (cmp > 0) allTied = false;
    }
    if (!beaten) { if (allTied) tie++; else win++; }
  }
  return (win + tie * 0.5) / iterations;
}

/**
 * 基于胜率的决策。
 *
 * @param ctx { holeCards, revealed, stage, pot, currentBet, alreadyBet, chips, bb, numOpponents }
 * @returns { action, amount, equity, reason }
 *   amount 为「加注到的总额」（引擎 RAISE 语义），check/fold/allin 时为 0
 */
function decideBotAction(ctx) {
  const { holeCards, revealed, stage, pot, currentBet, alreadyBet, chips, bb, numOpponents } = ctx;
  const toCall = Math.max(0, currentBet - alreadyBet);
  const equity = monteCarloEquity(holeCards, revealed, Math.max(1, numOpponents), stage);
  const potOdds = toCall > 0 ? toCall / (pot + toCall) : 0;
  const r = Math.random();
  // 翻前进攻修正：多人池里纯胜率低估了加注价值（加注能打薄对手池、提升胜率实现率），
  // 仅用于「是否加注」的阈值判断，跟注/弃牌仍用原始胜率
  const aggEquity = stage === 'preflop' ? Math.min(1, equity + 0.18) : equity;

  // 加注额工具：加到 currentBet + scale*pot，收敛在 [最小加注, 当前筹码] 内。
  // 注意引擎 RAISE 语义：amount 是「本轮新 currentBet 总额」，且从现有筹码全额扣，
  // 所以 amount 必须 ≤ chips（不能包含本轮已下注部分）
  const minRaiseTo = Math.max(currentBet * 2, bb);
  const raiseTo = (scale) => {
    let v = currentBet + Math.ceil(pot * scale);
    v = Math.max(v, minRaiseTo);
    return Math.min(v, chips);
  };
  const canRaise = chips > currentBet; // 筹码必须能覆盖「加注到 currentBet 以上」

  if (toCall === 0) {
    // 无需跟注：强牌下注价值，中牌控制底池，弱牌过牌（少量诈唬）
    if (aggEquity >= 0.78) {
      const amount = raiseTo(0.75 + r * 0.5);
      if (amount > currentBet) return { action: ACTIONS.RAISE, amount, equity, reason: 'value-strong' };
    }
    if (aggEquity >= 0.58 && r < 0.65) {
      const amount = raiseTo(0.45 + r * 0.25);
      if (amount > currentBet) return { action: ACTIONS.RAISE, amount, equity, reason: 'value-mid' };
    }
    if (equity < 0.3 && r < 0.07 && chips > minRaiseTo) {
      const amount = raiseTo(0.5);
      if (amount > currentBet) return { action: ACTIONS.RAISE, amount, equity, reason: 'bluff' };
    }
    return { action: ACTIONS.CHECK, amount: 0, equity, reason: 'control' };
  }

  // 需要跟注：胜率 vs 底池赔率
  if (toCall >= chips) {
    // 只能全下跟注或弃牌：要求胜率明显覆盖全下赔率
    if (equity >= Math.max(potOdds * 1.15, 0.42)) {
      return { action: ACTIONS.CALL, amount: toCall, equity, reason: 'commit-allin-call' };
    }
    return { action: ACTIONS.FOLD, amount: 0, equity, reason: 'odds-allin-fold' };
  }

  if (aggEquity >= 0.82 && canRaise) {
    // 超强牌：加注打价值，接近全下就直接推
    const amount = raiseTo(0.8 + r * 0.4);
    if (amount >= chips * 0.7) return { action: ACTIONS.ALLIN, amount: 0, equity, reason: 'monster-shove' };
    if (amount > currentBet) return { action: ACTIONS.RAISE, amount, equity, reason: 'monster-raise' };
  }

  if (aggEquity >= Math.max(potOdds * 1.6, 0.55) && r < 0.7 && canRaise) {
    const amount = raiseTo(0.5 + r * 0.25);
    if (amount > currentBet) return { action: ACTIONS.RAISE, amount, equity, reason: 'value-raise' };
  }

  if (equity >= potOdds * 0.95) {
    return { action: ACTIONS.CALL, amount: toCall, equity, reason: 'odds-call' };
  }

  // 赔率不够但差距很小且注额不大，偶尔跟注（避免被轻易诈唬打盖）
  if (equity >= potOdds * 0.75 && toCall <= bb * 3 && r < 0.35) {
    return { action: ACTIONS.CALL, amount: toCall, equity, reason: 'light-call' };
  }

  return { action: ACTIONS.FOLD, amount: 0, equity, reason: 'odds-fold' };
}

module.exports = { monteCarloEquity, decideBotAction };
