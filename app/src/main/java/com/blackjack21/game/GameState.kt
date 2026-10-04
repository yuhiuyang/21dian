package com.blackjack21.game

/** 游戏状态机（对应总方案第 7 节） */
enum class GameState {
    MAIN_MENU,
    BETTING,
    INITIAL_DEAL,
    PLAYER_TURN,   // 真人玩家（座位1）
    CPU_PLAYER_2,
    CPU_PLAYER_3,
    CPU_PLAYER_4,
    DEALER_TURN,
    SETTLEMENT,
    ROUND_RESULT,
    NEXT_ROUND,
    GAME_OVER
}
