// poker-engine/sng-manager.js — SNG 赛事管理器
'use strict';

const { createDeck, shuffle, deal } = require('./deck');
const { evaluateBest, compareHands } = require('./hand-evaluator');
const { SNG_DEFAULTS, TOURNAMENT_STATUS, PLAYER_STATUS, ACTIONS } = require('../shared/constants');

/**
 * SNG 赛事状态机
 *
 * 状态流转：
 * registering → started → finished
 *              ↘ cancelled (倒计时结束 < 2 人)
 *
 * 每手牌流程：
 * 1. 新一手开始 → 收盲注 → 发底牌 → 翻前下注 → 翻牌 → 转牌 → 河牌 → 摊牌
 * 2. 每手结束后检查淘汰，检查是否只剩 1 人
 */
class SNGManager {
  constructor(tournament, players) {
    this.tournament = tournament;
    this.players = players; // [{id, seatIndex, chipCount, status, nickname}]
    this.handNumber = 0;
    this.blindLevel = 1;
    this.blindTimer = null;
    this.currentHand = null;
    this.dealerIndex = 0; // 庄家位置（座位号）
    this.actionTimer = null; // 操作超时定时器
    this.actionTimeout = (tournament.action_timeout || SNG_DEFAULTS.ACTION_TIMEOUT) * 1000;
  }

  /**
   * 启动赛事
   */
  start() {
    // 所有非淘汰玩家改为 playing
    for (const p of this.players) {
      if (p.status !== PLAYER_STATUS.ELIMINATED && p.status !== PLAYER_STATUS.FOLDED) {
        p.status = PLAYER_STATUS.PLAYING;
      }
    }

    // 启动盲注定时器
    this.startBlindTimer();

    // 开始第一手
    try {
      this.startNewHand();
    } catch (e) {
      console.error('[SNG] startNewHand error:', e.message, e.stack);
    }
  }

  /**
   * 盲注升级定时器
   */
  startBlindTimer() {
    const interval = this.tournament.blind_interval || SNG_DEFAULTS.BLIND_INTERVAL;
    this.blindTimer = setInterval(() => {
      this.blindLevel++;
      console.log(`[SNG] Blind level up to ${this.blindLevel}`);
      this.emit('blind_level_up', {
        level: this.blindLevel,
        sb: SNG_DEFAULTS.START_BLIND_SB * Math.pow(2, this.blindLevel - 1),
        bb: SNG_DEFAULTS.START_BLIND_BB * Math.pow(2, this.blindLevel - 1),
      });
    }, interval * 1000);
  }

  /**
   * 获取当前盲注
   */
  getCurrentBlinds() {
    const base = this.tournament.start_blind || SNG_DEFAULTS.START_BLIND_SB;
    const sb = base * Math.pow(2, this.blindLevel - 1);
    const bb = sb * 2;
    return { sb, bb };
  }

