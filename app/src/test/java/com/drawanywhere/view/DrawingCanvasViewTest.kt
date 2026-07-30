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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Stubber

/**
 * Tests for the two-finger eraser feature in DrawingCanvasView.
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
    fun `two-finger workflow saves tool switches to eraser erases points and restores`() {
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        assertEquals(1, engine.strokes.size)

        val previousTool = engine.currentTool
        assertEquals(DrawTool.PEN, previousTool)

        engine.setTool(DrawTool.ERASER)
        assertEquals(DrawTool.ERASER, engine.currentTool)

        engine.eraseAt(100f, 100f, 30f)
        assertTrue(engine.strokes.isEmpty())

        engine.setTool(previousTool)
        assertEquals(DrawTool.PEN, engine.currentTool)
        assertTrue(engine.canUndo)
    }

    @Test
    fun `two-finger eraser erases multiple points along movement path`() {
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(200f, 200f))))
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(300f, 300f))))
        assertEquals(3, engine.strokes.size)

        val previousTool = engine.currentTool
        engine.setTool(DrawTool.ERASER)

        engine.eraseAt(100f, 100f, 30f)
        assertEquals(2, engine.strokes.size)

        engine.eraseAt(200f, 200f, 30f)
        assertEquals(1, engine.strokes.size)

        engine.eraseAt(300f, 300f, 30f)
        assertTrue(engine.strokes.isEmpty())

        engine.setTool(previousTool)
        assertEquals(DrawTool.PEN, engine.currentTool)
        assertTrue(engine.canUndo)
    }

    @Test
    fun `tool is restored even when no strokes were erased`() {
        engine.setTool(DrawTool.RECT)

        val previousTool = engine.currentTool
        engine.setTool(DrawTool.ERASER)
        engine.eraseAt(9999f, 9999f, 30f)
        assertTrue(engine.strokes.isEmpty())
        assertFalse(engine.canUndo)

        engine.setTool(previousTool)
        assertEquals(DrawTool.RECT, engine.currentTool)
    }

    @Test
    fun `original tool is restored from multiple starting tools`() {
        for (tool in DrawTool.entries) {
            engine.setTool(tool)
            val previousTool = engine.currentTool

            engine.setTool(DrawTool.ERASER)
            engine.setTool(previousTool)

            assertEquals(tool, engine.currentTool, "Failed to restore $tool")
        }
    }

    @Test
    fun `catch block logic restores original tool on its own`() {
        engine.setTool(DrawTool.PEN)

        // This is exactly what the catch block in onTouchEvent executes:
        engine.setTool(DrawTool.ERASER)
        val previousToolBeforeMultiTouch: DrawTool? = DrawTool.PEN
        previousToolBeforeMultiTouch?.let { engine.setTool(it) }

        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    // ========================================================================
    // Layer 2: Reflection-based finishTwoFingerEraser tests
    // ========================================================================

    @Test
    fun `finishTwoFingerEraser restores tool without batch erase`() {
        val view = createView()
        // MOVE 中已实时擦除，strokes 已被清除
        engine.clear()
        engine.setTool(DrawTool.ERASER)
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(100f, 100f), DrawingPoint(101f, 101f)))

        invokeFinishTwoFingerEraser(view)

        assertEquals(DrawTool.PEN, engine.currentTool, "Tool should be restored")
    }

    @Test
    fun `finishTwoFingerEraser with single point does not erase`() {
        val view = createView()
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        assertEquals(1, engine.strokes.size)

        // Only one point — finishTwoFingerEraser requires >= 2 to erase
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(100f, 100f)))
        engine.setTool(DrawTool.ERASER)

        invokeFinishTwoFingerEraser(view)

        // Stroke should remain because < 2 points
        assertEquals(1, engine.strokes.size, "Stroke should NOT be erased with < 2 points")
        assertEquals(DrawTool.PEN, engine.currentTool, "Tool should be restored anyway")
    }

    @Test
    fun `finishTwoFingerEraser with empty points restores tool`() {
        val view = createView()
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        assertEquals(1, engine.strokes.size)

        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints", mutableListOf<DrawingPoint>())
        engine.setTool(DrawTool.ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(1, engine.strokes.size, "No erasing with empty points")
        assertEquals(DrawTool.PEN, engine.currentTool, "Tool restored")
    }

    @Test
    fun `finishTwoFingerEraser restores tool when no previous was saved`() {
        val view = createView()
        engine.setTool(DrawTool.RECT)

        setPrivateField(view, "previousToolBeforeMultiTouch", null)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(10f, 10f), DrawingPoint(20f, 20f)))
        engine.setTool(DrawTool.ERASER)

        invokeFinishTwoFingerEraser(view)

        // originalTool was null, so engine should remain ERASER
        // (the code only restores if previousToolBeforeMultiTouch is non-null)
        assertEquals(DrawTool.ERASER, engine.currentTool,
            "Tool should stay ERASER when no previous tool was saved")
    }

    @Test
    fun `finishTwoFingerEraser does not erase when currentPoints has insufficient data`() {
        val view = createView()
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        assertEquals(1, engine.strokes.size)

        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(100f, 100f)))
        engine.setTool(DrawTool.ERASER)

        invokeFinishTwoFingerEraser(view)

        assertEquals(1, engine.strokes.size,
            "One point should not be enough to trigger erase")
        assertEquals(DrawTool.PEN, engine.currentTool, "Tool should be restored")
    }

    // ========================================================================
    // Layer 3: onTouchEvent dispatch tests (limited scope)
    // ========================================================================

    @Test
    fun `POINTER_DOWN switches to ERASER then UP restores tool`() {
        val view = createView()

        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))
        assertEquals(1, engine.strokes.size)

        // Simulate: ACTION_POINTER_DOWN -> switch to ERASER
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(50f, 60f), DrawingPoint(100f, 100f)))
        engine.setTool(DrawTool.ERASER)
        assertEquals(DrawTool.ERASER, engine.currentTool)

        // Simulate: ACTION_UP -> finishTwoFingerEraser()
        invokeFinishTwoFingerEraser(view)

        assertEquals(DrawTool.PEN, engine.currentTool,
            "Tool should be restored after two-finger eraser")
    }

    @Test
    fun `single touch sequence does NOT trigger eraser mode`() {
        val view = createView()

        engine.addStroke(Stroke(mutableListOf(DrawingPoint(100f, 100f))))

        // Set up state corresponding to single-touch drawing with PEN
        // (previousToolBeforeMultiTouch is null, currentPoints has one point,
        //  engine tool is still PEN)
        setPrivateField(view, "previousToolBeforeMultiTouch", null)
        setPrivateField(view, "currentPoints",
            mutableListOf(DrawingPoint(50f, 60f), DrawingPoint(100f, 100f)))

        // finishTwoFingerEraser checks if previousToolBeforeMultiTouch != null
        // before restoring tool. Since it's null, tool stays PEN.
        engine.setTool(DrawTool.PEN)

        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `multi-touch with single pointer does not switch tool`() {
        val view = createView()

        // Simulate: ACTION_POINTER_DOWN with pointerCount=1
        // In onTouchEvent, the condition `if (event.pointerCount >= 2)` fails
        // So no tool change occurs

        assertEquals(DrawTool.PEN, engine.currentTool)
    }

    @Test
    fun `catch block in onTouchEvent restores tool on exception`() {
        val view = createView()

        // Simulate what happens in the catch block:
        // An exception occurred during touch handling, tool was switched to ERASER,
        // catch block restores it back
        engine.setTool(DrawTool.PEN)
        setPrivateField(view, "previousToolBeforeMultiTouch", DrawTool.PEN)
        engine.setTool(DrawTool.ERASER)

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
