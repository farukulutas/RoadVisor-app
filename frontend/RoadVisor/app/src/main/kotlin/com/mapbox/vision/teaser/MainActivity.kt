package com.mapbox.vision.teaser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.method.LinkMovementMethod
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.CountDownTimer
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.vision.VisionManager
import com.mapbox.vision.VisionReplayManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.utils.SystemInfoUtils
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.teaser.MainActivity.VisionMode.Camera
import com.mapbox.vision.teaser.MainActivity.VisionMode.Replay
import com.mapbox.vision.teaser.api.RetrofitClient
import com.mapbox.vision.teaser.ar.ArMapActivity
import com.mapbox.vision.teaser.ar.ArNavigationActivity
import com.mapbox.vision.teaser.recorder.RecorderFragment
import com.mapbox.vision.teaser.replayer.ArReplayNavigationActivity
import com.mapbox.vision.teaser.replayer.ReplayModeFragment
import com.mapbox.vision.teaser.utils.PermissionsUtils
import com.mapbox.vision.teaser.utils.dpToPx
import com.mapbox.vision.teaser.view.hide
import com.mapbox.vision.teaser.view.show
import com.mapbox.vision.teaser.view.toggleVisibleGone
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.view.VisionView
import com.mapbox.vision.view.VisualizationMode
import kotlinx.android.synthetic.main.activity_ar_navigation.*
import java.io.File
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.ar_mode_emergency
import kotlinx.android.synthetic.main.activity_main.back
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity(), ReplayModeFragment.OnSelectModeItemListener {

    enum class VisionMode {
        Camera,
        Replay
    }

    companion object {
        private val BASE_SESSION_PATH =
            "${Environment.getExternalStorageDirectory().absolutePath}/MapboxVisionTelemetry"
        private const val START_AR_MAP_ACTIVITY_FOR_NAVIGATION_RESULT_CODE = 100
        private const val START_AR_MAP_ACTIVITY_FOR_RECORDING_RESULT_CODE = 110
    }

    private var country = Country.Unknown
    private var visionMode = Camera
    private var sessionPath = ""

    private var isPermissionsGranted = false
    private var visionManagerWasInit = false
    private var modelPerformance =
        ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH)

    private lateinit var directionsRoute: DirectionsRoute

    private lateinit var mediaPlayer: MediaPlayer

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var personDetected = false

    private val visionEventsListener = object : VisionEventsListener {

        override fun onCountryUpdated(country: Country) {
            runOnUiThread {
                this@MainActivity.country = country
                requireBaseVisionFragment()?.updateCountry(country)
            }
        }

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {
            runOnUiThread {
                requireSignDetectionFragment()?.drawSigns(frameSignClassifications)
            }
        }

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {
            runOnUiThread {
                requireLaneDetectionFragment()?.drawLanesDetection(roadDescription)
            }
        }

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            runOnUiThread {
                requireBaseVisionFragment()?.updateLastSpeed(vehicleState.speed)
            }
        }

        override fun onCameraUpdated(camera: com.mapbox.vision.mobile.core.models.Camera) {
            runOnUiThread {
                requireBaseVisionFragment()?.updateCalibrationProgress(camera.calibrationProgress)
                fps_performance_view.setCalibrationProgress(camera.calibrationProgress)
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onUpdateCompleted() {
            runOnUiThread {
                if (visionManagerWasInit) {
                    val frameStatistics = when (visionMode) {
                        Camera -> VisionManager.getFrameStatistics()
                        Replay -> VisionReplayManager.getFrameStatistics()
                    }
                    fps_performance_view.showInfo(frameStatistics)
                    if (visionMode == Replay) {
                        playback_seek_bar_view.setProgress(VisionReplayManager.getProgress())
                    }
                }
            }
        }

        /*override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {
            for (detection in frameDetections.detections) {
                if (detection.detectionClass == DetectionClass.Person && detection.confidence > 0.6) {
                    pedestrian.visibility = View.VISIBLE
                }
            }
        }*/
    }


override fun onCreate(savedInstanceState: Bundle?) {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onCreate(savedInstanceState)

    if (!SystemInfoUtils.isVisionSupported()) {
        AlertDialog.Builder(this)
            .setTitle(R.string.vision_not_supported_title)
            .setView(
                TextView(this).apply {
                    setPadding(dpToPx(20f).toInt())
                    movementMethod = LinkMovementMethod.getInstance()
                    isClickable = true
                    text = HtmlCompat.fromHtml(
                        getString(R.string.vision_not_supported_message),
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                }
            )
            .setCancelable(false)
            .show()

        VisionLogger.e(
            "BoardNotSupported",
            "System Info: [${SystemInfoUtils.obtainSystemInfo()}]"
        )
    }

    setContentView(R.layout.activity_main)

    if (!PermissionsUtils.requestPermissions(this)) {
        onPermissionsGranted()
    }

    if (intent.getStringExtra("fragment") == "sign") {
        showSignDetectionFragment()
    }

    ar_mode_emergency.setOnClickListener {
        showEmergencyAssistanceDialog()
    }

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    } else {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                Toast.makeText(this, "Lat: ${location?.latitude}, Long: ${location?.longitude}", Toast.LENGTH_LONG).show()
            }
    }
}

