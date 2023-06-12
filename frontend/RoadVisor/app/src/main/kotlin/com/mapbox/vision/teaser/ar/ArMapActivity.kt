package com.mapbox.vision.teaser.ar

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.route.NavigationMapRoute
import com.mapbox.vision.teaser.R
import com.mapbox.vision.teaser.utils.buildNavigationOptions
import kotlinx.android.synthetic.main.activity_ar_map.*
import okhttp3.*
import java.io.IOException
import com.mapbox.vision.teaser.ar.SearchLocation

data class LocationFeature(val name : String, val location : Point)
class ArMapActivity : AppCompatActivity(), MapboxMap.OnMapClickListener, OnMapReadyCallback {

    companion object {
        private val TAG = ArMapActivity::class.java.simpleName
        const val ARG_RESULT_JSON_ROUTE = "ARG_RESULT_JSON_ROUTE"
    }

    private var originPoint: Point? = null

    private lateinit var mapboxMap: MapboxMap
    private var mapboxNavigation: MapboxNavigation? = null

    private var destinationMarker: Marker? = null

    private var currentRoute: DirectionsRoute? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var locationComponent: LocationComponent? = null

    private val locationObserver = object : LocationObserver {
        override fun onEnhancedLocationChanged(enhancedLocation: Location, keyPoints: List<Location>) {
            originPoint = Point.fromLngLat(
                enhancedLocation.longitude,
                enhancedLocation.latitude
            )
        }

        override fun onRawLocationChanged(rawLocation: Location) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_map)

        back.setOnClickListener {
            onBackPressed()
        }
        mapView.onCreate(savedInstanceState)
        start_ar.setOnClickListener {
            val route = currentRoute
            if (route != null) {
                val jsonRoute = route.toJson()
                val data = Intent().apply {
                    putExtra(ARG_RESULT_JSON_ROUTE, jsonRoute)
                }
                setResult(RESULT_OK, data)
                finish()
            } else {
                Toast.makeText(this, "Route is not ready yet!", Toast.LENGTH_LONG).show()
            }
        }

        mapboxNavigation = MapboxNavigation(buildNavigationOptions())

        lateinit var searchLocations : ListView
        lateinit var listAdapter : ArrayAdapter<SearchLocation>
        lateinit var searchView : SearchView
        lateinit var searchLocationsList: ArrayList<SearchLocation>


        searchLocations = findViewById(R.id.locations_list)
        searchView = findViewById(R.id.search_box)

        searchLocationsList = ArrayList()
        listAdapter = ArrayAdapter<SearchLocation>(
            this,
            android.R.layout.simple_list_item_1,
            searchLocationsList
        )
        searchLocations.adapter = listAdapter

        search_box.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(p0: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                locations_list.visibility = View.VISIBLE

                val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/" + p0?.lowercase() + ".json?access_token=pk.eyJ1IjoiYXJkYWljb3oiLCJhIjoiY2xmeWhucGo4MTFwbDNkcXVpMXhoenVtbCJ9.vlTh7zlynI5WLeo0jtYelA"
                var client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body()?.string()

                        if (responseBody != null) {
                            searchLocationsList.clear()
                            val gson = Gson()
                            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                            val featuresArray = jsonObject.getAsJsonArray("features")
                            //Log.d("TAG", featuresArray.toString())

                            if (featuresArray.size() > 0) {
                                for (location in featuresArray) {
                                    var locationName = location.asJsonObject.get("text").toString()
                                    var locationCoordinates : DoubleArray = DoubleArray(2)

                                    locationCoordinates[0] = location.asJsonObject.get("center").asJsonArray.get(0).asDouble
                                    locationCoordinates[1] = location.asJsonObject.get("center").asJsonArray.get(1).asDouble

                                    searchLocationsList.add(SearchLocation(locationName, locationCoordinates))
                                }
                            }
                        }
                    }
                })
                return true
            }

        })

        searchLocations.setOnItemClickListener { parent, view, position, id ->
            getRoute(
                origin = originPoint!!,
                destination = Point.fromLngLat(searchLocationsList[position].coordinates[0], searchLocationsList[position].coordinates[1])
            )
            locations_list.visibility = View.INVISIBLE
            start_ar.visibility = View.VISIBLE
            searchLocationsList.clear()
        }
    }


    override fun onStart() {
        super.onStart()
        mapView.onStart()
        mapView.getMapAsync(this)

        if (ContextCompat.checkSelfPermission(this@ArMapActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) !==
            PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@ArMapActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this@ArMapActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this@ArMapActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
        mapboxNavigation?.startTripSession()
        mapboxNavigation?.registerLocationObserver(locationObserver)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                    if ((ContextCompat.checkSelfPermission(this@ArMapActivity,
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        mapboxNavigation?.unregisterLocationObserver(locationObserver)
        mapboxNavigation?.stopTripSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }

    override fun onMapClick(destination: LatLng): Boolean {
        destinationMarker?.let(mapboxMap::removeMarker)
        destinationMarker = mapboxMap.addMarker(MarkerOptions().position(destination))

        if (originPoint == null) {
            Toast.makeText(this, "Source location is not determined yet!", Toast.LENGTH_LONG).show()
            return false
        }

        getRoute(
            origin = originPoint!!,
            destination = Point.fromLngLat(destination.longitude, destination.latitude)
        )

        start_ar.visibility = View.VISIBLE

        return true
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.Builder().fromUri(Style.DARK)) {
            enableLocationComponent()
        }

        mapboxMap.addOnMapClickListener(this)
    }

    private fun getRoute(origin: Point, destination: Point) {
        mapboxNavigation?.requestRoutes(
            routeOptions = RouteOptions.builder()
                .applyDefaultParams()
                .accessToken(Mapbox.getAccessToken()!!)
                .coordinates(listOf(origin, destination))
                .build(),
            routesRequestCallback = object : RoutesRequestCallback {
                override fun onRoutesReady(routes: List<DirectionsRoute>) {
                    currentRoute = routes.first()

                    // Draw the route on the map
                    if (navigationMapRoute == null) {
                        navigationMapRoute = NavigationMapRoute.Builder(
                            mapView,
                            mapboxMap,
                            this@ArMapActivity,
                        )
                            .withStyle(R.style.MapboxStyleNavigationMapRoute)
                            .build()
                    } else {
                        navigationMapRoute?.updateRouteVisibilityTo(false)
                    }
                    navigationMapRoute?.addRoute(currentRoute)
                }

                override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
                    Toast.makeText(this@ArMapActivity, "Route request canceled!", Toast.LENGTH_LONG).show()
                }

                override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
                    Toast.makeText(this@ArMapActivity, "Route request failure!", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent() {
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .build()
        locationComponent = mapboxMap.locationComponent

        val locationComponentActivationOptions = LocationComponentActivationOptions
            .builder(this, mapboxMap.style!!)
            .locationComponentOptions(locationComponentOptions)
            .build()

        locationComponent?.let {
            it.activateLocationComponent(locationComponentActivationOptions)
            it.isLocationComponentEnabled = true
            it.cameraMode = CameraMode.TRACKING
        }
    }
}
