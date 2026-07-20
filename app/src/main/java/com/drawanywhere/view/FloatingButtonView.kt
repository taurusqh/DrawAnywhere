package com.drawanywhere.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.drawanywhere.R

interface FloatingButtonCallback {
    fun onFloatingButtonClicked()
}

class FloatingButtonView(context: Context) : FrameLayout(context) {

    var callback: FloatingButtonCallback? = null
    var onDragListener: ((dx: Float, dy: Float) -> Unit)? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val indicator: View

    init {
        layoutParams = FrameLayout.LayoutParams(
            dp(56), dp(56)
        )

        // 圆形背景
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF667EEA.toInt())
        }
        background = bg

        // 画笔图标
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_floating_pen)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).apply {
                gravity = Gravity.CENTER
            }
        }
        addView(icon)

        // 模式指示点（右下角）
        indicator = View(context).apply {
            val size = dp(8)
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(size, size)
                setColor(0xFF34C759.toInt())
            }
            background = dot
            layoutParams = FrameLayout.LayoutParams(dp(8), dp(8)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(4), dp(4))
            }
        }
        addView(indicator)
    }

    fun setModeIndicator(isDrawMode: Boolean) {
        val dot = indicator.background as? GradientDrawable
        dot?.setColor(if (isDrawMode) 0xFF34C759.toInt() else 0x40FFFFFF.toInt())
        indicator.invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.rawX
        val y = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                    isDragging = true
                    onDragListener?.invoke(dx, dy)
                }
                lastTouchX = x
                lastTouchY = y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    callback?.onFloatingButtonClicked()
                }
                return true
            }
        }
        return false
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
