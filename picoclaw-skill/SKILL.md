---
name: astro-dice
description: 占星骰子占卜。投掷行星+星座+宫位三颗骰子，按「一条河」流水十二程体系生成完整解读（水性/流法/归处三相）。Use when the user asks to roll astrology dice, 占星骰子, 掷骰占卜, or wants a cosmic reading / 宇宙指引.
metadata: {"nanobot":{"emoji":"🎲","requires":{"bins":["node"]}}}
---

# astro-dice 占星骰子

掷出三颗骰子（行星 · 星座 · 宫位），按「一条河」流水十二程体系输出完整解读。

## 使用方法

```bash
./roll_dice.sh [想问的问题]
# 或
node scripts/roll.js [想问的问题]
```

- `roll_dice.sh` 为入口包装脚本（须可执行），内部调用 `scripts/roll.js`，从任意目录调用均可。
- 不带参数直接掷骰；带参数时把问题写进解读（问题可含空格，不需要引号）。
- 脚本的输出即完整解读，**原样呈现**给用户，不要改写、补掷或自行另解。

## 解读体系（一条河，已定稿保密）

行星 = 水性、星座 = 流法、宫位 = 归处，三相合成一条河的完整叙事；程数即事情所处的阶段。
具体意象与断语属于作者保密内容，不在技能文档中展开——**脚本输出即权威解读，原样呈现，不要自行解释、补充或改写**。
骰面文本与断语全部在 `assets/rules.json`（JSON 不支持注释，可替换为自定义解读法）。

---

作者：Criz · 许可证：MIT（见仓库 LICENSE）
