#!/usr/bin/env node
// 占星骰子：掷行星+星座+宫位，按「一条河」流水十二程体系输出解读。
// Copyright (c) 2026 Criz, licensed under the MIT License.
// 用法: node roll.js [想问的问题]
const fs = require('fs');
const path = require('path');

const PLANETS = ['太阳', '月亮', '水星', '金星', '火星', '木星', '土星', '天王星', '海王星', '冥王星', '北交点', '南交点'];
const SIGNS = ['白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座', '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座'];

// (宫位 - 星座序) mod 12 → 相位名，对应 0°/30°/60°/90°/120°/150°/180°
const ASPECTS = ['合相', '邻宫', '六合', '刑', '拱', '梅花', '冲', '梅花', '拱', '刑', '六合', '邻宫'];

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
  const note = (rules.dignityNotes || {})[st] || '';
  return planet + '在' + sign + '：' + st + (note ? '——' + note : '');
}

function aspectInfo(sign, houseNum) {
  const idx = SIGNS.indexOf(sign) + 1;
  const d = ((houseNum - idx) % 12 + 12) % 12;
  return { name: ASPECTS[d], degree: Math.min(d, 12 - d) * 30 };
}

function aspectLine(rules, sign, houseNum) {
  const a = aspectInfo(sign, houseNum);
  const note = (rules.aspectNotes || {})[a.name] || '';
  return sign + '与第' + houseNum + '宫成' + a.name + '（' + a.degree + '°）' + (note ? '：' + note : '');
}

// 组合规则：「行星」在星座，落于第N宫——三相关键词 + 庙旺/相位合成断语；有 overrides 则以其为准
function ruleCombo(rules, planet, sign, houseNum, p, s, h) {
  const key = [planet, sign, houseNum + '宫'].join('|');
  if (rules.overrides && rules.overrides[key]) return rules.overrides[key];
  const pKw = p.kw || (p.keywords || [])[0] || '';
  const sKw = s.kw || '';
  const hKw = h.kw || '';
  const dn = (rules.dignityNotes || {})[dignityStatus(rules, planet, sign)] || '';
  const an = (rules.aspectNotes || {})[aspectInfo(sign, houseNum).name] || '';
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
    .replace('{planet_kw}', p.kw || (p.keywords || [])[0] || '')
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

function main() {
  const rules = loadRules();
  const question = process.argv.slice(2).join(' ').trim();
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

module.exports = { build, loadRules, dignityStatus, aspectInfo };
