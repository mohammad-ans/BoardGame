package com.example.boardgame

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.boardgame.ui.theme.BoardGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    lateinit var rollDice : Button
    lateinit var resetBtn : Button
    lateinit var diceImg : ImageView
    lateinit var player1 : TextView
    lateinit var player2 : TextView
    lateinit var turn : TextView
    var player1Pos = 0
    var player2Pos = 0
    var winningPoints = 50
    var player1Turn = true;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rollDice  = findViewById<Button>(R.id.roll_dice)
        resetBtn = findViewById<Button>(R.id.reset_game)
        diceImg = findViewById<ImageView>(R.id.dice_image)
        player1 = findViewById<TextView>(R.id.player1)
        player2 = findViewById<TextView>(R.id.player2)
        turn = findViewById<TextView>(R.id.player_turn)

        resetBtn.setOnClickListener(::resetGame)
        rollDice.setOnClickListener(::diceHandler)
    }
    fun diceRoll() : Int{
        return (1..6).random()
    }

    fun winner(message : String){
        turn.text = getString(R.string.game_over)
        rollDice.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    fun diceHandler(view : View) {
        rollDice.isEnabled = false
        val diceVal = diceRoll()

        diceImg.animate().rotationBy(360f).duration = 300

        lifecycleScope.launch {
            delay(300)
            updateDiceValue(diceVal)
        }

    }

    fun updateDiceValue(diceVal : Int) {

        val imgSrc = when(diceVal) {
            1 -> R.drawable.dice_one
            2 -> R.drawable.dice_two
            3 -> R.drawable.dice_three
            4 -> R.drawable.dice_four
            5 -> R.drawable.dice_five
            else -> R.drawable.dice_six
        }
        diceImg.setImageResource(imgSrc)
        if(player1Turn) {
            player1Pos += diceVal
            player1.text = getString(R.string.player_1_dynamic, player1Pos)
            if(player1Pos >= winningPoints) {
                winner("Player 1 wins")
                return
            }
            player1Turn = !player1Turn
            turn.text = getString(R.string.player_turn_dynamic, 1)
        }
        else{
            player2Pos += diceVal
            player2.text = getString(R.string.player_2_dynamic, player2Pos)

            if(player2Pos >= winningPoints) {
                winner("Player 2 wins")
                return
            }

            player1Turn = !player1Turn
            turn.text = getString(R.string.player_turn_dynamic, 2)
        }
        rollDice.isEnabled = true
    }

    fun resetGame(view : View) {
        player1Pos = 0
        player2Pos = 0
        player1Turn = true

        player1.text = getString(R.string.player_1)
        player2.text = getString(R.string.player_2)
        turn.text = getString(R.string.player_turn)
        diceImg.setImageResource(R.drawable.dice_one)

        rollDice.isEnabled = true
    }
}
