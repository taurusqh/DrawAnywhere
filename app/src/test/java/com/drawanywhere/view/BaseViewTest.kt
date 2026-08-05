package com.drawanywhere.view

import android.content.res.Resources
import android.util.DisplayMetrics
import org.mockito.Mockito
import org.mockito.stubbing.Stubber

/**
 * 基类：View 层测试的共享基础设施。
 *
 * 子类无需自己创建 Mock View，直接调用 [createMockView] 即可。
 * 避免每个测试都重复 ByteBuddy 代理创建，减少 Mockito 初始化开销。
 */
abstract class BaseViewTest {

    /**
     * 创建一个带有 engine 注入的 DrawingCanvasView mock。
     *
     * @param engine 测试用的 DrawingEngine 实例（注入到 view 的 engine 字段）
     */
    protected fun createMockView(engine: Any): DrawingCanvasView {
        val view = Mockito.mock(
            DrawingCanvasView::class.java,
            Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS)
        )

        // Stub invalidate() 避免 "not mocked" 异常
        Mockito.doNothing().`when`(view).invalidate()

        // Stub resources (drawCurrentStroke 等渲染方法需要)
        val resources = Mockito.mock(Resources::class.java)
        val displayMetrics = DisplayMetrics().apply { density = 3f }
        Mockito.doReturn(resources).`when`(view).resources
        Mockito.doReturn(displayMetrics).`when`(resources).displayMetrics

        // 通过反射注入 engine
        setField(view, "engine", engine)

        return view
    }

    /**
     * 通过反射调用 DrawingCanvasView.finishTwoFingerEraser()
     */
    protected fun invokeFinishTwoFingerEraser(view: DrawingCanvasView) {
        val method = DrawingCanvasView::class.java.getDeclaredMethod("finishTwoFingerEraser")
        method.isAccessible = true
        method.invoke(view)
    }

    /**
     * 通过反射设置私有字段
     */
    @Suppress("SameParameterValue")
    protected fun setField(target: Any, name: String, value: Any?) {
        val field = target::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    /**
     * 通过反射读取私有字段
     */
    protected fun getField(target: Any, name: String): Any? {
        val field = target::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }
}
