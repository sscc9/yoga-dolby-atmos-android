# 杜比音效 · YOGA 83BU 安卓定制版 (YogaDolbyAtmos)

基于联想 **YOGA Pro 14s 至尊版 (`83BU` / `ALC287` + `Dolby Atmos 3.7`)** 声卡驱动库原厂 20 段声学参数定制的 **免 Root、零延迟** Android 全局动态音效应用。

## 核心特性

- **原厂 20 频段（`47Hz – 19688Hz`）精准移植**：提取自本机 `Headphone_Default_Generic_Default_Atmos3.7.xml` 与 `DEV_0287_SUBSYS_17AA38BF_PCI_SUBSYS_380217AA.xml`。
- **耳机 / 扬声器双模自动切换**：
  - 连接蓝牙耳机 / 有线耳机 / USB 小尾巴时：自动切换至原厂 **Atmos 3.7 耳机曲线**（含 100Hz 低频下潜补偿与高频空气感延伸）；
  - 手机外放时：自动切换至适配手机双扬声器的 **清晰度增强 + 冲程防破音动态保护（Excursion Regulator）** 模式。
- **Android 9+ 原生硬件级 `DynamicsProcessing` 零延迟引擎**：
  - 20 段前级均衡（Pre-EQ）+ 20 段多频段动态压缩（MBC，还原音量均衡器与对白提亮）+ 20 段后级调音（Post-EQ）+ `-0.8 dBFS` 软限幅防削波（Brickwall Limiter）+ 硬件环绕虚拟器（`Virtualizer`）。
  - 无需 Root、无需 Shizuku、看视频打游戏零延迟，通吃网易云音乐、QQ音乐、Apple Music、Spotify、B站等主流应用。
- **完整复刻电脑版 Dolby Access 交互**：
  - 六大场景：**动态 / 电影 / 音乐 / 游戏 / 语音(网课) / 自定义**
  - 原厂三套智能目标音色（IEQ）：**均衡 (Balanced) / 温暖 (Warm) / 细腻 (Detailed)**
  - **10 段自定义图形均衡器（32Hz – 16kHz，±12dB）**，支持 3 组自定义存档与实时 20 段频响曲线绘制。
