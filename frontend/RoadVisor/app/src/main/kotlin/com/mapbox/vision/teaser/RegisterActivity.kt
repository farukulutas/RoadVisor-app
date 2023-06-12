package com.mapbox.vision.teaser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.mapbox.vision.teaser.api.ApiService
import com.mapbox.vision.teaser.api.ApiService.RegisterRequest
import com.mapbox.vision.teaser.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private val apiService by lazy { RetrofitClient.create(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val emailEditText = findViewById<TextInputEditText>(R.id.emailEditText)
        val passwordEditText = findViewById<TextInputEditText>(R.id.passwordEditText)
        val firstNameEditText = findViewById<TextInputEditText>(R.id.firstNameEditText)
        val lastNameEditText = findViewById<TextInputEditText>(R.id.lastNameEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val alreadyHaveAccountTextView = findViewById<TextView>(R.id.alreadyHaveAccountTextView)
        val backButton = findViewById<View>(R.id.arrow_back_)

        backButton.setOnClickListener {
            finish()
        }

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val firstName = firstNameEditText.text.toString()
            val lastName = lastNameEditText.text.toString()

            if (email.isEmpty() || password.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                val registerRequest = RegisterRequest(email = email, password = password, first_name = firstName, last_name = lastName)
                apiService.registerUser(registerRequest).enqueue(object : Callback<ApiService.RegisterResponse> {
                    override fun onResponse(call: Call<ApiService.RegisterResponse>, response: Response<ApiService.RegisterResponse>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegisterActivity, "Registration successful. Please check your email to activate your account.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiService.RegisterResponse>, t: Throwable) {
                        Toast.makeText(this@RegisterActivity, "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        alreadyHaveAccountTextView.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
