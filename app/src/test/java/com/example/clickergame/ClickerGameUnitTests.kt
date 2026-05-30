package com.example.clickergame

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder

class ClickerGameUnitTests {

    @Test
    fun testParsePlayers_validJson_returnsList() {
        val json = """
            [
                {"login":"player1","score":100},
                {"login":"player2","score":50}
            ]
        """.trimIndent()
        val players = parsePlayers(json)
        assertEquals(2, players.size)
        assertEquals("player1", players[0].login)
        assertEquals(100L, players[0].score)
        assertEquals("player2", players[1].login)
        assertEquals(50L, players[1].score)
    }

    @Test
    fun testParsePlayers_emptyJson_returnsEmptyList() {
        val json = "[]"
        val players = parsePlayers(json)
        assertEquals(0, players.size)
    }

    @Test
    fun testParsePlayers_invalidJson_returnsEmptyList() {
        val json = "not a json"
        val players = parsePlayers(json)
        assertEquals(0, players.size)
    }

    @Test
    fun testUrlEncoding_russianQuery_encodedCorrectly() {
        val query = "игрок"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assertEquals("%D0%B8%D0%B3%D1%80%D0%BE%D0%BA", encoded)
    }

    @Test
    fun testUrlEncoding_latinQuery_remainsReadable() {
        val query = "player"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assertEquals("player", encoded)
    }
    @Test
    fun testSearchHistory_savesLastFiveQueries() {
        val history = mutableListOf<String>()
        fun addQuery(query: String) {
            history.remove(query)
            history.add(0, query)
            if (history.size > 5) history.removeAt(5)
        }
        addQuery("a"); addQuery("b"); addQuery("c"); addQuery("d"); addQuery("e")
        addQuery("f")
        assertEquals(5, history.size)
        assertEquals(listOf("f", "e", "d", "c", "b"), history)
        addQuery("b")
        assertEquals(listOf("b", "f", "e", "d", "c"), history)
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
}