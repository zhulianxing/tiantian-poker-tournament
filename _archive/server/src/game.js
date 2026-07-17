// 扑克引擎 - 6人桌无限注德州扑克
const { v4: uuid } = require('uuid');
const EventEmitter = require('events');

const SUITS = ['♠', '♥', '♦', '♣'];
const RANKS = ['2','3','4','5','6','7','8','9','10','J','Q','K','A'];

class PokerEngine extends EventEmitter {
  constructor(tableCode, opts = {}) {
    super();
    this.tableCode = tableCode;
    this.maxPlayers = opts.maxPlayers || 6;
    this.startingChips = opts.startingChips || 2000;
    this.smallBlind = opts.smallBlind || 10;
    this.bigBlind = opts.bigBlind || 20;
    this.blindInterval = opts.blindInterval || 600; // seconds
    this.ante = opts.ante || 0;

    this.players = []; // { id, seatIndex, chips, bet, folded, isAllIn, nickname }
    this.deck = [];
    this.communityCards = [];
    this.pot = 0;
    this.currentBet = 0;
    this.dealerIndex = -1;
    this.currentTurnIndex = -1;
    this.handCount = 0;
    this.level = 0;
    this.lastBlindRaise = Date.now();
    this.minRaise = this.bigBlind;
    this.status = 'waiting'; // waiting, playing, showdown, finished
    this.lastAction = null;
    this.handHistory = [];

    // Timer
    this.actionTimer = null;
    this.actionTimeLimit = opts.actionTimeLimit || 30000; // 30s
  }

  // --- 牌组操作 ---
  buildDeck() {
    this.deck = [];
    for (const suit of SUITS) {
      for (const rank of RANKS) {
        this.deck.push({ rank, suit, code: rank + suit });
      }
    }
  }

  shuffle() {
    for (let i = this.deck.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [this.deck[i], this.deck[j]] = [this.deck[j], this.deck[i]];
    }
  }

  dealCard() {
    return this.deck.pop();
  }

  // --- 玩家管理 ---
  addPlayer(playerId, nickname, seatIndex) {
    if (this.players.length >= this.maxPlayers) return { ok: false, message: '牌桌已满' };
    
    // 找空座位
    const occupied = new Set(this.players.map(p => p.seatIndex));
    if (seatIndex === undefined || seatIndex === null || occupied.has(seatIndex)) {
      for (let i = 0; i < this.maxPlayers; i++) {
        if (!occupied.has(i)) { seatIndex = i; break; }
      }
    }
    if (seatIndex === undefined) return { ok: false, message: '无可用座位' };

    this.players.push({
      id: playerId,
      nickname,
      seatIndex,
      chips: this.startingChips,
      bet: 0,
      folded: false,
      isAllIn: false,
      hand: [],
      holeCards: [],
      isSittingOut: false,
    });

    this.emit('playersChanged', this.getPlayerList());
    return { ok: true, seatIndex };
  }

  removePlayer(playerId) {
    this.players = this.players.filter(p => p.id !== playerId);
    this.emit('playersChanged', this.getPlayerList());
  }

  getPlayer(id) {
    return this.players.find(p => p.id === id);
  }

  getActivePlayers() {
    return this.players.filter(p => !p.folded && !p.isSittingOut);
  }

  getPlayerList() {
    return this.players.map(p => ({
      id: p.id,
      nickname: p.nickname,
      seatIndex: p.seatIndex,
      chips: p.chips,
      folded: p.folded,
      isAllIn: p.isAllIn,
      bet: p.bet,
    }));
  }

  // --- 每个座位一个玩家 ---
  getPlayerAtSeat(seatIndex) {
    return this.players.find(p => p.seatIndex === seatIndex);
  }

  // --- 盲注管理 ---
  getCurrentBlinds() {
    const level = Math.floor((Date.now() - this.lastBlindRaise) / (this.blindInterval * 1000));
    const sb = this.smallBlind * Math.pow(2, level);
    const bb = this.bigBlind * Math.pow(2, level);
    return { sb: Math.floor(sb), bb: Math.floor(bb), level };
  }

