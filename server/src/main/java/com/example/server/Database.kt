package com.example.clickergame.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

// Таблица для счёта игроков
object Players : Table() {
    val userId = varchar("user_id", 64).uniqueIndex()
    val score = long("score").default(0)
    override val primaryKey = PrimaryKey(userId)
}

// Таблица для регистрации (логин, пароль, userId)
object Users : Table() {
    val id = integer("id").autoIncrement()
    val login = varchar("login", 64).uniqueIndex()
    val passwordHash = integer("password_hash")
    val userId = varchar("user_id", 64).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

fun initDatabase() {
    val dbFile = File("clicker.db")
    Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")
    transaction {
        SchemaUtils.create(Players, Users)
    }
}