# 漫流 Manliu

> 离线安卓条漫阅读器 — 创建图集、导入图片、调整顺序，然后连续向下滑动阅读。

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/manliu_muse.png" alt="漫流少女" width="160" />
</p>

<p align="center">
  <a href="https://github.com/Kelele20/Manliu/releases/tag/v0.3.3">📦 下载 v0.3.3 正式版 APK</a>
  &nbsp;·&nbsp;
  <a href="docs/使用与开发说明.md">📖 使用与开发说明</a>
  &nbsp;·&nbsp;
  <a href="docs/架构设计.md">🏗️ 架构设计</a>
</p>

---

## ✨ 功能亮点

| 功能 | 说明 |
|------|------|
| **批量导入** | 文件夹一次最多导入 10,000 张；相册/文件多选单次最多 100 张 |
| **连续滚动阅读** | 向下滑动翻页，自动保存阅读位置，支持页码跳转 |
| **拖拽排序** | 长按手柄拖动图集或图片调整顺序，靠近边缘自动滚动 |
| **智能排序** | 按文件名自然排序（2.jpg 在 10.jpg 前面），升序/降序可选 |
| **后台导入** | 前台服务保障持久处理，离开页面后仍继续；支持暂停、继续、取消和失败重试 |
| **备份恢复** | 导出 `.manliu` 备份文件，包含图集、图片、顺序和阅读进度 |
| **纯离线** | 不需要账户，不声明网络权限，图片存储在应用专属空间 |

## 📱 系统要求

- **最低版本**：Android 8.0（API 26）
- **目标版本**：Android 15（API 35）
- **仓库权限**：私有仓库，下载 APK 需要有权限的 GitHub 账号

## 🚀 安装

从 [Releases](https://github.com/Kelele20/Manliu/releases) 下载最新 APK：

| 版本 | 说明 |
|------|------|
| [v0.3.3 正式版](https://github.com/Kelele20/Manliu/releases/tag/v0.3.3) | 当前最新版，推荐使用 |
| [v0.3.0 旧版](https://github.com/Kelele20/Manliu/releases/tag/v0.3.0) | 保留在 Releases 供回退 |

> **升级提醒**：v0.3.3 与 v0.3.2 使用同一签名，可覆盖安装。升级前请先备份重要图集，不要先卸载旧版。

## 🎯 快速上手

```
新建图集 → 添加图片 → 从文件夹选择 · 全选/排序 → 开始阅读
```

1. 在首页点击 **新建** 按钮创建图集
2. 进入图集后点击 **添加图片**，选择图片来源
3. 推荐使用 **文件夹选择** 批量导入，支持全选和排序
4. 导入完成后点击 **开始阅读** 进入连续滚动阅读

> **提示**：Android 11+ 不能直接选择 `Android/data` 或 `Download` 根目录。可先将图片复制到 `Download/漫流待导入` 等子文件夹。

## 🏗️ 技术栈

| 技术 | 用途 |
|------|------|
| [Kotlin](https://kotlinlang.org/) | 编程语言 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | 声明式 UI 框架 |
| [Room](https://developer.android.com/training/data-storage/room) | 本地数据库（SQLite 抽象层） |
| [Coil](https://coil-kt.github.io/coil/) | 图片异步加载 |
| [KSP](https://github.com/google/ksp) | Kotlin 符号处理（Room 注解处理） |

## 🔨 从源码构建

### 环境要求

- JDK 17
- Android SDK 35
- Android Studio（推荐）

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/Kelele20/Manliu.git
cd Manliu

# 运行单元测试 + 构建 Debug APK
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows 用户：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`

> **签名说明**：开发签名密钥 `manliu-dev-key.keystore` 单独保存，不在 Git 仓库中。要用同一签名覆盖安装，需将密钥文件放在项目根目录。

## 🧪 测试

项目包含 **11 项 JVM 单元测试**，覆盖核心业务逻辑：

| 测试文件 | 测试项数 | 覆盖范围 |
|----------|----------|----------|
| `ArchiveLimitsTest` | 5 | 备份边界验证：图集/图片数量上限、清单/图片大小限制 |
| `ImportOrderingTest` | 4 | 导入顺序：失败重试插入位置、暂停恢复续传位置 |
| `ReaderProgressTest` | 2 | 阅读进度：插入图片后位置保持、页面列表更新 |

Pull Request 的 GitHub Actions 会自动运行以上测试和 Debug APK 构建。

## 📂 项目结构

```
Manliu/
├── app/
│   ├── build.gradle.kts          # 应用级构建配置
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/kelele/manliu/
│       │   │   ├── MainActivity.kt       # 单 Activity + Compose UI
│       │   │   ├── ComicDatabase.kt      # Room 实体、DAO、数据库迁移
│       │   │   ├── ComicRepository.kt    # 核心业务逻辑（单例仓库）
│       │   │   ├── ImportService.kt      # 图片导入前台服务
│       │   │   ├── ArchiveService.kt     # 备份恢复前台服务
│       │   │   ├── ArchiveManager.kt     # 备份导出与恢复引擎
│       │   │   ├── ArchiveLimits.kt      # 备份规格限制与校验
│       │   │   ├── DragReorderState.kt   # 拖拽排序状态机
│       │   │   ├── ImageSorting.kt       # 自然文件名排序算法
│       │   │   ├── ImportOrdering.kt     # 导入重试插入位置算法
│       │   │   └── ReaderProgress.kt     # 阅读进度恢复逻辑
│       │   └── res/                      # 资源文件
│       └── test/                         # JVM 单元测试
├── build.gradle.kts              # 项目级构建配置
├── settings.gradle.kts           # 项目设置
├── docs/                         # 文档目录
│   ├── 使用与开发说明.md
│   └── 架构设计.md
├── CONTRIBUTING.md               # 贡献指南
├── CHANGELOG.md                  # 版本变更记录
└── .github/workflows/            # CI 配置
    └── android-build.yml
```

详细架构说明请查看 [架构设计文档](docs/架构设计.md)。

## 📋 备份格式

`.manliu` 文件是一个 ZIP 压缩包，包含：

```
archive.manliu (ZIP)
├── manifest.json     # 图集元数据、图片顺序、阅读进度
├── a0/p0             # 第 1 个图集的第 1 张图片
├── a0/p1             # 第 1 个图集的第 2 张图片
├── a1/p0             # 第 2 个图集的第 1 张图片
└── ...
```

导出和恢复共用上限：

| 限制项 | 上限 |
|--------|------|
| 图集数量 | 1,000 |
| 图片数量 | 200,000 |
| 备份清单大小 | 64 MiB |
| 单张图片大小 | 100 MiB |

## 🔮 尚未实现

- 通用 ZIP/CBZ 漫画包导入
- 递归读取子文件夹
- 章节目录
- 自动去白边
- 自动备份

## 📄 许可

本项目为私有项目，仅供授权人员使用。
