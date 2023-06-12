package com.mapbox.vision.teaser

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.vision.teaser.api.ApiService
import com.mapbox.vision.teaser.api.ApiService.UrgentContact
import com.mapbox.vision.teaser.api.ApiService.NewUrgentContact
import com.mapbox.vision.teaser.api.RetrofitClient
import com.mapbox.vision.teaser.view.UrgentContactAdapter
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.appcompat.app.AlertDialog

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var firstNameInputLayout: TextInputLayout
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var lastNameInputLayout: TextInputLayout
    private lateinit var lastNameInput: TextInputEditText
    private lateinit var oldPasswordInputLayout: TextInputLayout
    private lateinit var oldPasswordInput: TextInputEditText
    private lateinit var newPasswordInputLayout: TextInputLayout
    private lateinit var newPasswordInput: TextInputEditText
    private lateinit var updateButton: Button
    private lateinit var addUrgentContactButton: Button
    private lateinit var updateUrgentContactButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    private val apiService by lazy { RetrofitClient.create(applicationContext, useAuthHeaders = true) }

    private lateinit var rvUrgentContacts: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        emailInputLayout = findViewById(R.id.email_input_layout)
        emailInput = findViewById(R.id.email_input)
        firstNameInputLayout = findViewById(R.id.first_name_input_layout)
        firstNameInput = findViewById(R.id.first_name_input)
        lastNameInputLayout = findViewById(R.id.last_name_input_layout)
        lastNameInput = findViewById(R.id.last_name_input)
        oldPasswordInputLayout = findViewById(R.id.old_password_input_layout)
        oldPasswordInput = findViewById(R.id.old_password_input)
        newPasswordInputLayout = findViewById(R.id.new_password_input_layout)
        newPasswordInput = findViewById(R.id.new_password_input)
        updateButton = findViewById(R.id.update_button)
        addUrgentContactButton = findViewById(R.id.add_urgent_contact_button)
        updateUrgentContactButton = findViewById(R.id.update_urgent_contact_button)
        sharedPreferences = getSharedPreferences("UrgentContactsPrefs", Context.MODE_PRIVATE)

        setProfileInfo()

        // Set click listener for update button
        updateButton.setOnClickListener {
            if (validateInput()) {
                getXCSRFToken { xcsrfToken ->
                    updateProfile(xcsrfToken)
                }
            }
        }

        addUrgentContactButton.setOnClickListener {
            val context = it.context
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.dialog_add_urgent_contact, null)

            val nameEditText: TextInputEditText = view.findViewById(R.id.name_edit_text)
            val emailEditText: TextInputEditText = view.findViewById(R.id.email_edit_text)

            val alertDialog: AlertDialog = AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setTitle("Add Urgent Contact")
                .setView(view)
                .setPositiveButton("Add") { _, _ ->
                    val name = nameEditText.text.toString().trim()
                    val email = emailEditText.text.toString().trim()

                    if (name.isNotEmpty() && email.isNotEmpty()) {
                        addNewUrgentContact(name, email) { isSuccess ->
                            if (isSuccess) {
                                fetchUrgentContacts()
                            } else {
                                Toast.makeText(this@AccountSettingsActivity, "Failed to add urgent contact", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@AccountSettingsActivity, "Please enter both name and email", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            alertDialog.window?.setBackgroundDrawableResource(R.drawable.rounded_background)
            alertDialog.show()

            val window = alertDialog.window
            val size = Point()

            val display = window?.windowManager?.defaultDisplay
            display?.getSize(size)

            val height = size.y * 0.5

            window?.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, height.toInt())
        }

        updateUrgentContactButton.setOnClickListener {
            val adapter = rvUrgentContacts.adapter as UrgentContactAdapter
            val selectedItemPosition = adapter.selectedItemPosition
            if (selectedItemPosition != -1) {
                val selectedContact = adapter.getSelectedItem()

                val context = it.context
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(R.layout.dialog_add_urgent_contact, null)

                val nameEditText: TextInputEditText = view.findViewById(R.id.name_edit_text)
                nameEditText.setText(selectedContact?.name)

                val emailEditText: TextInputEditText = view.findViewById(R.id.email_edit_text)
                emailEditText.setText(selectedContact?.email)

                val alertDialog: AlertDialog = AlertDialog.Builder(context, R.style.AlertDialogCustom)
                    .setTitle("Update Urgent Contact")
                    .setView(view)
                    .setPositiveButton("Update") { _, _ ->
                        val name = nameEditText.text.toString().trim()
                        val email = emailEditText.text.toString().trim()

                        if (name.isNotEmpty() && email.isNotEmpty()) {
                            if (name == selectedContact?.name && email == selectedContact.email) {
                                Toast.makeText(this@AccountSettingsActivity, "No changes detected", Toast.LENGTH_SHORT).show()
                            } else {
                                selectedContact?.id?.let { it1 ->
                                    updateUrgentContact(it1, name, email) { updatedUrgentContact ->
                                        if (updatedUrgentContact != null) {
                                            (rvUrgentContacts.adapter as UrgentContactAdapter).updateItemAtPosition(selectedItemPosition, updatedUrgentContact)
                                        } else {
                                            Toast.makeText(this@AccountSettingsActivity, "Failed to update urgent contact", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(this@AccountSettingsActivity, "Please enter both name and email", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .create()

                alertDialog.window?.setBackgroundDrawableResource(R.drawable.rounded_background)
                alertDialog.show()

                // Ekranın yüksekliğinin %50'si kadar bir yükseklik belirle
                val window = alertDialog.window
                val size = Point()

                val display = window?.windowManager?.defaultDisplay
                display?.getSize(size)

                val height = size.y * 0.5

                window?.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, height.toInt())
            } else {
                Toast.makeText(this@AccountSettingsActivity, "Please select an urgent contact to update", Toast.LENGTH_SHORT).show()
            }
        }

        rvUrgentContacts = findViewById(R.id.rv_urgent_contacts)

        fetchUrgentContacts()
    }

    override fun onResume() {
        super.onResume()
        setProfileInfo()
        fetchUrgentContacts()
    }

    private fun addNewUrgentContact(name: String, email: String, onComplete: (Boolean) -> Unit) {
        val newUrgentContact = NewUrgentContact(name, email)
        apiService
            .createUrgentContact(newUrgentContact)
            .enqueue(
                object : Callback<UrgentContact> {
                    override fun onResponse(call: Call<UrgentContact>, response: Response<UrgentContact>) {
                        if (response.isSuccessful) {
                            val addedContact = response.body()
                            if (addedContact != null) {
                                val urgentContacts = sharedPreferences.getString("urgentContacts", null)
                                val contactList =
                                    if (urgentContacts != null) {
                                        Gson()
                                            .fromJson(
                                                urgentContacts,
                                                object : TypeToken<List<UrgentContact>>() {}.type
                                            )
                                    } else {
                                        mutableListOf<UrgentContact>()
                                    }
                                contactList.add(addedContact)
                                sharedPreferences
                                    .edit()
                                    .putString("urgentContacts", Gson().toJson(contactList))
                                    .apply()
                                onComplete(true)
                            } else {
                                onComplete(false)
                            }
                        } else {
                            onComplete(false)
                        }
                    }
                    override fun onFailure(call: Call<UrgentContact>, t: Throwable) {
                        onComplete(false)
                    }
                }
            )
    }

    private fun updateUrgentContact(contactId: Int, name: String, email: String, onComplete: (UrgentContact?) -> Unit) {
        val updatedUrgentContact = NewUrgentContact(name, email)
        apiService
            .updateUrgentContact(contactId, updatedUrgentContact)
            .enqueue(
                object : Callback<UrgentContact> {
                    override fun onResponse(call: Call<UrgentContact>, response: Response<UrgentContact>) {
                        if (response.isSuccessful) {
                            onComplete(response.body())
                        } else {
                            onComplete(null)
                        }
                    }
                    override fun onFailure(call: Call<UrgentContact>, t: Throwable) {
                        onComplete(null)
                    }
                }
            )
    }

    private fun validateInput(): Boolean {
        var isValid = true

        val email = emailInput.text.toString().trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Please enter a valid email address"
            isValid = false
        } else {
            emailInputLayout.error = null
        }

        val firstName = firstNameInput.text.toString().trim()
        if (firstName.isEmpty()) {
            firstNameInputLayout.error = "Please enter your first name"
            isValid = false
        } else {
            firstNameInputLayout.error = null
        }

        val lastName = lastNameInput.text.toString().trim()
        if (lastName.isEmpty()) {
            lastNameInputLayout.error = "Please enter your last name"
            isValid = false
        } else {
            lastNameInputLayout.error = null
        }

        val oldPassword = oldPasswordInput.text.toString().trim()
        if (oldPassword.isNotEmpty() && oldPassword.length < 6) {
            oldPasswordInputLayout.error = "Password must be at least 6 characters long"
            isValid = false
        } else {
            oldPasswordInputLayout.error = null
        }

        val newPassword = newPasswordInput.text.toString().trim()
        if (newPassword.isNotEmpty() && newPassword.length < 6) {
            newPasswordInputLayout.error = "Password must be at least 6 characters long"
            isValid = false
        } else {
            newPasswordInputLayout.error = null
        }
        return isValid
    }

    private fun fetchUrgentContacts() {
        val savedContacts = sharedPreferences.getString("urgentContacts", null)
        if (savedContacts != null) {
            displayUrgentContacts(Gson().fromJson(savedContacts, object : TypeToken<List<UrgentContact>>() {}.type))
        } else {
            apiService.getUrgentContacts().enqueue(object : Callback<List<UrgentContact>> {
                override fun onResponse(
                    call: Call<List<UrgentContact>>,
                    response: Response<List<UrgentContact>>
                ) {
                    if (response.isSuccessful) {
                        val urgentContacts = response.body()
                        if (urgentContacts != null) {
                            displayUrgentContacts(urgentContacts)
                            saveUrgentContacts(urgentContacts)
                        } else {
                            Toast.makeText(
                                this@AccountSettingsActivity,
                                "Failed to fetch urgent contacts",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@AccountSettingsActivity,
                            "Error: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                private fun saveUrgentContacts(urgentContacts: List<UrgentContact>) {
                    sharedPreferences.edit().putString("urgentContacts", Gson().toJson(urgentContacts)).apply()
                }

                override fun onFailure(call: Call<List<UrgentContact>>, t: Throwable) {
                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun displayUrgentContacts(contacts: List<UrgentContact>) {
        val mutableContacts = contacts.toMutableList()
        val adapter = UrgentContactAdapter(mutableContacts, apiService, sharedPreferences)
        rvUrgentContacts.adapter = adapter
        rvUrgentContacts.layoutManager = LinearLayoutManager(this)
    }

    private fun updateProfile(xcsrfToken: String) {
        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPreferences.getString("email", "")
        val savedFirstName = sharedPreferences.getString("first_name", "")
        val savedLastName = sharedPreferences.getString("last_name", "")

        val email = emailInput.text.toString().trim().takeIf { it.isNotEmpty() && it != savedEmail }
        val firstName = firstNameInput.text.toString().trim().takeIf { it.isNotEmpty() && it != savedFirstName }
        val lastName = lastNameInput.text.toString().trim().takeIf { it.isNotEmpty() && it != savedLastName }
        val oldPassword = oldPasswordInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val newPassword = newPasswordInput.text.toString().trim().takeIf { it.isNotEmpty() }

        // Build the request with X-CSRFToken header
        val updateRequest = ApiService.UpdateProfileRequest(
            email,
            firstName,
            lastName,
            oldPassword,
            newPassword
        )
        apiService.updateProfile(updateRequest, xcsrfToken).enqueue(object : Callback<ApiService.UpdateProfileResponse> {
            override fun onResponse(
                call: Call<ApiService.UpdateProfileResponse>,
                response: Response<ApiService.UpdateProfileResponse>
            ) {
                if (response.isSuccessful) {
                    val updatedProfile = response.body()
                    if (updatedProfile != null) {
                        Toast.makeText(
                            this@AccountSettingsActivity,
                            "Profile updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        sharedPreferences.edit().apply {
                            updateRequest.email?.let { putString("email", it) }
                            updateRequest.firstName?.let { putString("first_name", it) }
                            updateRequest.lastName?.let { putString("last_name", it) }
                            apply()
                        }
                    } else {
                        Toast.makeText(
                            this@AccountSettingsActivity,
                            "Unable to update profile",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Error: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<ApiService.UpdateProfileResponse>, t: Throwable) {
                Toast.makeText(
                    this@AccountSettingsActivity,
                    "Error: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun getXCSRFToken(onTokenReceived: (String) -> Unit) {
        apiService.getXCSRFToken().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val csrfToken = response.headers()["Set-Cookie"]
                        ?.split(";")
                        ?.firstOrNull { it.contains("csrftoken") }
                        ?.split("=")
                        ?.getOrNull(1)

                    if (!csrfToken.isNullOrEmpty()) {
                        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit().apply {
                            putString("xcsrftoken", csrfToken)
                            apply()
                        }
                        onTokenReceived(csrfToken)
                    }
                } else {
                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Failed to get CSRF token",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(
                    this@AccountSettingsActivity,
                    "Failed to get CSRF token: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun setProfileInfo() {
        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val email = sharedPreferences.getString("email", "")
        val firstName = sharedPreferences.getString("first_name", "")
        val lastName = sharedPreferences.getString("last_name", "")

        emailInput.setText(email)
        firstNameInput.setText(firstName)
        lastNameInput.setText(lastName)
    }
}