  // --- 下一局 ---
  startNewHand() {
    if (this.getActivePlayers().length < 2) {
      if (this.getActivePlayers().length === 1) {
        this.status = 'finished';
        const winner = this.getActivePlayers()[0];
        this.emit('gameOver', { winner: { id: winner.id, nickname: winner.nickname }, pot: this.pot });
      }
      return false;
    }

    this.status = 'playing';
    this.buildDeck();
    this.shuffle();
    this.communityCards = [];
    this.pot = 0;
    this.currentBet = 0;
    this.handCount++;
    this.lastAction = null;
    this.handHistory = [];

    // 重置玩家下注状态
    for (const p of this.players) {
      p.bet = 0;
      p.folded = false;
      p.isAllIn = false;
      p.hand = [];
      p.holeCards = [];
    }

    // 选庄家
    if (this.dealerIndex < 0) {
      this.dealerIndex = Math.floor(Math.random() * this.players.length);
    } else {
      this.dealerIndex = (this.dealerIndex + 1) % this.players.length;
    }

    // 发底牌
    for (let i = 0; i < 2; i++) {
      for (const p of this.players) {
        if (!p.isSittingOut) {
          const card = this.dealCard();
          p.hand.push(card);
          p.holeCards.push(card);
        }
      }
    }

    // 找小盲大盲位置
    const active = this.getActivePlayers();
    const dealerSeat = this.players[this.dealerIndex].seatIndex;
    
    // 按座位排序
    const sortedActive = this.sortBySeat(active);
    const dealerActiveIdx = sortedActive.findIndex(p => p.seatIndex === dealerSeat);
    
    const sb = this.getCurrentBlinds();

    // 下盲注
    const sbPlayer = sortedActive[(dealerActiveIdx + 1) % sortedActive.length];
    const bbPlayer = sortedActive[(dealerActiveIdx + 2) % sortedActive.length];

    const sbAmount = Math.min(sb.sb, sbPlayer.chips);
    const bbAmount = Math.min(sb.bb, bbPlayer.chips);
    
    sbPlayer.chips -= sbAmount;
    sbPlayer.bet = sbAmount;
    sbPlayer.isAllIn = sbPlayer.chips === 0;

    bbPlayer.chips -= bbAmount;
    bbPlayer.bet = bbAmount;
    bbPlayer.isAllIn = bbPlayer.chips === 0;

    this.pot = sbAmount + bbAmount;
    this.currentBet = bbAmount;
    this.minRaise = bbAmount;

    // 第一轮行动从大盲的下家开始
    this.currentTurnIndex = (dealerActiveIdx + 3) % sortedActive.length;
    this.emit('handStarted', {
      handCount: this.handCount,
      dealer: this.getPlayerList().find(p => p.seatIndex === dealerSeat),
      blinds: { sb: sbAmount, bb: bbAmount },
      level: sb.level,
    });

    this.broadcastTableState();
    this.startActionTimer();
    return true;
  }

  sortBySeat(players) {
    return [...players].sort((a, b) => a.seatIndex - b.seatIndex);
  }

  // --- 行动 ---
  playerAction(playerId, action, amount = 0) {
    const player = this.getPlayer(playerId);
    if (!player) return { ok: false, message: '玩家不存在' };
    
    const active = this.getActivePlayers();
    const sorted = this.sortBySeat(active);
    const activeIdx = sorted.indexOf(player);
    
    // 检查轮次
    const currentPlayer = sorted[this.currentTurnIndex];
    if (!currentPlayer || currentPlayer.id !== playerId) {
      return { ok: false, message: '还没轮到你' };
    }

    // 当前下注额差
    const callAmount = this.currentBet - player.bet;

    let actionRecord = { playerId, nickname: player.nickname, action };

    switch (action) {
      case 'fold':
        player.folded = true;
        this.emit('playerFolded', { playerId: player.id, nickname: player.nickname });
        break;

      case 'check':
        if (callAmount > 0) return { ok: false, message: '不能过牌，需要跟注' };
        break;

      case 'call':
        const call = Math.min(callAmount, player.chips);
        player.chips -= call;
        player.bet += call;
        this.pot += call;
        if (player.chips === 0) player.isAllIn = true;
        actionRecord.amount = call;
        break;

      case 'raise':
      case 'bet':
        const minRaise = this.currentBet > 0 ? this.currentBet + this.minRaise : this.bigBlind;
        if (amount < minRaise && amount < player.chips) {
          return { ok: false, message: `最少加注到 ${minRaise}` };
        }
        const raiseAmount = Math.min(amount, player.chips);
        const totalBet = raiseAmount;
        const addChips = totalBet - player.bet;
        player.chips -= addChips;
        player.bet = totalBet;
        this.pot += addChips;
        if (player.chips === 0) player.isAllIn = true;
        this.currentBet = totalBet;
        this.minRaise = raiseAmount - callAmount;
        if (this.minRaise < this.bigBlind) this.minRaise = this.bigBlind;
        actionRecord.amount = totalBet;
        break;

      case 'allin':
        const allInAmount = player.chips;
        this.pot += allInAmount;
        player.bet += allInAmount;
        if (player.bet > this.currentBet) {
          this.currentBet = player.bet;
          this.minRaise = player.bet - (player.bet - allInAmount);
        }
        player.chips = 0;
        player.isAllIn = true;
        actionRecord.amount = allInAmount;
        break;

      default:
        return { ok: false, message: '无效操作' };
    }

    this.lastAction = actionRecord;
    this.handHistory.push(actionRecord);
    this.emit('action', actionRecord);
    this.clearActionTimer();

    // 检查是否只剩一个活跃玩家
    const remaining = this.getActivePlayers();
    if (remaining.length === 1) {
      return this.endHand(remaining[0]);
    }

    // 检查是否所有活着的玩家都 equalized
    const notAllIn = remaining.filter(p => !p.isAllIn);
    const allEqualized = notAllIn.every(p => p.bet === this.currentBet) && (notAllIn.length === 0 || notAllIn[0].bet === this.currentBet);
    
    if (allEqualized) {
      if (this.communityCards.length === 5) {
        return this.showdown();
      }
      this.dealCommunityCards();
    }

    // 找下一个行动者
    this.advanceTurn();
    this.broadcastTableState();
    this.startActionTimer();
    return { ok: true, actionRecord };
  }

