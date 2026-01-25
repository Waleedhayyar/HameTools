# HamTools

**业余无线电爱好者的全能工具箱** (・ω・)ノ

HamTools 是一款专为业余无线电爱好者 (HAM) 设计的 Android 应用程序，集成了通联日志管理、QSL 卡片制作、CW 莫斯电码练习、SSTV 慢扫描电视接收、传播预测、网格定位等多种实用功能。应用采用现代化的 Material 3 设计语言，支持 Android 12+ 的 Monet 动态取色主题，提供简洁美观的用户界面。

---

## 目录

- [项目概述](#项目概述)
- [功能特性](#功能特性)
- [系统要求](#系统要求)
- [项目架构](#项目架构)
- [技术栈](#技术栈)
- [模块详解](#模块详解)
  - [通联日志 (Logbook)](#通联日志-logbook)
  - [QSL 卡片工坊](#qsl-卡片工坊)
  - [CW 发报练习](#cw-发报练习)
  - [SSTV 接收器](#sstv-接收器)
  - [传播预测](#传播预测)
  - [网格定位](#网格定位)
  - [Q 简语速查](#q-简语速查)
- [SSTV 解码技术说明](#sstv-解码技术说明)
- [构建与运行](#构建与运行)
- [项目结构](#项目结构)
- [数据存储](#数据存储)
- [多语言支持](#多语言支持)
- [开源致谢](#开源致谢)
- [许可证](#许可证)

---

## 项目概述

HamTools 旨在为业余无线电爱好者提供一站式的移动端工具集。无论是日常的通联记录管理、QSL 卡片制作与分享，还是 CW 莫斯电码的学习练习、SSTV 图像的实时接收解码，亦或是查看当前的电波传播条件、计算网格定位，HamTools 都能满足您的需求。

应用完全使用 Kotlin 语言开发，采用 Jetpack Compose 构建现代化的声明式 UI，遵循 MVVM 架构模式和 Clean Architecture 设计原则，确保代码的可维护性和可扩展性。

### AI 辅助开发说明

本项目是一次 **AI 辅助编程的实践探索**。整个项目从零开始，全程使用 **[Cursor](https://cursor.sh/)** 编辑器配合 **Claude Opus 4.5** 大语言模型完成开发。从项目架构设计、代码编写、UI 实现、Bug 修复到文档撰写，均在 AI 的协助下完成。

这种开发模式展示了 AI 辅助编程在实际项目中的应用潜力：
- **高效的代码生成**：AI 能够快速理解需求并生成符合最佳实践的代码
- **架构设计建议**：在 MVVM、Clean Architecture 等架构模式的应用上提供专业指导
- **问题诊断与修复**：快速定位和解决编译错误、运行时问题
- **代码重构与优化**：持续改进代码质量和性能
- **文档编写**：生成详尽的技术文档和使用说明

当然，AI 辅助开发并不意味着完全自动化，开发者的判断、审查和决策仍然是项目成功的关键。

---

## 功能特性

### 核心功能

| 功能 | 说明 |
|------|------|
| **通联日志** | 完整的 QSO 记录管理，支持呼号、频率、模式、RST 信号报告、QTH、网格定位等字段 |
| **QSL 卡片工坊** | 可视化 QSL 卡片编辑器，支持多模板管理、自定义背景、文字元素拖拽布局 |
| **CW 发报练习** | 莫斯电码练习工具，实时生成 700Hz 音调，自动解码点划序列 |
| **SSTV 接收** | 实时 SSTV 慢扫描电视解码，支持 Robot、Martin、Scottie、PD、Wraase 等多种模式 |
| **传播预测** | 实时获取太阳/地磁指数 (SFI、K 指数、A 指数)，显示 HF 波段传播条件 |
| **网格定位** | GPS 实时定位转换为 Maidenhead 网格代码，支持正向/反向查询 |
| **Q 简语速查** | 内置完整的 Q 简语字典，支持搜索和分类浏览 |

### 用户体验

- **Material 3 设计语言**：遵循最新的 Google Material Design 3 规范
- **动态取色 (Monet)**：自动从系统壁纸提取主题色，打造个性化界面
- **多语言支持**：支持简体中文、English、日本語，可跟随系统或手动切换
- **深色/浅色主题**：自动适配系统主题设置
- **边缘到边缘显示**：充分利用屏幕空间，提供沉浸式体验
- **数据导出**：支持将通联日志导出为 JSON 或 CSV 格式

### 个人中心

- **执照卡片**：展示业余无线电执照信息，支持添加执照照片
- **通联统计**：自动统计总通联数、本年/本月/今日通联数
- **波段分布**：可视化展示各波段通联数量分布

---

## 系统要求

- **Android 版本**：Android 12 (API 31) 或更高版本
- **目标 SDK**：Android 15 (API 35)
- **编译 SDK**：Android 15 (API 35)
- **开发语言**：Kotlin 2.0.21
- **构建工具**：Gradle 8.7.3 with Kotlin DSL

---

## 项目架构

HamTools 采用 **MVVM (Model-View-ViewModel)** 架构模式，结合 **Clean Architecture** 分层设计：

```
app/
├── data/                    # 数据层
│   ├── local/              # 本地数据源 (Room Database)
│   ├── model/              # 数据模型
│   ├── remote/             # 远程数据源 (API)
│   └── repository/         # 数据仓库
├── di/                      # 依赖注入 (Hilt)
├── ui/                      # 表现层
│   ├── navigation/         # 导航配置
│   ├── screens/            # 各功能屏幕
│   └── theme/              # 主题配置
└── util/                    # 工具类
```

### 架构层次说明

1. **表现层 (Presentation Layer)**
   - 使用 Jetpack Compose 构建声明式 UI
   - ViewModel 管理 UI 状态和业务逻辑
   - 使用 StateFlow 进行响应式数据流

2. **领域层 (Domain Layer)**
   - Repository 接口定义数据操作契约
   - UseCase 封装业务逻辑 (部分场景)

3. **数据层 (Data Layer)**
   - Room Database 提供本地持久化
   - Retrofit + OkHttp 处理网络请求
   - DataStore 存储用户偏好设置

---

## 技术栈

### 核心框架

| 技术 | 版本 | 用途 |
|------|------|------|
| **Kotlin** | 2.0.21 | 开发语言 |
| **Jetpack Compose** | 2024.12.01 BOM | 声明式 UI 框架 |
| **Material 3** | 1.3.1 | UI 设计系统 |
| **Hilt** | 2.53.1 | 依赖注入 |
| **Room** | 2.6.1 | 本地数据库 |
| **Navigation Compose** | 2.8.5 | 导航框架 |

### 网络与数据

| 技术 | 版本 | 用途 |
|------|------|------|
| **Retrofit** | 2.11.0 | HTTP 客户端 |
| **OkHttp** | 4.12.0 | 网络层 |
| **Kotlinx Serialization** | 1.7.3 | JSON 序列化 |
| **DataStore** | 1.1.1 | 偏好设置存储 |
| **Coil** | 2.7.0 | 图片加载 |

### 平台服务

| 技术 | 版本 | 用途 |
|------|------|------|
| **Play Services Location** | 21.3.0 | 位置服务 |
| **Coroutines** | 1.9.0 | 异步编程 |
| **Lifecycle** | 2.8.7 | 生命周期管理 |

---

## 模块详解

### 通联日志 (Logbook)

通联日志是 HamTools 的核心功能模块，用于记录和管理业余无线电通联 (QSO) 记录。

**功能特点：**

- 完整的 QSO 记录字段，遵循 ADIF 标准：
  - 基本信息：对方呼号、频率、通信模式、RST 信号报告
  - 对方信息：操作员姓名、QTH 地点、网格定位、QSL 信息
  - 我方信息：发射功率、设备/天线配置
  - 传播与确认：传播模式、QSL 确认状态
- 支持多种通信模式：SSB、CW、FM、AM、FT8、FT4、RTTY、PSK31 等
- 支持多种传播模式：电离层、对流层、地波、卫星、EME 等
- QSL 状态追踪：支持实体卡片、LoTW、eQSL、ClubLog 等确认方式
- 长按记录可快速生成 QSL 卡片或删除

**数据模型：**

```kotlin
data class QsoLog(
    val id: Long,
    val callsign: String,        // 对方呼号
    val frequency: String,       // 频率
    val mode: Mode,              // 通信模式
    val rstSent: String,         // 发送的信号报告
    val rstRcvd: String,         // 接收的信号报告
    val timestamp: Long,         // UTC 时间戳
    val opName: String?,         // 对方姓名
    val qth: String?,            // 对方地点
    val gridLocator: String?,    // 网格定位
    val qslInfo: String?,        // QSL 信息
    val txPower: String?,        // 发射功率
    val rig: String?,            // 设备/天线
    val propagation: PropagationMode,
    val qslStatus: QslStatus,
    val remarks: String?         // 备注
)
```

---

### QSL 卡片工坊

QSL 卡片工坊是一个所见即所得 (WYSIWYG) 的 QSL 卡片编辑器，让您可以轻松设计和生成精美的 QSL 卡片。

**功能特点：**

- **多模板管理**：创建、保存、删除多个卡片模板
- **可视化编辑**：
  - 拖拽移动文字元素位置
  - 双指捏合缩放调整文字大小
  - 自定义文字颜色和粗细
- **丰富的占位符**：
  - 我的呼号 / 对方呼号
  - 频率 / 通信模式
  - 日期时间
  - RST 信号报告
  - QTH 地点 / 网格定位
  - 自定义文本
- **背景设置**：
  - 选择本地图片作为背景
  - 使用 Material 3 动态取色背景色
- **导出分享**：
  - 保存 PNG 图片到相册
  - 分享到微信、QQ、邮件等应用

**与通联日志联动：**

从通联日志长按记录选择「生成 QSL 卡片」，可自动填充该次通联的所有信息到卡片模板中。

---

### CW 发报练习

CW 发报练习模块帮助业余无线电爱好者学习和练习莫斯电码。

**功能特点：**

- **虚拟电键**：屏幕上的大圆形按钮模拟电键
- **音调生成**：按下电键时生成 700Hz 正弦波音调
- **实时反馈**：
  - 时间轴显示点 (Dit) 和划 (Dah) 序列
  - 自动识别点划（< 200ms 为点，>= 200ms 为划）
- **自动解码**：
  - 实时将点划序列解码为字母和数字
  - 显示解码结果和对应的莫斯电码
- **视觉效果**：
  - 点划时间轴以不同颜色和宽度显示
  - 按键按下时的缩放动画反馈

**技术实现：**

使用 Android AudioTrack API 以流式方式生成低延迟的 700Hz 正弦波音调，确保按键响应即时且无卡顿。

---

### SSTV 接收器

SSTV (Slow-Scan Television) 接收器是 HamTools 的重要特色功能，能够实时解码从麦克风或音频输入接收到的 SSTV 信号。

**重要说明：** SSTV 解码核心算法移植自开源项目 [Robot36](https://github.com/xdsopl/robot36)，遵循其 0BSD 许可证。详细的技术说明请参阅 [SSTV 解码技术说明](#sstv-解码技术说明) 章节。

**功能特点：**

- **实时解码**：从麦克风捕获音频并实时解码 SSTV 图像
- **瀑布图显示**：类似 Robot36 的 scopeView 风格，持续滚动显示解码图像
- **自由运行模式**：即使没有检测到同步信号，也能持续解码显示
- **同步脉冲检测**：自动检测同步脉冲并校准行时序
- **信号状态显示**：
  - 实时频率显示
  - 信号强度指示
  - 同步状态指示
  - 当前行号显示

**支持的 SSTV 模式：**

| 模式系列 | 支持模式 | 分辨率 | 颜色类型 |
|----------|----------|--------|----------|
| **Robot** | Robot 36, Robot 72 | 320x240 | YUV |
| **Martin** | Martin 1, Martin 2 | 320x256 | RGB |
| **Scottie** | Scottie 1, Scottie 2, Scottie DX | 320x256 | RGB |
| **PD** | PD 50, PD 90, PD 120, PD 160, PD 180, PD 240, PD 290 | 多种 | YUV |
| **Wraase** | Wraase SC2-180 | 320x256 | RGB |

---

### 传播预测

传播预测模块提供实时的太阳/地磁活动数据和 HF 波段传播条件预测，帮助业余无线电爱好者选择最佳的通联时机。

**功能特点：**

- **实时数据获取**：从 HamQSL.com 获取最新的太阳/地磁数据
- **太阳/地磁指数显示**：
  - SFI (太阳通量指数)
  - K 指数 (地磁扰动)
  - A 指数 (地磁活动)
  - 地磁状态 (Quiet/Unsettled/Active/Storm)
  - 信号噪声等级
  - 太阳黑子数、太阳风速度、X 射线等级 (如可用)
- **波段传播条件**：
  - 白天传播条件 (80m-10m 各波段)
  - 夜间传播条件 (80m-10m 各波段)
  - Good/Fair/Poor 三级评估
- **VHF 传播现象**：显示特殊的 VHF 传播条件 (如有)
- **智能缓存**：
  - 15 分钟缓存有效期
  - 网络错误时自动 fallback 到缓存数据
  - 下拉刷新更新数据

**数据来源：**

数据来自 [HamQSL.com](http://www.hamqsl.com/solarxml.php) 提供的 Solar-Terrestrial Data XML。

---

### 网格定位

网格定位模块提供 Maidenhead 网格定位系统的计算和转换功能。

**功能特点：**

- **实时定位模式**：
  - 使用 GPS 获取当前位置
  - 自动转换为 6 位网格代码
  - 显示经纬度、海拔、精度信息
  - 支持复制和分享网格代码
  - 优雅的加载动画和状态指示
- **手动查询模式**：
  - 正向查询：经纬度 -> 网格代码
  - 反向查询：网格代码 -> 经纬度
  - 在地图应用中查看位置

**Maidenhead 网格系统：**

Maidenhead Locator System 是业余无线电通用的地理位置编码系统，将地球表面划分为网格区域。完整的 6 位网格代码格式为 AA00aa（如 OM88hf），精度约为 5km x 5km。

---

### Q 简语速查

Q 简语 (Q Codes) 是业余无线电通信中常用的缩略语系统，Q 简语速查模块提供完整的 Q 简语字典。

**功能特点：**

- 完整的 Q 简语列表（QRA 到 QTR）
- 每个 Q 简语显示问句和答句含义
- 支持搜索功能
- 分类浏览（通用、频率、信号、干扰、功率等）

---

## SSTV 解码技术说明

HamTools 的 SSTV 解码功能基于开源项目 **Robot36** 的算法实现，该项目由 Ahmet Inan (xdsopl@gmail.com) 开发，遵循 0BSD (Zero-Clause BSD) 许可证。

### 致谢与引用

Robot36 是一个优秀的 Android SSTV 解码器，其核心 DSP 算法为本项目提供了重要参考。在此对原作者 Ahmet Inan 表示诚挚的感谢！

- **原项目地址**：https://github.com/xdsopl/robot36
- **原项目许可证**：0BSD (Zero-Clause BSD License)

### 移植与改进

本项目将 Robot36 的 Java DSP 算法移植为 Kotlin 实现，并进行了以下调整：

1. **语言转换**：从 Java 转换为 Kotlin，利用 Kotlin 的语言特性优化代码
2. **架构集成**：与 HamTools 的 MVVM 架构和 Compose UI 框架集成
3. **协程支持**：使用 Kotlin Coroutines 和 Flow 进行异步处理
4. **UI 现代化**：采用 Material 3 设计语言重新设计界面

### 核心 DSP 组件

移植的 DSP 组件位于 `app/src/main/java/com/ham/tools/ui/screens/tools/sstv/decoder/` 目录：

| 组件 | 说明 |
|------|------|
| `SstvDecoder.kt` | 主解码器，管理解码状态和输出 |
| `SstvDemodulator.kt` | FM 解调器，混频到基带并解调 |
| `dsp/Complex.kt` | 复数运算 |
| `dsp/ComplexConvolution.kt` | 复数卷积（用于滤波器） |
| `dsp/Filter.kt` | 低通滤波器系数生成 |
| `dsp/FrequencyModulation.kt` | FM 解调算法 |
| `dsp/Kaiser.kt` | Kaiser 窗函数 |
| `dsp/Phasor.kt` | 相位累加器（混频用） |
| `dsp/SchmittTrigger.kt` | 施密特触发器（同步检测） |
| `dsp/SimpleMovingAverage.kt` | 简单移动平均滤波 |
| `dsp/ExponentialMovingAverage.kt` | 指数移动平均滤波 |
| `dsp/ColorConverter.kt` | YUV/RGB 颜色转换 |

### 解码流程

```
麦克风音频 (44.1kHz, 16bit, Mono)
        |
        v
    音频处理器 (SstvAudioProcessor)
        |
        v
    解调器 (SstvDemodulator)
    - 混频到基带 (1900Hz 中心)
    - 低通滤波
    - FM 解调
    - 同步脉冲检测
        |
        v
    解码器 (SstvDecoder)
    - 扫描线缓冲
    - 自由运行/同步模式
    - 像素生成
        |
        v
    瀑布图显示 (SstvReceiverScreen)
```

### SSTV 信号频率

SSTV 使用频率调制来编码图像亮度信息：

| 频率 | 含义 |
|------|------|
| 1200 Hz | 同步脉冲 |
| 1500 Hz | 黑色 (最低亮度) |
| 2300 Hz | 白色 (最高亮度) |
| 1900 Hz | VIS 起始音 / 中间灰 |
| 1100 Hz | VIS 逻辑 1 |
| 1300 Hz | VIS 逻辑 0 |

---

## 构建与运行

### 前置条件

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 35
- Kotlin 2.0.21

### 构建步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/yourusername/HamTools.git
   cd HamTools
   ```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 依赖：
   ```bash
   ./gradlew build
   ```

4. 运行应用：
   - 连接 Android 设备（Android 12+）或启动模拟器
   - 点击 Android Studio 的 Run 按钮

### 构建变体

| 变体 | 说明 |
|------|------|
| **debug** | 调试版本，应用 ID 后缀 `.debug` |
| **release** | 发布版本，启用代码混淆和资源压缩 |

---

## 项目结构

```
HamTools/
├── app/
│   ├── build.gradle.kts          # 应用级构建配置
│   ├── proguard-rules.pro        # ProGuard 混淆规则
│   ├── schemas/                  # Room 数据库 schema 导出
│   └── src/main/
│       ├── AndroidManifest.xml   # 应用清单
│       ├── java/com/ham/tools/
│       │   ├── HamToolsApplication.kt    # Application 类
│       │   ├── data/
│       │   │   ├── local/                # Room DAO 和数据库
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── Converters.kt
│       │   │   │   ├── QslTemplateDao.kt
│       │   │   │   └── QsoLogDao.kt
│       │   │   ├── model/                # 数据模型
│       │   │   │   ├── Mode.kt           # 通信模式
│       │   │   │   ├── PropagationMode.kt
│       │   │   │   ├── QslStatus.kt
│       │   │   │   ├── QslTemplate.kt
│       │   │   │   ├── QsoLog.kt
│       │   │   │   ├── QsoStats.kt
│       │   │   │   ├── SolarData.kt
│       │   │   │   └── UserProfile.kt
│       │   │   ├── remote/               # 远程 API
│       │   │   │   ├── HamQslApi.kt
│       │   │   │   └── SolarXmlParser.kt
│       │   │   └── repository/           # 数据仓库
│       │   │       ├── PropagationRepository.kt
│       │   │       ├── QslTemplateRepository.kt
│       │   │       ├── QsoLogRepository.kt
│       │   │       └── UserPreferencesRepository.kt
│       │   ├── di/                       # Hilt 依赖注入模块
│       │   │   ├── AppModule.kt
│       │   │   └── NetworkModule.kt
│       │   ├── ui/
│       │   │   ├── MainActivity.kt       # 主 Activity
│       │   │   ├── MainViewModel.kt
│       │   │   ├── navigation/           # 导航配置
│       │   │   │   ├── HamToolsNavHost.kt
│       │   │   │   └── NavDestination.kt
│       │   │   ├── screens/              # 功能屏幕
│       │   │   │   ├── logbook/          # 通联日志
│       │   │   │   ├── onboarding/       # 引导页
│       │   │   │   ├── profile/          # 个人中心
│       │   │   │   └── tools/            # 工具箱
│       │   │   │       ├── cw/           # CW 练习
│       │   │   │       ├── gridlocator/  # 网格定位
│       │   │   │       ├── propagation/  # 传播预测
│       │   │   │       ├── qcodes/       # Q 简语
│       │   │   │       ├── qsl/          # QSL 卡片
│       │   │   │       └── sstv/         # SSTV 接收
│       │   │   │           ├── decoder/  # 解码器核心
│       │   │   │           │   └── dsp/  # DSP 算法
│       │   │   │           └── strategies/
│       │   │   └── theme/                # 主题配置
│       │   │       ├── Color.kt
│       │   │       ├── Shape.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   └── util/                     # 工具类
│       │       ├── DataExporter.kt
│       │       ├── GridLocatorUtils.kt
│       │       ├── LocaleManager.kt
│       │       └── QslCardGenerator.kt
│       └── res/                          # 资源文件
│           ├── values/                   # 默认资源 (中文)
│           ├── values-en/                # 英文资源
│           └── values-ja/                # 日文资源
├── build.gradle.kts              # 项目级构建配置
├── gradle/
│   └── libs.versions.toml        # 版本目录 (依赖版本管理)
├── gradle.properties             # Gradle 属性
├── settings.gradle.kts           # 项目设置
└── robot36-reference/            # Robot36 参考代码
```

---

## 数据存储

HamTools 使用多种数据存储方式：

### Room 数据库

用于存储持久化的业务数据：

- **qso_logs 表**：通联日志记录
- **qsl_templates 表**：QSL 卡片模板

数据库 schema 版本历史存储在 `app/schemas/` 目录。

### DataStore Preferences

用于存储用户偏好设置：

- **user_preferences**：用户配置（呼号、执照信息等）
- **propagation_cache**：传播数据缓存

### 文件存储

- QSL 卡片图片通过 MediaStore API 保存到系统相册
- 执照照片存储为 URI 引用

---

## 多语言支持

HamTools 支持以下语言：

| 语言 | 资源目录 | 说明 |
|------|----------|------|
| 简体中文 | `values/` | 默认语言 |
| English | `values-en/` | 英文 |
| 日本語 | `values-ja/` | 日文 |
| 跟随系统 | - | 自动检测系统语言 |

用户可以在「我的」->「设置」中手动切换语言，切换后应用会自动重启以应用新的语言设置。

---

## 开源致谢

HamTools 的开发离不开以下开源项目的支持：

### SSTV 解码

- **[Robot36](https://github.com/xdsopl/robot36)** by Ahmet Inan
  - 许可证：0BSD (Zero-Clause BSD License)
  - 用途：SSTV DSP 解码算法参考实现
  - 说明：本项目的 SSTV 解码核心算法移植自 Robot36 项目

### Android 开发框架

- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** - 现代 Android UI 工具包
- **[Hilt](https://dagger.dev/hilt/)** - 依赖注入框架
- **[Room](https://developer.android.com/training/data-storage/room)** - SQLite 数据库抽象层
- **[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)** - 导航框架
- **[Coil](https://coil-kt.github.io/coil/)** - 图片加载库

### 网络与数据

- **[Retrofit](https://square.github.io/retrofit/)** - 类型安全的 HTTP 客户端
- **[OkHttp](https://square.github.io/okhttp/)** - HTTP 客户端
- **[Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)** - JSON 序列化

### 数据来源

- **[HamQSL.com](http://www.hamqsl.com/)** - 太阳/地磁数据 API

---

## 许可证

### HamTools 应用

本项目采用 **BSD 3-Clause License (BSD-3)** 开源许可证。

```
BSD 3-Clause License

Copyright (c) 2024, Xiongzikun0106
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### SSTV DSP 算法来源

SSTV 解码相关的 DSP 代码移植自 Robot36 项目，原项目采用 0BSD 许可证：

```
Copyright (C) 2024 by Ahmet Inan <xdsopl@gmail.com>

Permission to use, copy, modify, and/or distribute this software for any 
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES 
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR 
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES 
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN 
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF 
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

0BSD 许可证是一种极度宽松的许可证，允许在任何项目中自由使用、修改和分发代码，与 BSD-3 许可证完全兼容。

---

## 联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 GitHub Issue
- 发送邮件至项目维护者

---

**73 de Xiongzikun0106** (｀・ω・´)ゞ

*祝您通联愉快，DX is waiting!*
