# DrawAnywhere 🎨

**悬浮画板 · 随时随地写画解题**

DrawAnywhere 是一款 Android 平板专用的悬浮画板工具。它可以在任何应用上层创建一个透明绘图覆盖层，让你在解题、阅读文档、看视频时随手书写标注，无需切换应用。

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🖊 **绘图工具** | 画笔、橡皮擦、直线、矩形、圆形、虚线 |
| 🎨 **颜色预设** | 红 🔴 / 黄 🟡 / 蓝 🔵 三色快速切换 |
| 📏 **三档粗细** | 细 / 中 / 粗，适配不同书写场景 |
| ↩ **撤销** | 支持多步撤销，误画无忧 |
| 🗑 **清屏** | 一键清除所有笔画 |
| 💾 **保存到相册** | PNG 格式保存到 `Pictures/Screenshots`，自动命名 `DrawAnywhere_yyyyMMdd_HHmmss.png` |
| 🔄 **绘图/穿透模式** | 绘图模式拦截触控，穿透模式让触控穿透到底层应用 |
| 📌 **悬浮按钮** | 可拖拽的紫色悬浮按钮，随时唤出/隐藏画板 |
| 🔋 **前台服务** | 状态栏常驻通知，可在通知栏切换模式或退出 |

## 📱 使用说明

### 快速上手

1. **安装后打开应用**，授予悬浮窗权限
2. 屏幕右上角出现 **紫色悬浮按钮**，点击打开画板
3. 使用底部工具栏选择工具、颜色、粗细
4. 在画板上书写/绘图
5. 点击 💾 保存到相册，点击 ✕ 隐藏画板，点击 ⏻ 退出应用

### 模式切换

- **绘图模式**：触控在画板上绘画，不影响底层应用
- **穿透模式**：触控穿透画板，直接操作底层应用（工具栏仍可点击）

点击工具栏右侧的"穿透/绘图"按钮，或点击状态栏通知进行切换。

### 保存路径

- Android 10+：`相册 → 截图`（Pictures/Screenshots）
- Android 9 以下：`相册 → DrawAnywhere`（Pictures/DrawAnywhere）
- 如果标准路径不可用，自动降级到应用内部目录

## 🏗 技术架构

```
com.drawanywhere/
├── drawing/          # 绘图核心（深度模块）
│   ├── DrawingEngine.kt   # 笔画管理、撤销/重做
│   ├── DrawingPoint.kt    # 自定义坐标点
│   ├── Stroke.kt          # 笔画数据模型
│   └── DrawTool.kt        # 工具枚举
├── view/             # 自定义视图
│   ├── DrawingCanvasView.kt   # 绘图画布 View
│   ├── ToolPaletteView.kt     # 工具栏 View
│   └── FloatingButtonView.kt  # 悬浮按钮 View
├── service/          # 前景服务
│   └── OverlayService.kt      # WindowManager 悬浮窗口管理
└── ui/               # 主界面
    └── MainActivity.kt        # 权限请求 & 服务启动
```

### 关键设计

- **双窗口架构**：画布和工具栏分属两个独立的 `WindowManager` 窗口，穿透模式下工具栏仍可点击
- **前景服务**：使用 `TYPE_APPLICATION_OVERLAY` + 前景服务，确保画板在后台稳定运行
- **三级保存降级**：MediaStore (IS_PENDING) → MediaStore (无 IS_PENDING) → File API → 应用缓存

## 🛠 构建指南

### 环境要求

- Android Studio Ladybug+ / IntelliJ IDEA
- JDK 17+
- Android SDK 36+
- Gradle 9.3.1（自动下载）

### 构建步骤

```bash
# 调试 APK
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 单元测试
./gradlew test
```

## 🔧 兼容性

| 项目 | 状态 |
|------|------|
| Android 8.0+ (API 26) | ✅ 完全支持 |
| 联想 ZUI | ✅ 已适配（悬浮窗手动授权引导 + 省电策略提醒） |
| 华为 EMUI | ✅ 兼容 |
| 三星 One UI | ✅ 兼容 |
| 小米 MIUI | ✅ 兼容 |
| **Android 7.0 以下** | ❌ 不支持（需 `TYPE_APPLICATION_OVERLAY`） |

### 联想 ZUI 注意事项

ZUI 对后台服务和悬浮窗有额外限制。若遇到服务被杀或权限问题：

1. **设置 → 应用管理 → DrawAnywhere → 其他权限 → 开启悬浮窗**
2. **设置 → 应用管理 → DrawAnywhere → 省电策略 → 无限制**
3. **设置 → 自启动管理 → DrawAnywhere → 开启**

## 📄 许可

MIT License

---

*Made with ❤️ for 学习和解题*
