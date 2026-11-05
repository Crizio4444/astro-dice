#!/usr/bin/env node
// 占星骰子：掷行星+星座+宫位，按「一条河」流水十二境体系输出解读。
// Copyright (c) 2026 Criz, licensed under the MIT License.
// 用法: node roll.js [想问的问题]
//      node roll.js --check   校验 rules.json 结构，不掷骰
const fs = require('fs');
const path = require('path');

const PLANETS = ['太阳', '月亮', '水星', '金星', '火星', '木星', '土星', '天王星', '海王星', '冥王星', '莉莉丝', '普里阿普斯'];
const SIGNS = ['白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座', '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座'];

// (宫位 - 星座序) mod 12 → 相位名，对应 0°/30°/60°/90°/120°/150°/180°
const ASPECTS = ['合相', '邻宫', '六合', '刑', '拱', '梅花', '冲', '梅花', '拱', '刑', '六合', '邻宫'];

// rules.json 缺 dignityNotes/aspectNotes 时的内置兜底断语（与安卓端一致）
const DEFAULT_DIGNITY_NOTES = {
  '入庙': '得势：水在自家河道，助力天然，放手去流。',
  '擢升': '得势：水被高高托起，势能正盛，宜乘势而上。',
  '失势': '受制：水入陌生河床，处处别扭，宜减速慎行。',
  '落陷': '受制：水势低徊无力，先固本蓄力，勿强求。',
  '平':   '无奇：水性如常，顺逆皆看流法与归处的配合。'
};
const DEFAULT_ASPECT_NOTES = {
  '合相': '同位共振：流法与归处同境，现路即去路，一顺百顺。',
  '邻宫': '相邻相承：只差一境，顺势微调即达。',
  '六合': '六合相扶：来去相就，水到渠成。',
  '刑':   '刑克相顶：流法与归处相别，过程有摩擦，宜绕行缓图。',
  '拱':   '拱照同气：流法与归处相和，来去皆顺。',
  '冲':   '对冲相张：流法与归处正对，张力最大，成在破立之间的平衡。',
  '梅花': '梅花相错：流法与归处错开半境，需调整适应，勿硬拧。'
};

