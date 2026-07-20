# PRD: DrawAnywhere — 悬浮学习画板

## Problem Statement

学生在手机上学习解题时（如看题、看解题视频、读 PDF），缺少一个能够**悬浮在其他应用上方**、**直接在屏幕上写写画画**的工具。现有的笔记应用要么是独立白板（不能覆盖在题目上），要么是截图标注（截图后再画，过程割裂），无法做到"边看边画"的自然学习体验。

## Solution

一个 Android 悬浮画板应用，通过系统悬浮窗能力创建一个透明的绘图覆盖层，用户可以：

- 在任何应用之上自由手写批注
- 一键切换"绘图模式"和"穿透模式"（穿透时触摸透到下层应用）
- 使用基础绘图工具（笔刷、颜色、形状、擦除）完成解题标注
- 通过悬浮按钮或通知栏快速开启/关闭

## User Stories

1. 作为学生用户，我希望在任何 App 上都能呼出一个透明画板，以便在屏幕上直接写写画画解题
2. 作为学生用户，我希望有一个常驻悬浮按钮，以便随时快速打开或关闭画板
3. 作为学生用户，我希望下拉通知栏也能控制画板的开关和模式切换，以防悬浮按钮被遮挡
4. 作为学生用户，我希望画板支持"绘图模式"和"穿透模式"一键切换，以便画完后能继续操作底层 App
5. 作为学生用户，我希望用**手指**直接在屏幕上画线写字，不需要额外设备
6. 作为学生用户，我希望有 2~3 种笔刷粗细可选，以适应不同大小的字和标注
7. 作为学生用户，我希望有 5 种预设颜色（红、黑、蓝、绿、白），以便区分不同的标注
8. 作为学生用户，我希望有橡皮擦工具可以局部擦除错误笔画，而不是只能全部清除
9. 作为学生用户，我希望有一键清屏功能，以便快速清空画布开始新一题
10. 作为学生用户，我希望支持撤销操作，以便画错时可以回退
11. 作为学生用户，我希望有形状工具（直线、矩形、圆形），以便画出更规整的标注
12. 作为学生用户，我希望可以保存当前绘图到相册/本地，以便日后复习
13. 作为学生用户，我希望关闭画板时不自动保存，以免留下大量无用的临时涂鸦
14. 作为学生用户，我希望画板开启时不影响 App 本身的显示和声音等正常功能
15. 作为学生用户，我希望画板的性能流畅、不卡顿，不会明显增加耗电

## Implementation Decisions

### 模块划分

| 模块 | 职责 |
|---|---|
| **OverlayService** | 前台服务，管理悬浮窗生命周期、权限请求、与 Notification 联动 |
| **DrawingEngine** | 核心状态管理：笔画列表、撤销/重做栈、当前工具/颜色/粗细、穿透状态 |
| **DrawingCanvasView** | 自定义 `View`，接收触摸事件，渲染所有笔画和形状到 Canvas |
| **FloatingButtonView** | 常驻悬浮小按钮，点击触发 OverlayService 显隐画板 |
| **ToolPaletteView** | 画板上的工具栏组件（颜色、粗细、工具选择、保存、清屏） |
| **PersistenceManager** | 将绘图保存为 PNG 到本地存储，提供加载能力 |

### 关键技术决策

- **悬浮窗实现**：使用 `TYPE_APPLICATION_OVERLAY`（Android 8.0+ 标准方式），通过 `WindowManager.addView()` 添加自定义 View
- **前台服务**：运行 `Foreground Service` 以保证进程存活，需要 `FOREGROUND_SERVICE_SPECIAL_USE` 权限
- **穿透模式**：通过 `WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE` 控制是否拦截触摸事件
- **笔刷渲染**：使用 Android `Path` + `Paint`，利用 `PathEffect` 模拟不同笔触
- **形状工具**：在 `ACTION_UP` 时根据起点和终点计算对应的形状几何（矩形、椭圆、直线）
- **撤销/重做**：使用 `Stack<List<DrawAction>>` 快照方式，每次 memento 记录完整笔画列表
- **存储方案**：将 Canvas 内容绘制到 `Bitmap`，通过 `MediaStore` 保存到相册或内部存储
- **触摸事件分发**：DrawingCanvasView 根据穿透状态决定是否消费事件（穿透模式直接返回 false）

### 交互流程

```
请求悬浮窗权限 → 启动 OverlayService → 显示悬浮按钮
                                            ↓
用户点击悬浮按钮 → 创建/显示 DrawingCanvasView（覆盖全屏）
用户点击画板上的切换按钮 → 切换绘图/穿透模式
用户点击保存 → PersistenceManager 保存当前画布
用户点击关闭/再次点击悬浮按钮 → 隐藏画板（悬浮按钮依然存在）
```

### 状态模型（来自原型）

```
enum class DrawTool { PEN, ERASER, LINE, RECT, CIRCLE }
enum class OverlayMode { DRAW, PENETRATE }

data class DrawingState(
  val strokes: List<Stroke>,
  val undoStack: List<List<Stroke>>,
  val currentTool: DrawTool,
  val currentColor: Int,
  val strokeWidth: Float,
  val mode: OverlayMode,
  val isPaletteVisible: Boolean
)
```

## Testing Decisions

- **测试唯一外部行为**：DrawingEngine 的 undo/redo 栈操作、工具切换、形状计算是纯状态变更，可独立验证
- **需测试模块**：
  - `DrawingEngine` — 单元测试覆盖：笔画添加/撤销/重做、形状工具坐标计算、切换工具/颜色/粗细、清屏
  - `PersistenceManager` — 单元测试覆盖：Bitmap 转文件、文件加载
- **不测试**：自定义 View 渲染、Service 生命周期、跨进程交互（依赖 Android 框架，用真实设备验证）
- **测试框架**：JUnit 5 + MockK（纯逻辑模块无需 Instrumented 测试）

## Out of Scope

- 白板模式（独立画布，不依赖底层 App）— 后续迭代
- 手写笔压感/倾斜支持 — 后续迭代
- 截图批注（先截图再标注）— 后续迭代
- 图片/PDF 导入批注 — 后续迭代
- 多图层支持 — 后续迭代
- 笔迹美化/平滑算法 — 后续迭代
- 导出为 PDF/矢量格式 — 后续迭代
- 分享功能 — 后续迭代
- 手势缩放/平移画布 — 后续迭代

## Further Notes

- 最低支持 Android 8.0（API 26），覆盖 95%+ 设备
- 需要 `SYSTEM_ALERT_WINDOW` 权限，启动时引导用户手动授权
- 悬浮按钮可自由拖动位置，记录位置偏好
- UI 设计保持极简，工具栏半透明且可收起，避免遮挡太多屏幕内容
