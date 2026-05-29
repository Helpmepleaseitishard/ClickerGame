package com.example.clickergame.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.JoinType

fun Application.configureRouting() {
    routing {
        // Эндпоинт для клика: увеличиваем счёт на 1
        post("/click") {
            val userId = call.request.headers["X-User-Id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing user id")
            transaction {
                val player = Players.select { Players.userId eq userId }.firstOrNull()
                if (player == null) {
                    // Новый игрок: создаём запись со счётом 1
                    Players.insert {
                        it[Players.userId] = userId
                        it[Players.score] = 1
                    }
                } else {
                    // Существующий игрок: увеличиваем счёт
                    val current = player[Players.score]
                    Players.update({ Players.userId eq userId }) {
                        it[Players.score] = current + 1
                    }
                }
            }
            call.respond(HttpStatusCode.OK, "ok")
        }
        post("/register") {
            val request = call.receiveText()
            println("📥 Register request: $request")

            val login = request.substringAfter("\"login\":\"").substringBefore("\"")
            val password = request.substringAfter("\"password\":\"").substringBefore("\"")
            println("👤 Login: '$login', Password: '$password'")

            if (login.isBlank() || password.isBlank()) {
                println("❌ Empty login or password")
                call.respond(HttpStatusCode.BadRequest, "Login and password required")
                return@post
            }

            val existing = transaction {
                Users.select { Users.login eq login }.firstOrNull()
            }
            if (existing != null) {
                println("⚠️ User '$login' already exists")
                call.respond(HttpStatusCode.Conflict, "Login already exists")
                return@post
            }

            val newUserId = java.util.UUID.randomUUID().toString()
            println("✅ Creating new user with userId: $newUserId")
            transaction {
                Users.insert {
                    it[Users.login] = login
                    it[Users.passwordHash] = password.hashCode()
                    it[Users.userId] = newUserId
                }
                Players.insert {
                    it[Players.userId] = newUserId
                    it[Players.score] = 0
                }
            }
            call.respondText("{\"userId\":\"$newUserId\"}", ContentType.Application.Json)
        }
        get("/score") {
            val userId = call.request.headers["X-User-Id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing user id")
            val score = transaction {
                Players.select { Players.userId eq userId }.firstOrNull()?.get(Players.score) ?: 0
            }
            call.respondText("{\"score\":$score}", ContentType.Application.Json)
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
            call.respondText("{\"userId\":\"${user[Users.userId]}\"}", ContentType.Application.Json)
        }
        // Эндпоинт для получения топа-10
        get("/top") {
            val players = transaction {
                Players.join(Users, JoinType.INNER, additionalConstraint = { Players.userId eq Users.userId })
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
                    Players.join(Users, JoinType.INNER, additionalConstraint = { Players.userId eq Users.userId })
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