package com.mapbox.vision.teaser.api

import android.content.Context
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://roadvisor.pythonanywhere.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val cookieStore = mutableListOf<Cookie>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.addAll(cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore
        }
    }

    fun create(context: Context, useAuthHeaders: Boolean = false): ApiService {
        val okHttpClientBuilder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .cookieJar(cookieJar)

        if (useAuthHeaders) {
            okHttpClientBuilder.addInterceptor(getAuthHeadersInterceptor(context))
        }

        val okHttpClient = okHttpClientBuilder.build()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private fun getAuthHeadersInterceptor(context: Context): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()

            val sharedPreferences = context.getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
            val csrfToken = sharedPreferences.getString("csrftoken", "") ?: ""
            val sessionId = sharedPreferences.getString("sessionid", "") ?: ""

            val request = original.newBuilder()
                .addHeader("Cookie", "csrftoken=$csrfToken; sessionid=$sessionId")
                .addHeader("Referer", "$BASE_URL")
                .addHeader("X-CSRFToken", csrfToken)
                .build()

            chain.proceed(request)
        }
    }

    fun saveCookies(context: Context) {
        val sharedPreferences = context.getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        cookieStore.forEach { cookie ->
            when (cookie.name()) {
                "csrftoken" -> editor.putString("csrftoken", cookie.value())
                "sessionid" -> editor.putString("sessionid", cookie.value())
            }
        }

        editor.apply()
    }
}