  /**
   * 开始新一手牌
   */
  startNewHand() {
    // 检查赛事是否结束
    const activePlayers = this.getActivePlayers();
    if (activePlayers.length <= 1) {
      this.finish();
      return;
    }

    this.handNumber++;

    // 移动庄家位
    if (this.handNumber > 1) {
      this.dealerIndex = this.nextActiveSeat(this.dealerIndex);
    }

    const { sb, bb } = this.getCurrentBlinds();

    // 洗牌
    const deck = shuffle(createDeck());

    // 发底牌（每人 2 张）
    const holeCards = {};
    for (const p of activePlayers) {
      holeCards[p.id] = deal(deck, 2);
    }

    // 发公共牌（3+1+1）
    const flop = deal(deck, 3);
    const turn = deal(deck, 1);
    const river = deal(deck, 1);
    const communityCards = [...flop, ...turn, ...river];

    // SB/BB 位置（收盲注前计算）
    const sbIndex = this.nextActiveSeat(this.dealerIndex);
    const bbIndex = this.nextActiveSeat(sbIndex);

    // 先创建 currentHand，再收盲注（collectBlind 会修改 pot 和玩家状态）
    this.currentHand = {
      handNumber: this.handNumber,
      deck,
      holeCards,
      communityCards,
      revealedCommunity: [],
      stage: 'preflop', // preflop → flop → turn → river → showdown
      pot: 0,
      currentBet: bb,
      minRaise: bb,
      actingIndex: -1, // 收盲注后重新计算
      lastRaiserIndex: bbIndex,
      actions: [],
    };

    this.collectBlind(sbIndex, sb);
    this.collectBlind(bbIndex, bb);

    // 收盲注后重新计算第一个行动者（可能 SB/BB 收盲注后 allin）
    let actingIndex = this.nextActiveSeat(bbIndex);

    // 如果没有可行动玩家（全部 allin），直接发完公共牌到摊牌
    if (actingIndex === -1) {
      this.currentHand.actingIndex = -1;
      this.emit('new_hand', {
        handNumber: this.handNumber,
        dealerIndex: this.dealerIndex,
        sbIndex,
        bbIndex,
        pot: this.currentHand.pot,
        seats: this.getSeatsSnapshot(),
      });

      for (const [pid, cards] of Object.entries(holeCards)) {
        this.emit('hole_cards', { playerId: pid, cards });
      }

      this.emit('hand_started', {
        handNumber: this.handNumber,
        stage: 'preflop',
        pot: this.currentHand.pot,
        currentBet: bb,
        actingIndex: -1,
        seats: this.getSeatsSnapshot(),
        dealerIndex: this.dealerIndex,
        sbIndex,
        bbIndex,
      });

      // 全员 allin，直接 run out board
      setTimeout(() => this.runOutBoard(), 1000);
      return;
    }

    this.currentHand.actingIndex = actingIndex;

    this.emit('new_hand', {
      handNumber: this.handNumber,
      dealerIndex: this.dealerIndex,
      sbIndex,
      bbIndex,
      pot: this.currentHand.pot,
      seats: this.getSeatsSnapshot(),
    });

    // 发送底牌给各玩家（点对点）
    for (const [pid, cards] of Object.entries(holeCards)) {
      this.emit('hole_cards', { playerId: pid, cards });
    }

    // 通知大屏开始新一手
    this.emit('hand_started', {
      handNumber: this.handNumber,
      stage: 'preflop',
      pot: this.currentHand.pot,
      currentBet: bb,
      actingIndex,
      seats: this.getSeatsSnapshot(),
      dealerIndex: this.dealerIndex,
      sbIndex,
      bbIndex,
    });

    // 启动操作倒计时
    this.startActionTimer(actingIndex);
  }

  /**
   * 收盲注
   */
  collectBlind(seatIndex, amount) {
    const player = this.players.find(p => p.seatIndex === seatIndex);
    if (!player) return;
    const actual = Math.min(amount, player.chipCount);
    player.chipCount -= actual;
    this.currentHand.pot += actual;
    this.currentHand.actions.push({
      playerId: player.id,
      action: 'bet',
      amount: actual,
      success: true,
    });
    if (player.chipCount === 0) {
      player.status = PLAYER_STATUS.ALLIN;
    }
  }

  /**
   * 找下一个活跃座位（PLAYING 状态，可以行动的）
   */
  nextActiveSeat(fromIndex) {
    const seats = this.players
      .filter(p => p.status === PLAYER_STATUS.PLAYING)
      .map(p => p.seatIndex)
      .sort((a, b) => a - b);
    if (seats.length === 0) return -1;
    for (const s of seats) {
      if (s > fromIndex) return s;
    }
    return seats[0]; // 绕回
  }

  /**
   * 获取活跃玩家（PLAYING + ALLIN）
   */
  getActivePlayers() {
    return this.players.filter(p =>
      p.status === PLAYER_STATUS.PLAYING || p.status === PLAYER_STATUS.ALLIN
    );
  }

