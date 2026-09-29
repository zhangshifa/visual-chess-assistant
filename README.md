# 视觉象棋助手（Visual Chess Assistant）

通过**手机摄像头**实时识别中国象棋棋盘与棋子，对棋局进行分析、模拟对弈与「走向」回放的开源 Android 应用。基于原生 Camera2 + 自研轻量计算机视觉 + 中国象棋搜索引擎，无需联网、无需云端模型。

> 本项目对应的可安装 APK 由仓库内置的 GitHub Actions 工作流在云端自动构建（见下方「构建 APK」）。

---

## 一、功能特性

- **摄像头识别棋盘**：把实体象棋盘放入屏幕引导框内，一键「识别棋盘」即可把真实棋局读入手机。
- **棋子自动分类**：用 14 种汉字模板（红/黑各 7 类：车马炮相士帅兵）做归一化互相关匹配，识别每个交叉点的棋子。
- **手动修正**：识别不准时，进入「手动摆子」模式，点击格子循环切换「红→黑→空」，保证可用。
- **AI 走子**：内置中国象棋引擎（alpha-beta 搜索 + 静态评估），可替当前行棋方给出最佳着法。
- **模拟对局**：引擎自动双方对弈，直观演示一局变化（AI vs AI）。
- **局面分析**：给出当前最佳着法、评分（红方视角）以及 3 步主要变例（PV）。
- **走向分析**：识别并落子后，叠加层会标出上一步箭头与 AI 建议着法，着法列表记录完整棋谱，便于复盘棋局走向。
- **切换视角**：红/黑翻转，适配不同摆放方向。

---

## 二、整体架构

```
视觉象棋助手/
├── android/                      # Android 原生工程（Gradle 8.2.1 / AGP 8.2.1）
│   ├── build.gradle              # 顶层构建（腾讯云 Maven 镜像）
│   ├── settings.gradle
│   ├── local.properties          # 本地 SDK 路径（不入库，见 .gitignore）
│   ├── gradle/wrapper/           # Gradle Wrapper（复用「闪光点」项目配置）
│   └── app/
│       ├── build.gradle
│       ├── src/main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/visualchess/
│       │   │   ├── app/MainActivity.java       # 相机 + 交互 + 对局流程
│       │   │   ├── chess/                      # 象棋引擎（纯算法）
│       │   │   │   ├── Piece.java              # 棋子常量/名称
│       │   │   │   ├── Move.java               # 着法
│       │   │   │   ├── Board.java              # 棋盘模型
│       │   │   │   └── Engine.java             # 走法生成+搜索+评估
│       │   │   ├── vision/                     # 视觉识别
│       │   │   │   ├── PieceRecognizer.java     # 棋子模板匹配
│       │   │   │   └── BoardDetector.java       # 棋盘校正+逐格识别
│       │   │   └── ui/
│       │   │       └── BoardOverlayView.java    # 摄像头叠加层（网格/棋子/箭头）
│       │   └── res/                            # 布局/字符串/颜色/主题/图标
│       └── proguard-rules.pro
├── docs/ARCHITECTURE.md          # 模块与算法细节
├── .github/workflows/build-apk.yml  # 云端自动构建 APK
└── README.md
```

**技术选型**
- **相机**：原生 `Camera2`（Android 框架内置，无额外依赖）。
- **UI**：AndroidX `AppCompatActivity` + 自定义 `View` 叠加层。
- **视觉识别**：纯 Android `Bitmap`/`Canvas` 实现，零第三方 CV 库。
- **象棋引擎**：纯 Java，零依赖，可在任意 JVM 环境复用。

---

## 三、摄像头识别原理

