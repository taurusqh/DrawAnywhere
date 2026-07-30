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

## 变更历史

| 日期 | 变更内容 | 目标 | 原因 |
|------|---------|------|------|
| 2026-07-28 | 初始建立 | 整体 | - |
