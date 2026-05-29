package com.example.clickergame

import android.content.Intent
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

        // Проверка авторизации
        val prefs = getSharedPreferences("clicker_prefs", MODE_PRIVATE)
        val savedUserId = prefs.getString("userId", null)
        if (savedUserId.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        userId = savedUserId

        val backgroundImage = findViewById<ImageView>(R.id.backgroundImage)
        val clickableObject = findViewById<ImageView>(R.id.clickableObject)
        val scoreText = findViewById<TextView>(R.id.scoreText)   // объявляем здесь
        val showTopButton = findViewById<Button>(R.id.showTopButton)
        val btnLogout = findViewById<Button>(R.id.btnLogout)


        // Загружаем счёт с сервера и обновляем UI
        lifecycleScope.launch {
            val serverScore = fetchScore()
            currentScore = serverScore
            scoreText.text = "Score: $currentScore"
            prefs.edit().putLong("score", currentScore).apply()
        }

        clickableObject.setOnClickListener {
            currentScore++
            scoreText.text = "Score: $currentScore"
            prefs.edit().putLong("score", currentScore).apply()
            lifecycleScope.launch {
                sendClick()
            }
        }

        showTopButton.setOnClickListener {
            startActivity(Intent(this, TopPlayersActivity::class.java))
        }

        btnLogout.setOnClickListener {
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private suspend fun fetchScore(): Long {
        return try {
            val response: String = client.get("$baseUrl/score") {
                header("X-User-Id", userId)
            }.body()
            response.substringAfter("\"score\":").substringBefore("}").toLongOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("clicker_prefs", MODE_PRIVATE)
        val currentUserId = prefs.getString("userId", null)
        if (currentUserId.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else if (currentUserId != userId) {
            userId = currentUserId
            lifecycleScope.launch {
                val serverScore = fetchScore()
                currentScore = serverScore
                findViewById<TextView>(R.id.scoreText).text = "Score: $currentScore"
                prefs.edit().putLong("score", currentScore).apply()
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

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}