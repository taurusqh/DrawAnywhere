package com.drawanywhere.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.PI
import com.drawanywhere.R
import com.drawanywhere.drawing.DrawingEngine
import com.drawanywhere.drawing.DrawTool

interface ToolPaletteCallback {
    fun onCloseClicked()
    fun onSaveClicked()
    fun onModeToggleClicked()
    fun onUndoClicked()
    fun onClearClicked()
    fun onExitClicked()
}

class ToolPaletteView(
    context: Context,
    private val engine: DrawingEngine
) : HorizontalScrollView(context) {

    var callback: ToolPaletteCallback? = null

    private val toolButtons = mutableMapOf<DrawTool, FrameLayout>()
    private var activeToolBtn: FrameLayout? = null
    private val colorButtons = mutableMapOf<Int, FrameLayout>()
    private var activeColorBtn: FrameLayout? = null
    private val strokeButtons = mutableListOf<FrameLayout>()
    private var activeStrokeBtn: FrameLayout? = null
    lateinit var modeButton: Button

    private val inner: LinearLayout

    init {
        // 外层横向可滚动
        isHorizontalScrollBarEnabled = false
        clipToPadding = false

        inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(8))
        }
        addView(inner)

        // 设置背景
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                dp(16).toFloat(), dp(16).toFloat(),
                dp(16).toFloat(), dp(16).toFloat(),
                0f, 0f, 0f, 0f
            )
            setColor(0xAA181824.toInt())
        }

        buildToolbar()
    }

    private fun buildToolbar() {
        // === 绘图工具 ===
        addToolButton(DrawTool.PEN)
        addToolButton(DrawTool.ERASER)
        addToolButton(DrawTool.LINE)
        addToolButton(DrawTool.RECT)
        addToolButton(DrawTool.CIRCLE)
        addToolButton(DrawTool.DASHED_LINE)
        addToolButton(DrawTool.WAVE_LINE)
        addDivider()

        // === 操作 ===
        addUndoButton()
        addActionButton("🗑") { callback?.onClearClicked() }
        addActionButton("💾") { callback?.onSaveClicked() }
        addActionButton("✕") { callback?.onCloseClicked() }
        addExitButton()
        addDivider()

        // === 颜色 ===
        addColorButton(0xFFFF3B30.toInt())  // 红
        addColorButton(0xFFFFD60A.toInt())  // 黄
        addColorButton(0xFF0A84FF.toInt())  // 蓝
        addDivider()

        // === 粗细 ===
        addStrokeButton(3f)
        addStrokeButton(7f)
        addStrokeButton(12f)
        addDivider()

        // === 模式切换 ===
        modeButton = createModeButton()
        inner.addView(modeButton)

        // 默认选中
        selectTool(DrawTool.PEN)
        selectColor(0xFFFF3B30.toInt())
        selectStroke(1)  // 中间粗细默认
    }

    private fun addToolButton(tool: DrawTool) {
        val size = dp(40)
        val icon = createToolIcon(tool, size)
        icon.isClickable = false
        // 底部选中指示条
        val indicator = View(context).apply {
            val lp = FrameLayout.LayoutParams(dp(24), dp(3))
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = dp(2)
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(1.5f).toFloat()
                setColor(0xFF667EEA.toInt())
            }
            visibility = View.GONE
            tag = "indicator"
        }
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(size, size)
            addView(icon)
            addView(indicator)
            setOnClickListener {
                engine.setTool(tool)
                selectTool(tool)
                postInvalidateViews()
            }
        }
        toolButtons[tool] = container
        inner.addView(container)
    }

    private fun createToolIcon(tool: DrawTool, size: Int): ImageView {
        val iconSize = size - dp(10)
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val cx = iconSize / 2f
        val cy = iconSize / 2f
        val h = iconSize / 2f - dp(4).toFloat()
        val s = dp(3).toFloat()  // stroke width

        when (tool) {
            DrawTool.PEN -> {
                // 原型风格：✏️ emoji
                f.style = Paint.Style.FILL
                f.color = Color.WHITE
                f.textSize = iconSize * 0.75f
                f.textAlign = Paint.Align.CENTER
                val fm = f.fontMetrics
                val baseY = cy - (fm.ascent + fm.descent) / 2f
                c.drawText("✏️", cx, baseY, f)
            }
            DrawTool.ERASER -> {
                // Material Design 橡皮擦：倾斜矩形 + 擦除面 + 把柄
                p.strokeWidth = s
                p.style = Paint.Style.FILL
                // 主体（浅灰/粉色矩形）
                f.color = 0xFFFF8A9E.toInt()
                val body = Path()
                body.moveTo(cx - h * 0.8f, cy + h * 0.3f)
                body.lineTo(cx - h * 0.3f, cy + h * 0.8f)
                body.lineTo(cx + h * 0.8f, cy - h * 0.3f)
                body.lineTo(cx + h * 0.3f, cy - h * 0.8f)
                body.close()
                c.drawPath(body, f)
                // 擦除面（蓝色区域）
                f.color = 0xFF4A8BFF.toInt()
                val eraser = Path()
                eraser.moveTo(cx - h * 0.8f, cy + h * 0.3f)
                eraser.lineTo(cx - h * 0.3f, cy + h * 0.8f)
                eraser.lineTo(cx + h * 0.15f, cy + h * 0.35f)
                eraser.lineTo(cx - h * 0.35f, cy - h * 0.15f)
                eraser.close()
                c.drawPath(eraser, f)
                // 白色擦除边缘
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(2).toFloat()
                p.color = Color.WHITE
                val edge = Path()
                edge.moveTo(cx - h * 0.65f, cy + h * 0.15f)
                edge.lineTo(cx - h * 0.15f, cy - h * 0.35f)
                c.drawPath(edge, p)
                // 右侧把柄
                f.color = 0x66666666.toInt()
                f.style = Paint.Style.FILL
                val handle = Path()
                handle.moveTo(cx + h * 0.3f, cy - h * 0.8f)
                handle.lineTo(cx + h * 0.8f, cy - h * 0.3f)
                handle.lineTo(cx + h * 0.9f, cy - h * 0.4f)
                handle.lineTo(cx + h * 0.4f, cy - h * 0.9f)
                handle.close()
                c.drawPath(handle, f)
            }
            DrawTool.LINE -> {
                p.strokeWidth = dp(3.5f).toFloat()
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                c.drawLine(cx - h, cy + h, cx + h, cy - h, p)
            }
            DrawTool.RECT -> {
                p.strokeWidth = dp(3.5f).toFloat()
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                c.drawRect(cx - h, cy - h * 0.8f, cx + h, cy + h * 0.8f, p)
            }
            DrawTool.CIRCLE -> {
                p.strokeWidth = dp(3.5f).toFloat()
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                c.drawCircle(cx, cy, h * 0.85f, p)
            }
            DrawTool.DASHED_LINE -> {
                p.strokeWidth = dp(3.5f).toFloat()
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                p.pathEffect = DashPathEffect(floatArrayOf(dp(6).toFloat(), dp(5).toFloat()), 0f)
                c.drawLine(cx - h, cy, cx + h, cy, p)
                p.pathEffect = null
            }
            DrawTool.WAVE_LINE -> {
                p.strokeWidth = dp(3f).toFloat()
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                val wl = h * 0.7f
                val amp = h * 0.25f
                val path = Path()
                path.moveTo(cx - wl, cy)
                for (i in 1..12) {
                    val frac = i / 12.0f
                    val px = cx - wl + frac * 2 * wl
                    val py = cy + amp * kotlin.math.sin(frac * 2 * PI.toFloat() * 2)
                    path.lineTo(px, py)
                }
                c.drawPath(path, p)
            }
        }

        return ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    private fun selectTool(tool: DrawTool) {
        toolButtons.values.forEach { container ->
            container.setBackgroundColor(Color.TRANSPARENT)
            container.alpha = 0.4f
            val ind = container.findViewWithTag<View>("indicator")
            ind?.visibility = View.GONE
        }
        val container = toolButtons[tool]
        container?.let {
            it.setBackgroundColor(0xFF4A6BDF.toInt())
            it.alpha = 1f
            val ind = it.findViewWithTag<View>("indicator")
            ind?.visibility = View.VISIBLE
        }
    }

    private fun addUndoButton() {
        val size = dp(40)
        val iconSize = size - dp(8)
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val cx = iconSize / 2f
        val cy = iconSize / 2f
        // 原型风格：↩ emoji
        f.textSize = iconSize * 0.7f
        f.textAlign = Paint.Align.CENTER
        val fm = f.fontMetrics
        val baseY = cy - (fm.ascent + fm.descent) / 2f
        c.drawText("↩", cx, baseY, f)
        val icon = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        icon.isClickable = false
        val btn = FrameLayout(context).apply {
            layoutParams = LayoutParams(size, size)
            addView(icon, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER })
            setOnClickListener { callback?.onUndoClicked() }
        }
        addClickEffect(btn)
        inner.addView(btn)
    }

    private fun addExitButton() {
        val size = dp(40)
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_power)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            val pad = dp(7)
            setPadding(pad, pad, pad, pad)
        }
        val btn = FrameLayout(context).apply {
            layoutParams = LayoutParams(size, size)
            addView(icon, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER })
            setOnClickListener { callback?.onExitClicked() }
        }
        addClickEffect(btn)
        inner.addView(btn)
    }

    private fun addActionButton(icon: String, onClick: () -> Unit) {
        val size = dp(40)
        val btn = createIconButton(icon, size)
        val wrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(size, size)
            addView(btn, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER })
            setOnClickListener { onClick() }
        }
        addClickEffect(wrapper)
        inner.addView(wrapper)
    }

    private fun addColorButton(color: Int) {
        val size = dp(30)
        val outerSize = size + dp(12)
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        val innerDot = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            background = dot
        }
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(outerSize, outerSize)
            setOnClickListener {
                engine.setColor(color)
                selectColor(color)
                postInvalidateViews()
            }
            addView(innerDot)
        }
        colorButtons[color] = container
        inner.addView(container)
    }

    private fun selectColor(color: Int) {
        activeColorBtn?.let { container ->
            // 恢复默认：无外环
            container.setBackgroundColor(Color.TRANSPARENT)
        }
        val container = colorButtons[color]
        container?.let {
            // 选中时：亮蓝色粗外环
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(4), 0xFF4A6BDF.toInt())
            }
            it.background = ring
        }
        activeColorBtn = container
    }

    private fun addStrokeButton(width: Float) {
        val btnSize = dp(44)
        val dotSize = when (width) {
            3f -> dp(5)
            7f -> dp(10)
            12f -> dp(17)
            else -> dp(8)
        }
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(btnSize, btnSize)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                engine.setStrokeWidth(width)
                val idx = strokeButtons.indexOf(this)
                selectStroke(idx)
                postInvalidateViews()
            }
        }
        val dot = View(context).apply {
            val lp = FrameLayout.LayoutParams(dotSize, dotSize)
            lp.gravity = Gravity.CENTER
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }
        container.addView(dot)
        strokeButtons.add(container)
        inner.addView(container)
    }

    private fun selectStroke(index: Int) {
        activeStrokeBtn?.let { btn ->
            btn.alpha = 0.35f
            btn.setBackgroundColor(Color.TRANSPARENT)
        }
        val btn = strokeButtons.getOrNull(index)
        btn?.let {
            it.alpha = 1f
            it.setBackgroundColor(0xFF4A6BDF.toInt())
        }
        activeStrokeBtn = btn
    }

    private fun createModeButton(): Button {
        return Button(context).apply {
            text = "✋ 穿透"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(12), 0, dp(12), 0)
            minimumWidth = 0
            minWidth = 0
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0x18FFFFFF.toInt())
                setStroke(dp(1), 0x1AFFFFFF.toInt())
            }
            background = bg
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(36))
            setOnClickListener { callback?.onModeToggleClicked() }
        }
    }

    private fun createIconButton(text: String, size: Int): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            alpha = 0.5f
            isClickable = false   // 让父容器 FrameLayout 处理点击
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minWidth = 0
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.TRANSPARENT)
            }
            background = bg
            layoutParams = LayoutParams(size, size)
            gravity = Gravity.CENTER
        }
    }

    private fun addDivider() {
        val divider = View(context).apply {
            val lp = LayoutParams(dp(1), dp(26))
            lp.setMargins(dp(6), 0, dp(6), 0)
            layoutParams = lp
            setBackgroundColor(0x14FFFFFF.toInt())
        }
        inner.addView(divider)
    }

    fun updateModeButton(isDrawMode: Boolean) {
        modeButton.text = if (isDrawMode) "✋ 穿透" else "✏️ 绘图"
    }

    private fun postInvalidateViews() {
        // 尝试刷新画布
        var p = parent
        while (p != null) {
            if (p is FrameLayout) {
                for (i in 0 until p.childCount) {
                    val child = p.getChildAt(i)
                    if (child is DrawingCanvasView) {
                        child.postInvalidate()
                        return
                    }
                }
            }
            p = p.parent
        }
        postInvalidate()
    }

    private fun addClickEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(60).start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    false
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
