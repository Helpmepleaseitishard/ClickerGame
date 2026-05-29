package com.example.clickergame.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureRouting() {
    routing {
        // Эндпоинт для клика: увеличиваем счёт на 1
        post("/click") {
            val userId = call.request.headers["X-User-Id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing user id")

            transaction {
                val current = Players.select { Players.userId eq userId }
                    .firstOrNull()?.get(Players.score) ?: 0
                if (current == 0L) {
                    // новый игрок
                    Players.insert { it[Players.userId] = userId; it[Players.score] = 1 }
                } else {
                    Players.update({ Players.userId eq userId }) { it[Players.score] = current + 1 }
                }
            }
            call.respond(HttpStatusCode.OK, "ok")
        }

        // Эндпоинт для получения топа-10
        get("/top") {
            val top = transaction {
                Players.selectAll().orderBy(Players.score to SortOrder.DESC).limit(10).map {
                    mapOf("userId" to it[Players.userId], "score" to it[Players.score])
                }
            }
            // ручной JSON, без сериализации
            val json = buildString {
                append("[")
                top.forEachIndexed { index, entry ->
                    if (index > 0) append(",")
                    append("{\"userId\":\"${entry["userId"]}\",\"score\":${entry["score"]}}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }
    }
}