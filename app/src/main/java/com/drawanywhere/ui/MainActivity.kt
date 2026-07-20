package com.drawanywhere.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.drawanywhere.R
import com.drawanywhere.service.OverlayService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_BATTERY_OPT = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 如果已有权限则直接启动服务
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            requestBatteryOptimization()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台检查权限
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        // 检测联想 ZUI 系统
        if (isZui()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.overlay_permission_zui_title))
                .setMessage(getString(R.string.overlay_permission_zui_msg))
                .setPositiveButton(getString(R.string.btn_settings)) { _, _ ->
                    // ZUI 没有标准 intent，引导用户去系统设置
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                }
                .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                    Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_LONG).show()
                }
                .setCancelable(true)
                .show()
        } else {
            // 标准 Android 流程
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.overlay_permission_title))
                .setMessage(getString(R.string.overlay_permission_msg))
                .setPositiveButton(getString(R.string.btn_grant)) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                }
                .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                    Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_LONG).show()
                }
                .setCancelable(true)
                .show()
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        // 仅在 ZUI 上提示省电设置
        if (!isZui()) return
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_opt_title))
            .setMessage(getString(R.string.battery_opt_msg))
            .setPositiveButton("前往设置") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_BATTERY_OPT)
            }
            .setNegativeButton("稍后", null)
            .setCancelable(true)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
                requestBatteryOptimization()
                Toast.makeText(this, "权限已授予，悬浮按钮已显示", Toast.LENGTH_SHORT).show()
            } else if (!isZui()) {
                // ZUI 下无法通过标准方式检测悬浮窗权限，不提示错误
                Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    /** 检测是否为联想 ZUI 系统 */
    private fun isZui(): Boolean {
        if (!Build.MANUFACTURER.equals("Lenovo", ignoreCase = true) &&
            !Build.BRAND.equals("Lenovo", ignoreCase = true)) return false
        // 检查 ZUI 特有属性
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val rom = method.invoke(null, "ro.lenovo.rom") as? String
            val zuiVer = method.invoke(null, "ro.zui.version") as? String
            rom.equals("zui", ignoreCase = true) || !zuiVer.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun onExitClicked(view: android.view.View) {
        // 停止画板服务
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_CLOSE_ALL
        }
        startService(intent)
        // 完全退出
        finishAffinity()
    }

    override fun onBackPressed() {
        // 退到后台，不关闭 App
        moveTaskToBack(true)
    }
}
