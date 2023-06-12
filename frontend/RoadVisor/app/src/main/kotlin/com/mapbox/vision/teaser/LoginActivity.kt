package com.mapbox.vision.teaser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.mapbox.vision.teaser.api.ApiService
import com.mapbox.vision.teaser.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var forgotPassword: TextView
    private lateinit var register: TextView

    private val apiService by lazy { RetrofitClient.create(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        if (isLoggedIn) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }

        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        forgotPassword = findViewById(R.id.forgot_password)
        register = findViewById(R.id.register)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (validateInput(email, password)) {
                login(email, password)
            } else {
                Toast.makeText(this, "Please enter valid email and password.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        forgotPassword.setOnClickListener {
            val forgotPasswordIntent = Intent(this@LoginActivity, ForgotPasswordActivity::class.java)
            startActivity(forgotPasswordIntent)
        }

        register.setOnClickListener {
            val registerIntent = Intent(this@LoginActivity, RegisterActivity::class.java)
            startActivity(registerIntent)
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        return email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email)
            .matches() && password.isNotEmpty()
    }

    private fun setLoggedInStatus(status: Boolean) {
        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("isLoggedIn", status)
        editor.apply()
    }

    private fun login(email: String, password: String) {
        val request = ApiService.LoginRequest(email, password)
        apiService.loginUser(request).enqueue(object : Callback<ApiService.LoginResponse> {
            override fun onResponse(
                call: Call<ApiService.LoginResponse>,
                response: Response<ApiService.LoginResponse>
            ) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        setLoggedInStatus(true)
                        RetrofitClient.saveCookies(this@LoginActivity)

                        // Store login response in SharedPreferences
                        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putInt("id", loginResponse.id)
                        editor.putString("email", loginResponse.email)
                        editor.putString("first_name", loginResponse.first_name)
                        editor.putString("last_name", loginResponse.last_name)
                        editor.apply()

                        val dashboardIntent = Intent(this@LoginActivity, DashboardActivity::class.java)
                        startActivity(dashboardIntent)
                        finish()

                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Login failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiService.LoginResponse>, t: Throwable) {
                Toast.makeText(this@LoginActivity, "Login failed! Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