  advanceTurn() {
    const active = this.getActivePlayers();
    if (active.length <= 1) return;

    const sorted = this.sortBySeat(active);
    let nextIdx = (this.currentTurnIndex + 1) % sorted.length;
    let safety = 0;

    while (
      (sorted[nextIdx].isAllIn || 
       sorted[nextIdx].bet === this.currentBet && this.communityCards.length > 0) &&
      safety < sorted.length * 2
    ) {
      nextIdx = (nextIdx + 1) % sorted.length;
      safety++;
    }

    this.currentTurnIndex = nextIdx;

    // 如果所有人都 equalized 了，推进
    const notAllIn = sorted.filter(p => !p.isAllIn);
    if (notAllIn.every(p => p.bet === this.currentBet) && this.communityCards.length < 5) {
      this.dealCommunityCards();
    }
  }

  dealCommunityCards() {
    // 烧一张
    this.dealCard();
    
    if (this.communityCards.length === 0) {
      // 翻牌
      this.communityCards.push(this.dealCard(), this.dealCard(), this.dealCard());
      this.emit('flop', this.communityCards);
    } else if (this.communityCards.length === 3) {
      // 转牌
      this.communityCards.push(this.dealCard());
      this.emit('turn', this.communityCards);
    } else if (this.communityCards.length === 4) {
      // 河牌
      this.communityCards.push(this.dealCard());
      this.emit('river', this.communityCards);
    }

    // 重置 currentBet 在新一轮开始
    this.currentBet = 0;
    for (const p of this.players) {
      p.bet = 0;
    }
  }

  // --- 摊牌 ---
  showdown() {
    const active = this.getActivePlayers();
    if (active.length === 0) return;
    
    // 评估手牌
    let best = { rank: -1, players: [] };
    for (const p of active) {
      const hand = this.evaluateHand(p.hand, this.communityCards);
      if (hand.rank > best.rank) {
        best = { rank: hand.rank, players: [p], description: hand.description };
      } else if (hand.rank === best.rank) {
        best.players.push(p);
      }
    }

    // 平分底池
    const share = Math.floor(this.pot / best.players.length);
    for (const p of best.players) {
      p.chips += share;
    }

    this.status = 'showdown';
    this.emit('showdown', {
      players: best.players.map(p => ({ id: p.id, nickname: p.nickname, hand: p.hand, handDescription: best.description })),
      communityCards: this.communityCards,
      pot: this.pot,
      winnerAmount: share,
    });

    // 自动下一局
    setTimeout(() => this.startNewHand(), 5000);
    this.broadcastTableState();
  }

  endHand(winner) {
    winner.chips += this.pot;
    this.status = 'showdown';
    this.emit('handWon', {
      winner: { id: winner.id, nickname: winner.nickname },
      pot: this.pot,
      communityCards: this.communityCards,
    });
    setTimeout(() => this.startNewHand(), 3000);
    this.broadcastTableState();
    return { ok: true, winner };
  }

