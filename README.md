# 漫流 Manliu

> 离线安卓条漫阅读器 — 创建图集、导入图片、调整顺序，然后连续向下滑动阅读。

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/manliu_muse.png" alt="漫流少女" width="160" />
</p>

<p align="center">
  <a href="https://github.com/Kelele20/Manliu/releases/tag/v0.3.3">📦 下载 APK</a>
  &nbsp;·&nbsp;
  <a href="docs/使用与开发说明.md">📖 使用与开发说明</a>
  &nbsp;·&nbsp;
  <a href="docs/架构设计.md">🏗️ 架构设计</a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">📝 更新日志</a>
</p>

---

## ✨ 功能亮点

| 功能 | 说明 |
|------|------|
| **丰富导入来源** | 支持**文件夹批量导入**（单次最多 10,000 张）、**ZIP / CBZ 漫画压缩包直接导入**、以及系统相册与文件多选（最多 100 张） |
| **连续滚动阅读** | 沉浸式向上滑动阅读，自动保存阅读进度，支持页码精确跳转与后台新增图片实时观察 |
| **手势缩放与细节查看** | 阅读器支持**双指自由捏合缩放（1x ~ 4x）**、**双击快速放大/还原**、放大模式下画布平移与“一键重置 1x” |
| **夜间与护眼** | 支持**纯黑沉浸模式（AMOLED Black）**与米白浅色模式一键切换；阅读器自动开启**屏幕常亮（Keep Screen On）** |
| **极致平滑拖拽排序** | 长按手柄拖动图集或图片调整顺序，内置**数学级位移补偿**与**物理弹性动画**，彻底消除抽搐与松手回弹闪烁 |
| **智能自然排序** | 按文件名自然排序（2.jpg 排在 10.jpg 前面），升序/降序可选 |
| **高性能与低内存** | 列表缩略图采用 **Coil 精确下采样解码**，即使导入几十兆的 4K/8K 扫描原图也能保持丝滑流畅，杜绝 OOM |
| **后台持久导入** | 前台服务保障持久处理，离开页面或切换阅读后仍继续；支持暂停、继续、取消和失败重试 |
| **完整备份与恢复** | 导出 `.manliu` 备份包（ZIP + JSON 清单），完整保留图集、图片文件、图片顺序与阅读进度 |
| **纯离线与隐私保护** | 无账户系统、**不声明网络权限**，图片存储在应用专属隔离空间 |

## 📱 系统要求

- **最低版本**：Android 8.0（API 26）
- **目标版本**：Android 15（API 35）
- **仓库权限**：私有仓库，下载 APK 需要有权限的 GitHub 账号

## 🎯 快速上手

```
新建图集 → 添加图片 → 选择文件夹 / ZIP压缩包 / 相册 → 开始阅读
```

1. 在首页点击 **新建** 按钮创建图集；
2. 进入图集后点击 **添加图片**：
   - **从文件夹选择**：推荐大批量导入（最多 10,000 张），支持全选与按名称/修改时间排序；
   - **从 ZIP / CBZ 压缩包导入**：直接选择漫画包，自动解压过滤垃圾文件并按自然数字顺序入库；
   - **从相册 / 文件选择**：支持多选（单批次最多 100 张）；
3. 导入完成后点击 **开始阅读** 进入沉浸式阅读器；
4. 阅读时长按画面唤出工具栏，可切换深黑/浅米背景、跳转页码；双指捏合可无级缩放查看画面细节。

> **提示**：Android 11+ 系统限制直接读取 `Android/data` 或 `Download` 根目录。若需使用文件夹导入，可先用文件管理器将图片复制到 `Download/漫流待导入` 等子文件夹后再行选取。

