# 占星骰子

一个占星骰子软件，有我的理解。欢迎 fork！本功法只适用于个人：）我们都是大自然的学生，我的占星理解就是大海。

一个安卓 App：掷出三颗骰子（行星 · 星座 · 宫位），并按规则文件自动生成解读。
解读内容全部来自 `app/src/main/assets/rules.json`，**想换成你自己的解读方法，改这一个文件就行，不用动代码**。

## 一、怎么打包成 APK

### 方式一：Android Studio（推荐，全程图形界面）
1. 安装 [Android Studio](https://developer.android.com/studio)（任意近年版本）
2. 打开本项目文件夹，等待右下角 Sync（首次会自动下载 Gradle 和依赖，需要联网）
3. 菜单 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
4. 完成后点 **locate**，得到 `app-debug.apk`，传到手机安装即可
   （手机需允许“安装未知来源应用”）

### 方式二：命令行
```bash
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

## 二、怎么换成「你的解读方法」

打开 `app/src/main/assets/rules.json`，所有字段说明如下：

| 字段 | 作用 |
|---|---|
| `planets` | 12 个行星各自的关键词 `kw`、核心含义 `core`、水的意象 `water`（这条河的水性）与关键词组 `keywords` |
| `signs` | 12 星座的关键词 `kw`、元素 `element`、模式 `mode`、流水阶段 `phase`（第几境·所属阶段期）、流法意象 `style` |
| `houses` | 12 宫位（键是数字字符串 `"1"`~`"12"`）的关键词 `kw`、名称 `name`、流水阶段 `phase`、归处意象 `domain` |
| `dignity` | 庙旺表：每颗行星的入庙 `domicile`、擢升 `exaltation`、失势 `detriment`、落陷 `fall` 星座 |
| `dignityNotes` | 入庙/擢升/失势/落陷/平五种状态的断语 |
| `aspectNotes` | 星座与宫位按天然对应（白羊↔1宫……）成合0°/邻30°/六合60°/刑90°/拱120°/梅花150°/冲180° 的断语 |
| `template` | 解读模板，占位符在掷骰后自动替换（见下方列表） |
| `overrides` | 针对具体组合的完整解读，优先级最高 |

**template 可用占位符：**
`{question}`（用户的问题）、`{planet}` `{planet_core}` `{planet_water}` `{planet_keywords}` `{planet_kw}`、
`{sign}` `{sign_element}` `{sign_mode}` `{sign_style}` `{sign_phase}` `{sign_kw}`、
`{house}`（数字）`{house_name}` `{house_domain}` `{house_phase}` `{house_kw}`、
`{dignity}`（庙旺行）`{aspect}`（相位行）、`{combo}`（综合句）

**组合规则（默认综合句）：** `「行星」在星座，落于第N宫——` 起句，拼接三相关键词与庙旺/相位断语；
命中 `overrides` 时以 override 文本替代。

**解读体系（当前内置）：一条河**（已定稿，具体内容保密） ——
行星 = 水性、星座 = 流法、宫位 = 归处，三相合成一条河的完整叙事；境数即事情所处的阶段。
具体意象与断语不在文档中公开，全部保存在 `app/src/main/assets/rules.json`（App 运行时读取）。

**overrides 的键格式：** `行星|星座|宫位数字`，例如 `"月亮|巨蟹座|4宫"`。
掷出这个组合时，用 override 文本替代默认综合句。三颗骰子共 12×12×12 = **1728 种组合**，
想写多少写多少，没写的组合会自动走组合规则模板。文件里已放了 10 条示例。

改完规则文件可以校验：`node astro-dice/scripts/roll.js --check` 会逐条列出结构问题
（缺条目、庙旺表星座名写错、overrides 键格式错等）；App 启动时也会自动校验，
不合法会提示具体原因并回退到内置简版规则。

## 二·五、AI 综合解读（Moonshot）

掷骰后 App 会先把本地规则解读（骰面/关键词/庙旺/相位/规则综合）显示出来，
随后调用 Moonshot（Kimi）API 生成 AI 综合段并自动替换；网络不可用或失败时保留本地规则综合。

- **在 App 里改**：界面下方「AI 设置」卡片可直接修改 **API 地址 / API Key / 模型**，
  点 **保存设置** 后生效（保存在本机，不需要重新打包）。
- **测试 API**：点 **测试 API**，会先保存当前设置，然后依次验证
  `GET {地址}/models`（key 与地址是否可用）和一次最小对话（模型是否可用），结果显示在按钮下方。
- 首次安装的默认值来自打包时的 `local.properties`（git 已忽略）：
  ```properties
  moonshot.api.key=你的key
  moonshot.model=kimi-k2.6   # 可换成账户可用的其他模型
  ```
- 未配置 key 时 App 只输出本地规则综合，不影响其他功能。
- 请求参数：`thinking` 关闭（低延迟），`max_tokens` 400，超时 10s/25s。

## 三、PicoClaw 插件

`astro-dice/` 是配套的 [PicoClaw](https://github.com/sipeed/picoclaw) 技能插件（目录名即技能名），同一套「流水十二境」规则：
在 PicoClaw 里对 AI 助手说「掷占星骰子」（或让它运行 `node scripts/roll.js [问题]`）即可掷骰解读。
规则文件与安卓 App 共用一份（`assets/rules.json`），改法相同。

安装（把技能放进 PicoClaw 的 workspace）：

```bash
cp -r astro-dice/. /root/.picoclaw/workspace/skills/astro-dice/
```

## 项目结构

```
app/src/main/
├── assets/rules.json                 ← 解读规则（改这里）
├── java/com/astrodice/app/MainActivity.java
└── res/                              ← 界面布局、颜色、图标
astro-dice/                          ← PicoClaw 技能插件（目录名 = 技能名）
├── SKILL.md                          ← 技能说明（触发与用法）
├── scripts/roll_dice.sh              ← 入口包装脚本（调用同目录 roll.js）
├── scripts/roll.js                   ← 掷骰+解读脚本（node）
└── assets/rules.json                 ← 与 App 相同的规则（含保密的解读内容）
```

---

版权所有 © 2026 Criz，基于 [AGPL-3.0 协议](LICENSE) 开源。
