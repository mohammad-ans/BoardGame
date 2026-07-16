package com.example.boardgame

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GameState : ViewModel() {
    val player1Pos = MutableLiveData<Int>(0)
    val player2Pos = MutableLiveData<Int>(0)
    val player1Turn = MutableLiveData<Int>(1)
    val diceVal = MutableLiveData<Int>(1)
    val fireworksRunning = MutableLiveData<Boolean>(false)

    fun changePosition(player : Int, diceValue : Int) {
        diceVal.value = diceValue
        if (player == 1){
            player1Turn.value = 2
            player1Pos.value = player1Pos.value?.plus(diceValue)
        }
        else{
            player1Turn.value = 1
            player2Pos.value = player2Pos.value?.plus(diceValue)
        }
    }
    fun changeFireworks(b : Boolean){
        fireworksRunning.value = b
    }


}

class GameSessionViewModel : ViewModel() {
    var connection : GameConnection? = null
    var isHost : Boolean = false
    var connectionType: String = "offline-p"
    var playerName : String = "Bot"
}
