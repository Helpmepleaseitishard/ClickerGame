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
                Players.update({ Players.userId eq userId }) {
                    it[Players.score] = current + 1
                }
            }
            call.respond(HttpStatusCode.OK, "ok")
        }
        post("/register") {
            val request = call.receiveText()
            val login = request.substringAfter("\"login\":\"").substringBefore("\"")
            val password = request.substringAfter("\"password\":\"").substringBefore("\"")
            val passwordHash = password.hashCode()

            if (login.isBlank() || password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Login and password required")
                return@post
            }

            val existing = transaction {
                Users.select { Users.login eq login }.firstOrNull()
            }
            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, "Login already exists")
                return@post
            }
            post("/login") {
                val request = call.receiveText()
                val login = request.substringAfter("\"login\":\"").substringBefore("\"")
                val password = request.substringAfter("\"password\":\"").substringBefore("\"")
                val passwordHash = password.hashCode()

                val user = transaction {
                    Users.select { (Users.login eq login) and (Users.passwordHash eq passwordHash) }
                        .firstOrNull()
                }
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid login or password")
                    return@post
                }
                call.respond(mapOf("userId" to user[Users.userId]))
            }
            val newUserId = java.util.UUID.randomUUID().toString()
            transaction {
                Users.insert {
                    it[Users.login] = login
                    it[Users.passwordHash] = passwordHash
                    it[Users.userId] = newUserId
                }
                // Создаём запись в таблице очков с нулевым счётом
                Players.insert {
                    it[Players.userId] = newUserId
                    it[Players.score] = 0
                }
            }
            call.respond(mapOf("userId" to newUserId))
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