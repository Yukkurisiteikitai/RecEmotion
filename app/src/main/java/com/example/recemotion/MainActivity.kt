package com.example.recemotion

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.recemotion.data.db.ConversationTopicEntity
import com.example.recemotion.databinding.ActivityMainBinding
import com.example.recemotion.presentation.ThoughtAnalysisViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * アプリのエントリーポイント。
 * ナビゲーションドロワーの管理とFragment切り替えのみを担当する。
 *
 * 画面ごとのロジックは各Fragmentが担当:
 * - セットアップ画面 (初回日次起動): SetupFragment
 * - メイン画面 (カメラ・感情検出・LLM解析): MainScreenFragment
 * - カレンダー画面: CalendarFragment
 * - 設定画面: SettingsFragment
 *
 * JNI (Rust連携) の宣言もここに置く。
 * Rustの関数名 (Java_com_example_recemotion_MainActivity_xxx) を変えないため。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector

    private val viewModel: ThoughtAnalysisViewModel by viewModels()

    private enum class Screen { SETUP, CHAT, MAIN, CALENDAR, SETTINGS }
    private var currentScreen: Screen = Screen.CHAT

    // ナビゲーションドロワーのトピック項目管理
    private val topicMenuIdMap = mutableMapOf<Int, Long>() // menuItemId -> topicId
    private val TOPIC_GROUP_ID = 100
    private val TOPIC_HEADER_ITEM_ID = 101
    private val TOPIC_ITEM_ID_BASE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupNavigation()
        setupSwipeGesture()
        observeTopicsForDrawer()

        if (savedInstanceState == null) {
            val needsSetup = isFirstLaunchToday()
            val setupFrag = SetupFragment()
            val chatFrag = ChatFragment()
            val mainFrag = MainScreenFragment()
            val calFrag = CalendarFragment()
            val settingsFrag = SettingsFragment()

            val tx = supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, chatFrag, TAG_CHAT)
                .add(R.id.fragmentContainer, mainFrag, TAG_MAIN)
                .add(R.id.fragmentContainer, calFrag, TAG_CALENDAR)
                .add(R.id.fragmentContainer, settingsFrag, TAG_SETTINGS)
                .hide(mainFrag)
                .hide(calFrag)
                .hide(settingsFrag)

            if (needsSetup) {
                tx.add(R.id.fragmentContainer, setupFrag, TAG_SETUP)
                    .hide(chatFrag)
                currentScreen = Screen.SETUP
            } else {
                currentScreen = Screen.CHAT
            }

            tx.commit()
        }
    }

    private fun observeTopicsForDrawer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTopics.collect { topics ->
                    updateTopicsInDrawer(topics)
                }
            }
        }
    }

    private fun updateTopicsInDrawer(topics: List<ConversationTopicEntity>) {
        val menu = binding.navView.menu
        menu.removeGroup(TOPIC_GROUP_ID)
        topicMenuIdMap.clear()
        if (topics.isEmpty()) return

        menu.add(TOPIC_GROUP_ID, TOPIC_HEADER_ITEM_ID, Menu.NONE, "── TOPICS ──").apply {
            isEnabled = false
        }

        topics.take(15).forEachIndexed { index, topic ->
            val itemId = TOPIC_ITEM_ID_BASE + index
            topicMenuIdMap[itemId] = topic.id
            val prefix = if (topic.isResolved) "✓ " else "· "
            menu.add(TOPIC_GROUP_ID, itemId, index + 1, "$prefix${topic.title}")
        }
    }

    private fun isFirstLaunchToday(): Boolean {
        val prefs = getSharedPreferences(SetupFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(SetupFragment.KEY_LAST_DATE, "")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return lastDate != today
    }

    /** SetupFragment のセットアップ完了時に呼ばれる */
    fun onSetupComplete() {
        val setupFrag = supportFragmentManager.findFragmentByTag(TAG_SETUP) ?: return
        val chatFrag = supportFragmentManager.findFragmentByTag(TAG_CHAT) ?: return
        supportFragmentManager.beginTransaction()
            .hide(setupFrag)
            .show(chatFrag)
            .commit()
        currentScreen = Screen.CHAT
        binding.navView.setCheckedItem(R.id.menu_chat)
    }

    private fun setupNavigation() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            val topicId = topicMenuIdMap[item.itemId]
            if (topicId != null) {
                viewModel.selectTopic(topicId)
                setScreen(Screen.CHAT)
                binding.navView.setCheckedItem(R.id.menu_chat)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                return@setNavigationItemSelectedListener true
            }
            when (item.itemId) {
                R.id.menu_chat -> setScreen(Screen.CHAT)
                R.id.menu_main -> setScreen(Screen.MAIN)
                R.id.menu_calendar -> setScreen(Screen.CALENDAR)
                R.id.menu_settings -> setScreen(Screen.SETTINGS)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.navView.setCheckedItem(R.id.menu_chat)
    }

    private fun setScreen(screen: Screen) {
        if (currentScreen == screen) return
        currentScreen = screen

        val setupFrag = supportFragmentManager.findFragmentByTag(TAG_SETUP)
        val mainFrag = supportFragmentManager.findFragmentByTag(TAG_MAIN) ?: return
        val calFrag = supportFragmentManager.findFragmentByTag(TAG_CALENDAR) ?: return
        val settingsFrag = supportFragmentManager.findFragmentByTag(TAG_SETTINGS) ?: return

        val chatFrag = supportFragmentManager.findFragmentByTag(TAG_CHAT)

        supportFragmentManager.beginTransaction().apply {
            // 全て非表示にしてから対象を表示
            setupFrag?.let { hide(it) }
            chatFrag?.let { hide(it) }
            hide(mainFrag); hide(calFrag); hide(settingsFrag)
            when (screen) {
                Screen.SETUP -> setupFrag?.let { show(it) }
                Screen.CHAT -> chatFrag?.let { show(it) }
                Screen.MAIN -> show(mainFrag)
                Screen.CALENDAR -> show(calFrag)
                Screen.SETTINGS -> show(settingsFrag)
            }
        }.commit()
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (diffX > 100 && kotlin.math.abs(velocityX) > 100) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val TAG = "RecEmotion_Main"
        private const val TAG_SETUP = SetupFragment.TAG
        private const val TAG_CHAT = ChatFragment.FRAGMENT_TAG
        private const val TAG_MAIN = "MAIN"
        private const val TAG_CALENDAR = "CALENDAR"
        private const val TAG_SETTINGS = SettingsFragment.TAG

        // Rust (librecemotion.so) のロード
        init {
            try {
                System.loadLibrary("recemotion")
                Log.i(TAG, "librecemotion.so loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load librecemotion.so: ${e.message}", e)
                throw e
            }
        }

        // JNI Bridge: MainScreenFragment から MainActivity.xxx() として呼び出す
        @JvmStatic external fun initSession(wakeTime: Long)
        @JvmStatic external fun pushFaceLandmarks(landmarks: FloatArray)
        @JvmStatic external fun getAnalysisJson(text: String): String
        @JvmStatic external fun updateStressLevel(level: Int)
    }
}