  /**
   * 获取未弃牌玩家
   */
  getNonFoldedPlayers() {
    return this.players.filter(p =>
      p.status !== PLAYER_STATUS.FOLDED && p.status !== PLAYER_STATUS.ELIMINATED
    );
  }

  /**
   * 获取当前座位快照（用于事件广播）
   */
  getSeatsSnapshot() {
    return this.players.map(p => ({
      seatIndex: p.seatIndex,
      playerId: p.id,
      nickname: p.nickname || p.id.substring(0, 8),
      chipCount: p.chipCount,
      status: p.status,
      currentBet: this.currentHand && this.currentHand.actions
        ? this.currentHand.actions
          .filter(a => a.playerId === p.id)
          .reduce((sum, a) => sum + (a.amount || 0), 0)
        : 0,
    }));
  }

  /**
   * 处理玩家操作
   */
  handleAction(playerId, action, amount = 0) {
    const hand = this.currentHand;
    if (!hand) return { error: 'no active hand' };

    const player = this.players.find(p => p.id === playerId);
    if (!player) return { error: 'player not found' };
    if (player.seatIndex !== hand.actingIndex) return { error: 'not your turn' };

    let result = { playerId, action, amount: 0, success: false };

    switch (action) {
      case ACTIONS.FOLD:
        player.status = PLAYER_STATUS.FOLDED;
        result.success = true;
        break;

      case ACTIONS.CHECK:
        if (hand.currentBet > 0) return { error: 'cannot check, there is a bet' };
        result.success = true;
        break;

      case ACTIONS.CALL: {
        const callAmount = Math.min(hand.currentBet, player.chipCount);
        player.chipCount -= callAmount;
        hand.pot += callAmount;
        result.amount = callAmount;
        if (player.chipCount === 0) player.status = PLAYER_STATUS.ALLIN;
        result.success = true;
        break;
      }

      case ACTIONS.RAISE: {
        if (amount <= hand.currentBet) return { error: 'raise must be higher than current bet' };
        const raiseAmount = Math.min(amount, player.chipCount);
        player.chipCount -= raiseAmount;
        hand.pot += raiseAmount;
        hand.currentBet = raiseAmount;
        hand.lastRaiserIndex = player.seatIndex;
        result.amount = raiseAmount;
        if (player.chipCount === 0) player.status = PLAYER_STATUS.ALLIN;
        result.success = true;
        break;
      }

      case ACTIONS.ALLIN: {
        const allinAmount = player.chipCount;
        hand.pot += allinAmount;
        if (allinAmount > hand.currentBet) {
          hand.currentBet = allinAmount;
          hand.lastRaiserIndex = player.seatIndex;
        }
        player.chipCount = 0;
        player.status = PLAYER_STATUS.ALLIN;
        result.amount = allinAmount;
        result.success = true;
        break;
      }

      default:
        return { error: 'unknown action' };
    }

    hand.actions.push(result);

    // 玩家已操作，清除倒计时
    this.clearActionTimer();

    // 广播操作结果
    this.emit('action_result', {
      playerId,
      action,
      amount: result.amount,
      handNumber: hand.handNumber,
      stage: hand.stage,
      pot: hand.pot,
      currentBet: hand.currentBet,
      actingIndex: hand.actingIndex,
      seats: this.getSeatsSnapshot(),
    });

    // 检查是否本轮下注结束
    this.advanceTurn();
    return result;
  }