1. **采集**：`Camera2` 持续输出预览到 `TextureView`；分析时取当前帧位图。
2. **定位棋盘**：用户将棋盘对齐屏幕中央的**引导框**（9×10 网格）；`BoardDetector` 用四点双线性校正把该四边形映射为标准的 270×300 矩形图。
3. **逐格裁剪**：按 9 列 ×10 行等分，对每个交叉点裁剪出棋子区域。
4. **棋子识别**（`PieceRecognizer`）：
   - 用中心区域暗像素占比判断是否「有子」；
   - 用 RGB 均值判断红/黑方；
   - 把格子缩放至模板尺寸，与红/黑两套共 14 个汉字模板做**归一化互相关（NCC）**，取最优匹配。
5. **人工兜底**：识别偏差通过「手动摆子」点击循环修正，保证任意棋盘都能录入。

> 说明：本方案为轻量级模板匹配，对印刷清晰、正对拍摄的棋盘效果最好；倾斜、反光、手写棋子等情况建议配合手动修正。后续可替换为 OpenCV + CNN 以提升鲁棒性（见 docs）。

---

## 四、中国象棋引擎

`chess/Engine.java` 实现完整规则与搜索：

- **走法生成**：车（直行）、马（蹩马腿）、炮（翻山/炮架）、象/相（塞象眼、不过河）、士/仕（九宫斜行）、将/帅（九宫直行 + 飞将）、兵/卒（过河方可横走）。
- **合法性**：过滤「送将」与「将帅对脸（白脸将）」。
- **评估**：子力价值 + 兵卒推进奖励 + 轻微中心化。
- **搜索**：Negamax + Alpha-Beta 剪枝 + MVV-LVA 着法排序，默认搜索深度 4（模拟对局深度 3）。
- **分析输出**：最佳着法、红方视角评分、3 步主要变例。

棋盘坐标约定：`row 0` 为黑方底线，`row 9` 为红方底线；红方向上走，黑方向下走。

---

## 五、构建 APK

### 方式一：GitHub Actions 云端构建（推荐，零本地依赖）
本仓库已内置 `.github/workflows/build-apk.yml`：
1. 把代码推送到 GitHub（`main` 分支，或打 `v*` tag）。
2. 仓库 **Actions** 页会自动编译并产出 APK 产物（Artifact）。
3. 若为 tag 推送，还会自动创建 **Release** 并附上 APK 下载。

### 方式二：本地构建（需本机已安装 JDK 17 + Android SDK）
```bash
# 1) 安装 JDK 17 与 Android SDK（build-tools;34.0.0、platforms;android-34、platform-tools）
# 2) 配置 local.properties：sdk.dir=<你的 SDK 路径>
echo "sdk.dir=/path/to/android-sdk" > android/local.properties
# 3) 编译
cd android
chmod +x ./gradlew
./gradlew assembleDebug          # 或 assembleRelease
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```
> 本项目沿用了「闪光点」App 的 Gradle 8.2.1 / 腾讯云 Maven 镜像配置，国内构建更快。

---

## 六、安装与使用

1. 获取 APK：从 GitHub Actions 产物 / Release 下载 `app-debug.apk`。
2. 安装到 Android 手机（允许「未知来源」安装）。
3. 首次打开授予**相机权限**。
4. 把实体象棋盘放入引导框内，对准拍摄 → 点「识别棋盘」。
5. 检查/修正局面后：
   - 「分析局面」看建议着法；
   - 「AI 走子」让引擎替当前方走一步；
   - 「模拟对局」自动演示一整局；
   - 「切换视角」翻转红黑；
   - 「手动摆子」点格子循环切换棋子；
   - 「重置」回到初始局面。

---

## 七、目录与依赖

- 仅依赖 `androidx.appcompat` 与 `androidx.constraintlayout`（来自腾讯云 Maven 镜像）。
- 引擎与识别模块为纯 Java，便于复用或替换为更强的模型。

---

## 八、已知限制与后续

- 棋子识别为轻量模板匹配，复杂光照/角度下需人工修正；后续可接入 OpenCV + 轻量 CNN。
- 引擎搜索深度与耗时可在 `MainActivity.SEARCH_DEPTH` 调整。
- 可扩展：残局库、开局库、联网对战、棋谱导入导出（PGN/自有格式）。

---

## 九、许可

本项目用于学习与个人使用。
