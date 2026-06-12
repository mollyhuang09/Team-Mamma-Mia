package com.studypin.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SignUpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        findViewById<Button>(R.id.btnSignUp).setOnClickListener {
            // TODO: implement sign up logic
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnGoToLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}