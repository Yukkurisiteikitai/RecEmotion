package com.example.recemotion

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.example.recemotion.data.db.EmotionTimelineDao
import com.example.recemotion.data.db.EmotionTimelineEntity
import com.example.recemotion.databinding.FragmentChatBinding
import com.example.recemotion.domain.model.LlmStage
import com.example.recemotion.presentation.ChatAdapter
import com.example.recemotion.presentation.ChatDisplayItem
import com.example.recemotion.presentation.ConversationDisplayItem
import com.example.recemotion.presentation.ThoughtAnalysisViewModel
import com.example.recemotion.ui.EmotionCursorDrawable
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Chat 画面 Fragment。
 * 感情カーソル付き入力フィールド・Markdown 出力・感情タイムライン記録を担当する。
 */
@AndroidEntryPoint
class ChatFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ThoughtAnalysisViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var emotionCursorDrawable: EmotionCursorDrawable

    @Inject lateinit var emotionTimelineDao: EmotionTimelineDao

    // 現在の感情/ストレス状態 (onResults で更新)
    private var currentEmotion = "Neutral"
    private var currentStress = 1
    private var currentEnergy = 3

    // 定期タイムライン記録 Job
    private var periodicEmotionJob: Job? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
            else Toast.makeText(requireContext(), "カメラ権限が必要です", Toast.LENGTH_SHORT).show()
        }

    // ── Fragment Lifecycle ────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceLandmarkerHelper = FaceLandmarkerHelper(
            context = requireContext(),
            faceLandmarkerHelperListener = this
        )
        chatAdapter = ChatAdapter()
        emotionCursorDrawable = EmotionCursorDrawable()

        setupUI()
        setupEmotionCursor()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        collectViewModelState()
        startPeriodicEmotionLogging()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopCamera()
            periodicEmotionJob?.cancel()
        } else {
            startCamera()
            startPeriodicEmotionLogging()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        periodicEmotionJob?.cancel()
        cameraExecutor.shutdown()
        faceLandmarkerHelper.clearFaceLandmarker()
        _binding = null
    }

    // ── UI Setup ─────────────────────────────────────────────────────

    private fun setupUI() {
        binding.recyclerChatHistory.adapter = chatAdapter

        binding.btnChatAnalyze.setOnClickListener {
            val text = binding.edtChatInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "テキストを入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 解析時点の感情をタイムラインに記録
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            viewLifecycleOwner.lifecycleScope.launch {
                emotionTimelineDao.insert(
                    EmotionTimelineEntity(
                        emotion = currentEmotion,
                        stressLevel = currentStress,
                        energyLevel = currentEnergy,
                        sessionDate = today,
                        trigger = "analysis"
                    )
                )
            }

            binding.chatProgressContainer.visibility = View.VISIBLE
            viewModel.analyze(text)
            binding.edtChatInput.setText("")

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun setupEmotionCursor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.edtChatInput.textCursorDrawable = emotionCursorDrawable
        }
    }

    // ── Camera ───────────────────────────────────────────────────────

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.chatViewFinder.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
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
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ── FaceLandmarker Callbacks ──────────────────────────────────────

    override fun onResults(result: FaceLandmarkerResult, inferenceTime: Long) {
        if (result.faceLandmarks().isEmpty()) return

        val landmarks = result.faceLandmarks()[0]
        val flattened = FloatArray(landmarks.size * 3)
        for (i in landmarks.indices) {
            val p = landmarks[i]
            flattened[i * 3] = p.x(); flattened[i * 3 + 1] = p.y(); flattened[i * 3 + 2] = p.z()
        }

        MainActivity.pushFaceLandmarks(flattened)
        val jsonStr = MainActivity.getAnalysisJson("")
        requireActivity().runOnUiThread { updateEmotionUI(jsonStr) }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "FaceLandmarker error: $error")
    }

    override fun onEmpty() {}

    // ── Emotion UI Update ─────────────────────────────────────────────

    private fun updateEmotionUI(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val emotionData = json.getJSONObject("emotion_data")
            val context = json.getJSONObject("context")

            val isCalibrated = emotionData.optBoolean("is_calibrated", false)
            if (isCalibrated) {
                binding.chatOverlayCalibration.visibility = View.GONE
            } else {
                binding.chatOverlayCalibration.visibility = View.VISIBLE
                return
            }

            currentEmotion = emotionData.optString("current_emotion", "Neutral")
            currentStress = context.optInt("stress_level", 1)
            currentEnergy = context.optInt("energy_level", 3)

            // HUD 更新
            binding.txtChatEmotion.text = currentEmotion.uppercase()
            binding.txtChatStress.text = "STRESS: $currentStress"

            // 感情ドット色更新
            binding.emotionDot.setBackgroundColor(EmotionCursorDrawable.emotionToColor(currentEmotion))

            // カーソル色更新 (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                emotionCursorDrawable.updateEmotion(currentEmotion, currentStress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error: ${e.message}")
        }
    }

    // ── Periodic Emotion Logging ──────────────────────────────────────

    private fun startPeriodicEmotionLogging() {
        periodicEmotionJob?.cancel()
        periodicEmotionJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(30_000L) // 30秒ごと
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                emotionTimelineDao.insert(
                    EmotionTimelineEntity(
                        emotion = currentEmotion,
                        stressLevel = currentStress,
                        energyLevel = currentEnergy,
                        sessionDate = today,
                        trigger = "periodic"
                    )
                )
            }
        }
    }

    // ── ViewModel 収集 ────────────────────────────────────────────────

    private fun collectViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progress.collect { progress ->
                    val isActive = progress.stage == LlmStage.LOADING ||
                            progress.stage == LlmStage.GENERATING
                    binding.chatProgressContainer.visibility = if (isActive) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.error?.let { error ->
                        binding.chatProgressContainer.visibility = View.GONE
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyItems.collect { items ->
                    val chatItems = items.flatMap { it.toChatDisplayItems() }
                    chatAdapter.submitList(chatItems) {
                        if (chatItems.isNotEmpty()) {
                            binding.recyclerChatHistory.smoothScrollToPosition(chatItems.size - 1)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "ChatFragment"
        const val FRAGMENT_TAG = "CHAT"
    }
}

/**
 * ConversationDisplayItem → ChatDisplayItem リスト変換。
 * ThoughtAnalysis は UserMessage (入力) + AssistantOutput (LLM応答) の2つに展開する。
 */
private fun ConversationDisplayItem.toChatDisplayItems(): List<ChatDisplayItem> {
    return when (this) {
        is ConversationDisplayItem.TopicHeader -> listOf(
            ChatDisplayItem.TopicDivider(id = id, title = title, isResolved = isResolved)
        )
        is ConversationDisplayItem.ThoughtAnalysis -> {
            val emotion = result?.emotions?.firstOrNull() ?: "Neutral"
            val ts = createdAt
            buildList {
                add(ChatDisplayItem.UserMessage(
                    id = id,
                    text = rawText,
                    emotion = emotion,
                    stressLevel = 1,
                    timestamp = ts
                ))
                if (result != null) {
                    val outputText = buildString {
                        if (result.emotions.isNotEmpty()) appendLine("**感情**: ${result.emotions.joinToString(", ")}")
                        if (result.statedFacts.isNotEmpty()) {
                            appendLine("\n**事実**:")
                            result.statedFacts.forEach { appendLine("- $it") }
                        }
                        if (result.assumptions.isNotEmpty()) {
                            appendLine("\n**仮定**:")
                            result.assumptions.forEach { appendLine("- ${it.text}") }
                        }
                        if (result.possibleBiases.isNotEmpty()) {
                            appendLine("\n**バイアス**: ${result.possibleBiases.joinToString(", ") { it.name }}")
                        }
                    }.trim()
                    if (outputText.isNotEmpty()) {
                        add(ChatDisplayItem.AssistantOutput(
                            id = id * -1L,
                            markdownText = outputText,
                            emotion = emotion,
                            timestamp = ts + 1
                        ))
                    }
                }
            }
        }
        is ConversationDisplayItem.SystemMessage -> listOf(
            ChatDisplayItem.SystemNotice(id = id, message = message, isError = isError)
        )
        is ConversationDisplayItem.ToDoItem -> listOf(
            ChatDisplayItem.SystemNotice(id = id, message = "TODO: $description", isError = false)
        )
    }
}
