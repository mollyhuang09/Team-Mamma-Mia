package com.studypin.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.studypin.app.location.LocationReminderManager

/** Shown directly from the leave notification so the prompt is not dependent on MainActivity timing. */
class LeaveSpotPromptActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_StudyPin_LeavePrompt)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_leave_spot)

        val spotId = intent.getStringExtra(LocationReminderManager.EXTRA_SPOT_ID)
        if (spotId == null) {
            finish()
            return
        }

        val spotName = intent.getStringExtra(LocationReminderManager.EXTRA_SPOT_NAME)
            ?: "this study spot"
        findViewById<TextView>(R.id.dialogTitle).text = "Done studying at $spotName?"

        findViewById<MaterialButton>(R.id.btnLeft).setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            LocationReminderManager.clearPendingReminder(this, spotId)
            LocationReminderManager.endVisit(this, spotId, userId)
            Toast.makeText(this, "Thanks for updating the study spot", Toast.LENGTH_SHORT).show()
            openSpotDetails(spotId)
        }

        findViewById<MaterialButton>(R.id.btnStillHere).setOnClickListener {
            LocationReminderManager.clearPendingReminder(this, spotId)
            LocationReminderManager.cancelReminder(this, spotId)
            LocationReminderManager.setEntered(this, spotId, true)
            Toast.makeText(this, "Okay, we’ll keep tracking your visit", Toast.LENGTH_SHORT).show()
            openSpotDetails(spotId)
        }
    }

    private fun openSpotDetails(spotId: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(LocationReminderManager.EXTRA_SPOT_ID, spotId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
