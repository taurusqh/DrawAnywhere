package com.drawanywhere.drawing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawingEngineTest {

    @Test
    fun `initial state should have empty strokes and default values`() {
        val engine = DrawingEngine()

        assertTrue(engine.strokes.isEmpty())
        assertEquals(DrawTool.PEN, engine.currentTool)
        assertEquals(0xFFFF0000.toInt(), engine.currentColor)
        assertEquals(7.0f, engine.currentStrokeWidth)
        assertFalse(engine.canUndo)
        assertFalse(engine.canRedo)
    }

    @Test
    fun `add stroke should append to strokes list`() {
        val engine = DrawingEngine()
        val stroke = Stroke(mutableListOf(DrawingPoint(10f, 20f)))

        engine.addStroke(stroke)

        assertEquals(1, engine.strokes.size)
    }

    @Test
    fun `strokes getter returns deep copy not original references`() {
        val engine = DrawingEngine()
        val stroke = Stroke(mutableListOf(DrawingPoint(10f, 20f)))

        engine.addStroke(stroke)

        // strokes getter 返回深拷贝，与原始对象不同引用
        assertNotSame(stroke, engine.strokes[0])
    }

    @Test
    fun `undo should revert strokes to previous state`() {
        val engine = DrawingEngine()
        val stroke = Stroke(mutableListOf(DrawingPoint(10f, 20f)))

        engine.addStroke(stroke)
        assertTrue(engine.canUndo)

        engine.undo()

        assertTrue(engine.strokes.isEmpty())
        assertFalse(engine.canUndo)
        assertTrue(engine.canRedo)
    }

    @Test
    fun `redo should reapply undone stroke`() {
        val engine = DrawingEngine()
        val stroke = Stroke(mutableListOf(DrawingPoint(10f, 20f)))

        engine.addStroke(stroke)
        engine.undo()
        assertTrue(engine.canRedo)

        engine.redo()

        assertEquals(1, engine.strokes.size)
        assertFalse(engine.canRedo)
        assertTrue(engine.canUndo)
    }

    @Test
    fun `setTool should switch current tool`() {
        val engine = DrawingEngine()

        engine.setTool(DrawTool.RECT)

        assertEquals(DrawTool.RECT, engine.currentTool)
    }

    @Test
    fun `setColor should switch current color`() {
        val engine = DrawingEngine()

        engine.setColor(0xFFD60A.toInt())

        assertEquals(0xFFD60A.toInt(), engine.currentColor)
    }

    @Test
    fun `setStrokeWidth should switch stroke width`() {
        val engine = DrawingEngine()

        engine.setStrokeWidth(8.0f)

        assertEquals(8.0f, engine.currentStrokeWidth)
    }

    @Test
    fun `clear should remove all strokes and allow undo`() {
        val engine = DrawingEngine()
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(10f, 20f))))
        engine.addStroke(Stroke(mutableListOf(DrawingPoint(30f, 40f))))
        assertEquals(2, engine.strokes.size)

        engine.clear()

        assertTrue(engine.strokes.isEmpty())
        assertTrue(engine.canUndo)
    }

    @Test
    fun `createStroke should use current tool color and width`() {
        val engine = DrawingEngine()
        engine.setTool(DrawTool.RECT)
        engine.setColor(0xFFD60A.toInt())
        engine.setStrokeWidth(12.0f)

        val stroke = engine.createStroke(listOf(DrawingPoint(10f, 10f), DrawingPoint(100f, 100f)))

        assertEquals(DrawTool.RECT, stroke.tool)
        assertEquals(0xFFD60A.toInt(), stroke.color)
        assertEquals(12.0f, stroke.width)
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun `eraseAt should remove strokes at the touch point`() {
        val engine = DrawingEngine()
        val stroke = Stroke(
            points = mutableListOf(DrawingPoint(100f, 100f)),
            color = 0xFFFF0000.toInt(),
            width = 4.0f,
            tool = DrawTool.PEN
        )
        engine.addStroke(stroke)
        assertEquals(1, engine.strokes.size)

        engine.eraseAt(100f, 100f)

        assertEquals(0, engine.strokes.size)
        assertTrue(engine.canUndo)
    }

    @Test
    fun `eraseAt should not remove strokes far from touch point`() {
        val engine = DrawingEngine()
        engine.addStroke(Stroke(
            points = mutableListOf(DrawingPoint(100f, 100f)),
            color = 0xFFFF0000.toInt(),
            width = 4.0f,
            tool = DrawTool.PEN
        ))

        engine.eraseAt(1000f, 1000f)

        assertEquals(1, engine.strokes.size)
    }

    @Test
    fun `undo after multiple strokes should step back one at a time`() {
        val engine = DrawingEngine()
        val stroke1 = Stroke(mutableListOf(DrawingPoint(10f, 10f)))
        val stroke2 = Stroke(mutableListOf(DrawingPoint(20f, 20f)))
        val stroke3 = Stroke(mutableListOf(DrawingPoint(30f, 30f)))

        engine.addStroke(stroke1)
        engine.addStroke(stroke2)
        engine.addStroke(stroke3)
        assertEquals(3, engine.strokes.size)

        engine.undo()
        assertEquals(2, engine.strokes.size)

        engine.undo()
        assertEquals(1, engine.strokes.size)

        engine.undo()
        assertEquals(0, engine.strokes.size)
        assertFalse(engine.canUndo)
    }
}
