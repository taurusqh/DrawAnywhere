package com.drawanywhere.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.drawanywhere.BuildConfig
import com.drawanywhere.R
import com.drawanywhere.service.OverlayService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_BATTERY_OPT = 1002
        private const val GITHUB_API = "https://api.github.com/repos/taurusqh/DrawAnywhere/releases/latest"
    }

    private lateinit var versionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        versionText = findViewById(R.id.versionText)
        versionText.text = "v${BuildConfig.VERSION_NAME}"

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

    // ===== 检查更新 =====

    fun onCheckUpdateClicked(view: android.view.View) {
        val button = view as Button
        button.isEnabled = false
        button.text = getString(R.string.checking_update)

        thread {
            val result = checkLatestVersion()
            runOnUiThread {
                button.isEnabled = true
                button.text = getString(R.string.check_update)
                when {
                    result == null -> {
                        Toast.makeText(this, R.string.check_update_failed, Toast.LENGTH_SHORT).show()
                    }
                    result.second == "no_release" -> {
                        Toast.makeText(this, R.string.already_latest, Toast.LENGTH_SHORT).show()
                    }
                    result.first == BuildConfig.VERSION_NAME -> {
                        Toast.makeText(this, R.string.already_latest, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        showUpdateDialog(result.first, result.second)
                    }
                }
            }
        }
    }

    /** 调用 GitHub API 获取最新 release，返回 (tag, html_url) 或 null */
    private fun checkLatestVersion(): Pair<String, String>? {
        return try {
            val url = URL(GITHUB_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            val code = conn.responseCode
            if (code == 404) {
                // 还没有任何 Release，视为已最新（不返回 null，返回特殊标记）
                return Pair("0", "no_release")
            }
            if (code != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.getString("tag_name").removePrefix("v")
            val htmlUrl = json.getString("html_url")
            Pair(tag, htmlUrl)
        } catch (_: Exception) {
            null
        }
    }

    /** 显示发现新版本对话框 */
    private fun showUpdateDialog(latestTag: String, downloadUrl: String) {
        val currentVer = BuildConfig.VERSION_NAME
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_msg, latestTag, currentVer))
            .setPositiveButton(getString(R.string.btn_download)) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setCancelable(true)
            .show()
    }

    // ===== 悬浮窗权限 =====

    private fun requestOverlayPermission() {
        // 检测联想 ZUI 系统
        if (isZui()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.overlay_permission_zui_title))
                .setMessage(getString(R.string.overlay_permission_zui_msg))
                .setPositiveButton(getString(R.string.btn_settings)) { _, _ ->
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
                Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    private fun isZui(): Boolean {
        if (!Build.MANUFACTURER.equals("Lenovo", ignoreCase = true) &&
            !Build.BRAND.equals("Lenovo", ignoreCase = true)) return false
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
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_CLOSE_ALL
        }
        startService(intent)
        finishAffinity()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
