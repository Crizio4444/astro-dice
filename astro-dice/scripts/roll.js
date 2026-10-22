#!/usr/bin/env node
// 占星骰子：掷行星+星座+宫位，按「一条河」流水十二程体系输出解读。
// Copyright (c) 2026 Criz, licensed under the MIT License.
// 用法: node roll.js [想问的问题]
//      node roll.js --no-ai [问题]   禁用 AI 综合（即使配置了 MOONSHOT_API_KEY）
//      node roll.js --check          校验 rules.json 结构，不掷骰
//      node roll.js --test-api       测试 AI 接口（GET /models + 最小对话）
// AI 综合（与安卓端一致）：设置 MOONSHOT_API_KEY 后，掷骰时自动调用 Moonshot 兼容
// 接口生成【综合】段；未配置 key 或调用失败时，回退本地规则综合。
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

const PLANETS = ['太阳', '月亮', '水星', '金星', '火星', '木星', '土星', '天王星', '海王星', '冥王星', '北交点', '南交点'];
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
  '合相': '同位共振：流法与归处同程，现路即去路，一顺百顺。',
  '邻宫': '相邻相承：只差一程，顺势微调即达。',
  '六合': '六合相扶：来去相就，水到渠成。',
  '刑':   '刑克相顶：流法与归处相别，过程有摩擦，宜绕行缓图。',
  '拱':   '拱照同气：流法与归处相和，来去皆顺。',
  '冲':   '对冲相张：流法与归处正对，张力最大，成在破立之间的平衡。',
  '梅花': '梅花相错：流法与归处错开半程，需调整适应，勿硬拧。'
};

// AI 综合配置，与安卓端 MainActivity 一致
const DEFAULT_API_BASE = 'https://api.moonshot.cn/v1';
const DEFAULT_API_MODEL = 'kimi-k2.6';
const AI_SYSTEM =
  '你是「占星骰子·一条河」体系的解读者。三颗骰子：行星=水性，星座=流法，宫位=归处。' +
  '综合段必须以「行星在星座，落于第N宫」起句，把三颗骰子的意象融成一条河的叙事，' +
  '语气简净如断语，结尾给出一句宜/忌建议，共100~180字，只输出解读正文。';
const AI_MAX_TOKENS = 400;
const AI_FALLBACK_NOTE = '（AI 解读不可用，以上为本地规则综合）';
const CONNECT_TIMEOUT_MS = 10000;
const READ_TIMEOUT_MS = 25000;

