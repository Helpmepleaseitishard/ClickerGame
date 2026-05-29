package com.example.clickergame

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.launch
import java.net.URLEncoder

class TopPlayersActivity : AppCompatActivity() {

    private val client = HttpClient(CIO)
    private val baseUrl = "http://10.0.2.2:8080"
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlayerAdapter
    private val playersList = mutableListOf<Player>()

    // Элементы поиска (коммит 12)
    private lateinit var editSearch: EditText
    private lateinit var btnSearch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_players)

        recyclerView = findViewById(R.id.recyclerViewTop)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PlayerAdapter(playersList)
        recyclerView.adapter = adapter

        // Инициализация поиска
        editSearch = findViewById(R.id.editSearch)
        btnSearch = findViewById(R.id.btnSearch)

        btnSearch.setOnClickListener {
            val query = editSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                loadTop() // если запрос пустой, загружаем весь топ
            }
        }

        loadTop()
    }

    private fun loadTop() {
        lifecycleScope.launch {
            val players = fetchTop()
            playersList.clear()
            playersList.addAll(players)
            adapter.notifyDataSetChanged()
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val players = searchTop(query)
            playersList.clear()
            playersList.addAll(players)
            adapter.notifyDataSetChanged()
        }
    }

    private suspend fun fetchTop(): List<Player> {
        return try {
            val response: String = client.get("$baseUrl/top").body()
            parsePlayers(response)
        } catch (e: Exception) {
            Toast.makeText(this@TopPlayersActivity, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    private suspend fun searchTop(query: String): List<Player> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val response: String = client.get("$baseUrl/top/search?q=$encodedQuery").body()
            parsePlayers(response)
        } catch (e: Exception) {
            Toast.makeText(this@TopPlayersActivity, "Ошибка поиска", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    private fun parsePlayers(json: String): List<Player> {
        val regex = """\{[^}]*\}""".toRegex()
        return regex.findAll(json).map { match ->
            val obj = match.value
            val login = obj.substringAfter("\"login\":\"").substringBefore("\"")
            val score = obj.substringAfter("\"score\":").substringBefore("}").toLongOrNull() ?: 0
            Player(login, score)
        }.toList()
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}