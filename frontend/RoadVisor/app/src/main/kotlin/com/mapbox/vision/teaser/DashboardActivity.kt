package com.mapbox.vision.teaser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import kotlinx.android.synthetic.main.activity_dashboard.*

class DashboardActivity : AppCompatActivity() {
    private lateinit var navigationIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setupLogoutButton()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        navigationIcon = findViewById(R.id.navigation_icon)

        navigationIcon.setOnClickListener {
            if (traffic_light_sign_detection_switch.isChecked && !augmented_reality_navigation_switch.isChecked
                && !pedestrian_detection_switch.isChecked && !emergency_assistance_switch.isChecked &&
                    !rear_camera_recording_switch.isChecked) {
                startSignDetectionFragment()
            }
            else {
                goToMainActivity()
            }
        }

        val profileSettingsButton = findViewById<ImageButton>(R.id.profile_settings_button)
        profileSettingsButton.setOnClickListener {
            // Start Profile Settings Activity
            startActivity(Intent(this, AccountSettingsActivity::class.java))
        }

        // New feature switches
        val augmentedRealityNavigationSwitch = findViewById<SwitchCompat>(R.id.augmented_reality_navigation_switch)
        val trafficLightSignDetectionSwitch = findViewById<SwitchCompat>(R.id.traffic_light_sign_detection_switch)
        val pedestrianDetectionSwitch = findViewById<SwitchCompat>(R.id.pedestrian_detection_switch)
        val emergencyAssistanceSwitch = findViewById<SwitchCompat>(R.id.emergency_assistance_switch)
        val rearCameraRecordingSwitch = findViewById<SwitchCompat>(R.id.rear_camera_recording_switch)

        augmentedRealityNavigationSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Handle Rear Camera Recording state change
        }

        trafficLightSignDetectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Handle Rear Camera Recording state change
        }

        pedestrianDetectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Handle Rear Camera Recording state change
        }

        emergencyAssistanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Handle Rear Camera Recording state change
        }

        rearCameraRecordingSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Handle Rear Camera Recording state change
        }

        // Add listeners for more features here
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun startSignDetectionFragment() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("fragment", "sign")
        startActivity(intent)
    }

    private fun setLoggedInStatus(status: Boolean) {
        val sharedPreferences = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("isLoggedIn", status)
        editor.apply()
    }

    private fun setupLogoutButton() {
        val logoutButton: ImageView = findViewById(R.id.logout_button)

        logoutButton.setOnClickListener {
            // Perform any logout-related tasks, e.g., clear session data
            setLoggedInStatus(false)
            clearSharedPreferences()

            // Navigate back to the login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun clearSharedPreferences() {
        // Clear myAppPrefs
        val myAppPrefs = getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val myAppPrefsEditor = myAppPrefs.edit()
        myAppPrefsEditor.clear()
        myAppPrefsEditor.apply()

        // Clear UrgentContactsPrefs
        val urgentContactsPrefs = getSharedPreferences("UrgentContactsPrefs", Context.MODE_PRIVATE)
        val urgentContactsPrefsEditor = urgentContactsPrefs.edit()
        urgentContactsPrefsEditor.clear()
        urgentContactsPrefsEditor.apply()
    }
}
