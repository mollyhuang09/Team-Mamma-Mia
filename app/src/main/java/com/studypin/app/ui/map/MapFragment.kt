package com.studypin.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.studypin.app.R
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.StudySpot
import com.studypin.app.databinding.FragmentMapBinding

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCancellationTokenSource: CancellationTokenSource? = null
    private var spotsListener: ListenerRegistration? = null
    private var spots: List<StudySpot> = emptyList()

    companion object {
        private val WATERLOO_ON_CANADA = LatLng(43.4643, -80.5204)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        } else {
            Toast.makeText(requireContext(), getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        spotsListener = StudySpotRepository.observeSpots(
            onSuccess = { loadedSpots ->
                if (!isAdded) return@observeSpots
                spots = loadedSpots
                addSpotMarkers()
            },
            onError = { error ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Could not load study spots: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )

        binding.fabMyLocation.setOnClickListener {
            if (checkLocationPermission()) {
                enableMyLocation()
                moveToCurrentLocation()
            } else {
                requestLocationPermission()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(WATERLOO_ON_CANADA, 12f))

        addSpotMarkers()

        if (checkLocationPermission()) {
            enableMyLocation()
            moveToCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun addSpotMarkers() {
        val map = googleMap ?: return

        map.clear()
        spots.filter { it.parentSpotId == null }.forEach { spot ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(spot.latitude, spot.longitude))
                    .title(spot.name)
                    .snippet(spot.address)
            )
            marker?.tag = spot.id
        }

        map.setOnMarkerClickListener { marker ->
            val spotId = marker.tag as? String ?: return@setOnMarkerClickListener false
            findNavController().navigate(
                R.id.action_map_to_spotDetail,
                bundleOf("spotId" to spotId)
            )
            true
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun enableMyLocation() {
        if (checkLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true
                googleMap?.uiSettings?.isMyLocationButtonEnabled = false
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun moveToCurrentLocation() {
        if (!checkLocationPermission()) return

        try {
            locationCancellationTokenSource?.cancel()
            val cancellationTokenSource = CancellationTokenSource()
            locationCancellationTokenSource = cancellationTokenSource

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    val target = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15f))
                } else {
                    Toast.makeText(requireContext(), getString(R.string.location_fetch_failed), Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), getString(R.string.location_fetch_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCancellationTokenSource?.cancel()
        locationCancellationTokenSource = null
        spotsListener?.remove()
        spotsListener = null
        _binding = null
    }
}
