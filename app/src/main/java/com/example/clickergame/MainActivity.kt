package com.example.clickergame

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val client = HttpClient(CIO)
    private val baseUrl = "http://10.0.2.2:8080"
    private lateinit var userId: String
    private var currentScore = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userId = "player_2"


        val backgroundImage = findViewById<ImageView>(R.id.backgroundImage)
        val clickableObject = findViewById<ImageView>(R.id.clickableObject)
        val scoreText = findViewById<TextView>(R.id.scoreText)
        val showTopButton = findViewById<Button>(R.id.showTopButton)


        val prefs = getSharedPreferences("clicker_prefs", MODE_PRIVATE)
        currentScore = prefs.getLong("score", 0)
        scoreText.text = "Score: $currentScore"

        clickableObject.setOnClickListener {
            currentScore++
            scoreText.text = "Score: $currentScore"
            prefs.edit().putLong("score", currentScore).apply()
            lifecycleScope.launch {
                sendClick()
            }
        }

        showTopButton.setOnClickListener {
            lifecycleScope.launch {
                val top = fetchTop()
                showTopDialog(top)
            }
        }
    }

    private suspend fun sendClick() {
        try {
            client.post("$baseUrl/click") {
                header("X-User-Id", userId)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchTop(): List<Pair<String, Long>> {
        return try {
            val response: String = client.get("$baseUrl/top").body()
            val regex = """\{[^}]*\}""".toRegex()
            regex.findAll(response).map { match ->
                val json = match.value
                val userId = json.substringAfter("\"userId\":\"").substringBefore("\"")
                val score = json.substringAfter("\"score\":").substringBefore("}").toLongOrNull() ?: 0
                userId to score
            }.toList()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось загрузить рейтинг: ${e.message}", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    private fun showTopDialog(top: List<Pair<String, Long>>) {
        val message = buildString {
            append("Топ игроков:\n")
            top.forEachIndexed { index, (id, score) ->
                val shortId = if (id.length > 12) id.take(12) + "..." else id
                append("${index+1}. $shortId : $score\n")
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Рейтинг")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}