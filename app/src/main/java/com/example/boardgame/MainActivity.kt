package com.example.boardgame

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import android.view.Gravity
import android.graphics.Typeface
import android.view.ViewGroup
import android.app.AlertDialog
import android.content.res.Configuration
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.marginBottom
import androidx.lifecycle.lifecycleScope
import com.example.boardgame.ui.theme.BoardGameTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs


class MainActivity : ComponentActivity() {
    lateinit var resetBtn : Button
    lateinit var diceImg : ImageView
    lateinit var player1 : TextView
    lateinit var player1Icon : ImageView
    lateinit var player2 : TextView
    lateinit var player2Icon : ImageView
    lateinit var turn : TextView
    lateinit var tiles : ArrayList<TextView>
    lateinit var grid : GridLayout
    var sizeTile : Int = 0
    var heightTile : Int = 0

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
    var player1Turn = 1;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            sizeTile = (resources.displayMetrics.heightPixels) / 9
            heightTile = (sizeTile * 3) / 4

        }
        else{
            sizeTile = resources.displayMetrics.widthPixels / 6
            heightTile = (sizeTile * 3) / 4
        }
        resetBtn = findViewById<Button>(R.id.reset_game)
        diceImg = findViewById<ImageView>(R.id.dice_image)
        player1 = findViewById<TextView>(R.id.player1)
        player2 = findViewById<TextView>(R.id.player2)
        turn = findViewById<TextView>(R.id.player_turn)
        player1Icon = findViewById<ImageView>(R.id.player1icon)
        player2Icon = findViewById<ImageView>(R.id.player2icon)
        grid = findViewById<GridLayout>(R.id.grid)
        tiles = ArrayList()
        val padding = (8 * resources.displayMetrics.density).toInt()

        var i = 50
        var j = i
        while(i > 0) {
            j = i - 5
            while(i > j) {
                addTile(i)
                i--
            }
            i -= 4
            while(i <= j) {
                addTile(i)
                i++
            }
            i -= 6
        }
        resetBtn.setOnClickListener(::resetGameCheck)
        diceImg.setOnClickListener(::diceHandler)
    }
    fun addTile(i : Int) {
        val temp = TextView(this)
        temp.text = "$i"

        val params = GridLayout.LayoutParams()
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        params.height=(sizeTile * 3) / 4

        temp.gravity = Gravity.CENTER
        temp.textSize = 14f
        temp.setTypeface(temp.typeface, Typeface.BOLD)

        if(((i-1) % 2) == 0)
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
        if(player1Turn == 1) {
            val start = player1Pos - 1
            player1Pos += diceVal
            mainAnimation(start, if (player1Pos <= 50) (player1Pos - 1) else 49) {
                setColor(if ((player1Pos - 1) < 50) (player1Pos - 1) else 49)
            }

            if(player1Pos >= winningPoints) {
                animationLaunch.join()
                changePosition(player1Icon, start, winningPoints - 1)
                resetTiles()
                tiles[49].setBackgroundColor(Color.BLUE)
                winner("Player 1 wins")
                return
            }
            if(snakes.containsKey(player1Pos)) {
                animationLaunch.join()
                val temp = player1Pos - 1
                player1Pos = snakes.getValue(player1Pos)
                animationLaunch = reverseAnimation(temp, player1Pos - 1) {
                    setColor(player1Pos - 1)
                }
            }
            if(ladders.containsKey(player1Pos)){
                animationLaunch.join()
                val temp = player1Pos - 1
                player1Pos = ladders.getValue(player1Pos)
                mainAnimation(temp, player1Pos - 1){
                    setColor(player1Pos - 1)
                }

            }
            animationLaunch.join()
            player1.text = getString(R.string.player_1_dynamic, player1Pos)
            changePosition(player1Icon, start, player1Pos - 1)

            player1Turn = 2
        }
        else{
            val start = player2Pos - 1
            player2Pos += diceVal

            mainAnimation(start, if (player2Pos <= 50) player2Pos - 1 else 49) {
                    setColor(if (player2Pos <= 50) (player2Pos - 1) else 49)
            }

            if(player2Pos >= winningPoints) {
                animationLaunch.join()
                changePosition(player2Icon, start, winningPoints - 1)
                resetTiles()
                tiles[49].setBackgroundColor(Color.RED)
                winner("Player 2 wins")
                return
            }

            if(snakes.containsKey(player2Pos)) {
                animationLaunch.join()
                val temp = player2Pos - 1
                player2Pos = snakes.getValue(player2Pos)
                animationLaunch = reverseAnimation(temp, player2Pos - 1) {
                    setColor(player2Pos - 1)
                }

            }
            if(ladders.containsKey(player2Pos)){
                animationLaunch.join()
                val temp = player2Pos - 1
                player2Pos = ladders.getValue(player2Pos)
                mainAnimation(temp, player2Pos - 1){
                    setColor(player2Pos - 1)
                }

            }
            animationLaunch.join()
            player2.text = getString(R.string.player_2_dynamic, player2Pos)
            changePosition(player2Icon, start, player2Pos - 1)
            player1Turn = 1
        }
        turn.text = getString(R.string.player_turn_dynamic, player1Turn)
        diceImg.isEnabled = true
    }

    fun resetGame(view : View) {
            player1Pos = 0
            player2Pos = 0
            player1Turn = 1

            player1.text = getString(R.string.player_1)
            player2.text = getString(R.string.player_2)
            turn.text = getString(R.string.player_turn)
            diceImg.setImageResource(R.drawable.dice_one)
            changePosition(player2Icon, player2Pos - 1, 0)
            changePosition(player1Icon, player1Pos - 1, 0)
            resetTiles()
            diceImg.isEnabled = true
    }
    fun resetGameCheck(view : View) {
        if(gameEnd()) {
            resetGame(view);
        }
        else if(player1Pos == 0 && player2Pos == 0){
            Toast.makeText(this, "Game is already at starting point.", Toast.LENGTH_LONG ).show()
        }
        else{
            showResetConfirmation(view)
        }
    }

    fun showResetConfirmation(view : View) {
        AlertDialog.Builder(this)
            .setTitle("Reset Game?")
            .setMessage("Current game in progress. Are you sure you want to reset?")
            .setPositiveButton("Reset", {_, _ -> resetGame(view)})
            .setNegativeButton("Nope", null)
            .setCancelable(true)
            .show()
    }

    fun animation1(pos : Int) {
        resetTiles()
        if(pos > -1)
            tiles[pos].setBackgroundColor(Color.YELLOW)
    }
    fun mainAnimation(start : Int, end : Int, func : () -> Unit) {

        animationLaunch = lifecycleScope.launch {
            for(pos in start..end) {
                animation1(pos)
                delay(100)
            }
            delay(150)
            func()
        }
    }

    fun gameEnd() : Boolean {
        return (player1Pos >= 50 || player2Pos >= 50)
    }
    fun reverseAnimation(start : Int, end : Int, func : () -> Unit) : Job{
        return lifecycleScope.launch {
            for(pos in start downTo end) {
                resetTiles()
                tiles[pos].setBackgroundColor(Color.YELLOW)
                delay(100)
            }
            delay(150)
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
    fun changePosition(player : ImageView, start : Int, end : Int) {
        val rowCheck = end / 5
        val p = player.layoutParams as ConstraintLayout.LayoutParams
        val bottom = heightTile * rowCheck
        var left = 0
        if((rowCheck % 2) == 0) {
            left = (end % 5) * tiles[0].width
        }
        else{
            left = abs((end % 5) - 4) * tiles[0].width
        }
        Toast.makeText(this, "$left and $bottom", Toast.LENGTH_LONG).show()
        Toast.makeText(this, "$sizeTile and $heightTile", Toast.LENGTH_LONG).show()
        p.setMargins(left, p.topMargin, p.rightMargin, bottom)
        player.layoutParams = p
    }

}