function loadRules() {
  const p = path.join(__dirname, '..', 'assets', 'rules.json');
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function dignityStatus(rules, planet, sign) {
  const d = (rules.dignity || {})[planet];
  if (!d) return '平';
  if ((d.domicile || []).includes(sign)) return '入庙';
  if (d.exaltation === sign) return '擢升';
  if ((d.detriment || []).includes(sign)) return '失势';
  if (d.fall === sign) return '落陷';
  return '平';
}

function dignityLine(rules, planet, sign) {
  const st = dignityStatus(rules, planet, sign);
  const note = (rules.dignityNotes || {})[st] || DEFAULT_DIGNITY_NOTES[st] || '';
  return planet + '在' + sign + '：' + st + (note ? '——' + note : '');
}

function aspectInfo(sign, houseNum) {
  const idx = SIGNS.indexOf(sign) + 1;
  const d = ((houseNum - idx) % 12 + 12) % 12;
  return { name: ASPECTS[d], degree: Math.min(d, 12 - d) * 30 };
}

function aspectLine(rules, sign, houseNum) {
  const a = aspectInfo(sign, houseNum);
  const note = (rules.aspectNotes || {})[a.name] || DEFAULT_ASPECT_NOTES[a.name] || '';
  return sign + '与第' + houseNum + '宫成' + a.name + '（' + a.degree + '°）' + (note ? '：' + note : '');
}

// 组合规则：「行星」在星座，落于第N宫——三相关键词 + 庙旺/相位合成断语；有 overrides 则以其为准
function ruleCombo(rules, planet, sign, houseNum, p, s, h) {
  const key = [planet, sign, houseNum + '宫'].join('|');
  if (rules.overrides && rules.overrides[key]) return rules.overrides[key];
  const pKw = p.kw || (p.keywords || []).join('、');
  const sKw = s.kw || '';
  const hKw = h.kw || '';
  const st = dignityStatus(rules, planet, sign);
  const dn = (rules.dignityNotes || {})[st] || DEFAULT_DIGNITY_NOTES[st] || '';
  const aspectName = aspectInfo(sign, houseNum).name;
  const an = (rules.aspectNotes || {})[aspectName] || DEFAULT_ASPECT_NOTES[aspectName] || '';
  return '「' + planet + '」在' + sign + '，落于第' + houseNum + '宫——'
    + pKw + '之流，' + sKw + '而往，归于' + hKw + '。' + dn + an;
}

function build(rules, planet, sign, houseNum, question) {
  const p = rules.planets[planet];
  const s = rules.signs[sign];
  const h = rules.houses[houseNum];
  if (!p || !s || !h) throw new Error('rules.json 缺少条目: ' + [planet, sign, houseNum].join('/'));
  const tpl = rules.template;
  if (!tpl) throw new Error('rules.json 缺少 template');
  return tpl
    .replace('{question}', question ? '问：' + question + '\n\n' : '')
    .replace('{planet}', planet)
    .replace('{planet_core}', p.core || '')
    .replace('{planet_water}', p.water || '')
    .replace('{planet_keywords}', (p.keywords || []).join('、'))
    .replace('{planet_kw}', p.kw || (p.keywords || []).join('、'))
    .replace('{sign}', sign)
    .replace('{sign_element}', s.element || '')
    .replace('{sign_mode}', s.mode || '')
    .replace('{sign_style}', s.style || '')
    .replace('{sign_phase}', s.phase || '')
    .replace('{sign_kw}', s.kw || '')
    .replace('{house}', houseNum)
    .replace('{house_name}', h.name || '')
    .replace('{house_domain}', h.domain || '')
    .replace('{house_phase}', h.phase || '')
    .replace('{house_kw}', h.kw || '')
    .replace('{dignity}', dignityLine(rules, planet, sign))
    .replace('{aspect}', aspectLine(rules, sign, parseInt(houseNum, 10)))
    .replace('{combo}', ruleCombo(rules, planet, sign, houseNum, p, s, h));
}

// 校验 rules.json：结构完整、引用合法（与安卓端校验项一致）。返回问题列表，空数组为通过。
function checkRules(rules) {
  const problems = [];
  const requireEntries = (obj, keys, label) => {
    if (!obj || typeof obj !== 'object') { problems.push('缺少 ' + label); return; }
    for (const k of keys) if (!obj[k]) problems.push(label + ' 缺少条目：' + k);
  };
  requireEntries(rules.planets, PLANETS, 'planets');
  requireEntries(rules.signs, SIGNS, 'signs');
  requireEntries(rules.houses, Array.from({ length: 12 }, (_, i) => String(i + 1)), 'houses');
  if (!rules.template) problems.push('缺少 template');
  for (const [planet, d] of Object.entries(rules.dignity || {})) {
    if (!PLANETS.includes(planet)) { problems.push('dignity 含未知行星：' + planet); continue; }
    if (!d || typeof d !== 'object') continue;
    for (const sign of [].concat(d.domicile || [], d.detriment || [])) {
      if (!SIGNS.includes(sign)) problems.push('dignity.' + planet + ' 含未知星座：' + sign);
    }
    for (const f of ['exaltation', 'fall']) {
      if (d[f] && !SIGNS.includes(d[f])) problems.push('dignity.' + planet + '.' + f + ' 含未知星座：' + d[f]);
    }
  }
  for (const key of Object.keys(rules.overrides || {})) {
    const parts = key.split('|');
    if (parts.length !== 3) { problems.push('overrides 键格式错误（应为 行星|星座|N宫）：' + key); continue; }
    if (!PLANETS.includes(parts[0])) problems.push('overrides 含未知行星：' + key);
    if (!SIGNS.includes(parts[1])) problems.push('overrides 含未知星座：' + key);
    if (!/^(?:[1-9]|1[0-2])宫$/.test(parts[2])) problems.push('overrides 宫位写法错误（应为 1宫~12宫）：' + key);
  }
  return problems;
}

function main() {
  const args = process.argv.slice(2);
  const rules = loadRules();
  if (args[0] === '--check') {
    const problems = checkRules(rules);
    if (problems.length) {
      console.error('rules.json 校验未通过：');
      for (const p of problems) console.error('  ✗ ' + p);
      process.exit(1);
    }
    const count = (o) => Object.keys(o || {}).length;
    console.log('✓ rules.json 校验通过：行星 ' + count(rules.planets)
      + ' / 星座 ' + count(rules.signs)
      + ' / 宫位 ' + count(rules.houses)
      + ' / overrides ' + count(rules.overrides));
    return;
  }
  const question = args.join(' ').trim();
  const pick = (arr) => arr[(Math.random() * arr.length) | 0];
  const planet = pick(PLANETS);
  const sign = pick(SIGNS);
  const houseNum = String(1 + ((Math.random() * 12) | 0));
  console.log(build(rules, planet, sign, houseNum, question));
}

if (require.main === module) {
  try {
    main();
  } catch (e) {
    console.error('astro-dice 出错: ' + e.message);
    process.exit(1);
  }
}

module.exports = { build, loadRules, checkRules, dignityStatus, aspectInfo };
