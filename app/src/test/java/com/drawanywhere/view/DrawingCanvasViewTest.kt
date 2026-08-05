package com.drawanywhere.view

import com.drawanywhere.drawing.DrawingEngine
import com.drawanywhere.drawing.DrawingPoint
import com.drawanywhere.drawing.DrawTool
import com.drawanywhere.drawing.Stroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 两指像素橡皮擦测试。
 *
 * 分层策略（优先用纯 engine 测试，减少 Mockito mock）：
 * 1. Engine 工作流测试 (无 Android 依赖，秒级执行)
 * 2. finishTwoFingerEraser View 方法测试 (通过 BaseViewTest 反射调用)
 */
class DrawingCanvasViewTest : BaseViewTest() {

    private lateinit var engine: DrawingEngine

    @BeforeEach
    fun setup() {
        engine = DrawingEngine()
        engine.setTool(DrawTool.PEN)
    }

    // ========================================================================
    // 1. Engine 层测试（无 Android 依赖，纯逻辑验证）
    // ========================================================================

    @Test
    fun `double finger switches to PIXEL_ERASER creates stroke and restores tool`() {
        val prev = engine.currentTool
        engine.setTool(DrawTool.PIXEL_ERASER)
        engine.addStroke(engine.createStroke(
            listOf(DrawingPoint(100f, 100f), DrawingPoint(200f, 200f))
        ))
        engine.setTool(prev)

        assertEquals(1, engine.strokes.size)
        assertEquals(DrawTool.PIXEL_ERASER, engine.strokes[0].tool)
        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `all tools are correctly restored after switching to PIXEL_ERASER`() {
        for (tool in DrawTool.entries) {
            engine.setTool(tool)
            val prev = engine.currentTool
            engine.setTool(DrawTool.PIXEL_ERASER)
            engine.setTool(prev)
            assertEquals(tool, engine.currentTool, "Failed to restore $tool")
        }
    }

    @Test
    fun `restoring tool does not create any stroke`() {
        engine.setTool(DrawTool.RECT)
        engine.setTool(DrawTool.PIXEL_ERASER)
        engine.setTool(DrawTool.RECT)

        assertEquals(0, engine.strokes.size)
        assertEquals(DrawTool.RECT, engine.currentTool)
    }

    @Test
    fun `catch block logic restores tool`() {
        // 模拟 onTouchEvent catch 块执行的逻辑
        engine.setTool(DrawTool.PIXEL_ERASER)
        val saved: DrawTool? = DrawTool.PEN
        saved?.let { engine.setTool(it) }

        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `catch block with null previous tool leaves ERASER unchanged`() {
        engine.setTool(DrawTool.PIXEL_ERASER)
        val saved: DrawTool? = null
        engine.setTool(saved ?: engine.currentTool)

        assertEquals(DrawTool.PIXEL_ERASER, engine.currentTool)
    }

    // ========================================================================
    // 2. View 层测试（通过反射调 finishTwoFingerEraser）
    // ========================================================================

    @Test
    fun `finishTwoFingerEraser creates stroke and restores tool`() {
        val view = createMockView(engine)
        setField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setField(view, "currentPoints", mutableListOf(
            DrawingPoint(100f, 100f), DrawingPoint(101f, 101f)
        ))
        setField(view, "lastEraserX", 101f)
        setField(view, "lastEraserY", 101f)
        engine.setTool(DrawTool.PIXEL_ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(1, engine.strokes.size)
        assertEquals(DrawTool.PIXEL_ERASER, engine.strokes[0].tool)
        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `finishTwoFingerEraser with insufficient points does not create stroke`() {
        val view = createMockView(engine)
        setField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setField(view, "currentPoints", mutableListOf(DrawingPoint(100f, 100f))) // < 2
        engine.setTool(DrawTool.PIXEL_ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(0, engine.strokes.size)
        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `finishTwoFingerEraser with empty points preserves existing strokes`() {
        val view = createMockView(engine)
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        setField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setField(view, "currentPoints", mutableListOf<DrawingPoint>())
        engine.setTool(DrawTool.PIXEL_ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(1, engine.strokes.size)
        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `finishTwoFingerEraser with null previous stays on PIXEL_ERASER`() {
        val view = createMockView(engine)
        setField(view, "previousToolBeforeMultiTouch", null)
        setField(view, "currentPoints", mutableListOf(
            DrawingPoint(10f, 10f), DrawingPoint(20f, 20f)
        ))
        engine.setTool(DrawTool.PIXEL_ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(DrawTool.PIXEL_ERASER, engine.currentTool)
    }
}
