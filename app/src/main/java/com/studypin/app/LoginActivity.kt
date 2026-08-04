package com.studypin.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.studypin.app.databinding.ActivityLoginBinding
import com.studypin.app.ui.applyStatusBarInset
import com.studypin.app.ui.showMessage

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarInset(extraTopDp = 12)

        auth = FirebaseAuth.getInstance()

        binding.btnLogin.setOnClickListener {
            performLogin()
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

        binding.etPassword.addTextChangedListener(clearErrorWatcher {
            binding.layoutPassword.error = null
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

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

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

        if (password.isBlank()) {
            binding.layoutPassword.error = "Password is required"
            isValid = false
        }

        if (!isValid) {
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    showLoginError(task.exception)
                }
            }
    }

    private fun showLoginError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthInvalidUserException -> {
                binding.layoutEmail.error = "Email not found"
            }
            is FirebaseAuthInvalidCredentialsException -> {
                binding.layoutPassword.error = "Password does not match"
            }
            else -> {
                showMessage("Unable to log in. Please try again.")
            }
        }
    }

    private fun clearValidationErrors() {
        binding.layoutEmail.error = null
        binding.layoutPassword.error = null
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }
}
