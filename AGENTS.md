# AGENTS.md — 占星骰子 AstroDice

帮助后续 agent 在本仓库高效工作的非显性知识。项目本身很小（一个单 Activity 安卓 App + 一个 PicoClaw 技能插件），但有几处容易踩坑的约定。

## 项目概况

- **安卓 App**（`app/`，Java、单 Activity `MainActivity.java` 约 950 行）：掷行星·星座·宫位三颗骰子，按「一条河」流水十二境体系生成解读；掷骰后本地规则解读先显示，再调 Moonshot（Kimi）API 生成 AI 综合段自动替换（失败则保留本地结果）。
- **PicoClaw 技能插件**（`astro-dice/`）：与 App 共用同一套规则，用 node 脚本掷骰输出五段（骰面/关键词/庙旺/相位/综合），纯本地、**不再调 AI**（AI 综合由宿主 AI 按 SKILL.md 的呈现规则撰写）。
- 两套代码各自独立实现同一套解读逻辑（Java 端与 node 端的 dignity/相位/综合算法必须语义一致），共享的唯一数据是 `rules.json`。

## 关键命令

```bash
./gradlew assembleDebug    # 打 debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # 打签名 release 包（需要 astrodice.keystore + local.properties 口令）
node astro-dice/scripts/roll.js --check   # 校验 rules.json 结构（改完规则文件必跑）
node astro-dice/scripts/roll.js [问题]    # 命令行掷骰
astro-dice/scripts/roll_dice.sh [问题]    # 插件入口包装脚本（须可执行，任意工作目录可调用）
```

- 构建需要 JDK 17 与 Android SDK（compileSdk 35、buildTools 34.0.0、AGP 8.5.2、Gradle 8.7 wrapper），首次构建需联网下载依赖。
- **本仓库没有测试、没有 lint 配置**。改动 rules.json 后的验证手段就是 `roll.js --check`；App 启动时也会做同样的校验（缺条目 / dignity 含未知星座 / overrides 键格式错），不合法会 toast 具体原因并回退内置简版规则，而不是崩溃。

## 最重要的约定：两份 rules.json 必须同步

`app/src/main/assets/rules.json` 与 `astro-dice/assets/rules.json` 是**内容相同的两份拷贝**（解读文案属于作者保密内容，只存在这里）。改其中一份必须同步另一份，git 历史里一直是成对修改的。校验器只检查结构，不检查两份是否一致——改完请 diff 确认。

- `rules.json` 顶层键：`version`（当前 5）、`planets`（12）、`signs`（12）、`houses`（键是字符串 `"1"`~`"12"`）、`dignity`、`dignityNotes`、`aspectNotes`、`template`（带 `{planet}` 等占位符）、`overrides`。
- overrides 键格式：`行星|星座|N宫`，如 `"月亮|巨蟹座|4宫"`。
- 行星集合固定为 12 个：太阳 月亮 水星 金星 火星 木星 土星 天王星 海王星 冥王星 莉莉丝 普里阿普斯（含莉莉丝/普里阿普斯，不是南北交点；两者在 dignity 表中为空对象）。这三个名字列表（PLANETS/SIGNS/相位表）在 `MainActivity.java:40-57` 和 `roll.js:9-13` 各硬编码一份，语义必须一致。
- 相位按天然对应（白羊↔1宫……双鱼↔12宫）取 `(宫位 - 星座序) mod 12`，0°合/30°邻/60°六合/90°刑/120°拱/150°梅花/180°冲。

## 配置与密钥（都不入库）

- `local.properties`（gitignored）注入构建期默认值：`moonshot.api.key`、`moonshot.model`（默认 kimi-k2.6）、release 签名口令（`keystore.store.password` / `keystore.key.alias` / `keystore.key.password`，keystore 文件 `astrodice.keystore` 在仓库根、已 gitignore，缺它时只能打 debug 包）。
- 这些值经 `buildConfigField` 变成 `BuildConfig.MOONSHOT_API_KEY / MOONSHOT_MODEL`；运行时用户在 App 内「AI 设置」卡片改的配置存 SharedPreferences（`astro_dice_prefs`），优先级高于 BuildConfig。
- 未配置 key 时 App 只输出本地规则综合，其余功能不受影响——改代码时不要破坏这条回退路径。

## 代码风格与架构

- App 是纯 Java 17、**无 AndroidX**（`android.useAndroidX=false`）、无第三方依赖，只用 `android.app.Activity` + 原生 widget 和 `org.json`。网络用 `HttpURLConnection` 手写，线程用 `Handler`/`Runnable`，不要引入 OkHttp/Room 之类。
- UI 全部在 `res/layout/activity_main.xml`（一个 378 行的 ScrollView 大布局）+ `res/drawable/` 几个 shape XML；主题见 `res/values/themes.xml`，整体黑白主题。字符串基本硬编码在 Java/布局里，`strings.xml` 没什么内容。
- 解读引擎：启动时 `loadRules()` 校验 rules.json 并预建查表缓存（planetMap/signMap/houseMap、12×12 庙旺矩阵、12×12 相位矩阵），之后掷骰全流程查表取数，不要在热路径上重新解析 JSON。
- 掷骰随机性分两层（`MainActivity.java:73-74`）：滚动动画的换面用普通 `Random`（纯视觉），**最终结果在动画停定那一刻用 `SecureRandom` 一次性抽 1~1728 再拆成三颗骰**——不要在点击瞬间预抽。动画减速节奏：间隔 70ms 起逐帧 +9ms 至 160ms 封顶。
- AI 请求参数：system prompt 硬编码在 `MainActivity.java:59`（AI_SYSTEM）、`thinking` 关闭、`max_tokens` 400、超时 10s/25s；插件端的 AI 调用已按 commit 3c7976a 回退删除，不要恢复。
- 历史记录存 SharedPreferences（上限 100 条），AI 综合返回后写回对应条目并标 ✦AI；点历史条目可还原骰面。

## 文档与术语

- 说明文档（README、SKILL）遵循「内容保密」原则：只写字段结构和用法，**不展开 rules.json 里的具体意象与断语文案**；也别在提交信息或注释里泄露。
- 术语已统一为「流水十二境 / 第N境」（早前叫「十二程」，见 commit 8fcfd02），新内容不要混用旧词。
- 文案语言：界面、文档、提交信息均为简体中文。
- 提交信息风格（参考 `git log`）：单行完整中文句，概括「做了什么 + 为什么/效果」，常含具体细节（字号、色值、参数）。

## 修改清单

- 改解读文案 → 两份 rules.json + `node astro-dice/scripts/roll.js --check`。
- 改掷骰/解读逻辑 → Java 与 roll.js 同步对齐（含内置兜底断语 DEFAULT_DIGNITY_NOTES/DEFAULT_ASPECT_NOTES 两处）。
- 改界面 → `activity_main.xml` + `MainActivity.java` 的 findViewById 段一起改。
