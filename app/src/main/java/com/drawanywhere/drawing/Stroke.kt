package com.drawanywhere.drawing

data class Stroke(
    val points: MutableList<DrawingPoint> = mutableListOf(),
    val color: Int = 0xFFFF0000.toInt(),
    val width: Float = 4.0f,
    val tool: DrawTool = DrawTool.PEN
)
