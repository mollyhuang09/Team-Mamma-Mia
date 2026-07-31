package com.studypin.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studypin.app.LandingActivity
import com.studypin.app.R
import com.studypin.app.data.ReviewRepository
import com.studypin.app.data.SavedSpotRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.data.UserProfileRepository
import com.studypin.app.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var savedSpotsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        
        displayUserInfo()
        setupClickListeners()
        loadCounts()
    }

    private fun displayUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            binding.userName.text = user.displayName ?: getString(R.string.user_name_placeholder)
            binding.userEmail.text = user.email ?: getString(R.string.user_email_placeholder)
            UserProfileRepository.loadProfile(
                uid = user.uid,
                onSuccess = { displayName, email ->
                    if (!isAdded) return@loadProfile
                    binding.userName.text = displayName ?: user.displayName ?: getString(R.string.user_name_placeholder)
                    binding.userEmail.text = email ?: user.email ?: getString(R.string.user_email_placeholder)
                },
                onError = { /* Auth values remain available as a local fallback. */ }
            )
        } else {
            // Guest mode or not logged in
            binding.userName.text = getString(R.string.guest_user)
            binding.userEmail.text = getString(R.string.sign_in_to_sync)
            binding.btnLogout.text = getString(R.string.sign_in)
        }
    }

    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.cardMyPins.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_manageMyPins)
        }
        binding.cardSavedSpots.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_manageSavedSpots)
        }
        binding.cardMyReviews.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_manageMyReviews)
        }

        binding.btnLogout.setOnClickListener {
            if (auth.currentUser != null) {
                logout()
            } else {
                goToLanding()
            }
        }
    }

    private fun loadCounts() {
        val uid = auth.currentUser?.uid ?: return

        StudySpotRepository.spotsForUser(
            userId = uid,
            onSuccess = { spots -> if (isAdded) binding.tvPinsCount.text = spots.size.toString() },
            onError = {}
        )

        savedSpotsListener = SavedSpotRepository.observeSavedSpotIds(
            userId = uid,
            onSuccess = { ids -> if (isAdded) binding.tvSavedCount.text = ids.size.toString() },
            onError = {}
        )

        ReviewRepository.reviewsByUser(
            userId = uid,
            onSuccess = { reviews -> if (isAdded) binding.tvReviewsCount.text = reviews.size.toString() },
            onError = {}
        )
    }

    private fun logout() {
        auth.signOut()
        goToLanding()
    }

    private fun goToLanding() {
        val intent = Intent(requireContext(), LandingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showEditProfileDialog() {
        val user = auth.currentUser ?: return
        val input = EditText(requireContext()).apply {
            setText(user.displayName.orEmpty())
            hint = getString(R.string.display_name)
            setSingleLine(true)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_profile))
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val displayName = input.text.toString().trim()
                if (displayName.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.display_name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates)
                    .addOnSuccessListener {
                        UserProfileRepository.saveProfile(user.uid, displayName, user.email.orEmpty()) { error ->
                            if (!isAdded) return@saveProfile
                            if (error == null) {
                                displayUserInfo()
                                Toast.makeText(requireContext(), getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), "Profile sync failed: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(requireContext(), "Could not update profile: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savedSpotsListener?.remove()
        savedSpotsListener = null
        _binding = null
    }
}
