package com.studypin.app.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.studypin.app.R
import com.studypin.app.ui.setOnApplyStatusBarInsetsListener
import com.google.android.material.button.MaterialButton
import java.util.Locale

class LocationPickerFragment : Fragment(), OnMapReadyCallback {

    companion object {
        const val RESULT_KEY = "location_picker_result"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ADDRESS = "address"

        private val DEFAULT_LOCATION = LatLng(43.4723, -80.5449) // Waterloo Campus
    }

    private var googleMap: GoogleMap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_location_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        // The map is drawn edge-to-edge, so without this the back button sits
        // under the status bar and its taps go to the system status bar instead.
        btnBack.setOnApplyStatusBarInsetsListener { statusBarHeight ->
            btnBack.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = statusBarHeight + dpToPx(16)
            }
        }

        view.findViewById<MaterialButton>(R.id.btnConfirmLocation).setOnClickListener {
            val center = googleMap?.cameraPosition?.target
            if (center != null) {
                var addressText = ""
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(center.latitude, center.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        addressText = address.getAddressLine(0) ?: ""
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                setFragmentResult(
                    RESULT_KEY,
                    bundleOf(
                        KEY_LATITUDE to center.latitude,
                        KEY_LONGITUDE to center.longitude,
                        KEY_ADDRESS to addressText
                    )
                )
                findNavController().popBackStack()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        googleMap?.uiSettings?.isMyLocationButtonEnabled = true

        moveToInitialLocation()
    }

    /**
     * Priority for where the picker first centers: the caller's already-picked
     * location (e.g. a geocoded address), then the device's current location,
     * then the hardcoded campus default as a last resort so the map never
     * opens blank.
     */
    private fun moveToInitialLocation() {
        val initialLat = arguments?.getDouble(KEY_LATITUDE)
        val initialLng = arguments?.getDouble(KEY_LONGITUDE)
        if (initialLat != null && initialLng != null) {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initialLat, initialLng), 15f))
            return
        }

        if (!hasLocationPermission()) {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15f))
            return
        }

        try {
            LocationServices.getFusedLocationProviderClient(requireContext())
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (!isAdded) return@addOnSuccessListener
                    val target = if (location != null) LatLng(location.latitude, location.longitude) else DEFAULT_LOCATION
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15f))
                }
                .addOnFailureListener {
                    if (isAdded) {
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15f))
                    }
                }
        } catch (e: SecurityException) {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15f))
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
