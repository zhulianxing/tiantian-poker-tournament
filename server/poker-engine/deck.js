// poker-engine/deck.js — 一副牌 + 洗牌
'use strict';

const SUITS = ['♠', '♥', '♦', '♣'];
const RANKS = ['2','3','4','5','6','7','8','9','10','J','Q','K','A'];
const RANK_VALUES = {
  '2':2,'3':3,'4':4,'5':5,'6':6,'7':7,'8':8,'9':9,'10':10,
  'J':11,'Q':12,'K':13,'A':14
};

/**
 * Card: { suit: '♠', rank: 'A', value: 14 }
 */
function createCard(suit, rank) {
  return { suit, rank, value: RANK_VALUES[rank] };
}

/**
 * 生成标准 52 张牌
 */
function createDeck() {
  const deck = [];
  for (const suit of SUITS) {
    for (const rank of RANKS) {
      deck.push(createCard(suit, rank));
    }
  }
  return deck;
}

/**
 * Fisher-Yates 洗牌
 */
function shuffle(deck) {
  const arr = [...deck];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

/**
 * 从牌堆顶发 n 张牌
 */
function deal(deck, n) {
  return deck.splice(0, n);
}

module.exports = { SUITS, RANKS, RANK_VALUES, createCard, createDeck, shuffle, deal };
