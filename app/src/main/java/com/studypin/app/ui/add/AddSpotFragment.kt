package com.studypin.app.ui.add

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.Capacity
import com.studypin.app.model.StudySpot
import com.studypin.app.utils.LocationUtils

class AddSpotFragment : Fragment() {

    // Hardcoded device location for testing the radius check, since
    // there's no real GPS/map-pin yet. Sits ~30m from Dana Porter
    // (spot_001) so the "is this inside an existing spot" prompt
    // reliably triggers during the demo.
    private val simulatedLat = 43.4700
    private val simulatedLng = -80.5428

    private val radiusCheckMeters = 75.0

    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etAddress: EditText
    private lateinit var etHours: EditText
    private lateinit var cbWifi: CheckBox
    private lateinit var cbOutlets: CheckBox
    private lateinit var cbWashroom: CheckBox
    private lateinit var cbPrinting: CheckBox
    private lateinit var capacitySpinner: Spinner
    private lateinit var btnSubmit: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_spot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etName = view.findViewById(R.id.etSpotName)
        etDescription = view.findViewById(R.id.etDescription)
        etAddress = view.findViewById(R.id.etAddress)
        etHours = view.findViewById(R.id.etHours)
        cbWifi = view.findViewById(R.id.cbWifiAdd)
        cbOutlets = view.findViewById(R.id.cbOutletsAdd)
        cbWashroom = view.findViewById(R.id.cbWashroom)
        cbPrinting = view.findViewById(R.id.cbPrinting)
        capacitySpinner = view.findViewById(R.id.capacitySpinner)
        btnSubmit = view.findViewById(R.id.btnSubmitSpot)

        capacitySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            Capacity.values().map { it.label }
        )

        btnSubmit.setOnClickListener { onSubmitClicked() }
    }

    private fun onSubmitClicked() {
        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Address is required", Toast.LENGTH_SHORT).show()
            return
        }

        // Step 1: check if this new spot sits inside an existing top-level spot
        val possibleParent = findNearbyTopLevelSpot()

        if (possibleParent != null) {
            showParentPrompt(possibleParent)
            return // wait for the user's choice before creating anything
        }

        // Step 2: no parent found, so check if this new spot should adopt
        // any nearby orphaned top-level spots as hidden gems
        val possibleOrphan = findNearbyOrphanSpot()

        if (possibleOrphan != null) {
            createSpot(parentId = null, isHiddenGem = false) { newSpot ->
                showOrphanPrompt(possibleOrphan, newSpot)
            }
            return
        }

        // Step 3: no relationships detected, just create a normal top-level spot
        createSpot(parentId = null, isHiddenGem = false) {
            Toast.makeText(requireContext(), "Spot added!", Toast.LENGTH_SHORT).show()
        }
    }

    /** Finds an existing top-level spot whose coordinates are within radius. */
    private fun findNearbyTopLevelSpot(): StudySpot? {
        return MockData.topLevelSpots().firstOrNull { existing ->
            LocationUtils.distanceInMeters(
                simulatedLat, simulatedLng,
                existing.latitude, existing.longitude
            ) <= radiusCheckMeters
        }
    }

    /**
     * Finds an existing top-level spot that has no parent and sits within
     * radius — a candidate to be retroactively adopted as a hidden gem
     * inside the spot currently being created.
     */
    private fun findNearbyOrphanSpot(): StudySpot? {
        return MockData.topLevelSpots().firstOrNull { existing ->
            LocationUtils.distanceInMeters(
                simulatedLat, simulatedLng,
                existing.latitude, existing.longitude
            ) <= radiusCheckMeters
        }
    }

    private fun showParentPrompt(parent: StudySpot) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hidden gem detected")
            .setMessage("This looks like it's inside \"${parent.name}\". Is this a hidden spot within that location?")
            .setPositiveButton("Yes, it's a hidden gem") { _, _ ->
                createSpot(parentId = parent.id, isHiddenGem = true) {
                    Toast.makeText(
                        requireContext(),
                        "Added as a hidden gem inside ${parent.name}!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("No, it's separate") { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    Toast.makeText(requireContext(), "Spot added!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showOrphanPrompt(orphan: StudySpot, newParent: StudySpot) {
        AlertDialog.Builder(requireContext())
            .setTitle("Link existing spot?")
            .setMessage("We found \"${orphan.name}\" nearby. Should it become a hidden gem inside this new spot?")
            .setPositiveButton("Yes, link it") { _, _ ->
                // TODO once Firestore is wired: update(orphan.id) { parentSpotId = newParent.id; isHiddenGem = true }
                Toast.makeText(
                    requireContext(),
                    "${orphan.name} linked as a hidden gem inside ${newParent.name}!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("No, keep separate") { _, _ ->
                Toast.makeText(requireContext(), "Spot added!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * Builds a StudySpot from the form fields and "saves" it.
     * For the prototype this just calls onComplete with the new object;
     * once Firestore is wired in, this is where the actual write happens.
     */
    private fun createSpot(
        parentId: String?,
        isHiddenGem: Boolean,
        onComplete: (StudySpot) -> Unit
    ) {
        val amenities = mutableListOf<String>()
        if (cbWifi.isChecked) amenities.add("wifi")
        if (cbOutlets.isChecked) amenities.add("outlets")
        if (cbWashroom.isChecked) amenities.add("washroom")
        if (cbPrinting.isChecked) amenities.add("printing")

        val selectedCapacity = Capacity.values()[capacitySpinner.selectedItemPosition]

        val newSpot = StudySpot(
            id = "spot_local_${System.currentTimeMillis()}",
            name = etName.text.toString().trim(),
            description = etDescription.text.toString().trim(),
            address = etAddress.text.toString().trim(),
            latitude = simulatedLat,
            longitude = simulatedLng,
            amenities = amenities,
            hours = etHours.text.toString().trim(),
            createdBy = "device_local_user",
            avgRating = 0.0,
            totalRatings = 0,
            capacity = selectedCapacity,
            currentCheckIns = 0,
            parentSpotId = parentId,
            isHiddenGem = isHiddenGem
        )

        // TODO once Firestore is wired: write newSpot to the studySpots collection
        onComplete(newSpot)
        clearForm()
    }

    private fun clearForm() {
        etName.text.clear()
        etDescription.text.clear()
        etAddress.text.clear()
        etHours.text.clear()
        cbWifi.isChecked = false
        cbOutlets.isChecked = false
        cbWashroom.isChecked = false
        cbPrinting.isChecked = false
        capacitySpinner.setSelection(0)
    }
}