  // --- 计时器 ---
  startActionTimer() {
    this.clearActionTimer();
    this.actionTimer = setTimeout(() => {
      // 超时自动弃牌
      const active = this.sortBySeat(this.getActivePlayers());
      if (this.currentTurnIndex < active.length) {
        const player = active[this.currentTurnIndex];
        if (player) {
          this.playerAction(player.id, 'fold');
          this.emit('timeout', { playerId: player.id, nickname: player.nickname });
        }
      }
    }, this.actionTimeLimit);
  }

  clearActionTimer() {
    if (this.actionTimer) {
      clearTimeout(this.actionTimer);
      this.actionTimer = null;
    }
  }

  // --- 广播 ---
  broadcastTableState() {
    const state = this.getTableState();
    this.emit('tableState', state);
  }

  getTableState() {
    return {
      tableCode: this.tableCode,
      status: this.status,
      handCount: this.handCount,
      players: this.getPlayerList(),
      communityCards: this.communityCards.map(c => c.code),
      pot: this.pot,
      currentBet: this.currentBet,
      currentTurn: this.currentTurnIndex >= 0 ? 
        this.sortBySeat(this.getActivePlayers())[this.currentTurnIndex]?.id : null,
      minRaise: this.minRaise,
      blinds: this.getCurrentBlinds(),
    };
  }

  // --- 手牌评估（简化版，用于快速开发）---
  evaluateHand(hole, board) {
    const cards = [...hole, ...board];
    
    // 先快检同花顺/皇家同花顺
    const cardsBySuit = {};
    for (const c of cards) {
      if (!cardsBySuit[c.suit]) cardsBySuit[c.suit] = [];
      cardsBySuit[c.suit].push(c);
    }
    
    // 同花
    for (const suit in cardsBySuit) {
      if (cardsBySuit[suit].length >= 5) {
        const suited = cardsBySuit[suit].map(c => RANKS.indexOf(c.rank));
        suited.sort((a, b) => b - a);
        
        // 检查顺子
        for (let i = 0; i <= suited.length - 5; i++) {
          if (suited[i] - suited[i + 4] === 4) {
            return { rank: 9, description: '同花顺', value: suited[i] };
          }
        }
        // A-5 同花顺
        if (suited[0] === 12 && suited.includes(3) && suited.includes(2) && suited.includes(1) && suited.includes(0)) {
          return { rank: 9, description: '同花顺', value: 3 };
        }
        
        return { rank: 6, description: '同花', value: suited[0] };
      }
    }
    
    // 统计点数
    const rankCounts = {};
    for (const c of cards) {
      const idx = RANKS.indexOf(c.rank);
      rankCounts[idx] = (rankCounts[idx] || 0) + 1;
    }
    
    const entries = Object.entries(rankCounts).map(([k, v]) => ({ rank: parseInt(k), count: v }));
    entries.sort((a, b) => b.count - a.count || b.rank - a.rank);
    
    // 四条 + 葫芦
    if (entries[0].count === 4) return { rank: 8, description: '四条', value: entries[0].rank };
    if (entries[0].count === 3 && entries.length > 1 && entries[1].count >= 2) {
      return { rank: 7, description: '葫芦', value: entries[0].rank };
    }
    
    // 顺子
    const uniqueRanks = [...new Set(cards.map(c => RANKS.indexOf(c.rank)))].sort((a, b) => b - a);
    for (let i = 0; i <= uniqueRanks.length - 5; i++) {
      if (uniqueRanks[i] - uniqueRanks[i + 4] === 4) {
        return { rank: 5, description: '顺子', value: uniqueRanks[i] };
      }
    }
    // A-5 顺子
    if (uniqueRanks[0] === 12 && uniqueRanks.includes(3) && uniqueRanks.includes(2) && uniqueRanks.includes(1) && uniqueRanks.includes(0)) {
      return { rank: 5, description: '顺子', value: 3 };
    }
    
    // 三条
    if (entries[0].count === 3) return { rank: 4, description: '三条', value: entries[0].rank };
    
    // 两对
    if (entries[0].count === 2 && entries.length > 1 && entries[1].count === 2) {
      return { rank: 3, description: '两对', value: entries[0].rank, secondValue: entries[1].rank };
    }
    
    // 一对
    if (entries[0].count === 2) return { rank: 2, description: '一对', value: entries[0].rank };
    
    // 高牌
    return { rank: 1, description: '高牌', value: entries[0].rank };
  }
}

module.exports = PokerEngine;
