package com.example.recemotion

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.example.recemotion.data.parser.CabochaDependencyParser
import com.example.recemotion.data.parser.CabochaModelManager
import com.example.recemotion.data.parser.DictionaryManager
import com.example.recemotion.data.parser.LogicalFlowAnalyzerImpl
import com.example.recemotion.data.parser.LogicalFlowQuestionGenerator
import com.example.recemotion.data.parser.LogicalFlowReportBuilder
import com.example.recemotion.data.parser.NativeCabochaParser
import com.example.recemotion.data.parser.ParserComparisonLogger
import com.example.recemotion.databinding.FragmentMainScreenBinding
import com.example.recemotion.domain.model.LlmStage
import com.example.recemotion.domain.model.QuestionType
import com.example.recemotion.domain.model.UserResponse
import com.example.recemotion.domain.model.VerificationQuestion
import com.example.recemotion.presentation.ConversationAdapter
import com.example.recemotion.presentation.ThoughtAnalysisViewModel
import com.example.recemotion.settings.SetupSettingsStore
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * MAIN画面のFragment。
 * カメラ・顔感情検出・LLM解析・コントロールUIを担当する。
 */
@AndroidEntryPoint
class MainScreenFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    private var _binding: FragmentMainScreenBinding? = null
    private val binding get() = _binding!!

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var modelDownloadHelper: ModelDownloadHelper
    private val thoughtAnalysisViewModel: ThoughtAnalysisViewModel by viewModels()
    private lateinit var conversationAdapter: ConversationAdapter

    @Inject lateinit var setupSettings: SetupSettingsStore
    @Inject lateinit var flowAnalyzerImpl: LogicalFlowAnalyzerImpl

    private var wakeTimeUnix: Long = 0

    // --- Parser 比較 ---
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var cabochaModelManager: CabochaModelManager
    private val kuromojiParser = CabochaDependencyParser()
    private var nativeParser: NativeCabochaParser? = null

    // --- Activity Result Launchers ---

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
            else Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT).show()
        }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) checkAndDownloadModel()
            else Toast.makeText(requireContext(), "Storage permission denied", Toast.LENGTH_SHORT).show()
        }

    private val openModelFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            binding.recyclerConversation.visibility = View.VISIBLE
            thoughtAnalysisViewModel.pushSystemMessage("Loading model...")

            if (copyModelFromUri(uri)) {
                thoughtAnalysisViewModel.pushSystemMessage("Model file copied successfully.")
                thoughtAnalysisViewModel.pushSystemMessage("Initializing MediaPipe LLM...")
                thoughtAnalysisViewModel.initModel()
                Toast.makeText(requireContext(), "Model imported and ready.", Toast.LENGTH_SHORT).show()
            } else {
                val errorMsg = "Failed to import model. Please ensure it is a valid MediaPipe LLM model (.bin or .task)."
                thoughtAnalysisViewModel.pushSystemMessage(errorMsg, isError = true)
                Toast.makeText(requireContext(), "Failed to import model.", Toast.LENGTH_LONG).show()
            }
        }

    // --- Fragment Lifecycle ---

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        // FaceLandmarker.createFromOptions() is a heavy I/O operation that blocks the calling thread.
        // Submit to cameraExecutor (single-thread FIFO) so it completes before any frame analysis tasks.
        val appContext = requireContext().applicationContext
        cameraExecutor.execute {
            try {
                faceLandmarkerHelper = FaceLandmarkerHelper(context = appContext, faceLandmarkerHelperListener = this)
            } catch (e: Exception) {
                Log.e(TAG, "FaceLandmarkerHelper init failed", e)
                view?.post { onError("FaceLandmarker init failed: ${e.message ?: "unknown"}") }
            }
        }
        modelDownloadHelper = ModelDownloadHelper(requireContext())
        conversationAdapter = ConversationAdapter(
            onGenerateToDo = { item -> thoughtAnalysisViewModel.generateToDo(item) },
            onToggleToDo = { id, completed -> thoughtAnalysisViewModel.toggleToDo(id, completed) },
            onResolveTopic = { topicId -> showResolveTopicDialog(topicId) }
        )

        setupUI()

        // SetupFragment が本日セットアップ済みならその結果を引き継ぐ（Rustのキャリブレーション状態を保持）
        viewLifecycleOwner.lifecycleScope.launch {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            if (setupSettings.lastDateFlow.first() == today) {
                val stored = setupSettings.wakeTimeUnixFlow.first()
                wakeTimeUnix = if (stored > 0) stored else defaultWakeTimeUnix()
                val c = Calendar.getInstance().also { it.timeInMillis = wakeTimeUnix * 1000 }
                binding.txtWakeTime.text = String.format("%02d:%02d",
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                // SetupFragment でキャリブレーション済みなのでオーバーレイを非表示に
                binding.overlayCalibration.visibility = View.GONE
            } else {
                wakeTimeUnix = defaultWakeTimeUnix()
                withContext(Dispatchers.IO) {
                    MainActivity.initSessionSafe(wakeTimeUnix, priority = 1)
                }
                // 本日未セットアップの場合はキャリブレーション用オーバーレイを明示的に表示
                binding.overlayCalibration.visibility = View.VISIBLE
            }
        }

        checkAndDownloadModel()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        collectLlmResults()
        collectLlmProgress()
        collectThoughtAnalysisState()
        collectHistoryItems()

        // --- 辞書・モデルインストール & NativeCabochaParser 初期化 ---
        dictionaryManager = DictionaryManager(requireContext())
        cabochaModelManager = CabochaModelManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            initNativeParser()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) stopCamera() else startCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Submit cleanup to executor before shutdown so it runs after any in-flight init/frame tasks.
        cameraExecutor.execute { faceLandmarkerHelper?.clearFaceLandmarker() }
        cameraExecutor.shutdown()
        _binding = null
    }

    // --- UI Setup ---

    private fun setupUI() {
        binding.recyclerConversation.adapter = conversationAdapter

        // 起床時刻ピッカー
        binding.btnSetWakeTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val newCal = Calendar.getInstance()
                newCal.set(Calendar.HOUR_OF_DAY, hour)
                newCal.set(Calendar.MINUTE, minute)
                wakeTimeUnix = newCal.timeInMillis / 1000

                binding.txtWakeTime.text = String.format("%02d:%02d", hour, minute)
                MainActivity.initSessionSafe(wakeTimeUnix)
                Toast.makeText(requireContext(), "Session Reset", Toast.LENGTH_SHORT).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        // ストレスレベルスライダー
        binding.sliderStress.addOnChangeListener { _, value, _ ->
            MainActivity.updateStressLevel(value.toInt())
            binding.txtStats.text = binding.txtStats.text.toString()
                .replace(Regex("STRESS: \\d+"), "STRESS: ${value.toInt()}")
        }

        // 再キャリブレーションボタン
        binding.btnReset.setOnClickListener {
            MainActivity.initSessionSafe(wakeTimeUnix)
        }

        // モデル選択ボタン
        binding.btnSelectModel.setOnClickListener {
            openModelFileLauncher.launch(arrayOf("*/*"))
        }

        // 論理フロー検証ボタン (旧 Kuromoji テスト)
        binding.btnKuromojiTest.setOnClickListener {
            showLogicalFlowDialog()
        }

        // 解析ボタン
        binding.btnAnalyze.setOnClickListener {
            val text = binding.edtReflection.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Please write a reflection first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.recyclerConversation.visibility = View.VISIBLE
            thoughtAnalysisViewModel.analyze(text)
            binding.edtReflection.setText("")

            // 両パーサーで比較実行（Logcat に出力）
            viewLifecycleOwner.lifecycleScope.launch {
                ParserComparisonLogger.compare(text, kuromojiParser, nativeParser)
            }

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun defaultWakeTimeUnix(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis / 1000
    }

    // --- NativeCabochaParser 初期化 ---

    private suspend fun initNativeParser() {
        if (!dictionaryManager.isInstalled()) {
            Log.i(TAG, "Installing MeCab dictionary (~51MB)...")
            dictionaryManager.install()
            Log.i(TAG, "Dictionary installed: ${dictionaryManager.dictPath}")
        }

        if (!cabochaModelManager.isInstalled()) {
            Log.i(TAG, "Installing CaboCha models (~81MB)...")
            cabochaModelManager.install()
            Log.i(TAG, "CaboCha models installed: ${cabochaModelManager.modelPath}")
        }

        val parser = NativeCabochaParser(
            mecabDicDir = dictionaryManager.dictPath,
            cabochaModelDir = cabochaModelManager.modelPath
        )
        val verifyResult = parser.nativeVerify(
            dictionaryManager.dictPath,
            cabochaModelManager.modelPath
        )
        Log.i(TAG, "NativeCabochaParser verify: $verifyResult (0=OK, 1=init失敗, 2=parse失敗)")

        if (verifyResult == 0) {
            nativeParser = parser
        } else {
            nativeParser = null
            Log.e(TAG, "NativeCabochaParser unavailable (code=$verifyResult)")
        }

        // 起動時ベンチマーク（Logcat に出力）
        ParserComparisonLogger.runBenchmark(kuromojiParser, nativeParser)
    }

    // --- Camera Control ---

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val helper = faceLandmarkerHelper
                        if (helper != null) {
                            helper.detectLiveStream(imageProxy, isFrontCamera = true)
                        } else {
                            Log.v(TAG, "FaceLandmarkerHelper not ready, skipping frame")
                        }
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // --- Face Landmark Callbacks ---

    override fun onResults(result: FaceLandmarkerResult, inferenceTime: Long) {
        if (result.faceLandmarks().isEmpty()) return

        val firstFaceLandmarks = result.faceLandmarks()[0]
        val flattened = FloatArray(firstFaceLandmarks.size * 3)
        for (i in firstFaceLandmarks.indices) {
            val point = firstFaceLandmarks[i]
            flattened[i * 3] = point.x()
            flattened[i * 3 + 1] = point.y()
            flattened[i * 3 + 2] = point.z()
        }

        MainActivity.pushFaceLandmarks(flattened)
        val jsonStr = MainActivity.getAnalysisJson("")
        requireActivity().runOnUiThread { updateUI(jsonStr) }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "FaceLandmarker Error: $error")
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEmpty() {}

    // --- UI Updates ---

    private fun updateUI(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val context = json.getJSONObject("context")
            val emotionData = json.getJSONObject("emotion_data")

            val isCalibrated = emotionData.optBoolean("is_calibrated", false)
            val currentEmotion = emotionData.optString("current_emotion", "Neutral")
            val energy = context.optInt("energy_level", 3)
            val stress = context.optInt("stress_level", 1)

            if (isCalibrated) {
                binding.overlayCalibration.visibility = View.GONE
            } else {
                binding.overlayCalibration.visibility = View.VISIBLE
                return
            }

            binding.txtEmotion.text = currentEmotion.uppercase()
            binding.txtStats.text = "ENERGY: $energy | STRESS: $stress"

        } catch (e: Exception) {
            Log.e(TAG, "JSON Parse Error: ${e.message}")
        }
    }

    // --- Model Management ---

    private fun checkAndDownloadModel() {
        if (needsStoragePermission() && !hasStoragePermission()) {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        if (!modelDownloadHelper.isModelDownloaded()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("LLM Model Required")
                .setMessage("""
                    This app requires a MediaPipe LLM model.

                    Download Steps:
                    1. Download a MediaPipe LLM model that fits your device.
                    2. Rename it to 'model.task' or 'model.bin'.
                    3. Move it to Downloads on your Android device:
                        /storage/emulated/0/Download/model.task
                        (or /storage/emulated/0/Download/model.bin)

                    Alternative: You can still push to internal storage with:
                        adb push model.task /data/data/com.example.recemotion/files/model.task
                        (or adb push model.bin /data/data/com.example.recemotion/files/model.bin)
                """.trimIndent())
                .setPositiveButton("Select File") { _, _ ->
                    openModelFileLauncher.launch(arrayOf("*/*"))
                }
                .setNeutralButton("I've Downloaded It") { _, _ ->
                    if (modelDownloadHelper.isModelDownloaded()) {
                        thoughtAnalysisViewModel.initModel()
                        Toast.makeText(requireContext(), "Model found! Ready to analyze.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Model not found. Please follow the instructions.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Copy ADB Command") { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(
                        "ADB Command",
                        "adb push model.task /data/data/com.example.recemotion/files/model.task"
                    )
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Command copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .show()
        } else {
            thoughtAnalysisViewModel.initModel()
        }
    }

    private fun copyModelFromUri(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)

            val fileName = getFileName(uri)
            Log.i(TAG, "Selected file: $fileName")

            if (!isSupportedModelFormat(fileName)) {
                val errorMsg = "Unsupported format: $fileName. Supported: .bin, .task"
                Log.e(TAG, errorMsg)
                Toast.makeText(requireContext(), "Unsupported format. Supported: .bin, .task", Toast.LENGTH_SHORT).show()
                return false
            }

            val extension = fileName.substringAfterLast(".")
            val targetFile = java.io.File(requireContext().filesDir, "model.$extension")
            Log.i(TAG, "Copying to: ${targetFile.absolutePath}")

            requireContext().contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) return false
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    Log.i(TAG, "Copied $totalBytes bytes")
                }
            }
            Log.i(TAG, "Model copied successfully to: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model file", e)
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(index) else ModelDownloadHelper.MODEL_FILENAME
        } ?: ModelDownloadHelper.MODEL_FILENAME
    }

    private fun isSupportedModelFormat(fileName: String): Boolean {
        return fileName.substringAfterLast(".").lowercase() in listOf("bin", "task")
    }

    private fun needsStoragePermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    private fun hasStoragePermission(): Boolean {
        if (!needsStoragePermission()) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // --- Coroutine Collectors ---

    private fun collectLlmResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                thoughtAnalysisViewModel.partialResults.collect { part ->
                    thoughtAnalysisViewModel.pushSystemMessage(part)
                }
            }
        }
    }

    private fun collectLlmProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                thoughtAnalysisViewModel.progress.collect { progress ->
                    val isActive = progress.stage == LlmStage.LOADING ||
                        progress.stage == LlmStage.GENERATING
                    binding.progressContainer.visibility = if (isActive) View.VISIBLE else View.GONE

                    val total = progress.total
                    if (total > 0L) {
                        binding.progressBar.isIndeterminate = false
                        val maxValue = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val currentValue = progress.current
                            .coerceAtMost(total)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                        binding.progressBar.max = maxValue
                        binding.progressBar.progress = currentValue
                    } else {
                        binding.progressBar.isIndeterminate = true
                    }
                }
            }
        }
    }

    private fun collectThoughtAnalysisState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                thoughtAnalysisViewModel.uiState.collect { state ->
                    if (state.isAnalyzing) {
                        binding.recyclerConversation.visibility = View.VISIBLE
                        binding.progressContainer.visibility = View.VISIBLE
                    }

                    state.error?.let { error ->
                        binding.progressContainer.visibility = View.GONE
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }

                    if (state.isNewTopicDetected) {
                        Toast.makeText(requireContext(), "新しい話題が始まりました：${state.topicTitle ?: "未定義"}", Toast.LENGTH_LONG).show()
                        showResolutionConfirmDialog()
                        thoughtAnalysisViewModel.dismissTopicNotification()
                    }

                    if (state.finalResult != null) {
                        binding.progressContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun collectHistoryItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                thoughtAnalysisViewModel.historyItems.collect { items ->
                    conversationAdapter.submitList(items) {
                        if (items.isNotEmpty()) {
                            binding.recyclerConversation.smoothScrollToPosition(items.size - 1)
                        }
                    }
                }
            }
        }
    }

    private fun showResolutionConfirmDialog() {
        val topicId = thoughtAnalysisViewModel.uiState.value.currentTopicId ?: return
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("話題の解決確認")
            .setMessage("新しい話題に移ったようです。前の話題はスッキリ解決しましたか？")
            .setPositiveButton("解決した") { _, _ ->
                showResolveTopicDialog(topicId)
            }
            .setNegativeButton("まだ途中", null)
            .show()
    }

    private fun showResolveTopicDialog(topicId: Long) {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "この議題の結論や、ToDoを実行した結果を記入してください。"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setPadding(48, 32, 48, 32)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("議題の完結")
            .setMessage("ToDoの結果や最終的な気づきを入力して、この議題を完了させましょう。")
            .setView(editText)
            .setPositiveButton("完了") { _, _ ->
                val result = editText.text.toString()
                if (result.isNotBlank()) {
                    thoughtAnalysisViewModel.resolveTopic(topicId, result)
                } else {
                    Toast.makeText(requireContext(), "結果を入力してください", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // --- 論理フロー検証システム (Kuromoji ベース) ---

    /** Step 1: テキスト入力ダイアログを表示 */
    private fun showLogicalFlowDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "分析するテキストを入力してください...\n（複数の文でもOK）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setPadding(48, 32, 48, 32)
        }

        val parserLabel = if (nativeParser != null) "CaboCha（NDK）" else "Kuromoji（フォールバック）"
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("論理フロー検証システム")
            .setMessage(
                "${parserLabel}でテキストの論理構造を抽出します。\n" +
                    "質問への回答を通じて「脳内フロー」との乖離を検出します。"
            )
            .setView(editText)
            .setPositiveButton("解析開始") { _, _ ->
                val text = editText.text.toString()
                if (text.isBlank()) {
                    Toast.makeText(requireContext(), "テキストを入力してください", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runLogicalFlowVerification(text)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    /** Step 2-5: 論理フロー検証の全フェーズを実行 */
    private fun runLogicalFlowVerification(text: String) {
        binding.recyclerConversation.visibility = View.VISIBLE
        binding.progressContainer.visibility = View.VISIBLE

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // ── Phase 1 & 2: 解析（nativeParser が有効なら CaboCha、なければ Kuromoji）──
                val analysis = flowAnalyzerImpl.analyze(text, nativeParser)
                val reportBuilder = LogicalFlowReportBuilder()

                binding.progressContainer.visibility = View.GONE
                Log.d(TAG, reportBuilder.buildPhase1Report(analysis))

                // ── Phase 3 移行確認 ─────────────────────────────────────────
                val questionGenerator = LogicalFlowQuestionGenerator()
                val questions = questionGenerator.generateQuestions(analysis)

                val proceed = suspendCancellableCoroutine { cont ->
                    val msg = if (questions.isEmpty()) {
                        "解析完了です。\n（テキストが短いか1文のみのため検証質問はありません）"
                    } else {
                        "解析完了です。\n${questions.size}件の検証質問に答えて\n「脳内フロー」との乖離を確認しましょう。"
                    }
                    val dialog = android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Phase 3: 検証フェーズ")
                        .setMessage(msg)
                        .setPositiveButton(if (questions.isEmpty()) "閉じる" else "質問に答える") { _, _ ->
                            if (cont.isActive) cont.resume(questions.isNotEmpty())
                        }
                        .apply {
                            if (questions.isNotEmpty()) {
                                setNegativeButton("スキップ") { _, _ ->
                                    if (cont.isActive) cont.resume(false)
                                }
                            }
                        }
                        .setCancelable(false)
                        .show()
                    cont.invokeOnCancellation { dialog.dismiss() }
                }

                if (!proceed) return@launch

                // ── Phase 3: インタラクティブ Q&A ───────────────────────────
                val userResponses = mutableListOf<UserResponse>()
                for ((index, question) in questions.withIndex()) {
                    val selected = showVerificationQuestion(question, index + 1, questions.size)
                    userResponses.add(
                        UserResponse(
                            questionId = question.id,
                            selectedOption = selected,
                            questionType = question.type,
                            relatedSentences = question.relatedSentences
                        )
                    )
                }

                // ── Phase 4 & 5: 乖離分析 + 最終レポート ────────────────────
                binding.progressContainer.visibility = View.VISIBLE
                val report = reportBuilder.buildReport(analysis, questions, userResponses)
                binding.progressContainer.visibility = View.GONE

                Log.d(TAG, reportBuilder.buildFinalReport(report))

            } catch (e: Exception) {
                Log.e(TAG, "Logical flow verification failed", e)
                binding.progressContainer.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 検証質問を AlertDialog で表示し、ユーザーが選択した選択肢を返す。
     * suspendCancellableCoroutine でコルーチンと同期する。
     */
    private suspend fun showVerificationQuestion(
        question: VerificationQuestion,
        current: Int,
        total: Int
    ): String = suspendCancellableCoroutine { cont ->
        val optionsArray = question.options.toTypedArray()
        var selectedIndex = 0

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("検証 Q$current/$total  [${question.type.label}]")
            .setMessage(question.questionText)
            .setSingleChoiceItems(optionsArray, 0) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("確認") { _, _ ->
                if (cont.isActive) cont.resume(question.options[selectedIndex])
            }
            .setCancelable(false)
            .show()

        cont.invokeOnCancellation { dialog.dismiss() }
    }

    companion object {
        const val TAG = "MainScreenFragment"
    }
}