private fun createSessionFolderIfNotExist() {
    val folder = File(BASE_SESSION_PATH)
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            throw IllegalStateException("Can't create image folder = $folder")
        }
    }
}

private fun onPermissionsGranted() {
    isPermissionsGranted = true

    createSessionFolderIfNotExist()
    back.setOnClickListener { onBackClick() }

    initRootLongTap()
    initRootTap()
    fps_performance_view.hide()

    initArNavigationButton()
    initReplayModeButton()
    startVision()
}

private fun initRootLongTap() {
    root.setOnLongClickListener {
        fps_performance_view.toggleVisibleGone()
        return@setOnLongClickListener true
    }
}

private fun initRootTap() {
    root.setOnClickListener {
        if (visionMode == Replay) {
            playback_seek_bar_view.toggleVisibleGone()
        }
    }
}

private fun initArNavigationButton() {
    ar_navigation_button_container.setOnClickListener {
        val locationPermissionCode = 100

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                when (visionMode) {
                    Camera -> startArMapActivityForNavigation()
                    Replay -> startArSession()
                }
            } else {
                showLocationSnackbar()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionCode
            )
        }
    }
}

private fun showLocationSnackbar() {
    Snackbar.make(
        ar_navigation_button_container,
        "You need to give permission to open your location.",
        Snackbar.LENGTH_LONG
    )
        .setAction("Open", View.OnClickListener {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivityForResult(intent, 1001)
            } else {
                // Location services already turned on
                Toast.makeText(this, "Location services already turned on", Toast.LENGTH_SHORT)
                    .show()
            }
        }).show()
}

private fun initReplayModeButton() {
    replay_mode_button_container.setOnClickListener {
        showReplayModeFragment()
    }
}

private fun startVision() {
    if (isPermissionsGranted && !visionManagerWasInit) {
        visionManagerWasInit = when (visionMode) {
            Camera -> initVisionManagerCamera(vision_view)
            Replay -> initVisionManagerReplay(vision_view, sessionPath)
        }
    }
}

private fun stopVision() {
    if (isPermissionsGranted && visionManagerWasInit) {
        visionManagerWasInit = false
        when (visionMode) {
            Camera -> destroyVisionManagerCamera()
            Replay -> destroyVisionManagerReplay()
        }
    }
}

override fun onResume() {
    super.onResume()
    startVision()
    vision_view.onResume()
}

override fun onPause() {
    super.onPause()
    stopVision()
    vision_view.onPause()
}

private fun onBackClick() {
    dashboard_container.show()
    back.hide()
    playback_seek_bar_view.hide()
}

private fun initVisionManagerCamera(visionView: VisionView): Boolean {
    VisionManager.create()
    visionView.setVisionManager(VisionManager)
    VisionManager.visionEventsListener = visionEventsListener
    VisionManager.start()
    VisionManager.setModelPerformance(modelPerformance)
    return true
}

private fun destroyVisionManagerCamera() {
    VisionManager.stop()
    VisionManager.destroy()
}

