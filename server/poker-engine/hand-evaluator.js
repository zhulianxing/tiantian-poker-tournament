// poker-engine/hand-evaluator.js — 德州扑克牌型判定
'use strict';

/**
 * 手牌评级器
 * 输入：2 张底牌 + 5 张公共牌（或任意 5-7 张牌）
 * 输出：最佳 5 张组合的评级
 *
 * 牌型排名（从高到低）：
 * 9: 皇家同花顺 (Royal Flush)
 * 8: 同花顺 (Straight Flush)
 * 7: 四条 (Four of a Kind)
 * 6: 葫芦 (Full House)
 * 5: 同花 (Flush)
 * 4: 顺子 (Straight)
 * 3: 三条 (Three of a Kind)
 * 2: 两对 (Two Pair)
 * 1: 一对 (One Pair)
 * 0: 高牌 (High Card)
 */

const HAND_NAMES = [
  '高牌', '一对', '两对', '三条', '顺子',
  '同花', '葫芦', '四条', '同花顺', '皇家同花顺'
];

/**
 * 从 7 张牌中找出最佳 5 张组合
 * 返回: { rank: 0-9, name: '...', cards: [5张], kickers: [values] }
 */
function evaluateBest(holeCards, communityCards) {
  const allCards = [...holeCards, ...communityCards];
  if (allCards.length < 5) {
    // 不足 5 张，只能做部分判定
    return evaluatePartial(allCards);
  }

  // 枚举所有 C(7,5) = 21 种组合
  const combos = combinations(allCards, 5);
  let best = null;

  for (const combo of combos) {
    const result = evaluate5(combo);
    if (!best || compareHands(result, best) > 0) {
      best = result;
    }
  }

  return best;
}

/**
 * 评估 5 张牌的牌型
 */
function evaluate5(cards) {
  // 按 value 降序排列
  const sorted = [...cards].sort((a, b) => b.value - a.value);
  const values = sorted.map(c => c.value);
  const suits = sorted.map(c => c.suit);

  // 统计各 value 出现次数
  const valueCounts = {};
  for (const v of values) {
    valueCounts[v] = (valueCounts[v] || 0) + 1;
  }
  const counts = Object.entries(valueCounts)
    .map(([v, c]) => ({ value: parseInt(v), count: c }))
    .sort((a, b) => b.count - a.count || b.value - a.value);

  const isFlush = suits.every(s => s === suits[0]);
  const isStraight = checkStraight(values);
  const isRoyal = isFlush && isStraight && values[0] === 14 && values[4] === 10;

  if (isRoyal) return { rank: 9, name: HAND_NAMES[9], cards: sorted, kickers: [14] };
  if (isFlush && isStraight) return { rank: 8, name: HAND_NAMES[8], cards: sorted, kickers: [values[0]] };
  if (counts[0].count === 4) return { rank: 7, name: HAND_NAMES[7], cards: sorted, kickers: [counts[0].value, counts[1].value] };
  if (counts[0].count === 3 && counts[1].count === 2) return { rank: 6, name: HAND_NAMES[6], cards: sorted, kickers: [counts[0].value, counts[1].value] };
  if (isFlush) return { rank: 5, name: HAND_NAMES[5], cards: sorted, kickers: values };
  if (isStraight) return { rank: 4, name: HAND_NAMES[4], cards: sorted, kickers: [values[0]] };
  if (counts[0].count === 3) return { rank: 3, name: HAND_NAMES[3], cards: sorted, kickers: [counts[0].value, ...counts.slice(1).map(c => c.value)] };
  if (counts[0].count === 2 && counts[1].count === 2) return { rank: 2, name: HAND_NAMES[2], cards: sorted, kickers: [counts[0].value, counts[1].value, counts[2].value] };
  if (counts[0].count === 2) return { rank: 1, name: HAND_NAMES[1], cards: sorted, kickers: [counts[0].value, ...counts.slice(1).map(c => c.value)] };
  return { rank: 0, name: HAND_NAMES[0], cards: sorted, kickers: values };
}

/**
 * 检查是否为顺子
 * 注意 A-2-3-4-5 也是一种顺子（轮子）
 */
function checkStraight(values) {
  const unique = [...new Set(values)].sort((a, b) => b - a);
  if (unique.length < 5) return false;

  // 正常顺子
  for (let i = 0; i <= unique.length - 5; i++) {
    if (unique[i] - unique[i + 4] === 4) return true;
  }

  // A-2-3-4-5（轮子）
  if (unique.includes(14) && unique.includes(2) && unique.includes(3) && unique.includes(4) && unique.includes(5)) {
    return true;
  }

  return false;
}

/**
 * 比较两手牌大小
 * 返回: 1 = a 赢, -1 = b 赢, 0 = 平
 */
function compareHands(a, b) {
  if (a.rank !== b.rank) return a.rank > b.rank ? 1 : -1;
  // 同牌型比 kickers
  for (let i = 0; i < Math.max(a.kickers.length, b.kickers.length); i++) {
    const av = a.kickers[i] || 0;
    const bv = b.kickers[i] || 0;
    if (av !== bv) return av > bv ? 1 : -1;
  }
  return 0;
}

/**
 * 从数组中取 n 个的所有组合
 */
function combinations(arr, n) {
  if (n === 0) return [[]];
  if (arr.length < n) return [];
  const [first, ...rest] = arr;
  const withFirst = combinations(rest, n - 1).map(c => [first, ...c]);
  const withoutFirst = combinations(rest, n);
  return [...withFirst, ...withoutFirst];
}

/**
 * 不足 5 张时的部分判定
 */
function evaluatePartial(cards) {
  const sorted = [...cards].sort((a, b) => b.value - a.value);
  const values = sorted.map(c => c.value);
  const valueCounts = {};
  for (const v of values) valueCounts[v] = (valueCounts[v] || 0) + 1;
  const counts = Object.entries(valueCounts)
    .map(([v, c]) => ({ value: parseInt(v), count: c }))
    .sort((a, b) => b.count - a.count || b.value - a.value);

  if (counts[0].count === 2) return { rank: 1, name: HAND_NAMES[1], cards: sorted, kickers: [counts[0].value] };
  return { rank: 0, name: HAND_NAMES[0], cards: sorted, kickers: values };
}

module.exports = { evaluateBest, evaluate5, compareHands, HAND_NAMES };