function loadRules() {
  const p = path.join(__dirname, '..', 'assets', 'rules.json');
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function kwOf(entry) {
  return entry.kw || (entry.keywords || []).join('、');
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
  const st = dignityStatus(rules, planet, sign);
  const dn = (rules.dignityNotes || {})[st] || DEFAULT_DIGNITY_NOTES[st] || '';
  const aspectName = aspectInfo(sign, houseNum).name;
  const an = (rules.aspectNotes || {})[aspectName] || DEFAULT_ASPECT_NOTES[aspectName] || '';
  return '「' + planet + '」在' + sign + '，落于第' + houseNum + '宫——'
    + kwOf(p) + '之流，' + kwOf(s) + '而往，归于' + kwOf(h) + '。' + dn + an;
}

// 模板填充（question 为掷骰时问的问题，可为空串）
function fillTemplate(rules, tpl, planet, sign, houseNum, question) {
  const p = rules.planets[planet];
  const s = rules.signs[sign];
  const h = rules.houses[houseNum];
  if (!p || !s || !h) throw new Error('rules.json 缺少条目: ' + [planet, sign, houseNum].join('/'));
  return tpl
    .replace('{question}', question ? '问：' + question + '\n\n' : '')
    .replace('{planet}', planet)
    .replace('{planet_core}', p.core || '')
    .replace('{planet_water}', p.water || '')
    .replace('{planet_keywords}', (p.keywords || []).join('、'))
    .replace('{planet_kw}', kwOf(p))
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
    .replace('{aspect}', aspectLine(rules, sign, parseInt(houseNum, 10)));
}

// {combo} 之前的部分：问/骰面/关键词/庙旺/相位，与安卓端 buildHead 一致
function buildHead(rules, planet, sign, houseNum, question) {
  const tpl = rules.template;
  if (!tpl) throw new Error('rules.json 缺少 template');
  const cut = tpl.indexOf('{combo}');
  let head = fillTemplate(rules, cut >= 0 ? tpl.slice(0, cut) : tpl, planet, sign, houseNum, question);
  if (cut < 0) head += '\n\n';
  return head;
}

function build(rules, planet, sign, houseNum, question) {
  if (!rules.template) throw new Error('rules.json 缺少 template');
  return fillTemplate(rules, rules.template, planet, sign, houseNum, question)
    .replace('{combo}', ruleCombo(rules, planet, sign, houseNum, rules.planets[planet], rules.signs[sign], rules.houses[houseNum]));
}

// ---------- AI 综合（Moonshot 兼容接口，行为与安卓端一致） ----------

// 环境变量配置：MOONSHOT_API_KEY / MOONSHOT_API_BASE / MOONSHOT_MODEL
function apiConfig(env) {
  const e = env || process.env;
  const key = (e.MOONSHOT_API_KEY || '').trim();
  let base = (e.MOONSHOT_API_BASE || DEFAULT_API_BASE).trim();
  while (base.endsWith('/')) base = base.slice(0, -1);
  const model = (e.MOONSHOT_MODEL || DEFAULT_API_MODEL).trim() || DEFAULT_API_MODEL;
  return { key, base, model };
}

// 连接 10s / 读取 25s（与安卓端 setConnectTimeout/setReadTimeout 一致）
function httpJson(method, urlStr, key, payload) {
  return new Promise((resolve, reject) => {
    const lib = urlStr.startsWith('https:') ? https : http;
    let settled = false;
    const fail = (err) => { if (!settled) { settled = true; reject(err); } };
    const req = lib.request(urlStr, {
      method: method,
      headers: {
        'Authorization': 'Bearer ' + key,
        'Content-Type': 'application/json; charset=utf-8'
      }
    }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('error', fail);
      res.on('end', () => {
        if (settled) return;
        settled = true;
        const text = Buffer.concat(chunks).toString('utf8');
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(text)); }
          catch (e) { reject(new Error('响应不是合法 JSON')); }
        } else {
          let msg = text;
          try { const j = JSON.parse(text); msg = (j.error && j.error.message) || text; } catch (e) {}
          reject(new Error('HTTP ' + res.statusCode + (msg ? '：' + msg : '')));
        }
      });
    });
    req.on('socket', (socket) => {
      socket.setTimeout(READ_TIMEOUT_MS, () => req.destroy(new Error('读取超时（' + READ_TIMEOUT_MS / 1000 + 's）')));
      if (!socket.connecting) return;
      const connTimer = setTimeout(() => req.destroy(new Error('连接超时（' + CONNECT_TIMEOUT_MS / 1000 + 's）')), CONNECT_TIMEOUT_MS);
      socket.once('connect', () => clearTimeout(connTimer));
      socket.once('error', () => clearTimeout(connTimer));
    });
    req.on('error', fail);
    if (payload != null) req.write(JSON.stringify(payload));
    req.end();
  });
}

function chatPayload(model, userPrompt, maxTokens) {
  return {
    model: model,
    messages: [
      { role: 'system', content: AI_SYSTEM },
      { role: 'user', content: userPrompt }
    ],
    max_tokens: maxTokens,
    thinking: { type: 'disabled' }
  };
}

