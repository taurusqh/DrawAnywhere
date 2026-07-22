## 分析报告

### 需求 1: 画笔宽度实时预览

#### 问题描述
当前粗细按钮的选中态仅有蓝色背景高亮（`selectStroke()`），用户无法直观看到所选宽度对应的实际线条效果。三个圆点（5dp/10dp/17dp）仅示意相对粗细，与实际线条宽度（3f/7f/12f 像素）无直接换算关系，反馈不准确。

#### 涉及文件
| 文件 | 角色 |
|------|------|
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\view\ToolPaletteView.kt` | 主修改文件 |
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\drawing\DrawingEngine.kt` | 读取 `currentStrokeWidth` |

#### 实现方案
在 `ToolPaletteView.addStrokeButton()` 中，当按钮被点击时，在按钮下方或旁边增加一个**水平线条预览条**（与选中指示器类似），线条的 `strokeWidth` 使用 `engine.currentStrokeWidth` 的实际值绘制。具体有两种子方案：

**方案 A（推荐）**：修改 `selectStroke()`，在选中的按钮容器内添加/更新一个自定义绘制的预览条，使用一条水平线（`canvas.drawLine`）以 `engine.currentStrokeWidth` 的实际像素宽度绘制，颜色使用当前画笔颜色或白色。预览条与选中指示条（蓝色条）共存或合并。

**方案 B**：在选中按钮下方显示一个独立的预览 View，实时绘制出粗细效果。实现更复杂但交互更清晰。

#### 改动范围
- 修改 `ToolPaletteView.kt` 的 `selectStroke()` 方法和 `addStrokeButton()` 方法
- 在选中的 stroke button 容器内新增一个自定义 View 或使用 `ImageView` 显示实时预览线条
- `dotSize` 的 `when` 分支需要与新宽度值同步更新（与需求 3 联动）
- **无需修改 `DrawingEngine` 或 `DrawingCanvasView`**

#### 预估工作量
- 1 个文件修改，约 30-50 行新增代码
- 涉及 Android Canvas 自定义绘制，无外部依赖

#### 风险等级：低
- 纯 UI 改动，不涉及数据流或绘制管线
- 预览 View 不参与笔画持久化，不影响撤销/保存逻辑

---

### 需求 2: 像素橡皮擦痕迹效果

#### 问题描述
当前 `PIXEL_ERASER` 在擦除过程中：
- `drawCurrentStroke()` 只绘制了一个半透明光标圆圈（`eraserCursorPaint`）
- 实际擦除在离屏缓冲上通过 `CLEAR` BlendMode 进行（`eraseLineOnOffscreen`），但擦除过程中**没有可见的擦除轨迹**
- 用户手指移过后，被擦除区域变成完全透明，在白底画布上看起来与未擦除区域无异（都是白色），因此完全看不到擦除了哪里

#### 涉及文件
| 文件 | 角色 |
|------|------|
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\view\DrawingCanvasView.kt` | 主修改文件 |
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\drawing\DrawingEngine.kt` | 无需修改 |

#### 实现方案
在 `drawCurrentStroke()` 的 `DrawTool.PIXEL_ERASER` 分支中，叠加绘制一条**可见的擦除轨迹**：

1. 在 `currentPoints` 不为空时，用 `paint` 绘制一条路径：
   - 使用半透明白色或浅灰色（如 `Color.argb(80, 255, 255, 255)`）
   - `strokeWidth` 使用 `engine.currentStrokeWidth * 3`（与擦除半径一致）
   - `style = Paint.Style.STROKE`，`strokeCap = Round`
2. 这条轨迹**仅在当前笔画（stroke）进行中可见**，笔画结束时（ACTION_UP）不再额外渲染
3. 为保证已完成的擦除笔画在撤销时也有提示，可以修改 `renderStrokeToOffscreen()` 对 `PIXEL_ERASER` 的处理：不再跳过渲染，而是用半透明颜色绘制轨迹路径，而不是用 CLEAR 模式。但这样会双重绘制（离屏缓冲已有 CLEAR 擦除），需要：
   - 在 `renderStrokeToOffscreen` 中保存并切换 BlendMode，用可见颜色（而非 CLEAR）渲染路径

**更简洁的方案**：不在离屏缓冲层面改动，只在 `drawCurrentStroke()` 中绘制当前触摸轨迹的可见反馈；已完成笔画追加到 `engine.strokes` 时，在 `onDraw` 中遍历所有 strokes 中的 PIXEL_ERASER 并用可见颜色绘制轨迹（类似 PEN 的处理方式），但这要求 `drawStroke()` 也支持半透明渲染。

**推荐方案**：
- 在 `drawCurrentStroke()` 中，PIXEL_ERASER 分支**额外绘制一条可见路径**，`paint` 使用 `Color.argb(60, 180, 180, 180)`，`strokeWidth = engine.currentStrokeWidth * 3`
- 在 `drawStroke()` 的 `PIXEL_ERASER` 分支中，改为先画 CLEAR 轨迹（实际擦除），再画半透明可见轨迹（视觉反馈），或仅保留 CLEAR 模式不做可见渲染（依赖离屏重建时重新绘制所有已完成的轨迹）

