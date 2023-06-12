package com.mapbox.vision.teaser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.vision.teaser.api.ApiService.PasswordResetRequest
import com.mapbox.vision.teaser.api.RetrofitClient
import kotlinx.android.synthetic.main.activity_forgot_password.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPasswordActivity : AppCompatActivity() {

    private val apiService by lazy { RetrofitClient.create(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        val backButton = findViewById<View>(R.id.arrow_back_)
        val alreadyHaveAccountTextView = findViewById<TextView>(R.id.alreadyHaveAccountTextView)

        resetPasswordButton.setOnClickListener {
            sendPasswordResetRequest()
        }

        backButton.setOnClickListener {
            finish()
        }

        alreadyHaveAccountTextView.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun sendPasswordResetRequest() {
        val email = emailInput.text.toString()
        if (email.isBlank()) {
            Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show()
            return
        }

        val passwordResetRequest = PasswordResetRequest(email = email)

        apiService.requestPasswordReset(passwordResetRequest).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@ForgotPasswordActivity, "Password reset request sent. Check your email.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ForgotPasswordActivity, "Failed to send password reset request.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@ForgotPasswordActivity, "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}