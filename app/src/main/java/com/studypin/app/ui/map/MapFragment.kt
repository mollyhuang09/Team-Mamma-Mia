package com.studypin.app.ui.map

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.databinding.FragmentMapBinding
import com.studypin.app.ui.applyStatusBarInset
import com.studypin.app.ui.filter.MapFilterBottomSheet
import com.studypin.app.ui.search.SearchFragment
import com.studypin.app.ui.toTagLabel
import com.studypin.app.model.StudySpot
import com.studypin.app.model.SpotStatus
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCancellationTokenSource: CancellationTokenSource? = null
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var etSearch: EditText
    private lateinit var spotPreviewSheet: View
    private lateinit var spotPreviewBehavior: BottomSheetBehavior<View>
    private val selectedMapAmenities = mutableSetOf<String>()
    private val selectedMapEnvironments = mutableSetOf<String>()
    private var selectedMapAvailableOnly = false
    private val allMapSpots = MockData.topLevelSpots()
        .filter { it.status != SpotStatus.REMOVED }
    private var mapSearchQuery = ""
    private var searchNavigationStarted = false
    private var selectedSpotId: String? = null

    companion object {
        // ICON 330, 330 Phillip Street, Waterloo.
        private val DEFAULT_MAP_LOCATION = LatLng(43.4762, -80.53867)
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

        view.findViewById<View>(R.id.headerContent).applyStatusBarInset()
        view.findViewById<MaterialCardView>(R.id.headerCard).cardElevation = 0f
        view.findViewById<TextView>(R.id.tvHeaderTitle).text = "Map"
        view.findViewById<TextView>(R.id.tvHeaderSubtitle).text = "Find and pin the best study spot"
        chipGroupTags = view.findViewById(R.id.chipGroupTags)
        etSearch = view.findViewById(R.id.etHeaderSearch)
        updateSelectedTagChips()
        setupSpotPreview(view)

        val headerSearch = view.findViewById<TextInputLayout>(R.id.layoutHeaderSearch)
        headerSearch.setOnClickListener { openSearchScreen() }
        etSearch.setOnClickListener { openSearchScreen() }
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) openSearchScreen()
        }

        parentFragmentManager.setFragmentResultListener(
            SearchFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val query = result.getString(SearchFragment.KEY_QUERY).orEmpty()
            mapSearchQuery = query.trim()
            etSearch.setText(query)
            etSearch.setSelection(query.length)
            updateMapMarkers()

            val matchingSpot = allMapSpots.firstOrNull { spot ->
                spot.name.contains(mapSearchQuery, ignoreCase = true) ||
                    spot.address.contains(mapSearchQuery, ignoreCase = true)
            }
            matchingSpot?.let { spot ->
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(spot.latitude, spot.longitude),
                        16f
                    )
                )
            }
        }

        view.findViewById<View>(R.id.btnHeaderFilters).setOnClickListener {
            showMapFilters()
        }

        childFragmentManager.setFragmentResultListener(
            MapFilterBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            selectedMapAmenities.clear()
            selectedMapAmenities.addAll(
                result.getStringArrayList(MapFilterBottomSheet.KEY_AMENITIES).orEmpty()
            )
            selectedMapEnvironments.clear()
            selectedMapEnvironments.addAll(
                result.getStringArrayList(MapFilterBottomSheet.KEY_ENVIRONMENTS).orEmpty()
            )
            selectedMapAvailableOnly = result.getBoolean(
                MapFilterBottomSheet.KEY_AVAILABLE_ONLY,
                false
            )
            updateSelectedTagChips()
            updateMapMarkers()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabMyLocation.setOnClickListener {
            if (checkLocationPermission()) {
                enableMyLocation()
                moveToCurrentLocation()
            } else {
                requestLocationPermission()
            }
        }
    }

    private fun showMapFilters() {
        if (childFragmentManager.findFragmentByTag(MapFilterBottomSheet.TAG) == null) {
            MapFilterBottomSheet.newInstance(
                selectedMapAmenities,
                selectedMapEnvironments,
                selectedMapAvailableOnly
            ).show(childFragmentManager, MapFilterBottomSheet.TAG)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_LOCATION, 15f))
        googleMap?.setOnMarkerClickListener { marker ->
            val spot = allMapSpots.firstOrNull { it.id == marker.tag as? String }
            if (spot != null) {
                showSpotPreview(spot)
            }
            true
        }
        updateMapMarkers()

        if (checkLocationPermission()) {
            enableMyLocation()
            moveToCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun updateMapMarkers() {
        val map = googleMap ?: return
        map.clear()

        allMapSpots
            .filter(::matchesSelectedMapFilters)
            .forEach { spot ->
                val options = MarkerOptions()
                    .position(LatLng(spot.latitude, spot.longitude))
                    .title(spot.name)
                    .snippet(spot.address)

                createSpotMarkerIcon(
                    isSelected = spot.id == selectedSpotId,
                    spot = spot
                )?.let { options.icon(it) }
                map.addMarker(options)?.tag = spot.id
            }

        if (selectedSpotId != null &&
            allMapSpots.none { it.id == selectedSpotId && matchesSelectedMapFilters(it) }
        ) {
            hideSpotPreview()
        }
    }

    private fun setupSpotPreview(view: View) {
        spotPreviewSheet = view.findViewById(R.id.mapSpotPreviewSheet)
        spotPreviewBehavior = BottomSheetBehavior.from(spotPreviewSheet)
        spotPreviewBehavior.isHideable = true

        spotPreviewSheet.findViewById<View>(R.id.btnClosePreview).setOnClickListener {
            hideSpotPreview()
        }
        spotPreviewSheet.findViewById<View>(R.id.btnOpenSpotDetails).setOnClickListener {
            openSelectedSpotDetail()
        }
    }

    private fun showSpotPreview(spot: StudySpot) {
        selectedSpotId = spot.id
        bindSpotPreview(spot)
        updateMapMarkers()
        spotPreviewSheet.visibility = View.VISIBLE
        spotPreviewBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hideSpotPreview() {
        selectedSpotId = null
        if (::spotPreviewBehavior.isInitialized) {
            spotPreviewBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        if (::spotPreviewSheet.isInitialized) {
            spotPreviewSheet.visibility = View.GONE
        }
        updateMapMarkers()
    }

    private fun openSelectedSpotDetail() {
        val spotId = selectedSpotId ?: return
        selectedSpotId = null
        spotPreviewSheet.visibility = View.GONE
        updateMapMarkers()
        findNavController().navigate(
            R.id.action_map_to_spotDetail,
            Bundle().apply { putString("spotId", spotId) }
        )
    }

    private fun bindSpotPreview(spot: StudySpot) {
        val inactive = isSpotInactive(spot)

        spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewSpotName).text = spot.name
        spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewHiddenGem).visibility =
            if (spot.isHiddenGem) View.VISIBLE else View.GONE
        spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewRating).text = String.format(
            Locale.CANADA,
            "%.1f (%d Reviews)",
            spot.avgRating,
            spot.totalRatings
        )
        spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewAvailability).text =
            "• ${spot.occupancyLabel()}"
        spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewAddress).text = spot.address

        spotPreviewSheet.findViewById<View>(R.id.tvPreviewRatingStar).visibility =
            if (inactive) View.GONE else View.VISIBLE
        spotPreviewSheet.findViewById<View>(R.id.tvPreviewRating).visibility =
            if (inactive) View.GONE else View.VISIBLE
        spotPreviewSheet.findViewById<View>(R.id.tvPreviewAvailability).visibility =
            if (inactive) View.GONE else View.VISIBLE

        val inactiveChip = spotPreviewSheet.findViewById<Chip>(R.id.chipPreviewInactive)
        inactiveChip.visibility = if (inactive) View.VISIBLE else View.GONE
        inactiveChip.isClickable = false
        inactiveChip.isFocusable = false
        if (inactive) {
            inactiveChip.setText(
                when (spot.status) {
                    SpotStatus.TEMPORARILY_CLOSED -> R.string.preview_temporarily_closed
                    else -> R.string.preview_currently_inactive
                }
            )
        }

        val chipGroup = spotPreviewSheet.findViewById<ChipGroup>(R.id.chipGroupPreviewTags)
        chipGroup.removeAllViews()
        chipGroup.visibility = if (inactive) View.GONE else View.VISIBLE
        if (!inactive) {
            spot.amenities.take(3).forEach { amenity ->
                val chip = layoutInflater.inflate(R.layout.item_spot_tag, chipGroup, false) as Chip
                chip.text = amenity.toTagLabel()
                chip.isClickable = false
                chip.isFocusable = false
                chipGroup.addView(chip)
            }
        }

        spotPreviewSheet.findViewById<View>(R.id.layoutPreviewInactiveReason).visibility =
            if (inactive) View.VISIBLE else View.GONE
        spotPreviewSheet.findViewById<View>(R.id.viewPreviewLastVerifiedDivider).visibility =
            if (inactive) View.VISIBLE else View.GONE
        spotPreviewSheet.findViewById<View>(R.id.layoutPreviewLastVerified).visibility =
            if (inactive) View.VISIBLE else View.GONE
        if (inactive) {
            spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewInactiveReasonTitle).setText(
                when (spot.status) {
                    SpotStatus.TEMPORARILY_CLOSED -> R.string.preview_closed_reason_title
                    else -> R.string.preview_inactive_reason_title
                }
            )
            spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewInactiveReason).setText(
                when (spot.status) {
                    SpotStatus.TEMPORARILY_CLOSED -> R.string.preview_closed_reason
                    else -> R.string.preview_inactive_reason
                }
            )
            spotPreviewSheet.findViewById<TextView>(R.id.tvPreviewLastVerifiedDate).text =
                spot.lastVerifiedLabel.ifBlank {
                    getString(R.string.preview_last_verified_unknown)
                }
        }
    }

    /** Only lifecycle/moderation state makes a spot inactive; occupancy is separate. */
    private fun isSpotInactive(spot: StudySpot): Boolean =
        spot.status == SpotStatus.UNDER_REVIEW ||
            spot.status == SpotStatus.TEMPORARILY_CLOSED

    private fun matchesSelectedMapFilters(spot: StudySpot): Boolean {
        val matchesAmenities = selectedMapAmenities.all { selectedAmenity ->
            spot.amenities.any { it.equals(selectedAmenity, ignoreCase = true) }
        }
        val matchesAvailability = !selectedMapAvailableOnly ||
            (spot.status == SpotStatus.ACTIVE &&
                spot.occupancyLabel() in listOf("Empty", "Available"))
        val matchesSearch = mapSearchQuery.isBlank() ||
            spot.name.contains(mapSearchQuery, ignoreCase = true) ||
            spot.address.contains(mapSearchQuery, ignoreCase = true)

        // StudySpot does not currently store environment values. Once the
        // database model gains an environments field, apply that filter here.
        return matchesAmenities && matchesAvailability && matchesSearch
    }

    private fun openSearchScreen() {
        if (searchNavigationStarted) return
        searchNavigationStarted = true
        etSearch.clearFocus()

        findNavController().navigate(
            R.id.action_map_to_search,
            Bundle().apply {
                putString(SearchFragment.KEY_INITIAL_QUERY, etSearch.text.toString())
            }
        )
    }

    private fun updateSelectedTagChips() {
        if (!::chipGroupTags.isInitialized) return

        chipGroupTags.removeAllViews()
        val selectedKeys = buildList {
            addAll(selectedMapAmenities)
            addAll(selectedMapEnvironments)
            if (selectedMapAvailableOnly) add("available_only")
        }

        if (selectedKeys.isEmpty()) {
            chipGroupTags.visibility = View.GONE
            return
        }

        selectedKeys.take(4).forEach { key ->
            addHeaderTagChip(key.toTagLabel())
        }
        if (selectedKeys.size > 4) {
            addHeaderTagChip("...")
        }
        chipGroupTags.visibility = View.VISIBLE
    }

    private fun addHeaderTagChip(label: String) {
        val chip = layoutInflater.inflate(
            R.layout.item_spot_tag,
            chipGroupTags,
            false
        ) as Chip
        chip.text = label
        chip.isClickable = false
        chip.isFocusable = false
        chipGroupTags.addView(chip)
    }

    private fun createSpotMarkerIcon(
        isSelected: Boolean,
        spot: StudySpot
    ): BitmapDescriptor? {
        val drawableResource = when {
            isSelected -> R.drawable.ic_map_pin_active
            !isSpotInactive(spot) ->
                R.drawable.ic_map_pin_available
            else -> R.drawable.ic_map_pin_unavailable
        }
        val drawable = ContextCompat.getDrawable(requireContext(), drawableResource)
            ?: return null
        val size = (36 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
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
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        searchNavigationStarted = false
    }
}