#### 改动范围
- 修改 `DrawingCanvasView.kt` 的 `drawCurrentStroke()`（约 5-10 行）
- 可能需要修改 `drawStroke()` 以支持已完成 PIXEL_ERASER 轨迹的可见反馈（约 10-15 行）
- **不涉及 `DrawingEngine` 或 `ToolPaletteView`**

#### 预估工作量
- 1 个文件修改，约 20-30 行
- 需要仔细处理 BlendMode 切换，避免与现有 CLEAR 逻辑冲突

#### 风险等级：中
- 涉及 BlendMode/Xfermode 切换，Android 版本差异（API 29+ vs 低版本）需要兼容
- 离屏缓冲重建（`rebuildOffscreen`）时需正确处理 PIXEL_ERASER 的可见轨迹渲染
- 若实现不当可能导致已完成的擦除笔画在 undo/redo 后轨迹消失
- 需要验证轨迹在半透明叠加下的视觉效果

---

### 需求 3: 线条宽度调整

#### 问题描述
当前三档宽度值：3f（细）、7f（中）、12f（粗）。中档（7→12）仅差 5f，区分度不足。用户难以感知中等宽度和较大宽度的区别。这些宽度同样用于 `PIXEL_ERASER`（乘以系数 3 后作为擦除半径）。

#### 涉及文件
| 文件 | 角色 |
|------|------|
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\view\ToolPaletteView.kt` | 宽度按钮注册（line 94-96）、按钮圆点尺寸（line 400-404） |
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\drawing\DrawingEngine.kt` | 默认宽度（line 14） |
| `D:\Android\DrawAnywhere\app\src\main\java\com\drawanywhere\view\DrawingCanvasView.kt` | 各工具下的宽度系数（擦除半径 3x） |

#### 实现方案
**步骤 1：调整宽度常数值**
| 档位 | 当前值 | 建议值 | 理由 |
|------|--------|--------|------|
| 细 | 3f | 3f（不变） | 细线已足够细 |
| 中 | 7f | **12f** | 拉开与细线的差距（3→12，4倍） |
| 大 | 12f | **24f** | 与中档拉开 2 倍差距，视觉效果明显 |

**步骤 2：更新按钮圆点尺寸**
在 `addStrokeButton()` 的 `dotSize` when 分支中同步调整圆点 dp：
| 宽度 | 当前 dotSize | 建议 dotSize |
|------|-------------|-------------|
| 3f | 5dp | 5dp |
| 12f | 10dp | **14dp** |
| 24f | 17dp | **22dp** |

**步骤 3：确认 PIXEL_ERASER 兼容性**
`DrawingCanvasView` 中 PIXEL_ERASER 的擦除半径为 `engine.currentStrokeWidth * 3`：
- 当前 12f → 半径 36f，已覆盖较大区域
- 将 24f → 半径 72f，擦除范围显著扩大
- 建议将擦除系数从 3x 调整为 2x 或保持 1.5x，避免擦除半径过大（72f ≈ 1cm+ 在多数屏幕密度上），影响精细操作
- 如果保持 3x 系数，新宽度 24f 时擦除半径 72f 可能过大，用户体验不佳

**步骤 4：更新默认值**
`DrawingEngine.kt` line 14: `currentStrokeWidth = 7.0f` → 改为新中档值

#### 改动范围
- `ToolPaletteView.kt`：3 处（addStrokeButton 调用参数、dotSize when 分支）
- `DrawingCanvasView.kt`：1 处（擦除半径系数，可选调整）
- `DrawingEngine.kt`：1 处（默认值）

#### 预估工作量
- 3 个文件，每处 1-3 行修改
- 纯数值调整，无逻辑变更

#### 风险等级：低
- 数值调整，不涉及逻辑变更
- 注意点：PIXEL_ERASER 的半径系数（3x）在新宽度下是否过大，建议同步评估调整

---

### 涉及 Agent

#### feature-engineer
负责与绘图核心逻辑相关的变更：
- **需求 2**（主）：修改 `DrawingCanvasView.drawCurrentStroke()` 和 `drawStroke()` 实现像素橡皮擦可见轨迹
- **需求 3**（次）：确认 PIXEL_ERASER 擦除半径系数与新宽度的兼容性，必要时调整 `DrawingCanvasView` 中的 `stroke.width * 3` 系数
- 负责所有涉及 `DrawingCanvasView` 的 Canvas 绘制逻辑修改和 BlendMode 兼容性处理

#### ui-service-engineer
负责 UI 组件的变更：
- **需求 1**（主）：修改 `ToolPaletteView.selectStroke()` 实现画笔宽度实时预览
- **需求 3**（主）：修改 `ToolPaletteView.addStrokeButton()` 的宽度参数和 `dotSize` 值
- 负责所有 `ToolPaletteView` 的 UI 布局和绘制修改
- 涉及 `DrawingEngine` 默认值的 **需求 3** 部分（`DrawingEngine.currentStrokeWidth` 默认值更新）

#### 协作关系
1. **需求 3 的宽度值**先确定，ui-service-engineer 更新 `ToolPaletteView` 的按钮参数和 `DrawingEngine` 默认值后，feature-engineer 再基于新宽度值确认 PIXEL_ERASER 的擦除半径系数
2. **需求 1 的预览实现**依赖需求 3 的最终宽度值，建议需求 3 先行
3. **需求 2 的轨迹宽度**也依赖需求 3 的最终值（轨迹宽度 = strokeWidth × 系数）