  /**
   * 推进到下一个行动者或下一阶段
   */
  advanceTurn() {
    const hand = this.currentHand;
    const activePlayers = this.getActivePlayers();

    // 只剩 1 个未弃牌的活跃玩家 → 直接结束本手
    const nonFolded = this.getNonFoldedPlayers();
    if (nonFolded.length === 1) {
      this.finishHand(nonFolded[0]);
      return;
    }

    // 全部 allin → 直接到摊牌
    const canAct = activePlayers.filter(p => p.status === PLAYER_STATUS.PLAYING);
    if (canAct.length <= 1) {
      this.runOutBoard();
      return;
    }

    // 找下一个行动者
    const nextIndex = this.nextActiveSeat(hand.actingIndex);
    if (nextIndex === -1) {
      this.advanceStage();
      return;
    }

    // 检查是否一轮下注完成
    // 当前玩家就是 lastRaiserIndex（BB preflop check 后回到自己）→ 一轮完成
    if (hand.actingIndex === hand.lastRaiserIndex) {
      this.advanceStage();
      return;
    }
    // 下一个行动者回到 lastRaiserIndex → 检查该玩家是否已操作过
    if (nextIndex === hand.lastRaiserIndex) {
      const lastRaiserPlayer = this.players.find(p => p.seatIndex === hand.lastRaiserIndex);
      const hasActed = lastRaiserPlayer && hand.actions.some(a => a.playerId === lastRaiserPlayer.id);
      if (hasActed) {
        this.advanceStage();
        return;
      }
      // 还没操作过 → 让其操作
      hand.actingIndex = nextIndex;
      this.emit('turn_changed', { actingIndex: nextIndex, handNumber: hand.handNumber, pot: hand.pot, currentBet: hand.currentBet, seats: this.getSeatsSnapshot() });
      this.startActionTimer(nextIndex);
      return;
    }

    hand.actingIndex = nextIndex;
    this.emit('turn_changed', { actingIndex: nextIndex, handNumber: hand.handNumber, pot: hand.pot, currentBet: hand.currentBet, seats: this.getSeatsSnapshot() });

    // 重置操作倒计时
    this.startActionTimer(nextIndex);
  }

