# 自动跳过 (AutoSkip)

Android 14 适配的自动过弹窗工具。基于 **无障碍服务（AccessibilityService）** 识别并自动点击弹窗中的
「跳过 / 关闭 / 下次再说 / 不再提醒」等按钮，纯本地运行、不联网、不上传任何数据。

## ✨ 功能

| 功能 | 说明 |
| --- | --- |
| 自动点击 | 监听窗口变化，匹配到弹窗按钮文本后自动点击 |
| 学习模式 | 手动点一次目标按钮，自动生成规则 |
| 规则管理 | 增删改、启用/停用、精确/包含匹配、限定包名 |
| 记录统计 | 每次跳过的 App、时间、命中规则；今日 & 累计次数，可清零 |
| 点击延迟 | 0~2000ms 可调，避免误触 |
| 开机自启 | 开机自动拉起服务 |
| 后台保活 | 前台常驻服务提升进程优先级 |

## 🔧 构建（GitHub Actions）

项目已内置工作流 `.github/workflows/build.yml`：

1. 把本工程推送到 GitHub 仓库
2. 进入 **Actions** 标签页，选择 **Build APK** → **Run workflow**
3. 构建完成后在 Artifacts 下载：
   - `AutoSkip-debug-apk`（可直接安装测试）
   - `AutoSkip-release-apk`（未签名，需自行签名后发布）

本地构建（如已装 Android SDK）：

```bash
gradle assembleDebug
```

## 📱 安装与使用

1. 安装 APK
2. 打开 App → 点击「去开启无障碍服务」→ 找到「自动跳过 · 无障碍服务」并开启
3. 首次使用建议开启 **学习模式**：切到目标 App，手动点一次要跳过的按钮
4. 关闭学习模式，返回 App 查看已生成的规则
5. 正常使用其他 App，弹窗会被自动点击

## 🛡️ 保活说明（重要）

Android 14 及各家 ROM 对后台限制较严，请额外做以下设置以保证长期存活：

- **电池设置**：本应用 → 不受限制 / 无限制
- **自启动管理**（小米/华为/OPPO/vivo 等）：允许本应用自启动
- **最近任务**：将本应用**锁定**（下拉锁图标）
- **省电模式**：关闭系统的智能省电对无障碍服务的限制

## 📐 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Room（规则、日志）
- DataStore（配置）
- AccessibilityService（核心自动点击）
- ForegroundService + BOOT_COMPLETED（保活/自启）

## ⚠️ 合规提示

本工具仅在用户主动开启无障碍服务后，于本地识别并点击界面按钮，
不采集、不联网、不上传任何数据，请遵守各应用的使用条款，勿用于违规用途。

## 📂 目录结构

```
app/src/main/java/com/autoskip/helper/
├── App.kt
├── data/          # Room + DataStore + Repository
├── service/       # 无障碍服务 / 保活 / 开机自启 / 匹配引擎
└── ui/            # Compose 界面（首页 / 规则 / 记录）
```
