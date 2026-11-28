/*
 * 占星骰子 AstroDice
 * Copyright (c) 2026 Criz
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.astrodice.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String[] PLANETS = {
            "太阳", "月亮", "水星", "金星", "火星", "木星",
            "土星", "天王星", "海王星", "冥王星", "莉莉丝", "普里阿普斯"
    };
    private static final String[] SIGNS = {
            "白羊座", "金牛座", "双子座", "巨蟹座", "狮子座", "处女座",
            "天秤座", "天蝎座", "射手座", "摩羯座", "水瓶座", "双鱼座"
    };
    private static final int HOUSE_COUNT = 12;

    // 与 PLANETS / SIGNS / 宫位序（1 起）一一对应
    private static final String[] PLANET_GLYPHS = {"☉", "☽", "☿", "♀", "♂", "♃", "♄", "♅", "♆", "♇", "⚸", "Π"};
    private static final String[] SIGN_GLYPHS = {"♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓"};
    private static final String[] HOUSE_GLYPHS = {"Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ", "Ⅺ", "Ⅻ"};

    // (宫位 - 星座序) mod 12 → 相位名
    private static final String[] ASPECT_NAMES =
            {"合相", "邻宫", "六合", "刑", "拱", "梅花", "冲", "梅花", "拱", "刑", "六合", "邻宫"};

    private static final String AI_SYSTEM =
            "你是「占星骰子·一条河」体系的解读者。三颗骰子：行星=水性，星座=流法，宫位=归处。"
                    + "综合段必须以「行星在星座，落于第N宫」起句，把三颗骰子的意象融成一条河的叙事，"
                    + "语气简净如断语，结尾给出一句宜/忌建议，共100~180字，只输出解读正文。";
    private static final String DEFAULT_API_BASE = "https://api.moonshot.cn/v1";

    private static final String PREFS = "astro_dice_prefs";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_API_BASE = "api_base";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_API_MODEL = "api_model";
    private static final String KEY_SETTINGS_OPEN = "ai_settings_open";
    private static final String KEY_AI_ENABLED = "ai_enabled";
    private static final int HISTORY_LIMIT = 100;

    private final Random random = new Random();
    private final SecureRandom rollRandom = new SecureRandom();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    private TextView dicePlanet, diceSign, diceHouse;
    private TextView dicePlanetGlyph, diceSignGlyph, diceHouseGlyph;
    private TextView tvResultTitle, tvResultBody;
    private LinearLayout resultCard, historyList;
    private ScrollView mainScroll;
    private EditText etQuestion;
    private Button btnRoll;
    private EditText etApiBase, etApiKey, etApiModel;
    private Button btnSaveApi, btnTestApi;
    private Switch switchAiEnabled;
    private TextView tvApiStatus;
    private LinearLayout aiSettingsBody;
    private TextView aiSettingsArrow;
    private boolean settingsOpen;

    private JSONObject rules;
    // 解读引擎缓存：loadRules() 校验通过后一次性构建
    private Map<String, JSONObject> planetMap, signMap, houseMap;
    private Map<String, Integer> planetIndex, signIndex;
    private Map<String, String> dignityMatrix;          // 行星|星座 → 庙旺状态
    private String[][] aspectNameMatrix;                // [星座序][宫位-1] → 相位名
    private int[][] aspectDegreeMatrix;                 // [星座序][宫位-1] → 度数
    private boolean rolling = false;
    private long rollDeadline;
    private long shuffleDelay = 70;
    private long rollSeq;
    private String finalPlanet, finalSign, finalHouseNum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dicePlanet = findViewById(R.id.dice_planet);
        diceSign = findViewById(R.id.dice_sign);
        diceHouse = findViewById(R.id.dice_house);
        dicePlanetGlyph = findViewById(R.id.dice_planet_glyph);
        diceSignGlyph = findViewById(R.id.dice_sign_glyph);
        diceHouseGlyph = findViewById(R.id.dice_house_glyph);
        tvResultTitle = findViewById(R.id.tv_result_title);
        tvResultBody = findViewById(R.id.tv_result_body);
        resultCard = findViewById(R.id.result_card);
        historyList = findViewById(R.id.history_list);
        mainScroll = findViewById(R.id.main_scroll);
        etQuestion = findViewById(R.id.et_question);
        btnRoll = findViewById(R.id.btn_roll);
        etApiBase = findViewById(R.id.et_api_base);
        etApiKey = findViewById(R.id.et_api_key);
        etApiModel = findViewById(R.id.et_api_model);
        btnSaveApi = findViewById(R.id.btn_save_api);
        btnTestApi = findViewById(R.id.btn_test_api);
        switchAiEnabled = findViewById(R.id.switch_ai_enabled);
        tvApiStatus = findViewById(R.id.tv_api_status);
        aiSettingsBody = findViewById(R.id.ai_settings_body);
        aiSettingsArrow = findViewById(R.id.ai_settings_arrow);

        btnRoll.setOnClickListener(v -> roll());
        btnSaveApi.setOnClickListener(v -> {
            saveApiSettings();
            toast("AI 设置已保存");
        });
        btnTestApi.setOnClickListener(v -> testApi());
        switchAiEnabled.setChecked(aiEnabled());
        switchAiEnabled.setOnCheckedChangeListener((btn, checked) ->
                sp().edit().putBoolean(KEY_AI_ENABLED, checked).apply());
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> clearHistory());
        settingsOpen = sp().getBoolean(KEY_SETTINGS_OPEN, false);
        applySettingsOpen();
        findViewById(R.id.ai_settings_header).setOnClickListener(v -> {
            settingsOpen = !settingsOpen;
            sp().edit().putBoolean(KEY_SETTINGS_OPEN, settingsOpen).apply();
            applySettingsOpen();
        });

        etApiBase.setText(apiBase());
        etApiKey.setText(apiKey());
        etApiModel.setText(apiModel());

        loadRules();
        renderHistory();
    }

    // ---------- 掷骰 ----------

    private void roll() {
        if (rolling) return;
        rolling = true;
        rollSeq++;
        btnRoll.setEnabled(false);
        resultCard.setVisibility(View.GONE);

        rollDeadline = System.currentTimeMillis() + 900 + random.nextInt(500);
        shuffleDelay = 70;
        shuffleRunnable.run();
    }

    private final Runnable shuffleRunnable = new Runnable() {
        @Override
        public void run() {
            int pi = random.nextInt(PLANETS.length);
            int si = random.nextInt(SIGNS.length);
            int hi = random.nextInt(HOUSE_COUNT);
            dicePlanet.setText(PLANETS[pi]);
            diceSign.setText(SIGNS[si]);
            diceHouse.setText(String.valueOf(hi + 1));
            dicePlanetGlyph.setText(PLANET_GLYPHS[pi]);
            diceSignGlyph.setText(SIGN_GLYPHS[si]);
            diceHouseGlyph.setText(HOUSE_GLYPHS[hi]);
            if (System.currentTimeMillis() < rollDeadline) {
                shuffleDelay = Math.min(shuffleDelay + 9, 160);
                handler.postDelayed(this, shuffleDelay);
            } else {
                // 停定这一刻才抽签：在 12×12×12=1728 种组合中等概率抽一个，再拆成三颗骰
                int n = rollRandom.nextInt(PLANETS.length * SIGNS.length * HOUSE_COUNT);
                finalPlanet = PLANETS[n / (SIGNS.length * HOUSE_COUNT)];
                finalSign = SIGNS[(n / HOUSE_COUNT) % SIGNS.length];
                finalHouseNum = String.valueOf(n % HOUSE_COUNT + 1);
                dicePlanet.setText(finalPlanet);
                diceSign.setText(finalSign);
                diceHouse.setText(finalHouseNum);
                dicePlanetGlyph.setText(PLANET_GLYPHS[indexOf(PLANETS, finalPlanet)]);
                diceSignGlyph.setText(SIGN_GLYPHS[indexOf(SIGNS, finalSign)]);
                diceHouseGlyph.setText(HOUSE_GLYPHS[Integer.parseInt(finalHouseNum) - 1]);
                showResult(finalPlanet, finalSign, finalHouseNum);
                rolling = false;
                btnRoll.setEnabled(true);
            }
        }
    };

    private void showResult(String planet, String sign, String houseNum) {
        String question = etQuestion.getText().toString().trim();
        String title = planet + " · " + sign + " · 第" + houseNum + "宫";
        String head;
        String combo;
        try {
            head = buildHead(planet, sign, houseNum, question);
            combo = ruleCombo(planet, sign, houseNum);
        } catch (Exception e) {
            tvResultTitle.setText(title);
            tvResultBody.setText("规则文件解析失败：" + e.getMessage()
                    + "\n请重新安装 App 或检查 assets/rules.json。");
            resultCard.setVisibility(View.VISIBLE);
            addHistory(planet, sign, houseNum, question);
            return;
        }
        tvResultTitle.setText(title);
        boolean ai = aiEnabled() && !apiKey().isEmpty();
        tvResultBody.setText(head + combo + (ai ? "\n\n✦ AI 综合生成中…" : ""));
        resultCard.setVisibility(View.VISIBLE);
        String historyId = addHistory(planet, sign, houseNum, question);
        if (ai) requestAiCombo(rollSeq, historyId, head, combo, planet, sign, houseNum, question);
    }

    // ---------- 解读（本地规则） ----------

    // 模板中 {combo} 之前的部分：问/骰面/关键词/庙旺/相位
    private String buildHead(String planet, String sign, String houseNum, String question) throws Exception {
        String tpl = rules.getString("template");
        int cut = tpl.indexOf("{combo}");
        String head = fill(cut >= 0 ? tpl.substring(0, cut) : tpl, planet, sign, houseNum, question);
        if (cut < 0) head += "\n\n";
        return head;
    }

    private String fill(String tpl, String planet, String sign, String houseNum, String question) throws Exception {
        JSONObject p = planetMap.get(planet);
        JSONObject s = signMap.get(sign);
        JSONObject h = houseMap.get(houseNum);
        if (p == null || s == null || h == null) {
            throw new Exception("rules.json 缺少骰面条目：" + planet + "/" + sign + "/第" + houseNum + "宫");
        }
        int house = Integer.parseInt(houseNum);
        return tpl
                .replace("{question}", question.isEmpty() ? "" : "问：" + question + "\n\n")
                .replace("{planet}", planet)
                .replace("{planet_core}", p.optString("core", ""))
                .replace("{planet_water}", p.optString("water", ""))
                .replace("{planet_keywords}", joinKeywords(p.optJSONArray("keywords")))
                .replace("{planet_kw}", p.optString("kw", joinKeywords(p.optJSONArray("keywords"))))
                .replace("{sign}", sign)
                .replace("{sign_element}", s.optString("element", ""))
                .replace("{sign_mode}", s.optString("mode", ""))
                .replace("{sign_style}", s.optString("style", ""))
                .replace("{sign_phase}", s.optString("phase", ""))
                .replace("{sign_kw}", s.optString("kw", ""))
                .replace("{house}", houseNum)
                .replace("{house_name}", h.optString("name", ""))
                .replace("{house_domain}", h.optString("domain", ""))
                .replace("{house_phase}", h.optString("phase", ""))
                .replace("{house_kw}", h.optString("kw", ""))
                .replace("{dignity}", dignityLine(planet, sign))
                .replace("{aspect}", aspectLine(sign, house));
    }

    // 组合规则：「行星」在星座，落于第N宫——三相关键词 + 庙旺/相位合成；有 overrides 则以其为准
    private String ruleCombo(String planet, String sign, String houseNum) throws Exception {
        JSONObject p = planetMap.get(planet);
        JSONObject s = signMap.get(sign);
        JSONObject h = houseMap.get(houseNum);
        if (p == null || s == null || h == null) {
            throw new Exception("rules.json 缺少骰面条目：" + planet + "/" + sign + "/第" + houseNum + "宫");
        }
        String key = planet + "|" + sign + "|" + houseNum + "宫";
        JSONObject ov = rules.optJSONObject("overrides");
        if (ov != null && ov.has(key)) return ov.getString(key);
        String pKw = p.optString("kw", joinKeywords(p.optJSONArray("keywords")));
        String sKw = s.optString("kw", "");
        String hKw = h.optString("kw", "");
        String noteD = dignityNote(dignityStatus(planet, sign));
        String noteA = aspectNote(aspectName(sign, Integer.parseInt(houseNum)));
        return "「" + planet + "」在" + sign + "，落于第" + houseNum + "宫——"
                + pKw + "之流，" + sKw + "而往，归于" + hKw + "。" + noteD + noteA;
    }

    // ---------- 庙旺 ----------

    // 查表结果；加载时由 computeDignity 预填
    private String dignityStatus(String planet, String sign) {
        String st = dignityMatrix.get(planet + "|" + sign);
        return st == null ? "平" : st;
    }

    private String computeDignity(String planet, String sign) {
        JSONObject dignity = rules.optJSONObject("dignity");
        if (dignity == null) return "平";
        JSONObject d = dignity.optJSONObject(planet);
        if (d == null) return "平";
        if (hasString(d.optJSONArray("domicile"), sign)) return "入庙";
        if (sign.equals(d.optString("exaltation", ""))) return "擢升";
        if (hasString(d.optJSONArray("detriment"), sign)) return "失势";
        if (sign.equals(d.optString("fall", ""))) return "落陷";
        return "平";
    }

    private String dignityLine(String planet, String sign) {
        String st = dignityStatus(planet, sign);
        return planet + "在" + sign + "：" + st + "——" + dignityNote(st);
    }

    private String dignityNote(String st) {
        JSONObject notes = rules.optJSONObject("dignityNotes");
        String note = notes == null ? "" : notes.optString(st, "");
        return note.isEmpty() ? defaultDignityNote(st) : note;
    }

    private String defaultDignityNote(String st) {
        if ("入庙".equals(st)) return "得势：水在自家河道，助力天然，放手去流。";
        if ("擢升".equals(st)) return "得势：水被高高托起，势能正盛，宜乘势而上。";
        if ("失势".equals(st)) return "受制：水入陌生河床，处处别扭，宜减速慎行。";
        if ("落陷".equals(st)) return "受制：水势低徊无力，先固本蓄力，勿强求。";
        return "无奇：水性如常，顺逆皆看流法与归处的配合。";
    }

    // ---------- 相位 ----------

    private String aspectName(String sign, int house) {
        Integer si = signIndex.get(sign);
        return aspectNameMatrix[si == null ? indexOf(SIGNS, sign) : si][house - 1];
    }

    private int aspectDegree(String sign, int house) {
        Integer si = signIndex.get(sign);
        return aspectDegreeMatrix[si == null ? indexOf(SIGNS, sign) : si][house - 1];
    }

    private String aspectLine(String sign, int house) {
        String name = aspectName(sign, house);
        return sign + "与第" + house + "宫成" + name + "（" + aspectDegree(sign, house) + "°）：" + aspectNote(name);
    }

    private String aspectNote(String name) {
        JSONObject notes = rules.optJSONObject("aspectNotes");
        String note = notes == null ? "" : notes.optString(name, "");
        return note.isEmpty() ? defaultAspectNote(name) : note;
    }

    private String defaultAspectNote(String name) {
        if ("合相".equals(name)) return "同位共振：流法与归处同境，现路即去路，一顺百顺。";
        if ("邻宫".equals(name)) return "相邻相承：只差一境，顺势微调即达。";
        if ("六合".equals(name)) return "六合相扶：来去相就，水到渠成。";
        if ("刑".equals(name)) return "刑克相顶：流法与归处相别，过程有摩擦，宜绕行缓图。";
        if ("拱".equals(name)) return "拱照同气：流法与归处相和，来去皆顺。";
        if ("冲".equals(name)) return "对冲相张：流法与归处正对，张力最大，成在破立之间的平衡。";
        return "梅花相错：流法与归处错开半境，需调整适应，勿硬拧。";
    }

    // ---------- AI 设置与调用（Moonshot 兼容接口） ----------

    private SharedPreferences sp() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String apiBase() {
        return sp().getString(KEY_API_BASE, DEFAULT_API_BASE).trim();
    }

    private String apiKey() {
        String k = sp().getString(KEY_API_KEY, BuildConfig.MOONSHOT_API_KEY);
        return k == null ? "" : k.trim();
    }

    // AI 解读总开关：关闭后掷骰只出本地规则综合，不再请求 AI
    private boolean aiEnabled() {
        return sp().getBoolean(KEY_AI_ENABLED, true);
    }

    private String apiModel() {
        String m = sp().getString(KEY_API_MODEL, BuildConfig.MOONSHOT_MODEL);
        m = m == null ? "" : m.trim();
        return m.isEmpty() ? "kimi-k2.6" : m;
    }

    private void saveApiSettings() {
        sp().edit()
                .putString(KEY_API_BASE, etApiBase.getText().toString().trim())
                .putString(KEY_API_KEY, etApiKey.getText().toString().trim())
                .putString(KEY_API_MODEL, etApiModel.getText().toString().trim())
                .apply();
    }

    private void applySettingsOpen() {
        aiSettingsBody.setVisibility(settingsOpen ? View.VISIBLE : View.GONE);
        aiSettingsArrow.setText(settingsOpen ? "▾" : "▸");
    }

    // 测试连接：先保存，再 GET /models 验证 key 与地址，最后用最小对话验证模型可用
    private void testApi() {
        saveApiSettings();
        final String base = normalizeBase(apiBase());
        final String key = apiKey();
        final String model = apiModel();
        if (key.isEmpty()) {
            tvApiStatus.setText("⚠ 请先填写 API Key 再测试");
            return;
        }
        tvApiStatus.setText("测试中…");
        btnTestApi.setEnabled(false);
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            String error = null;
            int modelCount = -1;
            try {
                modelCount = listModels(base, key);
                pingChat(base, key, model);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final boolean ok = error == null;
            final long ms = System.currentTimeMillis() - t0;
            final int n = modelCount;
            final String text = ok
                    ? "✓ 连接成功（" + ms + "ms）：发现 " + n + " 个模型，" + model + " 可用"
                    : "✗ 测试失败：" + error;
            handler.post(() -> {
                tvApiStatus.setText(text);
                btnTestApi.setEnabled(true);
            });
        }).start();
    }

    private int listModels(String base, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/models").openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        String resp;
        try {
            resp = readAll(c.getInputStream());
        } catch (Exception e) {
            throw new Exception("无法获取模型列表（" + c.getResponseCode() + "）：" + readError(c));
        }
        return new JSONObject(resp).optJSONArray("data").length();
    }

    private void pingChat(String base, String key, String model) throws Exception {
        JSONObject usr = new JSONObject().put("role", "user").put("content", "回复：ok");
        JSONArray messages = new JSONArray().put(usr);
        JSONObject payload = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("max_tokens", 8)
                .put("thinking", new JSONObject().put("type", "disabled"));
        JSONObject resp = postJson(base + "/chat/completions", key, payload);
        if (resp.optJSONArray("choices") == null || resp.optJSONArray("choices").length() == 0) {
            throw new Exception("模型 " + model + " 未返回内容");
        }
    }

    private void requestAiCombo(final long seq, final String historyId, final String head, final String reference,
                                final String planet, final String sign, final String houseNum,
                                final String question) {
        final String base = normalizeBase(apiBase());
        final String key = apiKey();
        final String model = apiModel();
        final String userPrompt;
        try {
            userPrompt = buildAiPrompt(planet, sign, houseNum, question, reference);
        } catch (Exception e) {
            return;
        }
        new Thread(() -> {
            String ai = null;
            try {
                JSONObject resp = postJson(base + "/chat/completions", key,
                        chatPayload(model, userPrompt, 400));
                ai = resp.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
            } catch (Exception ignored) {
            }
            final String result = ai == null ? null : ai.trim();
            handler.post(() -> {
                if (seq != rollSeq) return;
                if (result != null && !result.isEmpty()) {
                    tvResultBody.setText(head + result);
                    updateHistoryAi(historyId, result);
                } else {
                    tvResultBody.setText(head + reference + "\n\n（AI 解读不可用，以上为本地规则综合）");
                }
            });
        }).start();
    }

    private String buildAiPrompt(String planet, String sign, String houseNum, String question, String reference) throws Exception {
        JSONObject p = planetMap.get(planet);
        JSONObject s = signMap.get(sign);
        JSONObject h = houseMap.get(houseNum);
        if (p == null || s == null || h == null) {
            throw new Exception("rules.json 缺少骰面条目：" + planet + "/" + sign + "/第" + houseNum + "宫");
        }
        StringBuilder sb = new StringBuilder();
        if (!question.isEmpty()) sb.append("问：").append(question).append('\n');
        sb.append("【骰面】").append(planet).append(" · ").append(sign).append(" · 第").append(houseNum).append("宫\n");
        sb.append("【关键词】").append(p.optString("kw", "")).append(" · ")
                .append(s.optString("kw", "")).append(" · ").append(h.optString("kw", "")).append('\n');
        sb.append("【水性】").append(planet).append("——");
        String water = p.optString("water", "");
        sb.append(water.isEmpty() ? p.optString("core", "") : water).append('\n');
        sb.append("【流法】").append(sign).append("（").append(s.optString("element", ""))
                .append("·").append(s.optString("mode", "")).append("），流水")
                .append(s.optString("phase", "")).append("：").append(s.optString("style", "")).append('\n');
        sb.append("【归处】").append(h.optString("name", "")).append("，流水")
                .append(h.optString("phase", "")).append("：").append(h.optString("domain", "")).append('\n');
        sb.append("【庙旺】").append(dignityLine(planet, sign)).append('\n');
        sb.append("【相位】").append(aspectLine(sign, Integer.parseInt(houseNum))).append('\n');
        sb.append("【参考断语】").append(reference);
        return sb.toString();
    }

    private JSONObject chatPayload(String model, String userPrompt, int maxTokens) throws Exception {
        JSONObject sys = new JSONObject().put("role", "system").put("content", AI_SYSTEM);
        JSONObject usr = new JSONObject().put("role", "user").put("content", userPrompt);
        JSONArray messages = new JSONArray().put(sys).put(usr);
        return new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("max_tokens", maxTokens)
                .put("thinking", new JSONObject().put("type", "disabled"));
    }

    private JSONObject postJson(String url, String key, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        c.getOutputStream().write(payload.toString().getBytes(StandardCharsets.UTF_8));
        String resp;
        try {
            resp = readAll(c.getInputStream());
        } catch (Exception e) {
            throw new Exception(readError(c));
        }
        return new JSONObject(resp);
    }

    private static String normalizeBase(String base) {
        if (base == null || base.trim().isEmpty()) return DEFAULT_API_BASE;
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    // ---------- 规则文件 ----------

    private void loadRules() {
        String json = null;
        try {
            json = readAll(getAssets().open("rules.json"));
        } catch (Exception ignored) {
        }
        if (json != null) {
            try {
                rules = new JSONObject(json);
                buildEngineCache();
                return;
            } catch (Exception e) {
                toast("规则文件无效（" + e.getMessage() + "），已使用内置简版规则");
            }
        } else {
            toast("规则文件缺失，已使用内置简版规则");
        }
        rules = fallbackRules();
        try {
            buildEngineCache();
        } catch (Exception ignored) {
        }
    }

    /**
     * 校验 rules.json 结构并预建解读缓存（行星/星座/宫位索引、庙旺矩阵、相位矩阵）。
     * 任何不合法抛出带路径的异常，由 loadRules() 兜底为内置简版规则。
     */
    private void buildEngineCache() throws Exception {
        JSONObject planets = rules.optJSONObject("planets");
        if (planets == null) throw new Exception("rules.json 缺少 planets");
        JSONObject signs = rules.optJSONObject("signs");
        if (signs == null) throw new Exception("rules.json 缺少 signs");
        JSONObject houses = rules.optJSONObject("houses");
        if (houses == null) throw new Exception("rules.json 缺少 houses");
        planetMap = new HashMap<>();
        planetIndex = new HashMap<>();
        for (int i = 0; i < PLANETS.length; i++) {
            JSONObject o = planets.optJSONObject(PLANETS[i]);
            if (o == null) throw new Exception("rules.json 缺少 planets." + PLANETS[i]);
            planetMap.put(PLANETS[i], o);
            planetIndex.put(PLANETS[i], i);
        }
        signMap = new HashMap<>();
        signIndex = new HashMap<>();
        for (int i = 0; i < SIGNS.length; i++) {
            JSONObject o = signs.optJSONObject(SIGNS[i]);
            if (o == null) throw new Exception("rules.json 缺少 signs." + SIGNS[i]);
            signMap.put(SIGNS[i], o);
            signIndex.put(SIGNS[i], i);
        }
        houseMap = new HashMap<>();
        for (int i = 1; i <= HOUSE_COUNT; i++) {
            String k = String.valueOf(i);
            JSONObject o = houses.optJSONObject(k);
            if (o == null) throw new Exception("rules.json 缺少 houses." + k);
            houseMap.put(k, o);
        }
        if (rules.optString("template", "").isEmpty()) throw new Exception("rules.json 缺少 template");
        validateDignityRefs();
        validateOverrideKeys();
        dignityMatrix = new HashMap<>();
        for (String planet : PLANETS) {
            for (String sign : SIGNS) {
                dignityMatrix.put(planet + "|" + sign, computeDignity(planet, sign));
            }
        }
        aspectNameMatrix = new String[SIGNS.length][HOUSE_COUNT];
        aspectDegreeMatrix = new int[SIGNS.length][HOUSE_COUNT];
        for (int s = 0; s < SIGNS.length; s++) {
            for (int house = 1; house <= HOUSE_COUNT; house++) {
                int d = ((house - (s + 1)) % 12 + 12) % 12;
                aspectNameMatrix[s][house - 1] = ASPECT_NAMES[d];
                aspectDegreeMatrix[s][house - 1] = Math.min(d, 12 - d) * 30;
            }
        }
    }

    private void validateDignityRefs() throws Exception {
        JSONObject dignity = rules.optJSONObject("dignity");
        if (dignity == null) return;
        Iterator<String> it = dignity.keys();
        while (it.hasNext()) {
            String planet = it.next();
            if (!planetMap.containsKey(planet)) {
                throw new Exception("rules.json dignity 含未知行星：" + planet);
            }
            JSONObject d = dignity.optJSONObject(planet);
            if (d == null) continue;
            checkSignList(d.optJSONArray("domicile"), planet, "domicile");
            checkSign(d.optString("exaltation", ""), planet, "exaltation");
            checkSignList(d.optJSONArray("detriment"), planet, "detriment");
            checkSign(d.optString("fall", ""), planet, "fall");
        }
    }

    private void checkSignList(JSONArray arr, String planet, String field) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            checkSign(arr.optString(i), planet, field);
        }
    }

    private void checkSign(String sign, String planet, String field) throws Exception {
        if (!sign.isEmpty() && !signMap.containsKey(sign)) {
            throw new Exception("rules.json dignity." + planet + "." + field + " 含未知星座：" + sign);
        }
    }

    private void validateOverrideKeys() throws Exception {
        JSONObject ov = rules.optJSONObject("overrides");
        if (ov == null) return;
        Iterator<String> it = ov.keys();
        while (it.hasNext()) {
            String key = it.next();
            String[] parts = key.split("\\|", -1);
            if (parts.length != 3) throw new Exception("rules.json overrides 键格式错误（应为 行星|星座|N宫）：" + key);
            if (!planetMap.containsKey(parts[0])) throw new Exception("rules.json overrides 含未知行星：" + key);
            if (!signMap.containsKey(parts[1])) throw new Exception("rules.json overrides 含未知星座：" + key);
            if (!parts[2].matches("(?:[1-9]|1[0-2])宫")) {
                throw new Exception("rules.json overrides 宫位写法错误（应为 1宫~12宫）：" + key);
            }
        }
    }

    /**
     * 兜底规则：仅当 assets/rules.json 缺失或损坏时使用，内容极简。
     */
    private JSONObject fallbackRules() {
        JSONObject r = new JSONObject();
        try {
            JSONObject p = new JSONObject()
                    .put("太阳", entry("自我与生命力", "自我")).put("月亮", entry("情绪与安全感", "情绪"))
                    .put("水星", entry("思维与沟通", "沟通")).put("金星", entry("爱情与价值观", "爱情"))
                    .put("火星", entry("行动与欲望", "行动")).put("木星", entry("幸运与扩张", "幸运"))
                    .put("土星", entry("责任与考验", "责任")).put("天王星", entry("突变与自由", "变革"))
                    .put("海王星", entry("幻想与灵性", "梦想")).put("冥王星", entry("转化与权力", "蜕变"))
                    .put("莉莉丝", entry("本能与禁忌", "本能")).put("普里阿普斯", entry("繁衍与丰饶", "繁衍"));
            JSONObject s = new JSONObject()
                    .put("白羊座", sign("火象", "基本宫", "直接冲动", "冲动")).put("金牛座", sign("土象", "固定宫", "稳健务实", "守成"))
                    .put("双子座", sign("风象", "变动宫", "灵活善变", "变通")).put("巨蟹座", sign("水象", "基本宫", "敏感恋家", "恋家"))
                    .put("狮子座", sign("火象", "固定宫", "自信热情", "自信")).put("处女座", sign("土象", "变动宫", "细致挑剔", "细致"))
                    .put("天秤座", sign("风象", "基本宫", "优雅权衡", "权衡")).put("天蝎座", sign("水象", "固定宫", "深沉执着", "深沉"))
                    .put("射手座", sign("火象", "变动宫", "乐观直率", "直率")).put("摩羯座", sign("土象", "基本宫", "克制务实", "务实"))
                    .put("水瓶座", sign("风象", "固定宫", "理性疏离", "疏离")).put("双鱼座", sign("水象", "变动宫", "共情浪漫", "浪漫"));
            JSONObject hd = new JSONObject()
                    .put("1", house("第1宫·命宫", "自我形象", "命宫")).put("2", house("第2宫·财帛宫", "金钱与价值", "财帛"))
                    .put("3", house("第3宫·兄弟宫", "沟通与学习", "兄弟")).put("4", house("第4宫·田宅宫", "家庭与归属", "田宅"))
                    .put("5", house("第5宫·子女宫", "恋爱与创作", "子女")).put("6", house("第6宫·奴仆宫", "工作与健康", "奴仆"))
                    .put("7", house("第7宫·夫妻宫", "伴侣与合作", "夫妻")).put("8", house("第8宫·疾厄宫", "深层转化", "疾厄"))
                    .put("9", house("第9宫·迁移宫", "远行与信念", "迁移")).put("10", house("第10宫·官禄宫", "事业与名望", "官禄"))
                    .put("11", house("第11宫·福德宫", "朋友与理想", "福德")).put("12", house("第12宫·玄秘宫", "潜意识与隐秘", "玄秘"));
            r.put("planets", p).put("signs", s).put("houses", hd)
                    .put("overrides", new JSONObject())
                    .put("template",
                            "{question}【骰面】{planet} · {sign} · 第{house}宫\n\n"
                                    + "【关键词】{planet_kw} · {sign_kw} · {house_kw}\n"
                                    + "【庙旺】{dignity}\n"
                                    + "【相位】{aspect}\n\n"
                                    + "【综合】{combo}");
        } catch (Exception ignored) {
        }
        return r;
    }

    private JSONObject entry(String core, String kw) throws Exception {
        return new JSONObject().put("core", core).put("kw", kw).put("keywords", new JSONArray());
    }

    private JSONObject sign(String element, String mode, String style, String kw) throws Exception {
        return new JSONObject()
                .put("element", element).put("mode", mode).put("style", style).put("kw", kw);
    }

    private JSONObject house(String name, String domain, String kw) throws Exception {
        return new JSONObject().put("name", name).put("domain", domain).put("kw", kw);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static String readError(HttpURLConnection c) {
        int code = -1;
        try {
            code = c.getResponseCode();
        } catch (Exception ignored) {
        }
        try {
            InputStream es = c.getErrorStream();
            if (es == null) return "HTTP " + code;
            String body = readAll(es);
            try {
                JSONObject j = new JSONObject(body);
                String msg = j.optJSONObject("error") != null
                        ? j.optJSONObject("error").optString("message", body)
                        : body;
                return "HTTP " + code + "：" + msg;
            } catch (Exception e) {
                return "HTTP " + code + "：" + body;
            }
        } catch (Exception e) {
            return "HTTP " + code;
        }
    }

    // ---------- 历史记录 ----------

    private String addHistory(String planet, String sign, String houseNum, String question) {
        try {
            SharedPreferences sp = sp();
            JSONArray arr = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            String id = System.currentTimeMillis() + "-" + random.nextInt(1000);
            item.put("id", id);
            item.put("time", timeFmt.format(new Date()));
            item.put("title", planet + " · " + sign + " · 第" + houseNum + "宫");
            item.put("question", question);
            item.put("planet", planet);
            item.put("sign", sign);
            item.put("house", houseNum);
            next.put(item);
            for (int i = 0; i < arr.length() && i < HISTORY_LIMIT - 1; i++) {
                next.put(arr.get(i));
            }
            sp.edit().putString(KEY_HISTORY, next.toString()).apply();
            renderHistory();
            return id;
        } catch (Exception ignored) {
            return null;
        }
    }

    // AI 解读返回后写回对应历史记录
    private void updateHistoryAi(String id, String ai) {
        if (id == null || ai == null || ai.isEmpty()) return;
        try {
            SharedPreferences sp = sp();
            JSONArray arr = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it != null && id.equals(it.optString("id"))) {
                    it.put("ai", ai);
                    changed = true;
                    break;
                }
            }
            if (changed) {
                sp.edit().putString(KEY_HISTORY, arr.toString()).apply();
                renderHistory();
            }
        } catch (Exception ignored) {
        }
    }

    private void renderHistory() {
        historyList.removeAllViews();
        try {
            SharedPreferences sp = sp();
            JSONArray arr = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            if (arr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("还没有记录");
                empty.setTextColor(0x88FFFFFF);
                historyList.addView(empty);
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                TextView tv = new TextView(this);
                String q = it.optString("question");
                String line = it.optString("time") + "   " + it.optString("title");
                if (!it.optString("ai").isEmpty()) line += "   ✦AI";
                if (!q.isEmpty()) line += "\n问：" + q;
                tv.setText(line);
                tv.setTextColor(0xDDFFFFFF);
                tv.setTextSize(13);
                int padV = dp(8);
                tv.setPadding(0, padV, 0, padV);
                tv.setOnClickListener(v -> openHistory(it));
                historyList.addView(tv);
                View div = new View(this);
                div.setBackgroundColor(0x22FFFFFF);
                historyList.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        } catch (Exception ignored) {
        }
    }

    // 点击历史记录：把骰面与结果卡片刷新为该记录的内容
    private void openHistory(JSONObject it) {
        if (rolling) return;
        String planet = it.optString("planet");
        String sign = it.optString("sign");
        String houseNum = it.optString("house");
        String question = it.optString("question");
        String title = it.optString("title");
        if (title.isEmpty()) title = planet + " · " + sign + " · 第" + houseNum + "宫";
        if (planet.isEmpty() || sign.isEmpty() || houseNum.isEmpty()) {
            tvResultTitle.setText(title);
            tvResultBody.setText("该记录缺少骰子明细，无法还原解读。");
            resultCard.setVisibility(View.VISIBLE);
            scrollToResult();
            return;
        }
        rollSeq++;
        try {
            dicePlanet.setText(planet);
            diceSign.setText(sign);
            diceHouse.setText(houseNum);
            dicePlanetGlyph.setText(PLANET_GLYPHS[indexOf(PLANETS, planet)]);
            diceSignGlyph.setText(SIGN_GLYPHS[indexOf(SIGNS, sign)]);
            diceHouseGlyph.setText(HOUSE_GLYPHS[Integer.parseInt(houseNum) - 1]);
        } catch (Exception ignored) {
        }
        String head;
        String combo;
        try {
            head = buildHead(planet, sign, houseNum, question);
            combo = ruleCombo(planet, sign, houseNum);
        } catch (Exception e) {
            tvResultTitle.setText(title);
            tvResultBody.setText("规则文件解析失败：" + e.getMessage()
                    + "\n请重新安装 App 或检查 assets/rules.json。");
            resultCard.setVisibility(View.VISIBLE);
            scrollToResult();
            return;
        }
        tvResultTitle.setText(title);
        String ai = it.optString("ai");
        if (!ai.isEmpty()) {
            tvResultBody.setText(head + ai);
        } else if (aiEnabled() && !apiKey().isEmpty()) {
            tvResultBody.setText(head + combo + "\n\n✦ AI 综合生成中…");
            requestAiCombo(rollSeq, it.optString("id"), head, combo, planet, sign, houseNum, question);
        } else {
            tvResultBody.setText(head + combo);
        }
        resultCard.setVisibility(View.VISIBLE);
        scrollToResult();
    }

    private void scrollToResult() {
        mainScroll.post(() -> mainScroll.smoothScrollTo(0, resultCard.getTop()));
    }

    private void clearHistory() {
        sp().edit().remove(KEY_HISTORY).apply();
        renderHistory();
        toast("历史已清空");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) return i;
        }
        return 0;
    }

    private static boolean hasString(JSONArray arr, String v) {
        if (arr == null) return false;
        for (int i = 0; i < arr.length(); i++) {
            if (v.equals(arr.optString(i))) return true;
        }
        return false;
    }

    private String joinKeywords(JSONArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0) sb.append("、");
            sb.append(arr.optString(i));
        }
        return sb.toString();
    }
}
