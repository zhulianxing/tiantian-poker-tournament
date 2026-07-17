package com.pokernight.tvdisplay.data.model

/**
 * Server event types received via Socket.IO.
 */
object ServerEvent {
    const val TOURNAMENT_ACTIVATED = "tournament_activated"
    const val COUNTDOWN_TICK = "countdown_tick"
    const val TOURNAMENT_STARTED = "tournament_started"
    const val NEW_HAND = "new_hand"
    const val HOLE_CARDS = "hole_cards"
    const val HAND_STARTED = "hand_started"
    const val STAGE_CHANGED = "stage_changed"
    const val TURN_CHANGED = "turn_changed"
    const val ACTION_RESULT = "action_result"
    const val SHOWDOWN = "showdown"
    const val HAND_RESULT = "hand_result"
    const val PLAYER_ELIMINATED = "player_eliminated"
    const val TOURNAMENT_FINISHED = "tournament_finished"
    const val BLIND_LEVEL_UP = "blind_level_up"
    const val TABLE_STATE = "table_state"
    const val SEAT_JOINED = "seat_joined"
    const val SEAT_LEFT = "seat_left"
    const val CONNECT_ERROR = "connect_error"
    const val DISCONNECT = "disconnect"
}

/**
 * Client→Server event types.
 */
object ClientEvent {
    const val JOIN_TABLE = "join_table"
}

/**
 * Tournament phases.
 */
object Phase {
    const val IDLE = "idle"
    const val REGISTERING = "registering"
    const val STARTED = "started"
    const val FINISHED = "finished"
}

/**
 * Player status constants.
 */
object PlayerStatus {
    const val EMPTY = "empty"
    const val WAITING = "waiting"
    const val PLAYING = "playing"
    const val FOLDED = "folded"
    const val ALL_IN = "allin"
    const val ELIMINATED = "eliminated"
}

/**
 * Poker stages.
 */
object Stage {
    const val PREFLOP = "preflop"
    const val FLOP = "flop"
    const val TURN = "turn"
    const val RIVER = "river"
    const val SHOWDOWN = "showdown"
}
