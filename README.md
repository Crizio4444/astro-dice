# 占星骰子

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
| `planets` | 12 个行星各自的核心含义 `core`、水的意象 `water`（这条河的水性）与关键词 `keywords`（用顿号拼进解读） |
| `signs` | 12 星座的元素 `element`、模式 `mode`、流水阶段 `phase`（第几程·所属阶段期）、流法意象 `style` |
| `houses` | 12 宫位（键是数字字符串 `"1"`~`"12"`）的名称 `name`、流水阶段 `phase`、归处意象 `domain` |
| `template` | 解读模板，占位符在掷骰后自动替换（见下方列表） |
| `overrides` | 针对具体组合的完整解读，优先级最高 |

**template 可用占位符：**
`{question}`（用户的问题）、`{planet}` `{planet_core}` `{planet_water}` `{planet_keywords}`、
`{sign}` `{sign_element}` `{sign_mode}` `{sign_style}` `{sign_phase}`、
`{house}`（数字）`{house_name}` `{house_domain}` `{house_phase}`、`{combo}`（综合句）

**解读体系（当前内置）：一条河**（已定稿，具体内容保密） ——
行星 = 水性、星座 = 流法、宫位 = 归处，三相合成一条河的完整叙事；程数即事情所处的阶段。
具体意象与断语不在文档中公开，全部保存在 `app/src/main/assets/rules.json`（App 运行时读取）。

**overrides 的键格式：** `行星|星座|宫位数字`，例如 `"月亮|巨蟹座|4宫"`。
掷出这个组合时，用 override 文本替代默认综合句。三颗骰子共 12×12×12 = **1728 种组合**，
想写多少写多少，没写的组合会自动走模板拼接。文件里已放了 8 条入庙组合作为示例。

## 三、手机上不连电脑也能换规则

1. 把改好的 `rules.json` 发到手机（微信/QQ/邮件都行），保存到「下载」文件夹
2. 打开 App → 点 **导入规则文件** → 选中 `rules.json`
3. 想换回内置规则：点 **恢复默认规则**

> 注意：JSON 里不能有注释，逗号多了或少了都会导致解析失败（App 会提示并回退到内置简版规则）。

## 四、PicoClaw 插件

`picoclaw-skill/` 是配套的 [PicoClaw](https://github.com/sipeed/picoclaw) 技能插件，同一套「流水十二程」规则：
在 PicoClaw 里对 AI 助手说「掷占星骰子」（或让它运行 `node scripts/roll.js [问题]`）即可掷骰解读。
规则文件与安卓 App 共用一份（`assets/rules.json`），改法相同。

安装（把技能放进 PicoClaw 的 workspace）：

```bash
cp -r picoclaw-skill/. /root/.picoclaw/workspace/skills/astro-dice/
```

## 项目结构

```
app/src/main/
├── assets/rules.json                 ← 解读规则（改这里）
├── java/com/astrodice/app/MainActivity.java
└── res/                              ← 界面布局、颜色、图标
picoclaw-skill/                       ← PicoClaw 技能插件
├── SKILL.md                          ← 技能说明（触发与用法）
├── roll_dice.sh                      ← 入口包装脚本（调用下面的 roll.js）
├── scripts/roll.js                   ← 掷骰+解读脚本（node）
└── assets/rules.json                 ← 与 App 相同的规则（含保密的解读内容）
```

---

版权所有 © 2026 Criz，基于 [MIT 协议](LICENSE) 开源。
