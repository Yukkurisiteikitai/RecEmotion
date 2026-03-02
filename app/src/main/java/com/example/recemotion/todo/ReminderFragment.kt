package com.example.recemotion.todo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.recemotion.R
import com.example.recemotion.notification.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderFragment : Fragment() {

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

    companion object {
        const val TAG = "REMINDER"
    }
}
