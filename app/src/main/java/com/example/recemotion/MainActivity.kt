package com.example.recemotion

import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.recemotion.databinding.ActivityMainBinding

/**
 * アプリのエントリーポイント。
 * ナビゲーションドロワーの管理とFragment切り替えのみを担当する。
 *
 * 画面ごとのロジックは各Fragmentが担当:
 * - メイン画面 (カメラ・感情検出・LLM解析): MainScreenFragment
 * - カレンダー画面: CalendarFragment
 *
 * JNI (Rust連携) の宣言もここに置く。
 * Rustの関数名 (Java_com_example_recemotion_MainActivity_xxx) を変えないため。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector

    private enum class Screen { MAIN, CALENDAR }
    private var currentScreen: Screen = Screen.MAIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupNavigation()
        setupSwipeGesture()

        // 初回起動時のみFragmentを追加する（画面回転などでは再追加しない）
        if (savedInstanceState == null) {
            val mainFrag = MainScreenFragment()
            val calFrag = CalendarFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, mainFrag, TAG_MAIN)
                .add(R.id.fragmentContainer, calFrag, TAG_CALENDAR)
                .hide(calFrag)
                .commit()
        }
    }

    private fun setupNavigation() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_main -> setScreen(Screen.MAIN)
                R.id.menu_calendar -> setScreen(Screen.CALENDAR)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.navView.setCheckedItem(R.id.menu_main)
    }

    private fun setScreen(screen: Screen) {
        if (currentScreen == screen) return
        currentScreen = screen

        val mainFrag = supportFragmentManager.findFragmentByTag(TAG_MAIN) ?: return
        val calFrag = supportFragmentManager.findFragmentByTag(TAG_CALENDAR) ?: return

        supportFragmentManager.beginTransaction().apply {
            when (screen) {
                Screen.MAIN -> { show(mainFrag); hide(calFrag) }
                Screen.CALENDAR -> { hide(mainFrag); show(calFrag) }
            }
        }.commit()
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)

                // 横スワイプを優先し、左→右でドロワーを開く
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (diffX > 100 && kotlin.math.abs(velocityX) > 100) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })

        binding.mainContent.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    companion object {
        const val TAG = "RecEmotion_Main"
        private const val TAG_MAIN = "MAIN"
        private const val TAG_CALENDAR = "CALENDAR"

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