private fun initVisionManagerReplay(visionView: VisionView, sessionPath: String): Boolean {
    if (sessionPath.isEmpty()) {
        return false
    }

    this.sessionPath = sessionPath
    VisionReplayManager.create(sessionPath)
    VisionReplayManager.visionEventsListener = visionEventsListener
    VisionReplayManager.start()
    VisionReplayManager.setModelPerformance(modelPerformance)
    visionView.setVisionManager(VisionReplayManager)

    playback_seek_bar_view.setDuration(VisionReplayManager.getDuration())
    playback_seek_bar_view.onSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                VisionReplayManager.setProgress(progress.toFloat())
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }

    return true
}

private fun destroyVisionManagerReplay() {
    playback_seek_bar_view.onSeekBarChangeListener = null
    VisionReplayManager.stop()
    VisionReplayManager.destroy()
}

private fun showReplayModeFragment(stateLoss: Boolean = false) {
    val fragment = ReplayModeFragment.newInstance(BASE_SESSION_PATH)
    showFragment(fragment, ReplayModeFragment.TAG, stateLoss)
}

private fun showRecorderFragment(jsonRoute: String?, stateLoss: Boolean = false) {
    val fragment = RecorderFragment.newInstance(BASE_SESSION_PATH, jsonRoute)
    showFragment(fragment, RecorderFragment.TAG, stateLoss)
}

private fun showSafetyFragment(stateLoss: Boolean = false) {
    vision_view.visualizationMode = VisualizationMode.Clear
    val fragment = SafetyFragment.newInstance()
    showFragment(fragment, SafetyFragment.TAG, stateLoss)
}

private fun showSignDetectionFragment(stateLoss: Boolean = false) {
    vision_view.visualizationMode = VisualizationMode.Clear
    ar_mode_emergency.visibility = View.VISIBLE
    val fragment = SignDetectionFragment.newInstance(country)
    showFragment(fragment, SignDetectionFragment.TAG, stateLoss)
}

private fun showLaneFragment(stateLoss: Boolean = false) {
    vision_view.visualizationMode = VisualizationMode.LaneDetection
    val fragment = LaneFragment.newInstance()
    showFragment(fragment, LaneFragment.TAG, stateLoss)
}

private fun showSegmentationFragment(stateLoss: Boolean = false) {
    vision_view.visualizationMode = VisualizationMode.Segmentation
    showSegmentationDetectionFragment(stateLoss)
}

private fun showObjectDetectionFragment(stateLoss: Boolean = false) {
    vision_view.visualizationMode = VisualizationMode.Detection
    showSegmentationDetectionFragment(stateLoss)
}

private fun showSegmentationDetectionFragment(stateLoss: Boolean = false) {
    val fragment = SegmentationDetectionFragment.newInstance()
    showFragment(fragment, SegmentationDetectionFragment.TAG, stateLoss)
}

private fun showFragment(fragment: Fragment, tag: String, stateLoss: Boolean = false) {
    val fragmentTransaction = supportFragmentManager
        .beginTransaction()
        .replace(R.id.fragment_container, fragment, tag)
        .addToBackStack(tag)
    if (stateLoss) {
        fragmentTransaction.commitAllowingStateLoss()
    } else {
        fragmentTransaction.commit()
    }
    hideDashboardView()
}

private fun hideDashboardView() {
    dashboard_container.hide()
    title_teaser.hide()
}

private fun showDashboardView() {
    dashboard_container.show()
    title_teaser.show()
    playback_seek_bar_view.hide()
}

override fun onBackPressed() {
    val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
    if (fragment != null) {
        if (!(fragment is OnBackPressedListener && fragment.onBackPressed())) {

            if (fragment is RecorderFragment) {
                when (visionMode) {
                    Camera -> VisionManager.setModelPerformance(modelPerformance)
                    Replay -> VisionReplayManager.setModelPerformance(modelPerformance)
                }
            }

            if (supportFragmentManager.popBackStackImmediate() && supportFragmentManager.backStackEntryCount == 0) {
                showDashboardView()
            }
        }
    } else {
        super.onBackPressed()
    }
}

