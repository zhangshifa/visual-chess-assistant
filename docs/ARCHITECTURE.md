# 视觉象棋助手 · 架构与算法细节

## 1. 模块划分

| 包 | 文件 | 职责 |
|---|---|---|
| `com.visualchess.chess` | `Piece` | 棋子常量（红正黑负）、通用/完整中文名 |
| | `Move` | 着法数据（起点/终点/移动子/被吃子/记谱） |
| | `Board` | 10×9 棋盘模型、初始布局、克隆、河界/九宫判定 |
| | `Engine` | 走法生成、攻防判定、合法性过滤、评估、Alpha-Beta 搜索、分析 |
| `com.visualchess.vision` | `PieceRecognizer` | 渲染 14 个汉字模板，按 NCC 识别格子棋子 |
| | `BoardDetector` | 四点双线性校正 + 逐格裁剪 + 调用识别 |
| `com.visualchess.ui` | `BoardOverlayView` | 在预览上绘制网格/棋子/上一步箭头/AI 建议 |
| `com.visualchess.app` | `MainActivity` | 相机预览、按钮交互、对局状态、后台搜索线程 |

## 2. 坐标与规则约定

- 棋盘 10 行（`row 0..9`）× 9 列（`col 0..8`）。
- `row 0` 固定为黑方底线，`row 9` 为红方底线；红方向上走（`row-1`），黑方向下走（`row+1`）。
- 棋子值：`红 = +type`，`黑 = -type`。type：`ROOK=1, KNIGHT=2, CANNON=3, BISHOP=4, ADVISOR=5, KING=6, PAWN=7`。

### 特殊规则实现要点
- **蹩马腿**：横向跳看纵向邻格、纵向跳看横向邻格，该格非空则不可走。
- **炮架（翻山）**：非吃子时同车直行；吃子须隔且仅隔一个棋子（炮架）。
- **塞象眼**：象/相走「田」字，中心点非空不可走，且不可过河。
- **飞将（白脸将）**：将与帅同列且中间无子时，一方可将「吃」掉另一方，也作为送将判定。
- **过河兵**：兵/卒过河（红 `row≤4`，黑 `row≥5`）后方可横走，永不后退。

## 3. 搜索与评估

- `legalMoves`：先生成伪合法着法，再逐一对「走子后是否己方被将 / 将帅对脸」过滤。
- `eval`（红方视角）：子力价值 + 兵卒推进奖励 `（9-row）*8 / row*8` + 轻微中心化。
- `negamax` + Alpha-Beta + MVV-LVA 着法排序；根节点 `analyze()` 返回最佳着法、评分与 3 步主要变例（PV）。
- 默认 `SEARCH_DEPTH=4`，模拟对局 `SIM_DEPTH=3`，可在 `MainActivity` 调整。

## 4. 摄像头识别管线

```
Camera2 预览
   └─ TextureView.getBitmap()                // 取当前帧
        └─ BoardDetector.recognize(frame, corners, recognizer)
             ├─ rectify(): 四点双线性校正 → 270×300 标准图
             ├─ 9×10 等分裁剪每个交叉点
             └─ PieceRecognizer.recognize(cell)
                  ├─ 中心暗像素占比 → 是否有子
                  ├─ RGB 均值 → 红/黑
                  └─ 缩放至 48×48，与同色 7 模板做 NCC → 最优 type
```

- 引导框为屏幕上的轴对齐矩形（由 `BoardOverlayView` 在布局完成后按比例计算）。
- 视图坐标 → 图像坐标按缩放比换算后传给检测器。
- 识别结果写入 `board[10][9]`，叠加层实时绘制。

## 5. 性能与线程

- 相机打开、预览、识别、AI 搜索均在独立线程（`HandlerThread` / 单线程 `ExecutorService`）执行，避免阻塞 UI。
- 模拟对局逐步进线程并 `Thread.sleep` 间隔，UI 通过 `runOnUiThread` 刷新。

## 6. 构建与产物

- 构建配置复用「闪光点」App 的 Gradle 8.2.1 + AGP 8.2.1 + 腾讯云 Maven 镜像。
- 端到端 APK 由 `.github/workflows/build-apk.yml` 在云端生成（JDK 17 + Android SDK 34），产物为 `app-debug.apk`。
- 本地构建需自行安装 JDK 17 与 Android SDK，并在 `android/local.properties` 写入 `sdk.dir`。
