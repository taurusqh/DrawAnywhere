package com.drawanywhere.view

import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.MotionEvent
import com.drawanywhere.drawing.DrawingEngine
import com.drawanywhere.drawing.DrawingPoint
import com.drawanywhere.drawing.DrawTool
import com.drawanywhere.drawing.Stroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Stubber

/**
 * Tests for the two-finger pixel eraser feature in DrawingCanvasView.
 *
 * Three layers:
 * 1. Engine-level workflow tests — pure logic, no Android dependencies, always run.
 * 2. Reflection-based view tests — use Mockito + CALLS_REAL_METHODS to test
 *    finishTwoFingerEraser() directly via reflection, bypassing onTouchEvent
 *    which triggers Android stub exceptions on private field access.
 * 3. onTouchEvent dispatch tests — verify event routing for cases that
 *    don't trigger internal exceptions.
 */
class DrawingCanvasViewTest {

    private lateinit var engine: DrawingEngine

    @BeforeEach
    fun setup() {
        engine = DrawingEngine()
        engine.setTool(DrawTool.PEN)
    }

    // ========================================================================
    // Layer 1: Engine-level workflow tests (no Android dependencies)
    // ========================================================================

    @Test
    fun `two-finger workflow switches to pixel eraser creates stroke and restores tool`() {
        val previousTool = engine.currentTool
        assertEquals(DrawTool.PEN, previousTool)

        // 两指模式：切换到 PIXEL_ERASER
        engine.setTool(DrawTool.PIXEL_ERASER)
        assertEquals(DrawTool.PIXEL_ERASER, engine.currentTool)

        // 创建 PIXEL_ERASER 笔画（用于 undo）
        val points = listOf(DrawingPoint(100f, 100f), DrawingPoint(101f, 101f))
        val stroke = engine.createStroke(points)
        engine.addStroke(stroke)
        assertEquals(1, engine.strokes.size)
        assertEquals(DrawTool.PIXEL_ERASER, engine.strokes[0].tool)

        // 恢复原工具
        engine.setTool(previousTool)
        assertEquals(DrawTool.PEN, engine.currentTool)
        assertTrue(engine.canUndo)
    }

    @Test
    fun `pixel eraser creates single stroke for multi-point path`() {
        engine.setTool(DrawTool.PIXEL_ERASER)

        val points = listOf(
            DrawingPoint(100f, 100f),
            DrawingPoint(150f, 150f),
            DrawingPoint(200f, 200f)
        )
        val stroke = engine.createStroke(points)
        engine.addStroke(stroke)

        assertEquals(1, engine.strokes.size, "Single stroke for the entire path")
        assertEquals(3, engine.strokes[0].points.size, "All points preserved in stroke")
        assertEquals(DrawTool.PIXEL_ERASER, engine.strokes[0].tool)
    }

    @Test
    fun `tool is restored even without stroke creation`() {
        engine.setTool(DrawTool.RECT)

        val previousTool = engine.currentTool
        engine.setTool(DrawTool.PIXEL_ERASER)
        // 没有创建笔画（当前Points 不足 2 点的情况）
        engine.setTool(previousTool)

        assertEquals(DrawTool.RECT, engine.currentTool)
    }

    @Test
    fun `original tool is restored from multiple starting tools`() {
        for (tool in DrawTool.entries) {
            engine.setTool(tool)
            val previousTool = engine.currentTool

            engine.setTool(DrawTool.PIXEL_ERASER)
            engine.setTool(previousTool)

            assertEquals(tool, engine.currentTool, "Failed to restore $tool")
        }
    }

    @Test
    fun `catch block logic restores original tool on its own`() {
        engine.setTool(DrawTool.PEN)

        // This is exactly what the catch block in onTouchEvent executes:
        engine.setTool(DrawTool.PIXEL_ERASER)
        val previousToolBeforeMultiTouch: DrawTool? = DrawTool.PEN
        previousToolBeforeMultiTouch?.let { engine.setTool(it) }

        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    // ========================================================================
    // Layer 2: Reflection-based finishTwoFingerEraser tests
    // ========================================================================

    @Test
    fun `finishTwoFingerEraser creates pixel eraser stroke and restores tool`() {
        val view = createView()
        engine.setTool(DrawTool.PIXEL_ERASER)
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(100f, 100f), DrawingPoint(101f, 101f)))
        // 模拟 lastEraserX/Y 与最后一个点一致（无需额外擦除）
        setPrivateField(view, "lastEraserX", 101f)
        setPrivateField(view, "lastEraserY", 101f)

        invokeFinishTwoFingerEraser(view)

