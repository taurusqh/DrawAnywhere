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

    /** 更新信息 */
    private data class UpdateInfo(
        val tag: String,
        val apkUrl: String?
    )

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
                    result.tag == "no_release" -> {
                        Toast.makeText(this, R.string.already_latest, Toast.LENGTH_SHORT).show()
                    }
                    result.tag == BuildConfig.VERSION_NAME -> {
                        Toast.makeText(this, R.string.already_latest, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        showUpdateDialog(result)
                    }
                }
            }
        }
    }

    /** 调用 GitHub API 获取最新 release，返回更新信息或 null */
    private fun checkLatestVersion(): UpdateInfo? {
        return try {
            val url = URL(GITHUB_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            val code = conn.responseCode
            if (code == 404) return UpdateInfo("no_release", null)
            if (code != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.getString("tag_name").removePrefix("v")
            // 从 assets 中找到 APK 文件
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }
            UpdateInfo(tag, apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    /** 显示发现新版本对话框 */
    private fun showUpdateDialog(info: UpdateInfo) {
        val currentVer = BuildConfig.VERSION_NAME
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_msg, info.tag, currentVer))
            .setPositiveButton(getString(R.string.btn_download)) { _, _ ->
                if (info.apkUrl != null) {
                    downloadAndInstall(info.apkUrl)
                } else {
                    // 没有 APK 附件，跳转 Release 页面
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/taurusqh/DrawAnywhere/releases/tag/v${info.tag}"))
                    startActivity(intent)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setCancelable(true)
            .show()
    }

    /** 下载 APK 并调用系统安装器 */
    private fun downloadAndInstall(apkUrl: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.downloading_update))
            .setMessage("正在下载 v${BuildConfig.VERSION_NAME} 更新…")
            .setCancelable(false)
            .show()

        thread {
            try {
                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.connect()

                // 保存到应用专属目录
                val dir = java.io.File(getExternalFilesDir(null), "Update")
                dir.mkdirs()
                val apkFile = java.io.File(dir, "DrawAnywhere-v${BuildConfig.VERSION_NAME}.apk")
                // 删除旧的下载文件
                if (apkFile.exists()) apkFile.delete()

                val input = conn.inputStream
                val output = java.io.FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.close()
                input.close()

                runOnUiThread {
                    dialog.dismiss()
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 调用系统安装器安装 APK */
    private fun installApk(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "下载完成，请安装", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
