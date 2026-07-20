package com.drawanywhere.drawing

class DrawingEngine {

    private val _strokes = mutableListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes.toList()

    var currentTool: DrawTool = DrawTool.PEN
        private set

    var currentColor: Int = 0xFFFF0000.toInt()
        private set

    var currentStrokeWidth: Float = 7.0f
        private set

    private val undoStack = mutableListOf<List<Stroke>>()
    private val redoStack = mutableListOf<List<Stroke>>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun addStroke(stroke: Stroke) {
        undoStack.add(_strokes.toList())
        _strokes.add(stroke)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(_strokes.toList())
        _strokes.clear()
        _strokes.addAll(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(_strokes.toList())
        _strokes.clear()
        _strokes.addAll(redoStack.removeLast())
    }

    fun setTool(tool: DrawTool) {
        currentTool = tool
    }

    fun setColor(color: Int) {
        currentColor = color
    }

    fun setStrokeWidth(width: Float) {
        currentStrokeWidth = width
    }

    fun clear() {
        if (_strokes.isEmpty()) return
        undoStack.add(_strokes.toList())
        _strokes.clear()
        redoStack.clear()
    }

    fun createStroke(points: List<DrawingPoint>): Stroke {
        return Stroke(
            points = points.toMutableList(),
            color = currentColor,
            width = currentStrokeWidth,
            tool = currentTool
        )
    }

    fun eraseAt(x: Float, y: Float, radius: Float = 30f) {
        val radiusSq = radius * radius
        val indicesToRemove = mutableListOf<Int>()
        for (i in _strokes.indices) {
            val stroke = _strokes[i]
            for (pt in stroke.points) {
                val dx = pt.x - x
                val dy = pt.y - y
                if (dx * dx + dy * dy < radiusSq) {
                    indicesToRemove.add(i)
                    break
                }
            }
        }
        if (indicesToRemove.isNotEmpty()) {
            undoStack.add(_strokes.toList())
            for (i in indicesToRemove.sortedDescending()) {
                _strokes.removeAt(i)
            }
            redoStack.clear()
        }
    }
}
