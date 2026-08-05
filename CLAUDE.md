# DrawAnywhere Harness

**目标:** DrawAnywhere Android 画板应用的 Agent 驱动开发体系 — 开发、审查、测试全流程自动化。

## 触发器

DrawAnywhere 开发相关任务（开发功能、修复崩溃、审查代码、写测试）时，优先使用 `drawanywhere-orchestrator` 技能。单次小修改可直接调对应 Agent（`feature-engineer` / `ui-service-engineer` / `code-reviewer` / `test-engineer`）。简单问题（"这个功能在哪"、"项目的 API 级别是多少"）直接回答即可。

## Agent 资源

| Agent | 职责 | 技能 |
|-------|------|------|
| `feature-engineer` | DrawingEngine、绘图工具、Canvas 绘制 | 绘图核心开发 |
| `ui-service-engineer` | ToolPalette、OverlayService、Activity | UI 组件与悬浮窗服务 |
| `code-reviewer` | 代码审查（质量/兼容性/安全） | 代码审查 |
| `test-engineer` | JUnit 5 单元测试、覆盖率 | 测试验证 |

## 流程规则

### 需求确认
Phase 1 分析报告输出后，必须交给用户确认方案（包括技术选型、取舍决策），确认后再进入 Phase 2 开发。禁止分析完直接开干。

### 版本号发布
发布前 bump 版本号时：
1. 先 `git pull --rebase`
2. 用 `git log` 或远程标签确认最新 versionName
3. 在此基础上 versionCode +1，versionName 小版本 +1
4. 再改 `app/build.gradle.kts`

### 用 codegraph 理解代码结构，减少探索时间
- `codegraph context "xxx"` 快速生成上下文，不用 grep/Glob/Read 挨个翻
- `codegraph query "symbol_name"` 查找符号定义和引用
- 初始化：`codegraph init --path D:/Android/DrawAnywhere && codegraph index`

### 测试效率（重要：降低 Phase 4 耗时）

**时间分布：** Phase 4（测试编写+运行）耗时 30-40 分钟，瓶颈不是测试执行（~1s），而是 agent 从头理解和试错。

**策略 1：优先纯 Engine 测试（不依赖 Android，秒级运行）**
- `DrawingEngine` 纯 Kotlin 类，无需 Mockito，编译和运行都极快
- 大多数 View 层方法（如 `finishTwoFingerEraser`）的业务逻辑可以抽成 engine 层测试
- 测试编写时序：先写 engine 测试验证逻辑 → 再补少量 view 测试验证集成

**策略 2：用基类 `BaseViewTest` 避免重复 Mock 代码**
- 新 view 测试继承 `app/src/test/java/.../view/BaseViewTest.kt`
- `createMockView(engine)` 一键创建 mock view（已注入 engine、stub invalidate/resources）
- `setField()` / `getField()` / `invokeFinishTwoFingerEraser()` 等反射 helper 直接用
- 不再需要每个测试文件自己写 Mockito 初始化

**策略 4：开发期用 `--tests` 只跑目标测试类**
- `./gradlew :app:testDebugUnitTest --tests "*DrawingCanvasViewTest*"` — 只跑一个测试类（~10s）
- `./gradlew :app:testDebugUnitTest --tests "*DrawingEngineTest*"` — 纯逻辑测试（~5s）
- 最终提交前再全量跑：`./gradlew :app:testDebugUnitTest --rerun-tasks`

## 变更历史

| 日期 | 变更内容 | 目标 | 原因 |
|------|---------|------|------|
| 2026-07-28 | 初始建立 | 整体 | - |
