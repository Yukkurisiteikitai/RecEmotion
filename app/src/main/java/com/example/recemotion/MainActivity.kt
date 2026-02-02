package com.example.recemotion

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.OutOfQuotaPolicy
import com.example.recemotion.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textView.text = helloFromRust()

        binding.btnSend.setOnClickListener {
            val target = binding.editTarget.text.toString()
            val message = binding.editMessage.text.toString()

            if (target.isBlank() || message.isBlank()) {
                Toast.makeText(this, "Please enter target and message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Enqueue WorkManager task
            val workRequest = OneTimeWorkRequestBuilder<MessageSendWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(
                    "target" to target,
                    "sender" to "AndroidUser",
                    "message" to message
                ))
                .build()

            WorkManager.getInstance(this).enqueue(workRequest)
            Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
        }
    }

    external fun helloFromRust(): String

    companion object {
        init {
            System.loadLibrary("recemotion")
        }
        
        @JvmStatic
        external fun sendMessage(target: String, sender: String, message: String): Boolean
    }
}
