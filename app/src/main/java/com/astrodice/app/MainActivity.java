package com.astrodice.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String[] PLANETS = {
            "太阳", "月亮", "水星", "金星", "火星", "木星",
            "土星", "天王星", "海王星", "冥王星", "北交点", "南交点"
    };
    private static final String[] SIGNS = {
            "白羊座", "金牛座", "双子座", "巨蟹座", "狮子座", "处女座",
            "天秤座", "天蝎座", "射手座", "摩羯座", "水瓶座", "双鱼座"
    };
    private static final int HOUSE_COUNT = 12;

    private static final String PREFS = "astro_dice_prefs";
    private static final String KEY_RULE_URI = "rule_uri";
    private static final String KEY_HISTORY = "history";
    private static final int REQ_IMPORT_RULES = 1;
    private static final int HISTORY_LIMIT = 100;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    private TextView dicePlanet, diceSign, diceHouse;
    private TextView tvResultTitle, tvResultBody;
    private LinearLayout resultCard, historyList;
    private EditText etQuestion;
    private Button btnRoll;

    private JSONObject rules;
    private boolean rolling = false;
    private long rollDeadline;
    private String finalPlanet, finalSign, finalHouseNum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dicePlanet = findViewById(R.id.dice_planet);
        diceSign = findViewById(R.id.dice_sign);
        diceHouse = findViewById(R.id.dice_house);
        tvResultTitle = findViewById(R.id.tv_result_title);
        tvResultBody = findViewById(R.id.tv_result_body);
        resultCard = findViewById(R.id.result_card);
        historyList = findViewById(R.id.history_list);
        etQuestion = findViewById(R.id.et_question);
        btnRoll = findViewById(R.id.btn_roll);

        btnRoll.setOnClickListener(v -> roll());
        findViewById(R.id.btn_import).setOnClickListener(v -> pickRulesFile());
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetRules());
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> clearHistory());

        loadRules();
        renderHistory();
    }

    // ---------- 掷骰 ----------

    private void roll() {
        if (rolling) return;
        rolling = true;
        btnRoll.setEnabled(false);
        resultCard.setVisibility(View.GONE);

        finalPlanet = PLANETS[random.nextInt(PLANETS.length)];
        finalSign = SIGNS[random.nextInt(SIGNS.length)];
        finalHouseNum = String.valueOf(1 + random.nextInt(HOUSE_COUNT));

        rollDeadline = System.currentTimeMillis() + 900 + random.nextInt(500);
        shuffleRunnable.run();
    }

    private final Runnable shuffleRunnable = new Runnable() {
        @Override
        public void run() {
            dicePlanet.setText(PLANETS[random.nextInt(PLANETS.length)]);
            diceSign.setText(SIGNS[random.nextInt(SIGNS.length)]);
            diceHouse.setText(String.valueOf(1 + random.nextInt(HOUSE_COUNT)));
            if (System.currentTimeMillis() < rollDeadline) {
                handler.postDelayed(this, 70);
            } else {
                dicePlanet.setText(finalPlanet);
                diceSign.setText(finalSign);
                diceHouse.setText(finalHouseNum);
                showResult(finalPlanet, finalSign, finalHouseNum);
                rolling = false;
                btnRoll.setEnabled(true);
            }
        }
    };

    private void showResult(String planet, String sign, String houseNum) {
        String question = etQuestion.getText().toString().trim();
        String title = planet + " · " + sign + " · 第" + houseNum + "宫";
        String body;
        try {
            body = buildInterpretation(planet, sign, houseNum, question);
        } catch (Exception e) {
            body = "规则文件解析失败：" + e.getMessage()
                    + "\n请检查 rules.json 的格式（可对照 assets 里的默认文件），或点「恢复默认规则」。";
        }
        tvResultTitle.setText(title);
        tvResultBody.setText(body);
        resultCard.setVisibility(View.VISIBLE);
        addHistory(title, question);
    }

    // ---------- 解读 ----------

    private String buildInterpretation(String planet, String sign, String houseNum, String question) throws Exception {
        JSONObject planets = rules.getJSONObject("planets");
        JSONObject signs = rules.getJSONObject("signs");
        JSONObject houses = rules.getJSONObject("houses");

        JSONObject p = planets.getJSONObject(planet);
        JSONObject s = signs.getJSONObject(sign);
        JSONObject h = houses.getJSONObject(houseNum);

        String planetCore = p.getString("core");
        String planetKeywords = joinKeywords(p.optJSONArray("keywords"));
        String planetWater = p.optString("water", "");
        String signElement = s.optString("element", "");
        String signMode = s.optString("mode", "");
        String signStyle = s.getString("style");
        String houseName = h.getString("name");
        String houseDomain = h.getString("domain");
        String signPhase = s.optString("phase", "");
        String housePhase = h.optString("phase", "");

        String comboKey = planet + "|" + sign + "|" + houseNum + "宫";
        String combo;
        JSONObject overrides = rules.optJSONObject("overrides");
        if (overrides != null && overrides.has(comboKey)) {
            combo = overrides.getString(comboKey);
        } else {
            String planetNature = planetWater.isEmpty() ? planetCore : planetWater;
            String signPart = signPhase.isEmpty() ? sign : sign + "（" + signPhase + "）";
            String housePart = housePhase.isEmpty() ? houseName : houseName + "（" + housePhase + "）";
            combo = "如一条河：「" + planet + "」之性是" + planetNature
                    + "；它流作" + signPart + "，归于" + housePart
                    + "——" + houseDomain + "。水性、流法、归处三相合一：程数两相对照，此事的来路、现况与去处俱在其中。";
        }

        String template = rules.getString("template");
        return template
                .replace("{question}", question.isEmpty() ? "" : "问：" + question + "\n\n")
                .replace("{planet}", planet)
                .replace("{planet_core}", planetCore)
                .replace("{planet_water}", planetWater)
                .replace("{planet_keywords}", planetKeywords)
                .replace("{sign}", sign)
                .replace("{sign_element}", signElement)
                .replace("{sign_mode}", signMode)
                .replace("{sign_style}", signStyle)
                .replace("{sign_phase}", signPhase)
                .replace("{house}", houseNum)
                .replace("{house_name}", houseName)
                .replace("{house_domain}", houseDomain)
                .replace("{house_phase}", housePhase)
                .replace("{combo}", combo);
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

    // ---------- 规则文件 ----------

    private void loadRules() {
        String json = null;
        String uriStr = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_RULE_URI, null);
        if (uriStr != null) {
            try {
                InputStream in = getContentResolver().openInputStream(Uri.parse(uriStr));
                json = readAll(in);
            } catch (Exception ignored) {
            }
        }
        if (json == null) {
            try {
                json = readAll(getAssets().open("rules.json"));
            } catch (Exception ignored) {
            }
        }
        if (json != null) {
            try {
                rules = new JSONObject(json);
                return;
            } catch (Exception ignored) {
            }
        }
        rules = fallbackRules();
        toast("规则文件无效，已使用内置简版规则");
    }

    private void pickRulesFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_RULES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_RULES && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_RULE_URI, uri.toString()).apply();
                loadRules();
                toast("规则已导入");
            } catch (Exception e) {
                toast("导入失败：" + e.getMessage());
            }
        }
    }

    private void resetRules() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_RULE_URI).apply();
        loadRules();
        toast("已恢复内置默认规则");
    }

    /**
     * 兜底规则：仅当 assets/rules.json 缺失或损坏时使用，内容极简。
     */
    private JSONObject fallbackRules() {
        JSONObject r = new JSONObject();
        try {
            JSONObject p = new JSONObject()
                    .put("太阳", entry("自我与生命力")).put("月亮", entry("情绪与安全感"))
                    .put("水星", entry("思维与沟通")).put("金星", entry("爱情与价值观"))
                    .put("火星", entry("行动与欲望")).put("木星", entry("幸运与扩张"))
                    .put("土星", entry("责任与考验")).put("天王星", entry("突变与自由"))
                    .put("海王星", entry("幻想与灵性")).put("冥王星", entry("转化与权力"))
                    .put("北交点", entry("成长方向")).put("南交点", entry("过去惯性"));
            JSONObject s = new JSONObject()
                    .put("白羊座", sign("火象", "基本宫", "直接冲动")).put("金牛座", sign("土象", "固定宫", "稳健务实"))
                    .put("双子座", sign("风象", "变动宫", "灵活善变")).put("巨蟹座", sign("水象", "基本宫", "敏感恋家"))
                    .put("狮子座", sign("火象", "固定宫", "自信热情")).put("处女座", sign("土象", "变动宫", "细致挑剔"))
                    .put("天秤座", sign("风象", "基本宫", "优雅权衡")).put("天蝎座", sign("水象", "固定宫", "深沉执着"))
                    .put("射手座", sign("火象", "变动宫", "乐观直率")).put("摩羯座", sign("土象", "基本宫", "克制务实"))
                    .put("水瓶座", sign("风象", "固定宫", "理性疏离")).put("双鱼座", sign("水象", "变动宫", "共情浪漫"));
            JSONObject hd = new JSONObject()
                    .put("1", house("第1宫·命宫", "自我形象")).put("2", house("第2宫·财帛宫", "金钱与价值"))
                    .put("3", house("第3宫·兄弟宫", "沟通与学习")).put("4", house("第4宫·田宅宫", "家庭与归属"))
                    .put("5", house("第5宫·子女宫", "恋爱与创作")).put("6", house("第6宫·奴仆宫", "工作与健康"))
                    .put("7", house("第7宫·夫妻宫", "伴侣与合作")).put("8", house("第8宫·疾厄宫", "深层转化"))
                    .put("9", house("第9宫·迁移宫", "远行与信念")).put("10", house("第10宫·官禄宫", "事业与名望"))
                    .put("11", house("第11宫·福德宫", "朋友与理想")).put("12", house("第12宫·玄秘宫", "潜意识与隐秘"));
            r.put("planets", p).put("signs", s).put("houses", hd)
                    .put("overrides", new JSONObject())
                    .put("template",
                            "【行星】{planet}：{planet_core}。\n"
                                    + "【星座】{sign}（{sign_element}·{sign_mode}）：{sign_style}。\n"
                                    + "【宫位】{house_name}：{house_domain}。\n\n"
                                    + "【综合】{combo}");
        } catch (Exception ignored) {
        }
        return r;
    }

    private JSONObject entry(String core) throws Exception {
        return new JSONObject().put("core", core).put("keywords", new JSONArray());
    }

    private JSONObject sign(String element, String mode, String style) throws Exception {
        return new JSONObject()
                .put("element", element).put("mode", mode).put("style", style);
    }

    private JSONObject house(String name, String domain) throws Exception {
        return new JSONObject().put("name", name).put("domain", domain);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    // ---------- 历史记录 ----------

    private void addHistory(String title, String question) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("time", timeFmt.format(new Date()));
            item.put("title", title);
            item.put("question", question);
            next.put(item);
            for (int i = 0; i < arr.length() && i < HISTORY_LIMIT - 1; i++) {
                next.put(arr.get(i));
            }
            sp.edit().putString(KEY_HISTORY, next.toString()).apply();
            renderHistory();
        } catch (Exception ignored) {
        }
    }

    private void renderHistory() {
        historyList.removeAllViews();
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
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
                if (!q.isEmpty()) line += "\n问：" + q;
                tv.setText(line);
                tv.setTextColor(0xDDFFFFFF);
                tv.setTextSize(13);
                int padV = dp(8);
                tv.setPadding(0, padV, 0, padV);
                historyList.addView(tv);
                View div = new View(this);
                div.setBackgroundColor(0x22FFFFFF);
                historyList.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        } catch (Exception ignored) {
        }
    }

    private void clearHistory() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_HISTORY).apply();
        renderHistory();
        toast("历史已清空");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
