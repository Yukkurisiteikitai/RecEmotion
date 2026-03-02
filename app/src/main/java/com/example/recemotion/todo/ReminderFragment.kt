package com.example.recemotion.todo

import android.Manifest
import android.app.AlertDialog
import android.app.AlarmManager
import android.util.Log
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.recemotion.R
import com.example.recemotion.notification.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderFragment : Fragment() {

    private var isPermissionDialogShowing = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "POST_NOTIFICATIONS permission denied")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reminder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.btnTestNotification).setOnClickListener {
            ReminderScheduler.scheduleNow(
                requireContext(),
                title = "テストリマインド",
                description = "これはテスト通知です"
            )
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms() && !isPermissionDialogShowing) {
                isPermissionDialogShowing = true
                AlertDialog.Builder(requireContext())
                    .setTitle("正確なアラームの許可が必要です")
                    .setMessage("リマインダーを指定した時刻に通知するには、正確なアラームの許可が必要です。設定から許可してください。")
                    .setPositiveButton("設定を開く") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${requireContext().packageName}")
                        })
                    }
                    .setNegativeButton("あとで", null)
                    .setOnDismissListener { isPermissionDialogShowing = false }
                    .show()
            }
        }
    }

    companion object {
        const val TAG = "REMINDER"
    }
}
