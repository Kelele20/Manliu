# 贡献指南

感谢你对 **漫流 (Manliu)** 项目的关注！本文档将帮助你快速上手开发环境，并了解我们的协作规范。请在提交代码前仔细阅读。

---

## 目录

- [环境搭建](#环境搭建)
- [分支与 PR 规范](#分支与-pr-规范)
- [代码风格](#代码风格)
- [提交信息格式](#提交信息格式)
- [测试要求](#测试要求)
- [构建与验证步骤](#构建与验证步骤)
- [Issue 报告模板](#issue-报告模板)

---

## 环境搭建

### 前置要求

| 工具 | 版本要求 |
|---|---|
| **JDK** | 17（推荐使用 [Eclipse Temurin](https://adoptium.net/) 发行版） |
| **Android Studio** | 最新稳定版（Hedgehog 或更高） |
| **Android SDK** | API 35（compileSdk / targetSdk） |
| **Kotlin** | 2.0.21，以根目录 `build.gradle.kts` 中的插件版本为准 |

> [!IMPORTANT]
> 本项目最低支持 **Android 8.0（API 26）**，目标平台为 **Android 15（API 35）**。请确保 SDK Manager 中已安装对应 SDK Platform。

### 克隆与配置

```bash
# 1. 克隆仓库
git clone https://github.com/Kelele20/Manliu.git
cd Manliu

# 2. 用 Android Studio 打开项目，等待 Gradle Sync 完成

# 3. 确认 JDK 版本
java -version
# 预期输出应包含 "17.x.x"
```

### 签名密钥

开发签名密钥 `manliu-dev-key.keystore` **不包含在仓库中**。如需使用项目开发签名，请联系项目维护者获取，并将其放置于项目根目录。

- **Debug 构建**：根目录存在该密钥时使用该密钥；不存在时使用 Android 默认 debug 签名。覆盖安装要求新旧 APK 的签名一致。
- **Release 构建**：当前未配置签名，`assembleRelease` 生成未签名 APK，安装前需另行签名。将开发密钥放入根目录只影响 Debug 构建，不会自动配置 Release 签名。

### 关于网络权限

漫流是一款**纯离线应用**，未声明任何网络权限（`INTERNET` / `ACCESS_NETWORK_STATE`）。请勿引入需要网络访问的功能或依赖库。

---

## 分支与 PR 规范

### 分支命名

从 `main` 分支创建功能分支，命名格式如下：

| 类型 | 格式 | 示例 |
|---|---|---|
| 新功能 | `feature/<简短描述>` | `feature/reader-bookmark` |
| 缺陷修复 | `fix/<简短描述>` | `fix/import-crash` |
| 重构 | `refactor/<简短描述>` | `refactor/database-migration` |
| 文档 | `docs/<简短描述>` | `docs/update-readme` |
| 测试 | `test/<简短描述>` | `test/add-archive-tests` |

### PR 流程

1. **创建分支**：从最新的 `main` 分支切出功能分支。
2. **本地开发**：遵循本文档中的代码风格和测试要求。
3. **本地验证**：提交 PR 前，确保本地构建和测试全部通过（见[构建与验证步骤](#构建与验证步骤)）。
4. **提交 PR**：向 `main` 分支发起 Pull Request。
5. **CI 检查**：GitHub Actions 将自动运行单元测试和 Debug APK 构建。
6. **代码审查**：至少需要一位维护者审查通过后方可合并。

### PR 描述模板

```markdown
## 变更说明

<!-- 简要描述此 PR 的目的和实现方式 -->

## 变更类型

- [ ] 新功能
- [ ] 缺陷修复
- [ ] 重构
- [ ] 文档更新
- [ ] 测试补充

## 测试情况

- [ ] 已通过全部 31 项现有测试
- [ ] 已为新功能添加对应测试（如适用）
- [ ] 已在模拟器或真机上验证（如适用）

## 关联 Issue

<!-- 关联的 Issue 编号，例如：Closes #42 -->

## 截图 / 录屏

<!-- 如涉及 UI 变更，请附上截图或录屏 -->
```

> [!TIP]
> 保持每个 PR 聚焦于单一职责。大规模变更请拆分为多个小 PR，以便于审查。

---

## 代码风格

### 通用规范

- 遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)。
- 使用 **4 个空格**缩进，不使用 Tab。
- 源码统一位于 `app/src/main/java/com/kelele/manliu/` 目录下。
- 注解处理使用 **KSP**（Kotlin Symbol Processing），请勿使用 kapt。

### Jetpack Compose 规范

| 规则 | 说明 | 示例 |
|---|---|---|
| **组件函数命名** | 使用 PascalCase（大驼峰），名词形式 | `ReaderScreen`、`BookCard` |
| **修饰符参数** | Composable 函数应接受 `Modifier` 参数并设默认值 | `fun BookCard(modifier: Modifier = Modifier)` |
| **状态提升** | 优先使用状态提升模式，保持组件无状态 | 将状态管理移至 ViewModel |
| **预览函数** | 为可复用组件编写 `@Preview` 函数 | `@Preview @Composable fun PreviewBookCard()` |

### Kotlin 代码规范

```kotlin
// ✅ 推荐：使用 data class 表达数据模型
data class Book(
    val id: Long,
    val title: String,
    val author: String
)

// ✅ 推荐：函数命名使用 camelCase（小驼峰）
fun loadBookList(): List<Book> { ... }

// ✅ 推荐：常量命名使用 SCREAMING_SNAKE_CASE
companion object {
    const val MAX_ARCHIVE_SIZE = 100
}

// ❌ 避免：使用通配符导入
import com.kelele.manliu.data.*

// ✅ 推荐：明确导入
import com.kelele.manliu.data.Book
import com.kelele.manliu.data.BookDao
```

### Room 数据库规范

- Entity 类使用 `@Entity` 注解，表名使用 snake_case。
- DAO 接口方法命名应清晰表达意图（如 `getBookById`、`insertBook`）。
- 数据库版本变更时必须编写 Migration。

---

## 提交信息格式

采用 **约定式提交（Conventional Commits）** 格式：

```
<类型>(<范围>): <简要描述>

<正文（可选）>

<脚注（可选）>
```

### 类型说明

| 类型 | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 代码重构（不改变功能） |
| `test` | 添加或修改测试 |
| `docs` | 文档变更 |
| `style` | 代码格式调整（不影响逻辑） |
| `chore` | 构建配置、依赖更新等杂项 |
| `perf` | 性能优化 |

### 示例

```
feat(reader): 添加书签跳转功能

实现长按页码弹出书签列表，点击后跳转至对应页面。

Closes #28
```

```
fix(import): 修复导入大文件时的 OOM 崩溃

将文件读取方式从一次性加载改为分块流式读取，
降低内存峰值占用。
```

```
test(archive): 补充归档容量上限的边界测试
```

> [!NOTE]
> 提交描述使用简体中文。每行不超过 72 个字符（中文字符按 2 字符宽度计算）。

---

## 测试要求

### 现有测试概览

项目当前包含 **31 项 JVM 测试**，其中 12 项使用 Robolectric 验证真实 Room、前台服务及 Compose 交互，分布如下：

| 测试类 | 测试数量 | 测试内容 |
|---|---|---|
| `ArchiveLimitsTest` | 5 项 | 归档功能的容量限制与边界条件 |
| `ImportOrderingTest` | 4 项 | 导入功能的排序逻辑 |
| `ReaderProgressTest` | 2 项 | 阅读进度的计算与持久化 |
| `ArchiveParserTest` | 4 项 | 压缩包过滤与章节路径自然排序 |
| `ArchiveExtractionBudgetTest` | 4 项 | 解压数量、大小及空间边界 |
| `ArchiveImportIntegrationTest` | 10 项 | 真实入库、任务恢复及停止时的清理 |
| `ImportInteractionTest` | 2 项 | 页面退出和拖拽自动滚动 |

### 测试规范

1. **所有 PR 必须通过全部现有测试**，不允许跳过或禁用已有测试。
2. **新增功能必须附带对应的单元测试**。
3. 测试文件放置在 `app/src/test/` 目录下，包路径与源码保持一致。
4. 测试类命名以 `Test` 结尾（如 `BookParserTest`）。
5. 测试方法命名应清晰描述测试意图，推荐使用反引号格式：

```kotlin
@Test
fun `导入文件时应按文件名升序排列`() {
    // 测试实现...
}
```

> [!WARNING]
> 本项目**不运行 Android 仪器测试**（Instrumented Test）。请将所有测试编写为 JVM 单元测试。如需测试 Compose UI 组件，请使用 Robolectric 或将逻辑提取到纯 Kotlin 类中进行测试。

---

## 构建与验证步骤

在提交 PR 之前，请在本地执行以下完整验证：

```bash
# 1. 运行全部单元测试 + 构建 Debug APK（与 CI 一致）
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

### 各步骤说明

| 步骤 | 命令 | 说明 |
|---|---|---|
| 单元测试 | `./gradlew :app:testDebugUnitTest` | 运行全部 31 项 JVM 测试 |
| Debug 构建 | `./gradlew :app:assembleDebug` | 构建 Debug APK，验证编译无误 |
| 完整验证 | `./gradlew :app:testDebugUnitTest :app:assembleDebug` | 一次性执行以上两步 |
| 清理构建 | `./gradlew clean :app:assembleDebug` | 清理后重新构建，排查缓存问题 |

### CI 流程

GitHub Actions 在每个 PR 上会自动执行：

```
单元测试（testDebugUnitTest） → Debug APK 构建（assembleDebug）
```

> [!IMPORTANT]
> **CI 必须全部通过后 PR 才可合并。** 如 CI 失败，请根据日志排查问题并推送修复。

### 常见构建问题

| 问题 | 解决方案 |
|---|---|
| `Unsupported class file major version 65` | 确认使用 JDK 17，而非 JDK 21 |
| KSP 注解处理报错 | 运行 `./gradlew clean` 后重新构建 |
| SDK 版本不匹配 | 在 SDK Manager 中安装 API 35 SDK Platform |
| Gradle Sync 失败 | 检查网络代理设置，或尝试使用 `--refresh-dependencies` |

---

## Issue 报告模板

### 缺陷报告

```markdown
---
name: 🐛 缺陷报告
about: 报告一个需要修复的问题
labels: bug
---

## 缺陷描述

<!-- 清晰简要地描述遇到的问题 -->

## 复现步骤

1. 打开应用...
2. 进入「...」页面...
3. 点击「...」按钮...
4. 观察到...

## 期望行为

<!-- 描述你期望看到的正确行为 -->

## 实际行为

<!-- 描述实际发生的错误行为 -->

## 环境信息

- 设备型号：
- Android 版本：
- 应用版本：

## 截图 / 日志

<!-- 如有截图或 Logcat 日志，请粘贴在此 -->

## 补充说明

<!-- 其他可能有帮助的信息 -->
```

### 功能建议

```markdown
---
name: ✨ 功能建议
about: 提出一个新功能或改进建议
labels: enhancement
---

## 功能描述

<!-- 清晰描述你建议的功能 -->

## 使用场景

<!-- 描述在什么场景下需要这个功能 -->

## 建议实现方式

<!-- 如有想法，描述可能的实现方案 -->

## 补充说明

<!-- 其他可能有帮助的信息 -->
```

> [!CAUTION]
> 漫流是纯离线应用。任何涉及网络访问的功能建议（如在线书源、云同步等）将不会被采纳。

---

## 联系方式

如有任何疑问，请通过以下方式联系项目维护者：

- 在仓库中提交 Issue
- 在相关 PR 中留言讨论

感谢你的贡献！🎉

---

## 开源协议

本项目采用 [Apache License 2.0](LICENSE) 开源许可证。所有提交的代码与贡献均视为同意遵循该协议。
