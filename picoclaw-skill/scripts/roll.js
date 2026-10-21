#!/usr/bin/env node
// 占星骰子：掷行星+星座+宫位，按「一条河」流水十二程体系输出解读。
// Copyright (c) 2026 Criz, licensed under the MIT License.
// 用法: node roll.js [想问的问题]
const fs = require('fs');
const path = require('path');

const PLANETS = ['太阳', '月亮', '水星', '金星', '火星', '木星', '土星', '天王星', '海王星', '冥王星', '北交点', '南交点'];
const SIGNS = ['白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座', '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座'];

function loadRules() {
  const p = path.join(__dirname, '..', 'assets', 'rules.json');
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function build(rules, planet, sign, houseNum, question) {
  const p = rules.planets[planet];
  const s = rules.signs[sign];
  const h = rules.houses[houseNum];
  if (!p || !s || !h) throw new Error('rules.json 缺少条目: ' + [planet, sign, houseNum].join('/'));
  const kws = (p.keywords || []).join('、');
  const key = [planet, sign, houseNum + '宫'].join('|');
  const planetNature = p.water || p.core;
  const signPart = s.phase ? sign + '（' + s.phase + '）' : sign;
  const housePart = h.phase ? h.name + '（' + h.phase + '）' : h.name;
  const combo = (rules.overrides && rules.overrides[key]) ||
    '如一条河：「' + planet + '」之性是' + planetNature +
    '；它流作' + signPart + '，归于' + housePart +
    '——' + h.domain + '。水性、流法、归处三相合一：程数两相对照，此事的来路、现况与去处俱在其中。';

  const out = [];
  if (question) out.push('问：' + question, '');
  out.push('三骰掷出，各居一程，共成一条河。', '');
  out.push('【水性·行星】' + planet + '——' + (p.water || p.core) + '（' + p.core + '；关键词：' + kws + '）');
  out.push('【流法·星座】' + sign + '（' + s.element + '·' + s.mode + '），流水' + (s.phase || '') + '：' + s.style);
  out.push('【归处·宫位】' + h.name + '，流水' + (h.phase || '') + '：' + h.domain);
  out.push('', '【骰面】' + planet + ' · ' + sign + ' · 第' + houseNum + '宫', '');
  out.push('【综合·三相】' + combo);
  return out.join('\n');
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

module.exports = { build, loadRules };