override fun onSessionSelected(sessionName: String) {
    stopVision()
    visionMode = Replay
    sessionPath = "$BASE_SESSION_PATH/$sessionName"
    startVision()
}

override fun onCameraSelected() {
    stopVision()
    visionMode = Camera
    sessionPath = ""
    startVision()
}

override fun onRecordingSelected() {
    startArMapActivityForRecording()
}

private fun startArMapActivityForNavigation() {
    startArMapActivity(START_AR_MAP_ACTIVITY_FOR_NAVIGATION_RESULT_CODE)
}

private fun startArMapActivityForRecording() {
    startArMapActivity(START_AR_MAP_ACTIVITY_FOR_RECORDING_RESULT_CODE)
}

private fun startArMapActivity(resultCode: Int) {
    val intent = Intent(this@MainActivity, ArMapActivity::class.java)
    startActivityForResult(intent, resultCode)
}

private fun startArSession() {
    if (sessionPath.isEmpty()) {
        Toast.makeText(this, "Select a session first.", Toast.LENGTH_SHORT).show()
    } else {
        ArReplayNavigationActivity.start(this, sessionPath)
    }
}

override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (PermissionsUtils.arePermissionsGranted(this, requestCode)) {
        onPermissionsGranted()
    } else {
        val notGranted = PermissionsUtils.getNotGrantedPermissions(this).joinToString(", ")

        AlertDialog.Builder(this)
            .setTitle(R.string.permissions_missing_title)
            .setMessage(
                getString(R.string.permissions_missing_message, notGranted)
            )
            .setCancelable(false)
            .setPositiveButton(
                R.string.request_permissions
            ) { _, _ -> PermissionsUtils.requestPermissions(this) }
            .show()

        VisionLogger.e(
            MainActivity::class.java.simpleName,
            "Permissions are not granted : $notGranted"
        )
    }
}

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
        START_AR_MAP_ACTIVITY_FOR_NAVIGATION_RESULT_CODE -> {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val jsonRoute = data.getStringExtra(ArMapActivity.ARG_RESULT_JSON_ROUTE)
                if (!jsonRoute.isNullOrEmpty()) {
                    ArNavigationActivity.start(this, jsonRoute)
                }
            }
        }
        START_AR_MAP_ACTIVITY_FOR_RECORDING_RESULT_CODE -> {
            val jsonRoute = data?.getStringExtra(ArMapActivity.ARG_RESULT_JSON_ROUTE)
            onCameraSelected()
            vision_view.visualizationMode = VisualizationMode.Clear

            // set lowest model performance to allow fair 30 fps all the time
            VisionManager.setModelPerformance(ModelPerformance.Off)

            // Using state loss here to keep code simple, lost of RecorderFragment is not critical for UX
            showRecorderFragment(jsonRoute, stateLoss = true)
        }
        else -> super.onActivityResult(requestCode, resultCode, data)
    }
}

private fun requireSignDetectionFragment() = requireBaseVisionFragment() as? SignDetectionFragment

private fun requireLaneDetectionFragment() = requireBaseVisionFragment() as? LaneFragment

private fun requireBaseVisionFragment() =
    supportFragmentManager.findFragmentById(R.id.fragment_container) as? BaseVisionFragment

fun isCameraMode() = visionMode == Camera

private fun showEmergencyAssistanceDialog() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.emergency_assistance_dialog, null)
    dialogView.setBackgroundColor(Color.parseColor("#232E3D"))

    val messageTextView = dialogView.findViewById<TextView>(R.id.message_text_view)
    val timerTextView = dialogView.findViewById<TextView>(R.id.timer_text_view)
    val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
    val sendButton = dialogView.findViewById<Button>(R.id.yes_button)

    messageTextView.text =
        "Do you need immediate assistance? Your designated contacts and emergency services will be notified."

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
    alertDialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
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
                Toast.makeText(
                    this@MainActivity,
                    "Failed to send emergency assistance request. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
            runOnUiThread {
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@MainActivity,
                        "Emergency assistance request sent successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to send emergency assistance request. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    })
}
}