  /**
   * 启动操作超时定时器
   */
  startActionTimer(seatIndex) {
    this.clearActionTimer();
    const player = this.players.find(p => p.seatIndex === seatIndex);
    if (!player) return;

    this.actionTimer = setTimeout(() => {
      // Bot auto-action: simple strategy
      if (player.isBot) {
        const hand = this.currentHand;
        if (!hand) return;
        const toCall = Math.max(0, hand.currentBet - (this.getSeatCurrentBet(seatIndex) || 0));
        
        // Simple bot strategy: 
        // - If can check (toCall === 0): check
        // - If can afford call and pot odds reasonable: call (70%)
        // - Otherwise: fold
        if (toCall === 0) {
          console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} auto-check`);
          this.handleAction(player.id, ACTIONS.CHECK);
        } else if (player.chipCount >= toCall && Math.random() < 0.7) {
          console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} auto-call ${toCall}`);
          this.handleAction(player.id, ACTIONS.CALL, toCall);
        } else {
          console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} auto-fold`);
          this.handleAction(player.id, ACTIONS.FOLD);
        }
      } else {
        // Real player timeout: auto-fold
        console.log(`[SNG] Player ${player.id} timed out, auto-fold`);
        this.handleAction(player.id, ACTIONS.FOLD);
      }
    }, this.actionTimeout);

    this.emit('action_timer_started', {
      seatIndex,
      playerId: player.id,
      timeoutMs: this.actionTimeout,
    });
  }

  getSeatCurrentBet(seatIndex) {
    if (!this.currentHand || !this.currentHand.actions) return 0;
    const player = this.players.find(p => p.seatIndex === seatIndex);
    if (!player) return 0;
    return this.currentHand.actions
      .filter(a => a.playerId === player.id)
      .reduce((sum, a) => sum + (a.amount || 0), 0);
  }

  /**
   * 清除操作超时定时器
   */
  clearActionTimer() {
    if (this.actionTimer) {
      clearTimeout(this.actionTimer);
      this.actionTimer = null;
    }
  }

  /**
   * 推进到下一阶段（翻牌/转牌/河牌/摊牌）
   */
  advanceStage() {
    const hand = this.currentHand;
    const stages = ['preflop', 'flop', 'turn', 'river', 'showdown'];
    const currentIdx = stages.indexOf(hand.stage);

    if (currentIdx >= stages.length - 1) {
      this.showdown();
      return;
    }

    // 重置本轮下注
    hand.currentBet = 0;
    hand.lastRaiserIndex = -1;
    hand.actions = []; // 清空上一轮的操作记录，让 getSeatsSnapshot 的 currentBet 只反映当前轮

    const nextStage = stages[currentIdx + 1];
    hand.stage = nextStage;

    // 发公共牌
    if (nextStage === 'flop') {
      hand.revealedCommunity = hand.communityCards.slice(0, 3);
    } else if (nextStage === 'turn') {
      hand.revealedCommunity = hand.communityCards.slice(0, 4);
    } else if (nextStage === 'river') {
      hand.revealedCommunity = hand.communityCards.slice(0, 5);
    } else if (nextStage === 'showdown') {
      this.showdown();
      return;
    }

    this.emit('stage_changed', {
      stage: nextStage,
      communityCards: hand.revealedCommunity,
      pot: hand.pot,
      currentBet: hand.currentBet,
      actingIndex: hand.actingIndex,
      handNumber: hand.handNumber,
      seats: this.getSeatsSnapshot(),
    });

    // 设置第一个行动者（庄家左边第一个活跃玩家）
    const firstActive = this.nextActiveSeat(this.dealerIndex);
    hand.actingIndex = firstActive;
    hand.lastRaiserIndex = firstActive; // 一开始没人加注

    this.emit('turn_changed', { actingIndex: firstActive, handNumber: hand.handNumber, seats: this.getSeatsSnapshot() });

    // 重置操作倒计时
    this.startActionTimer(firstActive);
  }

  /**
   * 直接发完剩余公共牌（全员 allin）
   */
  runOutBoard() {
    const hand = this.currentHand;
    while (hand.revealedCommunity.length < 5) {
      if (hand.revealedCommunity.length === 0) {
        hand.revealedCommunity = hand.communityCards.slice(0, 3);
        this.emit('stage_changed', { stage: 'flop', communityCards: hand.revealedCommunity, pot: hand.pot, handNumber: hand.handNumber, seats: this.getSeatsSnapshot() });
      } else if (hand.revealedCommunity.length === 3) {
        hand.revealedCommunity = hand.communityCards.slice(0, 4);
        this.emit('stage_changed', { stage: 'turn', communityCards: hand.revealedCommunity, pot: hand.pot, handNumber: hand.handNumber, seats: this.getSeatsSnapshot() });
      } else if (hand.revealedCommunity.length === 4) {
        hand.revealedCommunity = hand.communityCards.slice(0, 5);
        this.emit('stage_changed', { stage: 'river', communityCards: hand.revealedCommunity, pot: hand.pot, handNumber: hand.handNumber, seats: this.getSeatsSnapshot() });
      }
    }
    this.showdown();
  }

  /**
   * 摊牌 — 比较所有未弃牌玩家的牌
   */
  showdown() {
    const hand = this.currentHand;
    const nonFolded = this.getNonFoldedPlayers();

    if (nonFolded.length === 1) {
      this.finishHand(nonFolded[0]);
      return;
    }

    // 评估每个玩家的最佳牌型
    const results = nonFolded.map(player => {
      const holeCards = hand.holeCards[player.id] || [];
      const evaluation = evaluateBest(holeCards, hand.revealedCommunity);
      return { player, evaluation };
    });

    // 找出赢家（可能平局）
    results.sort((a, b) => compareHands(b.evaluation, a.evaluation));
    const winners = [results[0]];
    for (let i = 1; i < results.length; i++) {
      if (compareHands(results[i].evaluation, results[0].evaluation) === 0) {
        winners.push(results[i]);
      } else break;
    }

    // 分配底池（平局时余数分给排名最高的赢家）
    const baseAmount = Math.floor(hand.pot / winners.length);
    const remainder = hand.pot - baseAmount * winners.length;
    for (let i = 0; i < winners.length; i++) {
      winners[i].player.chipCount += baseAmount + (i < remainder ? 1 : 0);
    }

    this.emit('showdown', {
      handNumber: hand.handNumber,
      winners: winners.map(w => ({
        playerId: w.player.id,
        handName: w.evaluation.name,
        cards: w.evaluation.cards,
      })),
      allResults: results.map(r => ({
        playerId: r.player.id,
        handName: r.evaluation.name,
        cards: r.evaluation.cards,
      })),
      pot: hand.pot,
      winAmount: baseAmount,
    });

    this.finishHand(null, winners);
  }

  /**
   * 结束当前手牌
   */
  finishHand(winner, showdownWinners = null) {
    const hand = this.currentHand;
    if (!hand) return;

    // 清除操作倒计时
    this.clearActionTimer();

    if (winner) {
      // 单人获胜（其他人都弃牌了）
      winner.chipCount += hand.pot;
      console.log(`[SNG] Hand #${hand.handNumber} finished: ${winner.nickname || winner.id.substring(0,8)} wins ${hand.pot} (fold)`);
      this.emit('hand_result', {
        handNumber: hand.handNumber,
        winnerId: winner.id,
        pot: hand.pot,
        showdown: false,
        seats: this.getSeatsSnapshot(),
      });
    }

    // 检查淘汰
    for (const p of this.players) {
      if (p.chipCount === 0 && p.status !== PLAYER_STATUS.ELIMINATED) {
        p.status = PLAYER_STATUS.ELIMINATED;
        console.log(`[SNG] ${p.nickname || p.id.substring(0,8)} eliminated`);
        this.emit('player_eliminated', { playerId: p.id, handNumber: hand.handNumber });
      }
    }

    // 恢复弃牌玩家为 playing（为下一手准备）
    for (const p of this.players) {
      if (p.status === PLAYER_STATUS.FOLDED) {
        p.status = PLAYER_STATUS.PLAYING;
      }
    }

    // 移动庄家位
    const nextDealer = this.nextActiveSeat(this.dealerIndex);
    if (nextDealer !== -1) {
      this.dealerIndex = nextDealer;
    }

    // 检查赛事是否结束
    const activePlayers = this.getActivePlayers();
    if (activePlayers.length <= 1) {
      this.finish();
      return;
    }

    // 开始下一手
    this.currentHand = null;
    setTimeout(() => {
      try {
        this.startNewHand();
      } catch (e) {
        console.error('[SNG] startNewHand (after finish) error:', e.message, e.stack);
      }
    }, 3000); // 3 秒间隔
  }

  /**
   * 结束赛事
   */
  finish() {
    if (this.blindTimer) {
      clearInterval(this.blindTimer);
      this.blindTimer = null;
    }

    this.clearActionTimer();

    this.tournament.status = TOURNAMENT_STATUS.FINISHED;
    this.tournament.finished_at = new Date();

    // 排名：最后淘汰的排前面
    const eliminated = this.players.filter(p => p.status === PLAYER_STATUS.ELIMINATED);
    const survivors = this.players.filter(p => p.status !== PLAYER_STATUS.ELIMINATED);

    const rankings = [
      ...survivors.map(p => ({ playerId: p.id, rank: 1, chips: p.chipCount })),
      ...eliminated.reverse().map((p, i) => ({ playerId: p.id, rank: i + 2, chips: 0 })),
    ];

    this.emit('tournament_finished', { rankings, seats: this.getSeatsSnapshot() });
  }

  /**
   * 事件发射器（由外部覆盖）
   */
  emit(event, data) {
    console.log(`[SNG] Event: ${event}`, JSON.stringify(data).substring(0, 200));
  }
}

module.exports = { SNGManager };