// AI 用户提示词，结构与安卓端 buildAiPrompt 相同
function buildAiPrompt(rules, planet, sign, houseNum, question, reference) {
  const p = rules.planets[planet];
  const s = rules.signs[sign];
  const h = rules.houses[houseNum];
  if (!p || !s || !h) throw new Error('rules.json 缺少条目: ' + [planet, sign, houseNum].join('/'));
  const lines = [];
  if (question) lines.push('问：' + question);
  lines.push('【骰面】' + planet + ' · ' + sign + ' · 第' + houseNum + '宫');
  lines.push('【关键词】' + kwOf(p) + ' · ' + kwOf(s) + ' · ' + kwOf(h));
  lines.push('【水性】' + planet + '——' + (p.water || p.core || ''));
  lines.push('【流法】' + sign + '（' + (s.element || '') + '·' + (s.mode || '') + '），流水'
    + (s.phase || '') + '：' + (s.style || ''));
  lines.push('【归处】' + (h.name || '') + '，流水' + (h.phase || '') + '：' + (h.domain || ''));
  lines.push('【庙旺】' + dignityLine(rules, planet, sign));
  lines.push('【相位】' + aspectLine(rules, sign, parseInt(houseNum, 10)));
  lines.push('【参考断语】' + reference);
  return lines.join('\n');
}

// 掷骰后的 AI 综合调用；失败抛出异常，由调用方回退本地规则综合
async function aiCombo(rules, cfg, planet, sign, houseNum, question, reference) {
  const prompt = buildAiPrompt(rules, planet, sign, houseNum, question, reference);
  const resp = await httpJson('POST', cfg.base + '/chat/completions', cfg.key,
    chatPayload(cfg.model, prompt, AI_MAX_TOKENS));
  const content = resp.choices && resp.choices[0] && resp.choices[0].message
    && resp.choices[0].message.content;
  if (!content) throw new Error('模型 ' + cfg.model + ' 未返回内容');
  return String(content).trim();
}

// 与安卓端「测试 API」一致：GET /models 验证 key 与地址，最小对话验证模型可用
async function testApi(cfg) {
  if (!cfg.key) throw new Error('请先设置 MOONSHOT_API_KEY 再测试');
  const t0 = Date.now();
  const models = await httpJson('GET', cfg.base + '/models', cfg.key, null);
  const n = Array.isArray(models.data) ? models.data.length : 0;
  const ping = await httpJson('POST', cfg.base + '/chat/completions', cfg.key, {
    model: cfg.model,
    messages: [{ role: 'user', content: '回复：ok' }],
    max_tokens: 8,
    thinking: { type: 'disabled' }
  });
  if (!ping.choices || !ping.choices.length) throw new Error('模型 ' + cfg.model + ' 未返回内容');
  return '✓ 连接成功（' + (Date.now() - t0) + 'ms）：发现 ' + n + ' 个模型，' + cfg.model + ' 可用';
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

async function main() {
  const args = process.argv.slice(2);
  const rules = loadRules();
  const flags = new Set(args.filter((a) => a.startsWith('--')));
  const words = args.filter((a) => !a.startsWith('--'));
  if (flags.has('--check')) {
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
  if (flags.has('--test-api')) {
    try {
      console.log(await testApi(apiConfig()));
    } catch (e) {
      console.error('✗ 测试失败：' + e.message);
      process.exit(1);
    }
    return;
  }
  const question = words.join(' ').trim();
  const pick = (arr) => arr[(Math.random() * arr.length) | 0];
  const planet = pick(PLANETS);
  const sign = pick(SIGNS);
  const houseNum = String(1 + ((Math.random() * 12) | 0));
  const cfg = apiConfig();
  // 未配置 key 或 --no-ai：只输出本地规则综合（与安卓端无 key 时一致）
  if (!cfg.key || flags.has('--no-ai')) {
    console.log(build(rules, planet, sign, houseNum, question));
    return;
  }
  const head = buildHead(rules, planet, sign, houseNum, question);
  const reference = ruleCombo(rules, planet, sign, houseNum,
    rules.planets[planet], rules.signs[sign], rules.houses[houseNum]);
  try {
    console.log(head + await aiCombo(rules, cfg, planet, sign, houseNum, question, reference));
  } catch (e) {
    console.error('AI 综合失败：' + e.message);
    console.log(head + reference + '\n\n' + AI_FALLBACK_NOTE);
  }
}

if (require.main === module) {
  main().catch((e) => {
    console.error('astro-dice 出错: ' + e.message);
    process.exit(1);
  });
}

module.exports = {
  build, buildHead, buildAiPrompt, loadRules, checkRules,
  dignityStatus, aspectInfo, ruleCombo, apiConfig, testApi, aiCombo
};
