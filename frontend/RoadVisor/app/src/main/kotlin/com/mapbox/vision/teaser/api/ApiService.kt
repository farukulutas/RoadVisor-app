package com.mapbox.vision.teaser.api

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    data class LoginRequest(val email: String, val password: String)
    data class LoginResponse(val id: Int, val email: String, val first_name: String, val last_name: String)

    @POST("/api/v1/login/")
    fun loginUser(@Body request: LoginRequest): Call<LoginResponse>

    data class PasswordResetRequest(val email: String)

    @POST("/api/v1/password-reset/request/")
    fun requestPasswordReset(@Body resetRequest: PasswordResetRequest): Call<Void>

    data class RegisterRequest(val email: String, val password: String, val first_name: String, val last_name: String)
    data class RegisterResponse(val id: Int, val email: String, val first_name: String, val last_name: String)

    @POST("/api/v1/accounts/register/")
    fun registerUser(@Body request: RegisterRequest): Call<RegisterResponse>

    data class UpdateProfileRequest(
        @SerializedName("email") val email: String?,
        @SerializedName("first_name") val firstName: String?,
        @SerializedName("last_name") val lastName: String?,
        @SerializedName("old_password") val oldPassword: String?,
        @SerializedName("password") val newPassword: String?
    )

    data class UpdateProfileResponse(
        val email: String,
        val firstName: String,
        val lastName: String
    )

    @PATCH("/api/v1/accounts/update/")
    fun updateProfile(@Body request: UpdateProfileRequest, @Header("X-CSRFToken") xcsrfToken: String): Call<UpdateProfileResponse>

    @GET("/api/v1/get_csrf_token/")
    fun getXCSRFToken(): Call<ResponseBody>

    data class UrgentContact(
        val id: Int,
        val name: String,
        val email: String
    )

    data class NewUrgentContact(
        val name: String,
        val email: String
    )

    @GET("/api/v1/urgent-contacts/")
    fun getUrgentContacts(): Call<List<UrgentContact>>

    @POST("/api/v1/urgent-contacts/")
    fun createUrgentContact(@Body newUrgentContact: NewUrgentContact): Call<UrgentContact>

    @PUT("/api/v1/urgent-contacts/{contactId}/")
    fun updateUrgentContact(
        @Path("contactId") contactId: Int,
        @Body updatedUrgentContact: NewUrgentContact
    ): Call<UrgentContact>

    @DELETE("api/v1/urgent-contacts/{id}/")
    fun deleteUrgentContact(@Path("id") id: Int): Call<Void>

    @POST("/api/v1/urgent-contacts/send-email/")
    fun sendEmail(@Body locationRequestBody: RequestBody): Call<ResponseBody>
}