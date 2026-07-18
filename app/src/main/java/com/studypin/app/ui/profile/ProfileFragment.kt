package com.studypin.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.studypin.app.LandingActivity
import com.studypin.app.R
import com.studypin.app.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

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
    }

    private fun displayUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            binding.userName.text = user.displayName ?: getString(R.string.user_name_placeholder)
            binding.userEmail.text = user.email ?: getString(R.string.user_email_placeholder)
        } else {
            // Guest mode or not logged in
            binding.userName.text = getString(R.string.guest_user)
            binding.userEmail.text = getString(R.string.sign_in_to_sync)
            binding.btnLogout.text = getString(R.string.sign_in)
        }
    }

    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile clicked", Toast.LENGTH_SHORT).show()
        }

        binding.layoutMyReviews.setOnClickListener {
            Toast.makeText(requireContext(), "My Reviews clicked", Toast.LENGTH_SHORT).show()
        }

        binding.layoutSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Settings clicked", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            if (auth.currentUser != null) {
                logout()
            } else {
                goToLanding()
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}