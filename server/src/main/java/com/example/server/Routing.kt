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
            val players = transaction {
                (Players innerJoin Users)
                    .slice(Players.score, Users.login)
                    .selectAll()
                    .orderBy(Players.score to SortOrder.DESC)
                    .limit(20)
                    .map { mapOf("login" to it[Users.login], "score" to it[Players.score]) }
            }
            val json = buildString {
                append("[")
                players.forEachIndexed { idx, p ->
                    if (idx > 0) append(",")
                    append("{\"login\":\"${p["login"]}\",\"score\":${p["score"]}}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }
        get("/top/search") {
            val query = call.request.queryParameters["q"] ?: ""
            val players = if (query.isBlank()) {
                emptyList()
            } else {
                transaction {
                    (Players innerJoin Users)
                        .slice(Players.score, Users.login)
                        .select { Users.login like "%$query%" }
                        .orderBy(Players.score to SortOrder.DESC)
                        .limit(20)
                        .map { mapOf("login" to it[Users.login], "score" to it[Players.score]) }
                }
            }
            val json = buildString {
                append("[")
                players.forEachIndexed { idx, p ->
                    if (idx > 0) append(",")
                    append("{\"login\":\"${p["login"]}\",\"score\":${p["score"]}}")
                }
                append("]")
            }
            call.respondText(json, ContentType.Application.Json)
        }
    }
}