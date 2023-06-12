package com.mapbox.vision.teaser.ar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.vision.VisionManager
import com.mapbox.vision.ar.VisionArManager
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.detection.DetectionClass
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.teaser.R
import com.mapbox.vision.teaser.api.RetrofitClient
import com.mapbox.vision.teaser.models.ArFeature
import com.mapbox.vision.teaser.utils.buildNavigationOptions
import com.mapbox.vision.teaser.utils.getRoutePoints
import kotlinx.android.synthetic.main.activity_ar_navigation.*
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArNavigationActivity : AppCompatActivity(), RoutesObserver {

    companion object {
        private val TAG = ArNavigationActivity::class.java.simpleName

        private const val ARG_INPUT_JSON_ROUTE = "ARG_INPUT_JSON_ROUTE"

        fun start(context: Activity, jsonRoute: String) {
            val intent = Intent(context, ArNavigationActivity::class.java).apply {
                putExtra(ARG_INPUT_JSON_ROUTE, jsonRoute)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var directionsRoute: DirectionsRoute
    private lateinit var mapboxNavigation: MapboxNavigation

    private var activeArFeature: ArFeature = ArFeature.LaneAndFence

    private lateinit var mediaPlayer: MediaPlayer
    private var drawPedestrian = false
    private lateinit var alertLayout: RelativeLayout

    private val visionEventsListener = object : VisionEventsListener {
        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {
            for (detection in frameDetections.detections) {
                if (detection.detectionClass == DetectionClass.Person && detection.confidence > 0.6) {
                    drawPedestrian = true
                }
            }

            if (drawPedestrian) {
                runOnUiThread {
                    alertLayout.visibility = View.VISIBLE
                    mediaPlayer.start()
                    drawPedestrian = false
                }
            }
            else {
                runOnUiThread {
                    alertLayout.visibility = View.INVISIBLE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_navigation)

        val jsonRoute = intent.getStringExtra(ARG_INPUT_JSON_ROUTE)
        if (jsonRoute.isNullOrEmpty()) {
            finish()
        }

        directionsRoute = DirectionsRoute.fromJson(jsonRoute)

        back.setOnClickListener {
            onBackPressed()
        }

        applyArFeature()
        ar_mode_view.setOnClickListener {
            activeArFeature = activeArFeature.getNextFeature()
            applyArFeature()
        }

        ar_mode_emergency.setOnClickListener {
            showEmergencyAssistanceDialog()
        }

        mapboxNavigation = MapboxNavigation(buildNavigationOptions())

        mediaPlayer = MediaPlayer.create(this, R.raw.lane_departure_warning)

        alertLayout = findViewById(R.id.alert_layout)
    }

    private fun applyArFeature() {
        ar_mode_view.setImageResource(activeArFeature.drawableId)
        ar_view.setLaneVisible(activeArFeature.isLaneVisible)
        ar_view.setFenceVisible(activeArFeature.isFenceVisible)
    }


    override fun onResume() {
        super.onResume()

        if (ContextCompat.checkSelfPermission(this@ArNavigationActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) !==
            PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@ArNavigationActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this@ArNavigationActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this@ArNavigationActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
        mapboxNavigation?.startTripSession()
        mapboxNavigation.setRoutes(listOf(directionsRoute))

        VisionManager.create()
        VisionManager.visionEventsListener = visionEventsListener
        VisionManager.start()
        VisionManager.setModelPerformance(
            ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.LOW)
        )

        VisionArManager.create(VisionManager)
        ar_view.setArManager(VisionArManager)
        ar_view.onResume()

        VisionArManager.setRoute(
            Route(
                points = directionsRoute.getRoutePoints(),
                eta = directionsRoute.duration().toFloat()
            )
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                    if ((ContextCompat.checkSelfPermission(this@ArNavigationActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION) ===
                                PackageManager.PERMISSION_GRANTED)) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ar_view.onPause()
        VisionArManager.destroy()

        VisionManager.stop()
        VisionManager.destroy()

        mapboxNavigation.stopTripSession()
        mapboxNavigation.setRoutes(emptyList())
    }

    override fun onRoutesChanged(routes: List<DirectionsRoute>) {
        println("Routes changed ${routes.joinToString(", ")}")
        // TODO
    }

    private fun showEmergencyAssistanceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.emergency_assistance_dialog, null)
        dialogView.setBackgroundColor(Color.parseColor("#232E3D")) // Arka plan rengini güncelle

        val messageTextView = dialogView.findViewById<TextView>(R.id.message_text_view)
        val timerTextView = dialogView.findViewById<TextView>(R.id.timer_text_view)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val sendButton = dialogView.findViewById<Button>(R.id.yes_button)

        messageTextView.text = "Do you need immediate assistance? Your designated contacts and emergency services will be notified."

        cancelButton.setBackgroundColor(Color.parseColor("#F7D358"))
        sendButton.setBackgroundColor(Color.parseColor("#F7D358"))

        var isCancelled = false

        val alertDialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener {
            isCancelled = true
            alertDialog.dismiss()
        }

        // Prepare a timer for auto-dismiss and triggering the positive action
        val autoTriggerTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingSeconds = millisUntilFinished / 1000
                timerTextView.text = "$remainingSeconds"
            }

            override fun onFinish() {
                if (!isCancelled) {
                    val location = getCurrentLocation()
                    sendRequestToAPI(location)
                }
                alertDialog.dismiss()
            }
        }

        sendButton.setOnClickListener {
            isCancelled = true
            val location = getCurrentLocation()
            sendRequestToAPI(location)
            alertDialog.dismiss()
        }

        // Start the timer
        autoTriggerTimer.start()
        alertDialog.show()

        // Setting size of the dialog window
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        //setting width and height to 90% of the display
        alertDialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun getCurrentLocation(): String {
        val currentStep = directionsRoute.legs()?.firstOrNull()?.steps()?.firstOrNull()
        val currentLocation = currentStep?.maneuver()?.location()
        return if (currentLocation != null) {
            "${currentLocation.latitude()},${currentLocation.longitude()}"
        } else {
            ""
        }
    }

    private fun sendRequestToAPI(location: String) {
        val apiService = RetrofitClient.create(this, true)

        val json = MediaType.parse("application/json; charset=utf-8")
        val requestBody = RequestBody.create(json, "{\"location\": \"$location\"}")

        apiService.sendEmail(requestBody).enqueue(object : Callback<ResponseBody> {
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ArNavigationActivity, "Failed to send emergency assistance request. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ArNavigationActivity, "Emergency assistance request sent successfully.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ArNavigationActivity, "Failed to send emergency assistance request. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
