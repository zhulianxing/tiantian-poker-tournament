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
      if (this.blindLevel >= 30) { console.log('[SNG] Blind level capped at 30'); return; }
      this.blindLevel++;
      console.log(`[SNG] Blind level up to ${this.blindLevel}`);
      const { sb, bb } = this.getCurrentBlinds();
      this.emit('blind_level_up', {
        level: this.blindLevel,
        sb,
        bb,
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
    if (this.isFinished) return;
    // 检查赛事是否结束
    const activePlayers = this.getActivePlayers();
    if (activePlayers.length <= 1) {
      this.finish();
      return;
    }

    // 检查是否所有玩家筹码都不够付 BB → 按筹码排名结束
    const { sb: _sb, bb: _bb } = this.getCurrentBlinds();
    const canAffordBlind = activePlayers.filter(p => p.chipCount > 0);
    if (canAffordBlind.length === 0 || (canAffordBlind.length === 1 && activePlayers.length > 1)) {
      // 所有玩家筹码耗尽，按当前筹码排名
      this.finish();
      return;
    }
    // 如果只剩 2 人且双方筹码都不够 BB，直接结束
    if (activePlayers.length === 2 && activePlayers.every(p => p.chipCount < _bb)) {
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

      case ACTIONS.CHECK: {
        // 与本轮自己已下注额比较：全员跟注到平点时大盲应能过牌
        const myBetForCheck = this.getSeatCurrentBet(player.seatIndex);
        if (hand.currentBet > myBetForCheck) return { error: 'cannot check, there is a bet' };
        result.success = true;
        break;
      }

      case ACTIONS.CALL: {
        const alreadyBet = this.getSeatCurrentBet(player.seatIndex);
        const toCall = Math.max(0, hand.currentBet - alreadyBet);
        const callAmount = Math.min(toCall, player.chipCount);
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

    // 检查是否一轮下注完成：所有可行动玩家本轮都已操作且下注额已匹配 currentBet。
    // （旧逻辑依赖行动轮转回 lastRaiserIndex，若该玩家 allin/弃牌则永远转不回去 → 死循环）
    const canActPlayers = this.players.filter(p => p.status === PLAYER_STATUS.PLAYING);
    const actedIds = new Set(hand.actions.filter(a => a.action !== 'bet').map(a => a.playerId));
    const roundComplete = canActPlayers.length > 0 && canActPlayers.every(p =>
      actedIds.has(p.id) && this.getSeatCurrentBet(p.seatIndex) >= hand.currentBet
    );
    if (roundComplete) {
      this.advanceStage();
      return;
    }

    // 找下一个行动者
    const nextIndex = this.nextActiveSeat(hand.actingIndex);
    if (nextIndex === -1) {
      this.advanceStage();
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
      // Bot auto-action: smarter strategy
      if (player.isBot) {
        const hand = this.currentHand;
        if (!hand) return;
        const alreadyBet = this.getSeatCurrentBet(seatIndex);
        const toCall = Math.max(0, hand.currentBet - alreadyBet);
        const potOdds = toCall > 0 ? toCall / (hand.pot + toCall) : 0;
        const chipRatio = player.chipCount / 1000; // 相对筹码
        
        // Bot 策略：
        // - 可以 check → 80% check, 20% raise（偷池）
        // - 需要跟注：根据底池赔率和筹码决定
        //   - potOdds < 25% 且筹码充足 → 85% call, 15% raise
        //   - potOdds 25-40% → 60% call, 5% raise, 35% fold
        //   - potOdds > 40% → 20% call, 80% fold
        // - 筹码 < 3BB → push or fold（短筹码策略）
        const { bb } = this.getCurrentBlinds();
        
        if (toCall === 0) {
          if (Math.random() < 0.2 && player.chipCount > bb * 5) {
            // 偷池 raise
            const raiseAmt = Math.min(player.chipCount, bb * 3);
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} raise ${raiseAmt} (steal)`);
            this.handleAction(player.id, ACTIONS.RAISE, raiseAmt);
          } else {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} check`);
            this.handleAction(player.id, ACTIONS.CHECK);
          }
        } else if (player.chipCount < bb * 3) {
          // 短筹码 push or fold
          if (Math.random() < 0.5) {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} all-in (short stack)`);
            this.handleAction(player.id, ACTIONS.ALLIN);
          } else {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} fold (short stack)`);
            this.handleAction(player.id, ACTIONS.FOLD);
          }
        } else if (potOdds < 0.25) {
          if (Math.random() < 0.15) {
            const raiseAmt = Math.min(player.chipCount, hand.currentBet * 2 + bb);
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} raise ${raiseAmt}`);
            this.handleAction(player.id, ACTIONS.RAISE, raiseAmt);
          } else {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} call ${toCall}`);
            this.handleAction(player.id, ACTIONS.CALL, toCall);
          }
        } else if (potOdds < 0.4) {
          const r = Math.random();
          if (r < 0.05) {
            const raiseAmt = Math.min(player.chipCount, hand.currentBet * 2 + bb);
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} raise ${raiseAmt}`);
            this.handleAction(player.id, ACTIONS.RAISE, raiseAmt);
          } else if (r < 0.65) {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} call ${toCall}`);
            this.handleAction(player.id, ACTIONS.CALL, toCall);
          } else {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} fold`);
            this.handleAction(player.id, ACTIONS.FOLD);
          }
        } else {
          if (Math.random() < 0.2) {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} call ${toCall} (expensive)`);
            this.handleAction(player.id, ACTIONS.CALL, toCall);
          } else {
            console.log(`[SNG] Bot ${player.nickname || player.id.substring(0,8)} fold (expensive)`);
            this.handleAction(player.id, ACTIONS.FOLD);
          }
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
        seatIndex: w.player.seatIndex,
        handName: w.evaluation.name,
        cards: w.evaluation.cards,
        holeCards: hand.holeCards[w.player.id] || [],
      })),
      allResults: results.map(r => ({
        playerId: r.player.id,
        seatIndex: r.player.seatIndex,
        handName: r.evaluation.name,
        cards: r.evaluation.cards,
        holeCards: hand.holeCards[r.player.id] || [],
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
        winAmount: hand.pot,
        showdown: false,
        seats: this.getSeatsSnapshot(),
      });
    } else if (showdownWinners && showdownWinners.length > 0) {
      // 摊牌获胜（筹码已在 showdown() 中分配）
      console.log(`[SNG] Hand #${hand.handNumber} finished: ${showdownWinners.map(w => w.player.nickname || w.player.id.substring(0,8)).join(',')} wins ${hand.pot} (showdown)`);
      this.emit('hand_result', {
        handNumber: hand.handNumber,
        winnerId: showdownWinners[0].player.id,
        winners: showdownWinners.map(w => w.player.id),
        pot: hand.pot,
        winAmount: Math.floor(hand.pot / showdownWinners.length),
        handName: showdownWinners[0].evaluation.name,
        showdown: true,
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

    // 排名：存活者按筹码降序排列，淘汰者按淘汰顺序倒排
    const eliminated = this.players.filter(p => p.status === PLAYER_STATUS.ELIMINATED);
    const survivors = this.players.filter(p => p.status !== PLAYER_STATUS.ELIMINATED)
      .sort((a, b) => b.chipCount - a.chipCount);

    const rankings = [
      ...survivors.map((p, i) => ({ playerId: p.id, rank: i + 1, chips: p.chipCount, nickname: p.nickname })),
      ...eliminated.reverse().map((p, i) => ({ playerId: p.id, rank: survivors.length + i + 1, chips: 0, nickname: p.nickname })),
    ];

    console.log(`[SNG] Tournament ${this.tournament.id} finished. Rankings:`);
    rankings.forEach(r => console.log(`  #${r.rank}: ${r.nickname || r.playerId.substring(0,8)} (${r.chips} chips)`));

    // 发送赛事结束事件，poker-socket 通过回调写回数据库
    this.emit('tournament_finished', {
      tournamentId: this.tournament.id,
      rankings,
      seats: this.getSeatsSnapshot(),
    });

    // 标记赛事已完成，防止重复 finish
    this.isFinished = true;
  }

  /**
   * 事件发射器（由外部覆盖）
   */
  emit(event, data) {
    console.log(`[SNG] Event: ${event}`, JSON.stringify(data).substring(0, 200));
  }
}

module.exports = { SNGManager };