## 🏗️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| [Kotlin](https://kotlinlang.org/) | 2.0.21 | 现代强类型 Android 编程语言 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | BOM 2024.10.01 | 现代声明式 UI 框架 |
| [Room](https://developer.android.com/training/data-storage/room) | 2.6.1 | 本地 SQLite 抽象数据库层（支持事务与 Flow 响应式观察） |
| [Coil](https://coil-kt.github.io/coil/) | 2.7.0 | 异步图片加载与采样解码 |
| [KSP](https://github.com/google/ksp) | 2.0.21-1.0.25 | Kotlin 符号处理（编译期 Room DAO 生成） |

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

# 运行单元测试并构建 Debug APK
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows PowerShell 用户：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

构建输出产物：`app/build/outputs/apk/debug/app-debug.apk`

> **签名说明**：开发签名密钥 `manliu-dev-key.keystore` 属于私人密钥，不在公开 Git 仓库中。如需覆盖已有正式安装包，将密钥文件置于项目根目录再行打包即可。

## 🧪 单元测试

项目包含 **13 项 JVM 自动化单元测试**，覆盖核心业务边界：

| 测试类 | 项数 | 覆盖核心逻辑 |
|--------|------|--------------|
| `ArchiveLimitsTest` | 5 | 备份边界限制验证：图集/图片数量上限、清单/图片大小硬限制、先验后写机制 |
| `ImportOrderingTest` | 4 | 导入排序逻辑：失败项重试插入位置、暂停恢复续传位置、用户重排后插入算法 |
| `ReaderProgressTest` | 2 | 阅读进度保持：跨页插入后稳定图片 ID 定位、页面列表动态更新 |
| `ArchiveParserTest` | 2 | 压缩包导入解析：ZIP/CBZ 图片过滤、垃圾文件排除、乱序条目自然排序 |

Pull Request 提交时，GitHub Actions 会在云端自动运行全部单元测试及 APK 构建。

## 📂 项目结构

```
Manliu/
├── app/
│   ├── build.gradle.kts          # 应用级构建配置
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/kelele/manliu/
│       │   │   ├── MainActivity.kt       # 轻量入口：生命周期与顶层页面调度
│       │   │   ├── ComicDatabase.kt      # Room 实体 (Album, Page, ImportJob)、DAO、版本迁移
│       │   │   ├── ComicRepository.kt    # 业务仓库层：图集管理、分批暂存、ZIP解压、事务处理
│       │   │   ├── ImportService.kt      # 前台服务：多选与大批量后台持久化导入
│       │   │   ├── ArchiveService.kt     # 前台服务：全量/单图集备份与恢复
│       │   │   ├── ArchiveManager.kt     # 备份导出引擎与还原解压解析器
│       │   │   ├── DragReorderState.kt   # 长按拖拽排序状态机（防闪动与坐标平滑补偿算法）
│       │   │   ├── ImageSorting.kt       # 自然文件名排序算法
│       │   │   ├── ImportOrdering.kt     # 导入重试插入位置算法
│       │   │   ├── ReaderProgress.kt     # 阅读器稳定进度定位计算
│       │   │   └── ui/                   # 模块化 UI 层
│       │   │       ├── theme/            # 色彩定义与主题体系 (Color.kt, Theme.kt)
│       │   │       ├── components/       # 通用卡片与弹窗组件 (ProgressCards.kt)
│       │   │       ├── library/          # 图库首页 (LibraryScreen.kt)
│       │   │       ├── album/            # 图集管理 (AlbumScreen.kt, FolderImportDialog.kt)
│       │   │       └── reader/           # 沉浸式阅读器 (ReaderScreen.kt)
│       │   └── res/                      # 图标与图形资源
│       └── test/                         # JVM 单元测试套件
├── docs/                                 # 深度设计与使用说明文档
│   ├── 使用与开发说明.md
│   └── 架构设计.md
├── CONTRIBUTING.md                       # 开发规范与贡献指南
├── CHANGELOG.md                          # 详细版本变更历史
└── .github/workflows/                    # GitHub Actions CI 流水线
    └── android-build.yml
```

详细架构演进可参考 [架构设计文档](docs/架构设计.md)。

## 📋 备份与归档机制

`.manliu` 文件为标准 ZIP 归档包，内含版本化清单：

```
archive.manliu (ZIP)
├── manifest.json     # 图集元数据、页面相对路径、阅读进度
├── a0/p0             # 第 1 个图集的第 1 张图片
├── a0/p1             # 第 1 个图集的第 2 张图片
├── a1/p0             # 第 2 个图集的第 1 张图片
└── ...
```

规格上限说明：

| 限制维度 | 最大上限 |
|----------|----------|
| 图集数量 | 1,000 个 |
| 图片总数 | 200,000 张 |
| 清单文件 | 64 MiB |
| 单图大小 | 100 MiB |

## 🔮 未来规划

- 递归读取多层级子文件夹
- 自动智能切除白边
- 离线定时本地备份
- 左右双页翻页模式（传统漫模式）

## 📄 许可说明

本项目为私有开源仓库，仅供授权个人及团队使用。
