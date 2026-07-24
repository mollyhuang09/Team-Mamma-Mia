package com.studypin.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.studypin.app.data.UserProfileRepository
import com.studypin.app.databinding.ActivitySignupBinding

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnSignUp.setOnClickListener {
            performSignUp()
        }

        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun performSignUp() {
        val displayName = binding.etDisplayName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (displayName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                UserProfileRepository.saveProfile(
                                    uid = user.uid,
                                    displayName = displayName,
                                    email = user.email.orEmpty()
                                ) { profileError ->
                                    setLoading(false)
                                    if (profileError == null) {
                                        startActivity(Intent(this, MainActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Account created, but profile sync failed: ${profileError.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                setLoading(false)
                                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !isLoading
    }
}
