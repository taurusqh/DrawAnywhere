package com.drawanywhere.drawing

class DrawingEngine {

    private val _strokes = mutableListOf<Stroke>()
    val strokes: List<Stroke> get() = deepCopyStrokes(_strokes)

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
        // 深拷贝当前状态入 undo 栈
        undoStack.add(deepCopyStrokes(_strokes))
        _strokes.add(stroke.deepCopy())
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        // 深拷贝当前状态入 redo 栈
        redoStack.add(deepCopyStrokes(_strokes))
        _strokes.clear()
        _strokes.addAll(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(deepCopyStrokes(_strokes))
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
        undoStack.add(deepCopyStrokes(_strokes))
        _strokes.clear()
        redoStack.clear()
    }

    fun createStroke(points: List<DrawingPoint>): Stroke {
        return Stroke(
            points = points.map { it.copy() }.toMutableList(),
            color = currentColor,
            width = currentStrokeWidth,
            tool = currentTool
        )
    }

    fun eraseAt(x: Float, y: Float, radius: Float = 30f) {
        val radiusSq = radius * radius
        val indicesToRemove = mutableListOf<Int>()
        // 先收集要删除的索引，防止并发修改
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
            undoStack.add(deepCopyStrokes(_strokes))
            for (i in indicesToRemove.sortedDescending()) {
                _strokes.removeAt(i)
            }
            redoStack.clear()
        }
    }

    // ===== 深拷贝工具 =====

    /** 深拷贝一个 Stroke（含 points 列表） */
    fun Stroke.deepCopy(): Stroke {
        return Stroke(
            points = this.points.map { it.copy() }.toMutableList(),
            color = this.color,
            width = this.width,
            tool = this.tool
        )
    }

    /** 深拷贝整个 strokes 列表（每个 Stroke 及其 points 都独立） */
    private fun deepCopyStrokes(source: List<Stroke>): List<Stroke> {
        return source.map { it.deepCopy() }
    }
}
