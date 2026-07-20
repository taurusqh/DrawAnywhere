package com.drawanywhere.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.drawanywhere.R
import com.drawanywhere.drawing.DrawingEngine
import com.drawanywhere.view.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var engine: DrawingEngine

    // 悬浮按钮
    private var floatingButton: FloatingButtonView? = null
    private var floatingParams: WindowManager.LayoutParams? = null

    // 画板覆盖层（canvas + toolbar）
    private var overlayContainer: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var toolPaletteParams: WindowManager.LayoutParams? = null
    private var drawingCanvas: DrawingCanvasView? = null
    private var toolPalette: ToolPaletteView? = null

    private var isDrawMode: Boolean = true
    private var isOverlayVisible: Boolean = false
    private var isExiting: Boolean = false

    override fun onCreate() {
        super.onCreate()
        engine = DrawingEngine()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 主动退出后阻止系统自动重启
        if (isExiting) return START_NOT_STICKY

        when (intent?.action) {
            ACTION_SHOW_FLOATING -> showFloatingButton()
            ACTION_HIDE -> hideOverlay()
            ACTION_TOGGLE_MODE -> toggleMode()
            ACTION_CLOSE_ALL -> exitApp()
        }

        // 默认行为：显示悬浮按钮
        if (intent == null || intent.action == null) {
            showFloatingButton()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        hideFloatingButton()
        super.onDestroy()
    }

    /** 完全退出应用：清除通知 + 移除窗口 + 停止服务 + 杀掉进程 */
    private fun exitApp() {
        // 标记已退出，防止 START_STICKY 重启
        isExiting = true
        try {
            stopForeground(true)
        } catch (_: Exception) {}
        hideOverlay()
        hideFloatingButton()
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // ===== 悬浮按钮 =====

    private fun showFloatingButton() {
        if (floatingButton != null) return

        val btn = FloatingButtonView(this)
        btn.callback = object : FloatingButtonCallback {
            override fun onFloatingButtonClicked() {
                if (isOverlayVisible) {
                    // 画板可见时，切换模式
                    toggleMode()
                } else {
                    // 画板隐藏时，显示画板
                    showOverlay()
                }
            }
        }
        btn.onDragListener = { dx, dy ->
            val fp = floatingParams
            if (fp != null) {
                fp.x = (fp.x + dx).toInt()
                fp.y = (fp.y + dy).toInt()
                windowManager.updateViewLayout(btn, fp)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = getStatusBarHeight() + 100
        }

        windowManager.addView(btn, params)
        floatingButton = btn
        floatingParams = params
    }

    private fun hideFloatingButton() {
        floatingButton?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingButton = null
        floatingParams = null
    }

    // ===== 画板覆盖层（画布 + 工具栏分成两个独立窗口） =====

    private fun showOverlay() {
        if (overlayContainer != null) {
            overlayContainer?.visibility = View.VISIBLE
            toolPalette?.visibility = View.VISIBLE
            isOverlayVisible = true
            isDrawMode = true
            updateModeIndicator()
            return
        }

        // ===== 窗口1: 绘图画布（响应穿透模式） =====
        val canvas = DrawingCanvasView(this, engine).apply {
            isDrawMode = true
        }
        drawingCanvas = canvas

        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_ATTACHED_IN_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(canvas, canvasParams)
        overlayContainer = canvas
        overlayParams = canvasParams

        // ===== 窗口2: 工具栏（永远可点击） =====
        val palette = ToolPaletteView(this, engine).apply {
            callback = object : ToolPaletteCallback {
                override fun onCloseClicked() { hideOverlay() }
                override fun onSaveClicked() { saveScreenshot() }
                override fun onModeToggleClicked() { toggleMode() }
                override fun onUndoClicked() { engine.undo(); drawingCanvas?.safeInvalidate() }
                override fun onClearClicked() { engine.clear(); drawingCanvas?.safeInvalidate() }
                override fun onExitClicked() { exitApp() }
            }
        }
        toolPalette = palette

        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }
        windowManager.addView(palette, toolbarParams)
        toolPaletteParams = toolbarParams

        isOverlayVisible = true
        isDrawMode = true
        updateModeIndicator()
        updateNotification()
    }

    private fun hideOverlay() {
        overlayContainer?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        overlayContainer = null
        overlayParams = null
        drawingCanvas = null

        toolPalette?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        toolPalette = null
        toolPaletteParams = null

        isOverlayVisible = false
        updateNotification()
    }

    // ===== 模式切换 =====

    private fun toggleMode() {
        if (!isOverlayVisible) {
            showOverlay()
            return
        }

        isDrawMode = !isDrawMode

        // 更新画布模式
        drawingCanvas?.isDrawMode = isDrawMode

        // 更新工具栏按钮文字
        toolPalette?.updateModeButton(isDrawMode)

        // 更新悬浮按钮指示点
        floatingButton?.setModeIndicator(isDrawMode)

        // 仅修改画布窗口的触摸拦截（工具栏窗口始终保持可点击）
        overlayContainer?.let { container ->
            overlayParams?.let { params ->
                if (isDrawMode) {
                    params.flags = params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    params.flags = params.flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                try {
                    windowManager.updateViewLayout(container, params)
                } catch (_: Exception) {}
            }
        }

        updateNotification()
    }

    /** 屏幕居中提示浮层（替代 Toast，确保 ZUI 可见） */
    private fun showCenterToast(message: String, isError: Boolean = false) {
        try {
            val density = resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val textView = TextView(this).apply {
                setText(message)
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(padding * 2, padding, padding * 2, padding)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = (12 * density).toFloat()
                    setColor(if (isError) 0xCCE53935.toInt() else 0xCC2E7D32.toInt())
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            windowManager.addView(textView, params)
            // 2 秒后移除
            textView.postDelayed({
                try { windowManager.removeView(textView) } catch (_: Exception) {}
            }, 2000L)
        } catch (_: Exception) {
            // 兜底：普通 Toast
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ===== 保存截图 =====

    private fun saveScreenshot() {
        val bitmap = drawingCanvas?.renderToBitmap() ?: return
        val filename = "DrawAnywhere_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
        try {
            // 主方案：MediaStore（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (saveViaMediaStore(bitmap, filename)) return
            }
            // 备用方案 A：无 IS_PENDING 重试
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (saveViaMediaStoreSimple(bitmap, filename)) return
            }
            // 备用方案 B：传统 File API
            saveViaFile(bitmap, filename)
        } catch (e: Exception) {
            // 最终备用：缓存目录
            try {
                val fallbackDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: cacheDir
                val file = java.io.File(fallbackDir, filename)
                java.io.FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                showCenterToast("已保存: 应用目录")
            } catch (e2: Exception) {
                showCenterToast("保存失败: ${e.message}", isError = true)
            }
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap, filename: String): Boolean {
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false

            contentResolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            showCenterToast("已保存: 相册 → 截图")
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /** 无 IS_PENDING 方式（兼容部分国产 ROM） */
    private fun saveViaMediaStoreSimple(bitmap: Bitmap, filename: String): Boolean {
        return try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DrawAnywhere")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            contentResolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
            showCenterToast("已保存: 相册 → DrawAnywhere")
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 传统 File API 备用（Android 9 以下） */
    private fun saveViaFile(bitmap: Bitmap, filename: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val subDir = java.io.File(dir, "DrawAnywhere")
        subDir.mkdirs()
        val file = java.io.File(subDir, filename)
        java.io.FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        // 通知图库刷新
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = Uri.fromFile(file)
        sendBroadcast(intent)
        showCenterToast("已保存: 相册 → DrawAnywhere")
    }

    // ===== 通知 =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_overlay_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val toggleIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_MODE
        }
        val togglePending = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_CLOSE_ALL
        }
        val exitPending = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val modeLabel = if (isDrawMode) "✏️ 绘图" else "✋ 穿透"

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("当前模式: $modeLabel · 点此切换")
            .setContentIntent(togglePending)
            .setSubText(getString(R.string.notification_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_edit,
                getString(R.string.action_toggle_mode),
                togglePending
            ).build())
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_exit),
                exitPending
            ).build())
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification())
    }

    // ===== 工具 =====

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun updateModeIndicator() {
        floatingButton?.setModeIndicator(isDrawMode)
        toolPalette?.updateModeButton(isDrawMode)
    }

    companion object {
        const val CHANNEL_ID = "draw_anywhere_overlay"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW_FLOATING = "com.drawanywhere.action.SHOW_FLOATING"
        const val ACTION_HIDE = "com.drawanywhere.action.HIDE"
        const val ACTION_TOGGLE_MODE = "com.drawanywhere.action.TOGGLE_MODE"
        const val ACTION_CLOSE_ALL = "com.drawanywhere.action.CLOSE_ALL"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_FLOATING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
