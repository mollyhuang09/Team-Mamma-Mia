package com.studypin.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.studypin.app.LandingActivity
import com.studypin.app.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
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
            logout()
        }
    }

    private fun logout() {
        // For now, just navigate to LandingActivity
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