package com.drawanywhere.view

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.drawanywhere.drawing.*

class DrawingCanvasView(
    context: Context,
    private val engine: DrawingEngine
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** CLEAR 模式（API 29+ 用 BlendMode，更低版本用 PorterDuffXfermode） */
    private val clearBlend: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BlendMode.CLEAR
        } else {
            @Suppress("DEPRECATION")
            PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    // ===== 离屏缓冲（像素橡皮擦使用） =====

    private var offscreenBitmap: Bitmap? = null
    private var offscreenCanvas: Canvas? = null

    /** 像素橡皮擦专用画笔（固定 CLEAR 模式） */
    private val pixelEraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        @Suppress("DEPRECATION")
        xfermode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) null else PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = BlendMode.CLEAR
        }
    }

    /** 离屏缓冲脏标记：引擎状态变更后需要重建 */
    private var offscreenDirty: Boolean = true

    /** 当前是否处于绘图模式 */
    var isDrawMode: Boolean = true
        set(value) {
            if (!value && field) finishStroke()
            field = value
        }

    var onSaveRequest: (() -> Unit)? = null

    /** 安全刷新（跨窗口安全，通过主线程 Handler 中转） */
    fun safeInvalidate() {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    if (isAttachedToWindow) {
                        // 外部调用安全刷新说明引擎状态已变更（undo/clear），标记重建
                        offscreenDirty = true
                        invalidate()
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // 当前笔画状态
    private var currentPoints = mutableListOf<DrawingPoint>()
    private var currentStartPoint: DrawingPoint? = null
    private var previewEndPoint: DrawingPoint? = null

    // 像素橡皮擦状态
    private var lastEraserX = 0f
    private var lastEraserY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            try {
                val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                offscreenBitmap?.recycle()
                offscreenBitmap = newBitmap
                offscreenCanvas = Canvas(newBitmap)
                offscreenDirty = true
            } catch (_: OutOfMemoryError) {
                // 大屏设备可能 OOM，保留旧缓冲
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 需要重建离屏缓冲时（undo/clear/ERASER 后），重新渲染所有笔画
        if (offscreenDirty) {
            rebuildOffscreen()
        }

        // 绘制已完成的笔画（来自离屏缓冲）
        offscreenBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // 绘制当前进行中的笔画预览
        drawCurrentStroke(canvas)
    }

    // ===== 笔画渲染 =====

    /** 在任意 Canvas 上绘制一个 Stroke（支持所有工具类型） */
    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        val isPixelEraser = stroke.tool == DrawTool.PIXEL_ERASER

        if (isPixelEraser) {
            // 像素橡皮擦：用 CLEAR 模式绘制路径（从缓冲中擦除像素）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                paint.blendMode = BlendMode.CLEAR
            } else {
                @Suppress("DEPRECATION")
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            paint.color = Color.TRANSPARENT
            paint.strokeWidth = stroke.width * 2
            paint.pathEffect = null
        } else {
            paint.xfermode = null
            paint.color = stroke.color
            paint.strokeWidth = if (stroke.tool == DrawTool.ERASER) stroke.width * 2 else stroke.width
            paint.pathEffect = if (stroke.tool == DrawTool.DASHED_LINE) {
                DashPathEffect(floatArrayOf(12f * resources.displayMetrics.density, 8f * resources.displayMetrics.density), 0f)
            } else {
                null
            }
        }

        when (stroke.tool) {
            DrawTool.PEN, DrawTool.ERASER, DrawTool.PIXEL_ERASER -> drawPath(canvas, stroke.points)
            DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE, DrawTool.DASHED_LINE, DrawTool.WAVE_LINE -> drawShape(canvas, stroke.points, stroke.tool)
        }

        paint.xfermode = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = null
        }
        paint.pathEffect = null
    }

    private fun drawWaveLine(canvas: Canvas, start: DrawingPoint, end: DrawingPoint) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance < 1f) return
        val density = resources.displayMetrics.density
        val wavelength = 12.5f * density
        val amplitude = 3.75f * density
        val cycles = (distance / wavelength).toFloat().coerceAtLeast(1f)
        val steps = (cycles * 8).toInt().coerceAtLeast(8)
        val path = Path()
        path.moveTo(start.x, start.y)
        for (i in 1..steps) {
            val frac = i.toFloat() / steps.toFloat()
            val x = start.x + dx * frac
            val y = start.y + dy * frac
            val waveY = amplitude * kotlin.math.sin(frac * 2 * kotlin.math.PI.toFloat() * cycles)
            val perpX = -dy / distance
            val perpY = dx / distance
            path.lineTo(x + perpX * waveY, y + perpY * waveY)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPath(canvas: Canvas, points: List<DrawingPoint>) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawShape(canvas: Canvas, points: List<DrawingPoint>, tool: DrawTool) {
        if (points.size < 2) return
        val start = points[0]
        val end = points.last()

        when (tool) {
            DrawTool.LINE, DrawTool.DASHED_LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            DrawTool.WAVE_LINE -> drawWaveLine(canvas, start, end)
            DrawTool.RECT -> {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val right = maxOf(start.x, end.x)
                val bottom = maxOf(start.y, end.y)
                canvas.drawRect(left, top, right, bottom, paint)
            }
            DrawTool.CIRCLE -> {
                val cx = (start.x + end.x) / 2
                val cy = (start.y + end.y) / 2
                val rx = kotlin.math.abs(end.x - start.x) / 2
                val ry = kotlin.math.abs(end.y - start.y) / 2
                if (rx > 0 && ry > 0) {
                    canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
                }
            }
            else -> {}
        }
    }

    // ===== 当前笔画预览 =====

    /** 像素橡皮擦光标画笔（独立 Paint，不污染主 paint） */
    private val eraserCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(100, 200, 200, 200)
        strokeWidth = 2f
    }

    /** 像素橡皮擦轨迹画笔（半透明，显示擦除痕迹） */
    private val eraserTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 180, 180, 180)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawCurrentStroke(canvas: Canvas) {
        val tool = engine.currentTool
        paint.color = engine.currentColor
        paint.strokeWidth = engine.currentStrokeWidth
        paint.pathEffect = if (tool == DrawTool.DASHED_LINE) {
            DashPathEffect(floatArrayOf(12f * resources.displayMetrics.density, 8f * resources.displayMetrics.density), 0f)
        } else {
            null
        }

        when (tool) {
            DrawTool.PEN -> {
                if (currentPoints.size >= 2) {
                    drawPath(canvas, currentPoints)
                }
            }
            DrawTool.ERASER -> {
                paint.color = android.graphics.Color.WHITE
                paint.strokeWidth = engine.currentStrokeWidth * 3
                if (currentPoints.size >= 2) {
                    drawPath(canvas, currentPoints)
                }
            }
            DrawTool.PIXEL_ERASER -> {
                // 绘制擦除轨迹（半透明路径，显示擦过的地方和宽度）
                if (currentPoints.size >= 2) {
                    eraserTrailPaint.strokeWidth = engine.currentStrokeWidth * 2
                    val path = Path()
                    path.moveTo(currentPoints[0].x, currentPoints[0].y)
                    for (i in 1 until currentPoints.size) {
                        path.lineTo(currentPoints[i].x, currentPoints[i].y)
                    }
                    canvas.drawPath(path, eraserTrailPaint)
                }
                // 绘制橡皮擦光标：半透明圆圈表示擦除位置和大小
                if (currentPoints.isNotEmpty()) {
                    val lastPt = currentPoints.last()
                    eraserCursorPaint.strokeWidth = 2f * resources.displayMetrics.density
                    val cursorRadius = engine.currentStrokeWidth * 1.5f
                    canvas.drawCircle(lastPt.x, lastPt.y, cursorRadius, eraserCursorPaint)
                }
            }
            DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE, DrawTool.DASHED_LINE, DrawTool.WAVE_LINE -> {
                val start = currentStartPoint ?: return
                val end = previewEndPoint ?: return
                drawShape(canvas, listOf(start, end), tool)
            }
        }

        paint.pathEffect = null
    }

    // ===== 离屏缓冲操作 =====

    /** 将已完成的笔画渲染到离屏缓冲 */
    private fun renderStrokeToOffscreen(stroke: Stroke) {
        if (stroke.tool == DrawTool.PIXEL_ERASER) {
            // 像素橡皮擦已完成实时擦除，无需重复渲染
            return
        }
        val canvas = offscreenCanvas ?: return
        drawStroke(canvas, stroke)
    }

    /** 在离屏缓冲上擦除像素（两点一线） */
    private fun eraseLineOnOffscreen(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val canvas = offscreenCanvas ?: return
        pixelEraserPaint.strokeWidth = engine.currentStrokeWidth * 2
        canvas.drawLine(fromX, fromY, toX, toY, pixelEraserPaint)
    }

    /** 在离屏缓冲上擦除单个点 */
    private fun erasePointOnOffscreen(x: Float, y: Float) {
        val canvas = offscreenCanvas ?: return
        pixelEraserPaint.strokeWidth = engine.currentStrokeWidth * 2
        canvas.drawPoint(x, y, pixelEraserPaint)
    }

    /** 重建离屏缓冲：清空并从引擎重新渲染所有笔画 */
    private fun rebuildOffscreen() {
        offscreenBitmap?.eraseColor(Color.TRANSPARENT)
        offscreenCanvas?.let { canvas ->
            for (stroke in engine.strokes) {
                drawStroke(canvas, stroke)
            }
        }
        offscreenDirty = false
    }

    /** 手动清除离屏缓冲（配合 engine.clear() 使用） */
    fun clearOffscreen() {
        offscreenBitmap?.eraseColor(Color.TRANSPARENT)
        offscreenDirty = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        offscreenBitmap?.recycle()
        offscreenBitmap = null
        offscreenCanvas = null
    }

    // ===== 触摸事件 =====

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawMode) return false
        val x = event.x
        val y = event.y

        try {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentStartPoint = DrawingPoint(x, y)
                    previewEndPoint = DrawingPoint(x, y)

                    when (engine.currentTool) {
                        DrawTool.PEN, DrawTool.ERASER -> {
                            currentPoints = mutableListOf(DrawingPoint(x, y))
                        }
                        DrawTool.PIXEL_ERASER -> {
                            // 起始位置先擦除一个点
                            currentPoints = mutableListOf(DrawingPoint(x, y))
                            erasePointOnOffscreen(x, y)
                            lastEraserX = x
                            lastEraserY = y
                        }
                        else -> {
                            currentPoints = mutableListOf(DrawingPoint(x, y))
                        }
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    when (engine.currentTool) {
                        DrawTool.PEN, DrawTool.ERASER -> {
                            currentPoints.add(DrawingPoint(x, y))
                        }
                        DrawTool.PIXEL_ERASER -> {
                            // 从上一点到当前点擦除一条线段，形成连续擦除轨迹
                            currentPoints.add(DrawingPoint(x, y))
                            eraseLineOnOffscreen(lastEraserX, lastEraserY, x, y)
                            lastEraserX = x
                            lastEraserY = y
                        }
                        else -> {
                            previewEndPoint = DrawingPoint(x, y)
                        }
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    when (engine.currentTool) {
                        DrawTool.ERASER -> {
                            currentPoints.add(DrawingPoint(x, y))
                            val eraserRadius = 40 * resources.displayMetrics.density
                            for (pt in currentPoints) {
                                engine.eraseAt(pt.x, pt.y, eraserRadius)
                            }
                            // ERASER 删除了笔画，标记离屏缓冲需要重建
                            offscreenDirty = true
                        }
                        DrawTool.PIXEL_ERASER -> {
                            // 确保最后一段也被擦除
                            if (x != lastEraserX || y != lastEraserY) {
                                eraseLineOnOffscreen(lastEraserX, lastEraserY, x, y)
                            }
                            // 创建像素橡皮擦笔画用于撤销
                            currentPoints.add(DrawingPoint(x, y))
                            val stroke = engine.createStroke(currentPoints)
                            engine.addStroke(stroke)
                            // 已实时擦除，不重复渲染
                        }
                        DrawTool.PEN -> {
                            currentPoints.add(DrawingPoint(x, y))
                            finishStroke()
                        }
                        DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE, DrawTool.DASHED_LINE, DrawTool.WAVE_LINE -> {
                            previewEndPoint = DrawingPoint(x, y)
                            finishShapeStroke()
                        }
                    }
                    currentPoints = mutableListOf()
                    currentStartPoint = null
                    previewEndPoint = null
                    invalidate()
                    return true
                }
            }
        } catch (_: Exception) {
            // 触摸事件处理异常时兜底，防止 ANR
            currentPoints = mutableListOf()
            currentStartPoint = null
            previewEndPoint = null
            invalidate()
        }
        return false
    }

    private fun finishStroke() {
        if (currentPoints.size < 2) return
        val stroke = engine.createStroke(currentPoints)
        engine.addStroke(stroke)
        renderStrokeToOffscreen(stroke)
    }

    private fun finishShapeStroke() {
        val start = currentStartPoint ?: return
        val end = previewEndPoint ?: return
        val points = mutableListOf(start, end)
        val stroke = engine.createStroke(points)
        engine.addStroke(stroke)
        renderStrokeToOffscreen(stroke)
    }

    /** 将画布内容渲染到 Bitmap（白底） */
    fun renderToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        draw(canvas)
        return bitmap
    }
}
