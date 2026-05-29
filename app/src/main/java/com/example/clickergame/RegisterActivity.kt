package com.example.clickergame

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private val client = HttpClient(CIO)
    private val baseUrl = "http://10.0.2.2:8080"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val editLogin = findViewById<EditText>(R.id.editLogin)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoToLogin = findViewById<Button>(R.id.btnGoToLogin)

        btnRegister.setOnClickListener {
            val login = editLogin.text.toString().trim()
            val password = editPassword.text.toString().trim()
            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val userId = register(login, password)
                if (userId != null) {
                    getSharedPreferences("clicker_prefs", MODE_PRIVATE).edit()
                        .putString("userId", userId).apply()
                    Toast.makeText(this@RegisterActivity, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "Ошибка регистрации. Логин может быть занят.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private suspend fun register(login: String, password: String): String? {
        return try {
            val response = client.post("$baseUrl/register") {
                contentType(ContentType.Application.Json)
                setBody("{\"login\":\"$login\",\"password\":\"$password\"}")
            }
            if (response.status.value == 200) {
                val body = response.body<String>()
                body.substringAfter("\"userId\":\"").substringBefore("\"")
            } else {
                val errorText = response.body<String>()
                println("Registration failed: ${response.status.value} - $errorText")
                null
            }
        } catch (e: Exception) {
            println("Network error: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}