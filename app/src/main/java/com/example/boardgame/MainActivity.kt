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
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Typeface
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.example.boardgame.ui.theme.BoardGameTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    lateinit var resetBtn : Button
    lateinit var diceImg : ImageView
    lateinit var player1 : TextView
    lateinit var player2 : TextView
    lateinit var turn : TextView
    lateinit var tiles : ArrayList<TextView>
    lateinit var grid : GridLayout
    lateinit var animationLaunch : Job
    lateinit var reverseAnimationLaunch : Job
    var player1Pos = 0
    var player2Pos = 0
    var winningPoints = 50
    val snakes = mapOf<Int, Int>(
        49 to 38,
        47 to 36,
        42 to 33,
        44 to 33,
        40 to 29,
        35 to 24,
        30 to 19,
        23 to 12,
        15 to 4)
    val ladders = mapOf<Int, Int>(
        32 to 41,
        34 to 46,
        11 to 28,
        3 to 14,
        16 to 27
    )
    var player1Turn = true;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resetBtn = findViewById<Button>(R.id.reset_game)
        diceImg = findViewById<ImageView>(R.id.dice_image)
        player1 = findViewById<TextView>(R.id.player1)
        player2 = findViewById<TextView>(R.id.player2)
        turn = findViewById<TextView>(R.id.player_turn)
        grid = findViewById<GridLayout>(R.id.grid)
//        grid.layoutParams.height = resources.displayMetrics.heightPixels / 2
        tiles = ArrayList()

        val padding = (8 * resources.displayMetrics.density).toInt()
        val sizeTile = resources.displayMetrics.widthPixels / 6

        var i = 50
        var j = i
        while(i > 0) {
            j = i - 5
            while(i > j) {
                addTile(i, sizeTile)
                i--
            }
            i -= 4
            while(i <= j) {
                addTile(i, sizeTile)
                i++
            }
            i -= 6
        }
        resetBtn.setOnClickListener(::resetGame)
        diceImg.setOnClickListener(::diceHandler)
    }
    fun addTile(i : Int, sizeTile : Int) {
        val temp = TextView(this)
        temp.text = "$i"

        val params = GridLayout.LayoutParams()
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        params.height=(sizeTile * 3) / 4

        temp.gravity = Gravity.CENTER
        temp.textSize = 14f
        temp.setTypeface(temp.typeface, Typeface.BOLD)

        if((i % 2) == 0)
            temp.setBackgroundColor("#dbffcd".toColorInt())
        else
            temp.setBackgroundColor("#669ca4".toColorInt())
        temp.layoutParams = params

        grid.addView(temp)
        val tempVar = i % 10
        if(tempVar in 6..9 || tempVar == 0)
            tiles.add(0, temp)
        else
            tiles.add(tempVar - 1, temp)
    }
    fun diceRoll() : Int{
        return (1..6).random()
    }

    fun winner(message : String){
        turn.text = getString(R.string.game_over)
        diceImg.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    fun diceHandler(view : View) {
        diceImg.isEnabled = false
        val diceVal = diceRoll()

        diceImg.animate().rotationBy(360f).duration = 300

        lifecycleScope.launch {
            delay(300)
            updateDiceValue(diceVal)
        }


    }

    suspend fun updateDiceValue(diceVal : Int) {

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
                    setColor(if ((player1Pos - 2) < 49) player1Pos - 2 else 48)
                }
            }

            if(player1Pos >= winningPoints) {
                resetTiles()
                tiles[49].setBackgroundColor(Color.BLUE)
                winner("Player 1 wins")
                animationLaunch.cancel()
                return
            }
            if(snakes.containsKey(player1Pos)) {
                animationLaunch.join()
                val temp = player1Pos - 2
                player1Pos = snakes.getValue(player1Pos)
                animationLaunch = reverseAnimation(temp, player1Pos - 1) {
                    setColor(player1Pos - 1)
                    player1.text = getString(R.string.player_1_dynamic, player1Pos)
                }
            }
            if(ladders.containsKey(player1Pos)){
                animationLaunch.join()
                val temp = player1Pos
                player1Pos = ladders.getValue(player1Pos)
                mainAnimation(temp, player1Pos){
                    setColor(player1Pos - 2)
                    player1.text = getString(R.string.player_1_dynamic, player1Pos)
                }

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
                    setColor(if ((player2Pos - 2) < 49) (player2Pos - 2) else 48)
                }
            }

            if(player2Pos >= winningPoints) {
                animationLaunch.cancel()
                resetTiles()
                tiles[49].setBackgroundColor(Color.RED)
                winner("Player 2 wins")
                return
            }

            if(snakes.containsKey(player2Pos)) {
                animationLaunch.join()
                val temp = player2Pos - 2
                player2Pos = snakes.getValue(player2Pos)
                animationLaunch = reverseAnimation(temp, player2Pos - 1) {
                    setColor(player2Pos - 1)
                    player2.text = getString(R.string.player_2_dynamic, player2Pos)
                }

            }
            if(ladders.containsKey(player2Pos)){
                animationLaunch.join()
                val temp = player2Pos
                player2Pos = ladders.getValue(player2Pos)
                mainAnimation(temp, player1Pos){
                    setColor(player2Pos - 2)
                    player2.text = getString(R.string.player_2_dynamic, player2Pos)
                }

            }
            player1Turn = !player1Turn
            turn.text = getString(R.string.player_turn_dynamic, 2)
        }
        animationLaunch.join()
        diceImg.isEnabled = true
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
        diceImg.isEnabled = true
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
                delay(100)
            }
            func()
        }
    }


    fun reverseAnimation(start : Int, end : Int, func : () -> Unit) : Job{
        return lifecycleScope.launch {
            for(pos in start downTo end) {
                resetTiles()
                tiles[pos].setBackgroundColor(Color.YELLOW)
                delay(100)
            }
            func()
        }
    }
    fun setColor(i : Int) {
        if((i % 2) == 0)
            tiles[i].setBackgroundColor("#dbffcd".toColorInt())
        else
            tiles[i].setBackgroundColor("#669ca4".toColorInt())
    }

    fun resetTiles() {
        for ((index, tile) in tiles.withIndex()) {
            setColor(index)
        }
    }
}