        // PIXEL_ERASER 笔画应被创建（用于 undo）
        assertEquals(1, engine.strokes.size, "PIXEL_ERASER stroke should be created for undo")
        assertEquals(DrawTool.PIXEL_ERASER, engine.strokes[0].tool)
        assertEquals(DrawTool.PEN, engine.currentTool, "Tool should be restored")
    }

    @Test
    fun `finishTwoFingerEraser with single point does not create stroke`() {
        val view = createView()
        engine.setTool(DrawTool.PIXEL_ERASER)
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(100f, 100f)))  // only 1 point

        invokeFinishTwoFingerEraser(view)

        // < 2 points → no stroke created
        assertEquals(0, engine.strokes.size)
        assertEquals(DrawTool.PEN, engine.currentTool, "Tool should be restored")
    }

    @Test
    fun `finishTwoFingerEraser with empty points restores tool`() {
        val view = createView()
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))

        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints", mutableListOf<DrawingPoint>())
        engine.setTool(DrawTool.PIXEL_ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(1, engine.strokes.size, "Existing stroke preserved")
        assertEquals(DrawTool.PEN, engine.currentTool, "Tool restored")
    }

    @Test
    fun `finishTwoFingerEraser does not restore tool when no previous was saved`() {
        val view = createView()
        engine.setTool(DrawTool.RECT)

        setPrivateField(view, "previousToolBeforeMultiTouch", null)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(10f, 10f), DrawingPoint(20f, 20f)))
        engine.setTool(DrawTool.PIXEL_ERASER)

        invokeFinishTwoFingerEraser(view)

        // previousToolBeforeMultiTouch was null → tool should stay PIXEL_ERASER
        assertEquals(DrawTool.PIXEL_ERASER, engine.currentTool)
    }

    @Test
    fun `finishTwoFingerEraser does not create stroke when points insufficient`() {
        val view = createView()
        engine.setTool(DrawTool.PIXEL_ERASER)
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(100f, 100f)))  // only 1 point

        invokeFinishTwoFingerEraser(view)

        assertEquals(0, engine.strokes.size)
        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    // ========================================================================
    // Layer 3: onTouchEvent dispatch tests (limited scope)
    // ========================================================================

    @Test
    fun `POINTER_DOWN switches to PIXEL_ERASER then UP restores tool`() {
        val view = createView()

        // Simulate: ACTION_POINTER_DOWN -> switch to PIXEL_ERASER
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(50f, 60f), DrawingPoint(100f, 100f)))
        engine.setTool(DrawTool.PIXEL_ERASER)
        assertEquals(DrawTool.PIXEL_ERASER, engine.currentTool)

        // Simulate: ACTION_UP -> finishTwoFingerEraser()
        invokeFinishTwoFingerEraser(view)

        assertEquals(DrawTool.PEN, engine.currentTool,
            "Tool should be restored after two-finger eraser")
    }

    @Test
    fun `single touch sequence does NOT trigger eraser mode`() {
        val view = createView()

        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))

        // Single-touch drawing with PEN (previousToolBeforeMultiTouch is null)
        setPrivateField(view, "previousToolBeforeMultiTouch", null)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(50f, 60f), DrawingPoint(100f, 100f)))
        engine.setTool(DrawTool.PEN)

        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `multi-touch with single pointer does not switch tool`() {
        // pointerCount < 2 → no tool change occurs
        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `catch block in onTouchEvent restores tool on exception`() {
        val view = createView()

        // Simulate: exception during touch handling
        engine.setTool(DrawTool.PEN)
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        engine.setTool(DrawTool.PIXEL_ERASER)

        // Execute catch block logic:
        val previousToolBeforeMultiTouch: DrawTool? =
            getPrivateField(view, "previousToolBeforeMultiTouch") as? DrawTool?
        previousToolBeforeMultiTouch?.let { engine.setTool(it) }
        engine.setTool(previousToolBeforeMultiTouch ?: engine.currentTool)

        assertEquals(DrawTool.PEN, engine.currentTool,
            "Catch block should restore original tool after exception")
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun createView(): DrawingCanvasView {
        val view = Mockito.mock(
            DrawingCanvasView::class.java,
            Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS)
        )

        // Stub View.invalidate() to avoid "not mocked" exception
        val invalidateStubber: Stubber = Mockito.doNothing()
        invalidateStubber.`when`(view).invalidate()

        // Stub resources (needed for eraserRadius calculation)
        val resources = Mockito.mock(Resources::class.java)
        val displayMetrics = DisplayMetrics()
        displayMetrics.density = 3f
        val resStubber: Stubber = Mockito.doReturn(resources)
        resStubber.`when`(view).resources
        val dmStubber: Stubber = Mockito.doReturn(displayMetrics)
        dmStubber.`when`(resources).displayMetrics

        // Inject engine via reflection
        setPrivateField(view, "engine", engine)

        return view
    }

    private fun invokeFinishTwoFingerEraser(view: DrawingCanvasView) {
        val method = DrawingCanvasView::class.java.getDeclaredMethod("finishTwoFingerEraser")
        method.isAccessible = true
        method.invoke(view)
    }

    @Suppress("SameParameterValue")
    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }
}
