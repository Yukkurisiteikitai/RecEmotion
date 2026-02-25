package com.example.recemotion.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * EditText のカーソルを感情の色で描画するカスタム Drawable。
 * `editText.setTextCursorDrawable(EmotionCursorDrawable())` で設定する（API 29+）。
 *
 * 感情→色マッピング:
 *   HAPPY     → #4CAF50 (緑)
 *   SAD       → #2196F3 (青)
 *   ANGRY     → #F44336 (赤)
 *   FEARFUL   → #FF9800 (橙)
 *   DISGUSTED → #9C27B0 (紫)
 *   SURPRISED → #FFEB3B (黄)
 *   NEUTRAL / その他 → #FFFFFF (白)
 *
 * ストレスレベル (1-5) によって alpha を変化させる:
 *   stress=1 → alpha 128、stress=5 → alpha 255
 */
class EmotionCursorDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private var currentColor = Color.WHITE
    private var currentAlpha = 255

    fun updateEmotion(emotion: String, stressLevel: Int) {
        currentColor = emotionToColor(emotion)
        // stress 1→128, stress 5→255 の線形マッピング
        currentAlpha = 128 + ((stressLevel.coerceIn(1, 5) - 1) * 127 / 4)
        paint.color = currentColor
        paint.alpha = currentAlpha
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.width() / 2f
        canvas.drawLine(cx, bounds.top.toFloat(), cx, bounds.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        fun emotionToColor(emotion: String): Int {
            return when (emotion.uppercase().trim()) {
                "HAPPY"     -> Color.parseColor("#4CAF50")
                "SAD"       -> Color.parseColor("#2196F3")
                "ANGRY"     -> Color.parseColor("#F44336")
                "FEARFUL"   -> Color.parseColor("#FF9800")
                "DISGUSTED" -> Color.parseColor("#9C27B0")
                "SURPRISED" -> Color.parseColor("#FFEB3B")
                else        -> Color.WHITE
            }
        }
    }
}
