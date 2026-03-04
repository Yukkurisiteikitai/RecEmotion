package com.example.recemotion

import android.Manifest
import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.recemotion.databinding.FragmentSetupBinding
import com.example.recemotion.ui.hapticTick
import com.example.recemotion.presentation.CalibrationButtonState
import com.example.recemotion.presentation.SetupUiState
import com.example.recemotion.presentation.SetupViewModel
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class SetupFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SetupViewModel by viewModels()
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService
    private var countdownJob: Job? = null
    private var calibrationActive = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.onCameraPermissionResult(isGranted)
            if (isGranted) {
                startCalibration()
            } else {
                Toast.makeText(requireContext(), "カメラ許可が必要です", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceLandmarkerHelper = FaceLandmarkerHelper(
            context = requireContext(),
            faceLandmarkerHelperListener = this
        )

        val cameraGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.onCameraPermissionResult(cameraGranted)

        // スクリーンタイム or デフォルト(7:00) で起床時刻を初期化
        detectWakeTime()

        setupClickListeners()
        observeUiState()

        // 自動キャリブレーションが設定済みの場合は自動で開始
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.getSavedAutoCalibrate()) {
                if (cameraGranted) {
                    startCalibration()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden && calibrationActive) stopCamera()
    }

    // --- Wake Time Detection ---

    private fun detectWakeTime() {
        val detected = getFirstScreenOnToday()
        if (detected != null) {
            viewModel.onWakeTimeDetected(detected.first, detected.second)
        }
        // フォールバックはViewModel init で 7:00 設定済み
    }

    private fun getFirstScreenOnToday(): Pair<Int, Int>? {
        return try {
            val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    requireContext().packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    requireContext().packageName
                )
            }
            if (mode != AppOpsManager.MODE_ALLOWED) return null

            val usageManager = requireContext()
                .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis

            val events = usageManager.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()
            var firstScreenOn: Long? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                    firstScreenOn = event.timeStamp
                    break
                }
            }
            firstScreenOn?.let {
                val c = Calendar.getInstance().also { c -> c.timeInMillis = it }
                Pair(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            }
        } catch (e: Exception) {
            null
        }
    }

    // --- Click Listeners ---

    private fun setupClickListeners() {
        binding.layoutCalibrationButton.setOnClickListener {
            if (viewModel.uiState.value.calibrationState == CalibrationButtonState.NOW_SETTING) return@setOnClickListener
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                return@setOnClickListener
            }
            startCalibration()
        }

        binding.checkAutoCalibrate.setOnCheckedChangeListener { view, isChecked ->
            view.hapticTick()
            viewModel.onAutoCalibrateChanged(isChecked)
        }

        binding.btnClearCalibration.setOnClickListener {
            stopCamera()
            viewModel.onClearCalibration()
        }

        binding.txtWakeTimeSetup.setOnClickListener {
            countdownJob?.cancel()
            showWakeTimePicker()
        }

        binding.btnSetupConfirm.setOnClickListener {
            countdownJob?.cancel()
            completeSetup()
        }
    }

    private fun showWakeTimePicker() {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            viewModel.onWakeTimeChanged(hour, minute)
            startWakeTimeCountdown()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    // --- Calibration ---

    private fun startCalibration() {
        if (calibrationActive) stopCamera()
        calibrationActive = true
        viewModel.onCalibrationStarted()
        binding.viewFinderSetup.visibility = View.VISIBLE

        val wakeTimeUnix = viewModel.uiState.value.wakeTimeUnix
        MainActivity.initSessionSafe(if (wakeTimeUnix > 0) wakeTimeUnix else defaultWakeTimeUnix())
        startCamera()
    }

    private fun defaultWakeTimeUnix(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis / 1000
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinderSetup.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        faceLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera = true)
                        imageProxy.close()
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                viewModel.onCalibrationError("カメラの起動に失敗しました: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
        }, ContextCompat.getMainExecutor(requireContext()))
        calibrationActive = false
        _binding?.viewFinderSetup?.visibility = View.GONE
    }

    // --- FaceLandmarkerHelper Callbacks ---

    override fun onResults(result: FaceLandmarkerResult, inferenceTime: Long) {
        if (result.faceLandmarks().isEmpty()) return
        val firstFace = result.faceLandmarks()[0]
        val flattened = FloatArray(firstFace.size * 3)
        for (i in firstFace.indices) {
            val pt = firstFace[i]
            flattened[i * 3] = pt.x()
            flattened[i * 3 + 1] = pt.y()
            flattened[i * 3 + 2] = pt.z()
        }
        MainActivity.pushFaceLandmarks(flattened)
        val jsonStr = MainActivity.getAnalysisJson("")
        requireActivity().runOnUiThread {
            if (viewModel.uiState.value.calibrationState == CalibrationButtonState.NOW_SETTING) {
                checkCalibrationStatus(jsonStr)
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        requireActivity().runOnUiThread {
            if (viewModel.uiState.value.calibrationState == CalibrationButtonState.NOW_SETTING) {
                viewModel.onCalibrationError(error)
                stopCamera()
            }
        }
    }

    override fun onEmpty() {}

    private fun checkCalibrationStatus(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val isCalibrated = json.getJSONObject("emotion_data").optBoolean("is_calibrated", false)
            if (isCalibrated) {
                stopCamera()
                viewModel.onCalibrationSuccess()
                startWakeTimeCountdown()
            }
        } catch (e: Exception) {
            // キャリブレーション中のパースエラーは無視
        }
    }

    // --- Wake Time Countdown ---

    private fun startWakeTimeCountdown() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            for (i in 5 downTo 1) {
                viewModel.onCountdownTick(i)
                delay(1000L)
            }
            completeSetup()
        }
    }

    // --- Setup Complete ---

    private fun completeSetup() {
        val state = viewModel.uiState.value
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        viewModel.saveSetup(today, state.wakeTimeUnix, state.autoCalibrate)
        viewModel.onSetupComplete()
        (requireActivity() as MainActivity).onSetupComplete()
    }

    // --- UI State Observer ---

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> applyUiState(state) }
            }
        }
    }

    private fun applyUiState(state: SetupUiState) {
        // --- キャリブレーションボタン状態 ---
        when (state.calibrationState) {
            CalibrationButtonState.UNSET -> {
                binding.progressCalibration.visibility = View.GONE
                binding.txtCalibrationIcon.visibility = View.VISIBLE
                binding.txtCalibrationIcon.text = "✕"
                binding.txtCalibrationIcon.setTextColor(Color.parseColor("#888888"))
                binding.txtCalibrationStatus.text = "顔感情分析セットアップ"
                binding.txtCalibrationStatus.setTextColor(Color.WHITE)
                binding.txtCalibrationError.visibility = View.GONE
            }
            CalibrationButtonState.NOW_SETTING -> {
                binding.progressCalibration.visibility = View.VISIBLE
                binding.txtCalibrationIcon.visibility = View.GONE
                binding.txtCalibrationStatus.text = "キャリブレーション中..."
                binding.txtCalibrationStatus.setTextColor(Color.WHITE)
                binding.txtCalibrationError.visibility = View.GONE
            }
            CalibrationButtonState.PASS_SETTINGS -> {
                binding.progressCalibration.visibility = View.GONE
                binding.txtCalibrationIcon.visibility = View.VISIBLE
                binding.txtCalibrationIcon.text = "↺"
                binding.txtCalibrationIcon.setTextColor(Color.parseColor("#4CAF50"))
                binding.txtCalibrationStatus.text = "キャリブレーション完了"
                binding.txtCalibrationStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.txtCalibrationError.visibility = View.GONE
            }
            CalibrationButtonState.ERROR_SETTING -> {
                binding.progressCalibration.visibility = View.GONE
                binding.txtCalibrationIcon.visibility = View.VISIBLE
                binding.txtCalibrationIcon.text = "↺"
                binding.txtCalibrationIcon.setTextColor(Color.parseColor("#F44336"))
                binding.txtCalibrationStatus.text = "セットアップエラー"
                binding.txtCalibrationStatus.setTextColor(Color.parseColor("#F44336"))
                if (state.calibrationErrorMsg != null) {
                    binding.txtCalibrationError.visibility = View.VISIBLE
                    binding.txtCalibrationError.text = state.calibrationErrorMsg
                } else {
                    binding.txtCalibrationError.visibility = View.GONE
                }
            }
        }

        // --- 自動起動セクション ---
        binding.sectionAutoCalibrate.visibility =
            if (state.showAutoSection) View.VISIBLE else View.GONE
        binding.checkAutoCalibrate.isChecked = state.autoCalibrate
        // クリアボタン: 自動ON + 成功状態の場合のみ
        binding.btnClearCalibration.visibility =
            if (state.autoCalibrate && state.calibrationState == CalibrationButtonState.PASS_SETTINGS)
                View.VISIBLE else View.GONE

        // --- Wake Time セクション ---
        binding.sectionWakeTime.visibility =
            if (state.showWakeTimeSection) View.VISIBLE else View.GONE
        binding.txtWakeTimeSetup.text = state.wakeTimeText
        binding.txtWakeCountdown.text =
            if (state.wakeCountdown > 0) "${state.wakeCountdown}秒後に確定..." else "確定済み"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownJob?.cancel()
        cameraExecutor.shutdown()
        faceLandmarkerHelper.clearFaceLandmarker()
        _binding = null
    }

    companion object {
        const val TAG = "SetupFragment"
    }
}
