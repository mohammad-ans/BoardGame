package com.example.boardgame

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.GridView
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
import android.graphics.Color
import android.view.Gravity
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.boardgame.ui.theme.BoardGameTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    lateinit var rollDice : Button
    lateinit var resetBtn : Button
    lateinit var diceImg : ImageView
    lateinit var player1 : TextView
    lateinit var player2 : TextView
    lateinit var turn : TextView
    lateinit var tiles : ArrayList<TextView>
    lateinit var grid : GridLayout
    lateinit var animationLaunch : Job
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
        grid = findViewById<GridLayout>(R.id.grid)
//        grid.layoutParams.height = resources.displayMetrics.heightPixels / 2
        tiles = ArrayList()

        val padding = (8 * resources.displayMetrics.density).toInt()
        val sizeTile = resources.displayMetrics.widthPixels / 5
        for(i in 50 downTo 1) {
            val temp = TextView(this)
            temp.text = "$i"
            temp.textSize = 20f
            temp.gravity = Gravity.CENTER
            val params = GridLayout.LayoutParams()
            params.width=sizeTile
            params.height=(sizeTile * 2) / 3
            temp.setBackgroundColor(Color.LTGRAY)
            temp.layoutParams = params
            grid.addView(temp)
            tiles.add(0, temp)

        }
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
            val start = player1Pos - 1
            player1Pos += diceVal
            player1.text = getString(R.string.player_1_dynamic, player1Pos)
            mainAnimation(start, if (player1Pos <= 50) player1Pos else 49) {
                tiles[if ((player1Pos - 1) < 50) player1Pos - 1 else 49].setBackgroundColor(Color.BLUE)
                if(player1Pos > 1){
                    tiles[if ((player1Pos - 2) < 49) player1Pos - 2 else 48].setBackgroundColor(Color.LTGRAY)
                }
            }

            if(player1Pos >= winningPoints) {
                resetTiles()
                tiles[49].setBackgroundColor(Color.BLUE)
                winner("Player 1 wins")
                animationLaunch.cancel()
                return
            }
            player1Turn = !player1Turn
            turn.text = getString(R.string.player_turn_dynamic, 1)
        }
        else{
            val start = player2Pos
            player2Pos += diceVal
            player2.text = getString(R.string.player_2_dynamic, player2Pos)


            mainAnimation(start, if (player2Pos <= 50) player2Pos else 49) {
                tiles[if ((player2Pos - 1) < 50) (player2Pos - 1) else 49].setBackgroundColor(Color.RED)
                if(player2Pos > 1){
                    tiles[if ((player2Pos - 2) < 49) (player2Pos - 2) else 48].setBackgroundColor(Color.LTGRAY)
                }
            }

            if(player2Pos >= winningPoints) {
                animationLaunch.cancel()
                resetTiles()
                tiles[49].setBackgroundColor(Color.RED)
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

        resetTiles()
        rollDice.isEnabled = true
    }
    fun animation1(pos : Int) {
        resetTiles()
        if(pos > 0) {
            tiles[pos - 1].setBackgroundColor(Color.YELLOW)
        }
    }
    fun mainAnimation(start : Int, end : Int, func : () -> Unit) {

        animationLaunch = lifecycleScope.launch {
            for(pos in start..(end - 1)) {
                animation1(pos)
                delay(150)
            }
            func()
        }
    }
    fun resetTiles() {
        for (tile in tiles) {
            tile.setBackgroundColor(Color.LTGRAY)
        }
    }
}
