package com.studypin.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.studypin.app.data.UserProfileRepository
import com.studypin.app.databinding.ActivitySignupBinding
import com.studypin.app.ui.applyStatusBarInset

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarInset(extraTopDp = 12)

        auth = FirebaseAuth.getInstance()

        binding.btnSignUp.setOnClickListener {
            performSignUp()
        }

        binding.btnBack.setOnClickListener {
            goToLanding()
        }

        setupValidationClearing()
    }

    private fun setupValidationClearing() {
        binding.etEmail.addTextChangedListener(clearErrorWatcher {
            binding.layoutEmail.error = null
        })

        val passwordWatcher = clearErrorWatcher {
            binding.layoutPassword.error = null
            binding.layoutConfirmPassword.error = null
        }
        binding.etPassword.addTextChangedListener(passwordWatcher)

        binding.etConfirmPassword.addTextChangedListener(clearErrorWatcher {
            binding.layoutPassword.error = null
            binding.layoutConfirmPassword.error = null
        })
    }

    private fun clearErrorWatcher(onTextChanged: () -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }

    private fun goToLanding() {
        startActivity(
            Intent(this, LandingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private fun performSignUp() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        clearValidationErrors()
        var isValid = true

        when {
            email.isBlank() -> {
                binding.layoutEmail.error = "Email is required"
                isValid = false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.layoutEmail.error = "Enter a valid email"
                isValid = false
            }
        }

        val passwordError = when {
            password.isBlank() -> "Password is required"
            password.length < 8 -> "Password must be at least 8 characters"
            password.none { it.isDigit() || (!it.isLetterOrDigit() && !it.isWhitespace()) } ->
                "Password must include a number or symbol"
            else -> null
        }

        if (passwordError != null) {
            binding.layoutPassword.error = passwordError
            isValid = false
        }

        if (password.isNotBlank() && password != confirmPassword) {
            binding.layoutConfirmPassword.error = "Passwords do not match"
            if (passwordError == null) {
                binding.layoutPassword.error = "Passwords do not match"
            }
            isValid = false
        }

        if (!isValid) {
            return
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val displayName = email.substringBefore('@').ifBlank { "StudyPin user" }
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
                    showSignUpError(task.exception)
                }
            }
    }

    private fun showSignUpError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthUserCollisionException -> {
                binding.layoutEmail.error = "Email is already registered"
            }
            is FirebaseAuthWeakPasswordException -> {
                binding.layoutPassword.error = "Password must be at least 8 characters"
            }
            is FirebaseAuthInvalidCredentialsException -> {
                binding.layoutEmail.error = "Enter a valid email"
            }
            else -> {
                Toast.makeText(
                    this,
                    "Unable to create account. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun clearValidationErrors() {
        binding.layoutEmail.error = null
        binding.layoutPassword.error = null
        binding.layoutConfirmPassword.error = null
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !isLoading
    }
}
