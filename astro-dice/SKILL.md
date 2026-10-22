---
name: astro-dice
description: 占星骰子占卜。投掷行星+星座+宫位三颗骰子，按「一条河」流水十二程体系生成完整解读（水性/流法/归处三相，36关键词+庙旺+相位），配置 MOONSHOT_API_KEY 后调用 Moonshot 模型生成 AI 综合解读。Use when the user asks to roll astrology dice, 占星骰子, 掷骰占卜, or wants a cosmic reading / 宇宙指引.
metadata: {"nanobot":{"emoji":"🎲","requires":{"bins":["node"]}}}
---

# astro-dice 占星骰子

掷出三颗骰子（行星 · 星座 · 宫位），按「一条河」流水十二程体系输出完整解读。

## 使用方法

```bash
scripts/roll_dice.sh [想问的问题]
# 或
node scripts/roll.js [想问的问题]
# 校验 rules.json（改完规则文件后跑一次，有问题会逐条列出）
node scripts/roll.js --check
# 测试 AI 接口连通性（GET /models + 最小对话，需先设置 MOONSHOT_API_KEY）
node scripts/roll.js --test-api
# 禁用 AI 综合，本地规则直出（即使配置了 MOONSHOT_API_KEY）
node scripts/roll.js --no-ai [问题]
```

- `scripts/roll_dice.sh` 为入口包装脚本（须可执行），内部调用同目录的 `roll.js`，从任意目录调用均可。
- 不带参数直接掷骰；带参数时把问题写进解读（问题可含空格，不需要引号）。

## 输出结构（rules.json version 5）

脚本输出五段：

1. **【骰面】** 行星 · 星座 · 第N宫
2. **【关键词】** 行星kw · 星座kw · 宫位kw（36 面各配 1 词，见 `assets/rules.json` 的 `kw` 字段）
3. **【庙旺】** 行星在星座的入庙/擢升/失势/落陷/平（`dignity` + `dignityNotes` 表）
4. **【相位】** 星座与宫位按天然对应（白羊↔1宫……）相距角度：合0°/邻30°/六合60°/刑90°/拱120°/梅花150°/冲180°（`aspectNotes` 表）
5. **【综合】** 配置了 `MOONSHOT_API_KEY` 时由 Moonshot 模型生成（AI 综合）；未配置 key、带 `--no-ai` 或调用失败时，为本地规则综合（有 overrides 用 overrides，否则按「组合规则」模板生成），失败时末尾附「（AI 解读不可用，以上为本地规则综合）」提示

## AI 综合解读（Moonshot）

- 设置环境变量 `MOONSHOT_API_KEY` 后，掷骰时自动调用 Moonshot（Kimi）兼容接口生成【综合】段，无需你再撰写；未配置 key 时只输出本地规则综合，不影响其他功能；调用失败自动回退本地规则综合并附提示。
- `MOONSHOT_API_BASE`（默认 `https://api.moonshot.cn/v1`）与 `MOONSHOT_MODEL`（默认 `kimi-k2.6`）可换接口地址与模型，参数与安卓端一致：thinking 关闭、`max_tokens` 400、超时 10s/25s。

## 呈现规则

- 五段（骰面/关键词/庙旺/相位/综合）**原样呈现**，不要改写、补掷或另解——【综合】已是模型/规则成文的断语。
- 用户要求再深入或换种说法时，基于已掷出的骰面与关键词展开，保持「一条河」文风（行星=水性、星座=流法、宫位=归处），但不要在对话中展开全部保密文案。

## 解读体系（一条河，已定稿保密）

行星 = 水性、星座 = 流法、宫位 = 归处，三相合成一条河的完整叙事；程数即事情所处的阶段。
骰面文本与断语属于作者保密内容，不在技能文档中展开——数据与意象以 `assets/rules.json` 为准，可替换为自定义解读法。

---

作者：Criz · 许可证：MIT（见仓库 LICENSE）
