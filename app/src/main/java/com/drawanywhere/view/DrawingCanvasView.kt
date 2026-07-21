package com.drawanywhere.view

import android.content.Context
import android.graphics.*
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
                    if (isAttachedToWindow) invalidate()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // 当前笔画状态
    private var currentPoints = mutableListOf<DrawingPoint>()
    private var currentStartPoint: DrawingPoint? = null // 形状工具的起点
    private var previewEndPoint: DrawingPoint? = null    // 形状工具的预览终点

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制已完成的笔画
        paint.style = Paint.Style.STROKE
        for (stroke in engine.strokes) {
            drawStroke(canvas, stroke)
        }

        // 绘制当前进行中的笔画
        drawCurrentStroke(canvas)
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        paint.color = stroke.color
        paint.strokeWidth = if (stroke.tool == DrawTool.ERASER) stroke.width * 2 else stroke.width
        paint.pathEffect = if (stroke.tool == DrawTool.DASHED_LINE) {
            DashPathEffect(floatArrayOf(12f * resources.displayMetrics.density, 8f * resources.displayMetrics.density), 0f)
        } else {
            null
        }

        when (stroke.tool) {
            DrawTool.PEN, DrawTool.ERASER -> drawPath(canvas, stroke.points)
            DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE, DrawTool.DASHED_LINE, DrawTool.WAVE_LINE -> drawShape(canvas, stroke.points, stroke.tool)
        }

        paint.pathEffect = null
    }

    private fun drawWaveLine(canvas: Canvas, start: DrawingPoint, end: DrawingPoint) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance < 1f) return
        val density = resources.displayMetrics.density
        val wavelength = 50f * density
        val amplitude = 15f * density
        val steps = (distance / wavelength).toInt().coerceAtLeast(1) + 2
        val path = Path()
        path.moveTo(start.x, start.y)
        for (i in 1..steps) {
            val frac = i.toFloat() / steps.toFloat()
            val x = start.x + dx * frac
            val y = start.y + dy * frac
            val waveY = amplitude * kotlin.math.sin(frac * 2 * kotlin.math.PI.toFloat() * (distance / wavelength))
            // 垂直于线条方向的偏移
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
            DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE, DrawTool.DASHED_LINE, DrawTool.WAVE_LINE -> {
                val start = currentStartPoint ?: return
                val end = previewEndPoint ?: return
                drawShape(canvas, listOf(start, end), tool)
            }
        }

        paint.pathEffect = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawMode) return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentStartPoint = DrawingPoint(x, y)
                previewEndPoint = DrawingPoint(x, y)

                // 画笔/橡皮擦：开始连续轨迹
                if (engine.currentTool == DrawTool.PEN || engine.currentTool == DrawTool.ERASER) {
                    currentPoints = mutableListOf(DrawingPoint(x, y))
                } else {
                    // 形状工具：仅记录起点，结束时绘制
                    currentPoints = mutableListOf(DrawingPoint(x, y))
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // 画笔/橡皮擦：添加轨迹点
                if (engine.currentTool == DrawTool.PEN || engine.currentTool == DrawTool.ERASER) {
                    currentPoints.add(DrawingPoint(x, y))
                } else {
                    // 形状工具：更新预览终点
                    previewEndPoint = DrawingPoint(x, y)
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
                    }
                    DrawTool.PEN -> {
                        currentPoints.add(DrawingPoint(x, y))
                        finishStroke()
                    }
                    // 形状工具
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
        return false
    }

    private fun finishStroke() {
        if (currentPoints.size < 2) return
        val stroke = engine.createStroke(currentPoints)
        engine.addStroke(stroke)
    }

    private fun finishShapeStroke() {
        val start = currentStartPoint ?: return
        val end = previewEndPoint ?: return
        val points = mutableListOf(start, end)
        val stroke = engine.createStroke(points)
        engine.addStroke(stroke)
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
