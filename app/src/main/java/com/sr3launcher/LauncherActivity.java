package com.sr3launcher;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Лаунчер Saints Row: The Third: разрешение, графика, счётчик FPS. */
public class LauncherActivity extends Activity {
    private static final String GAME_ACTIVITY = "com.feralinteractive.saintsrow3_android.MainActivity";
    private static final String[] PRESET_BLOCKS = {"BatterySaverPreset", "PerformancePreset", "GraphicsPreset"};
    private static final String[] SETUP = {"HKEY_CURRENT_USER", "Software", "Feral Interactive", "Saints Row The Third", "Setup"};
    private static final String[] D3D_CONFIG = {"HKEY_CURRENT_USER", "Software", "IndirectX", "Direct3D", "Config"};

    private static final int BG = 0xFF120E1C;
    private static final int CARD = 0xFF1D1730;
    private static final int ACCENT = 0xFFB52FE0;
    private static final int TEXT = 0xFFF2EEF8;
    private static final int MUTED = 0xFF9C93B3;

    /** Описание одного выпадающего параметра графики. */
    private static final class Opt {
        final String key, title, type, hint;
        final String[] labels, values;
        final int defIndex;
        Spinner spinner;
        List<String> vals = new ArrayList<String>();

        Opt(String key, String title, String type, String hint, String[] labels, String[] values, int defIndex) {
            this.key = key; this.title = title; this.type = type; this.hint = hint;
            this.labels = labels; this.values = values; this.defIndex = defIndex;
        }
    }

    private static final String[] L3 = {"Низкие", "Средние", "Высокие"};
    private static final String[] V3 = {"low", "medium", "high"};
    private static final String[] L4 = {"Выкл.", "Низкое", "Среднее", "Высокое"};
    private static final String[] V4 = {"off", "low", "medium", "high"};
    private static final String[] LS = {"Минимум", "Низкая", "Средняя", "Высокая"};
    private static final String[] VS = {"very_low", "low", "medium", "high"};

    private final Opt[] opts = new Opt[] {
        new Opt("GfxShadow", "Качество теней", "string", "Разрешение и дальность теневых карт", L3, V3, 1),
        new Opt("GfxLighting", "Освещение", "string", "Качество динамического освещения", L3, V3, 2),
        new Opt("GfxSSAO", "Затенение углов (SSAO)", "string", "Мягкие тени в складках и щелях", L4, V4, 0),
        new Opt("GfxSSLR", "Отражения в экране (SSLR)", "string", "Отражения на лужах, стекле и машинах", L4, V4, 0),
        new Opt("GfxVolumetricFog", "Объёмный туман", "string", "Лучи света и дымка", L4, V4, 1),
        new Opt("GfxAA", "Сглаживание", "string", "TAA чище, но чуть мылит; FXAA резче; выкл. даёт лесенки",
            new String[] {"Выкл.", "FXAA", "TAA"}, new String[] {"off", "fxaa", "taa"}, 2),
        new Opt("GfxAnisotropic", "Фильтрация текстур", "string", "Чёткость текстур на дороге и стенах под углом",
            new String[] {"2x", "4x", "8x", "16x"}, new String[] {"2x", "4x", "8x", "16x"}, 2),
        new Opt("GfxLodMultiplier", "Детализация вдали (LOD)", "double", "Больше значение: модели упрощаются позже",
            new String[] {"0.5x", "0.75x", "1x", "1.5x", "2x", "3x"},
            new String[] {"0.500000", "0.750000", "1.000000", "1.500000", "2.000000", "3.000000"}, 2),
        new Opt("GfxSceneCharacters", "Персонажи на сцене", "string", "Количество и детализация NPC", LS, VS, 2),
        new Opt("GfxSceneVehicles", "Транспорт на сцене", "string", "Количество и детализация машин", LS, VS, 2),
        new Opt("GfxSceneMeshes", "Объекты и здания", "string", "Дальность прорисовки мелких объектов", LS, VS, 2),
        new Opt("GfxSceneTrees", "Деревья", "string", "Дальность и густота деревьев", LS, VS, 2),
        new Opt("GfxSceneUndergrowth", "Трава и кусты", "string", "Густота растительности", LS, VS, 1),
        new Opt("GfxSceneVFX", "Эффекты (VFX)", "string", "Дым, огонь, взрывы, частицы", LS, VS, 2),
    };

    /** Дополнительный параметр из файла настроек, которого нет в меню игры. */
    private static final class Ex {
        final String key, title, hint, type;
        final String[] path, labels, values;
        final boolean presets;
        final int defIndex;
        boolean special, present;
        Spinner spinner;
        List<String> vals = new ArrayList<String>();

        Ex(String key, String title, String hint, String[] path, boolean presets, String type, String[] labels, String[] values, int defIndex) {
            this.key = key; this.title = title; this.hint = hint; this.path = path; this.presets = presets;
            this.type = type; this.labels = labels; this.values = values; this.defIndex = defIndex;
        }
    }

    private static final String[] OFFON = {"Выкл.", "Вкл."};
    private static final String[] V01 = {"0", "1"};
    private static final String[] GO = join(SETUP, "GameOptions");
    private static final String[] DRS = join(SETUP, "DynamicResolutionScaling");
    private static final String[] MOT = join(SETUP, "MotionAimSettings");
    private static final String[] GP = join(SETUP, "GraphicsPreset");

    private static Ex onoff(String key, String title, String hint, String[] path, int def) {
        return new Ex(key, title, hint, path, false, "integer", OFFON, V01, def);
    }
    private static Ex num(String key, String title, String hint, String[] path, boolean presets, String type, String[] labels, String[] values, int def) {
        return new Ex(key, title, hint, path, presets, type, labels, values, def);
    }

    private final Ex[] exPost = new Ex[] {
        num("GfxDoF", "Глубина резкости", "Размытие дальнего и ближнего плана (значения low/medium/high не проверены)", GO, false, "string", L4, V4, 0),
        num("GfxFilmGrain", "Зернистость плёнки", "Шум поверх картинки", GO, false, "string", L4, V4, 0),
        num("GfxMotionBlur", "Размытие движения", "Смазывание при быстром повороте камеры", GO, false, "string", L4, V4, 0),
    };
    private final Ex[] exRange = new Ex[] {
        num("GfxFadeDistMultiplier", "Дальность прорисовки", "Множитель расстояния, на котором объекты исчезают", SETUP, false, "double",
            new String[] {"0.5x", "0.75x", "1x", "1.5x", "2x", "3x"},
            new String[] {"0.500000", "0.750000", "1.000000", "1.500000", "2.000000", "3.000000"}, 2),
        num("GfxCubemapSpecFadeDistSqStart", "Отражения: начало затухания", "Квадрат расстояния, с которого отражения слабеют", GP, true, "double",
            new String[] {"1000", "3000", "5000", "10000", "20000"},
            new String[] {"1000.000000", "3000.000000", "5000.000000", "10000.000000", "20000.000000"}, 2),
        num("GfxCubemapSpecFadeDistSqEnd", "Отражения: конец затухания", "Квадрат расстояния, где отражения пропадают совсем", GP, true, "double",
            new String[] {"3000", "5000", "10000", "20000", "40000"},
            new String[] {"3000.000000", "5000.000000", "10000.000000", "20000.000000", "40000.000000"}, 2),
    };
    private final Ex exMin = num("MinScaling", "Мин. масштаб разрешения", "Ниже этого DRS не опускает (нужно включённое динамическое разрешение)", DRS, false, "double",
        new String[] {"50%", "60%", "70%", "80%", "90%", "100%"},
        new String[] {"0.500000", "0.600000", "0.700000", "0.800000", "0.900000", "1.000000"}, 1);
    private final Ex exMax = num("MaxScaling", "Макс. масштаб разрешения", "Выше этого DRS не поднимается", DRS, false, "double",
        new String[] {"50%", "60%", "70%", "80%", "90%", "100%"},
        new String[] {"0.500000", "0.600000", "0.700000", "0.800000", "0.900000", "1.000000"}, 3);
    private final Ex[] exDrs = new Ex[] {
        exMin, exMax,
        num("FPSTarget", "Цель DRS по FPS", "Под какую частоту DRS подгоняет разрешение", DRS, false, "double",
            new String[] {"30", "40", "60", "90", "120"},
            new String[] {"30.000000", "40.000000", "60.000000", "90.000000", "120.000000"}, 0),
        num("StepCount", "Шагов масштаба", "Сколько ступеней между минимумом и максимумом", DRS, false, "integer",
            new String[] {"3", "5", "8", "10"}, new String[] {"3", "5", "8", "10"}, 1),
        num("DropDelayFrames", "Задержка снижения (кадров)", "Меньше: DRS реагирует на просадку быстрее", DRS, false, "integer",
            new String[] {"3", "6", "12", "24"}, new String[] {"3", "6", "12", "24"}, 1),
        num("RaiseDelayFrames", "Задержка повышения (кадров)", "Больше: разрешение возвращается медленнее и скачет реже", DRS, false, "integer",
            new String[] {"4", "8", "16", "32"}, new String[] {"4", "8", "16", "32"}, 0),
        onoff("UseGPUTime", "DRS по времени GPU", "Ориентироваться на время кадра GPU, а не CPU", DRS, 1),
        onoff("SyncWithFramerateManager", "DRS вместе с менеджером кадров", "Согласовывать масштаб с лимитом FPS", DRS, 1),
    };
    private final Ex[] exFrame = new Ex[] {
        onoff("EnableFramerateManager", "Менеджер частоты кадров", "Игра сама меняет FPS между отзывчивым и простаивающим режимом", D3D_CONFIG, 1),
        num("GfxResponsiveFps", "FPS в активной игре", "Лимит менеджера при действии", GP, true, "integer",
            new String[] {"30", "40", "60", "90", "120"}, new String[] {"30", "40", "60", "90", "120"}, 0),
        num("GfxIdleFps", "FPS в простое", "Лимит в меню и когда ничего не происходит", GP, true, "integer",
            new String[] {"15", "20", "30", "60"}, new String[] {"15", "20", "30", "60"}, 2),
        num("FramerateManagerResponsiveFPSDuration", "Время активного режима, мс", "Сколько держится высокий FPS после ввода", D3D_CONFIG, false, "integer",
            new String[] {"1000", "5000", "10000", "30000"}, new String[] {"1000", "5000", "10000", "30000"}, 1),
    };
    private final Ex[] exEngine = new Ex[] {
        onoff("EnableIdxD3D11Threaded", "Многопоточный рендер", "Может поднять FPS, на некоторых GPU вызывает сбои", D3D_CONFIG, 1),
        num("IdxD3D11ThreadedMaxQueuedFrames", "Очередь кадров", "0: минимум задержки, больше: плавнее, но с задержкой управления", D3D_CONFIG, false, "integer",
            new String[] {"0", "1", "2", "3"}, new String[] {"0", "1", "2", "3"}, 0),
        onoff("VulkanUseSeparateSubmitThread", "Отдельный поток отправки (Vulkan)", "Разгружает главный поток", D3D_CONFIG, 1),
        onoff("VulkanUseSecondaryCmdBuffersForGraphicsPass", "Вторичные буферы команд (Vulkan)", "Экспериментально, по умолчанию выкл.", D3D_CONFIG, 0),
        onoff("VulkanRenderToBackbuffer", "Рендер сразу в бэкбуфер", "Экономит проход копирования", D3D_CONFIG, 1),
        onoff("EnableShaderDeduplicator", "Дедупликация шейдеров", "Меньше шейдеров в кэше", D3D_CONFIG, 1),
        onoff("EnableF3DWarmer", "Прогрев шейдеров", "Прогревает кэш, меньше подлагов в начале", D3D_CONFIG, 1),
        onoff("SingleThreadedWarmer", "Прогрев в одном потоке", "Выкл. быстрее прогревает, но грузит все ядра", D3D_CONFIG, 1),
    };
    private final Ex[] exCtl = new Ex[] {
        onoff("VcpEnableGyroControls", "Гироскоп для прицела", "Прицеливание наклоном телефона", GO, 0),
        onoff("Enabled", "Прицел по движению (MotionAim)", "Тонкая настройка ниже", MOT, 0),
        num("SensitivityX", "MotionAim: чувствительность X", "", MOT, false, "double",
            new String[] {"100", "200", "400", "600", "800"},
            new String[] {"100.000000", "200.000000", "400.000000", "600.000000", "800.000000"}, 2),
        num("SensitivityY", "MotionAim: чувствительность Y", "", MOT, false, "double",
            new String[] {"100", "200", "400", "600", "800"},
            new String[] {"100.000000", "200.000000", "400.000000", "600.000000", "800.000000"}, 2),
        num("SmoothFrames", "MotionAim: сглаживание (кадров)", "Больше: плавнее, но с запаздыванием", MOT, false, "integer",
            new String[] {"1", "3", "5", "8"}, new String[] {"1", "3", "5", "8"}, 1),
        onoff("VcpEnableIntegratedSteering", "Руль наклоном", "Управление машиной наклоном телефона", GO, 0),
        onoff("VcpAimLockOnlyIfAiming", "Автоприцел только при прицеливании", "", GO, 1),
        onoff("VcpAimLockOnlyIfMoving", "Автоприцел только в движении", "", GO, 1),
        onoff("GamepadVehicleSnap", "Привязка руля геймпада к курсу", "Помощь поворота машины на геймпаде", GO, 0),
        onoff("AirplaneAssist", "Помощь при управлении самолётом", "", GO, 1),
        onoff("CrouchToggle", "Приседание переключателем", "", GO, 0),
        onoff("VCPQTEEnableAccessibilityMode", "Упрощённые QTE", "", GO, 0),
        onoff("MinimapStatic", "Неподвижная миникарта", "Карта не вращается за камерой", GO, 0),
        onoff("VcpMoveStickLocationIsDynamic", "Плавающий стик движения", "Стик появляется там, где коснулись", GO, 1),
    };
    private final Ex[] exSys = new Ex[] {
        onoff("RenderUnderNotch", "Рисовать под вырезом экрана", "", SETUP, 1),
        onoff("AllowAutorotate", "Автоповорот", "", SETUP, 1),
        onoff("AllowPausing", "Пауза по кнопке", "", SETUP, 1),
        onoff("PauseOnSuspend", "Пауза при сворачивании", "", SETUP, 1),
        onoff("AllowSendCrashReports", "Отправлять отчёты о сбоях", "", SETUP, 0),
        onoff("AllowSendUsageData", "Отправлять данные об использовании", "", SETUP, 0),
        onoff("DoShowBatteryOptimisationsWarning", "Предупреждение об оптимизации батареи", "", SETUP, 1),
        onoff("ShowStartupLogos", "Заставки при запуске", "Выкл.: пропуск логотипов (ключ не проверен)", SETUP, 1),
        onoff("EnableKillShotCamera", "Камера убийственного выстрела", "Замедленная камера при добивании (ключ не проверен)", SETUP, 1),
        onoff("EnableCrashCamera", "Камера аварии", "Показ аварии со стороны (ключ не проверен)", SETUP, 1),
    };
    private final Ex[][] exAll = {exPost, exRange, exDrs, exFrame, exEngine, exCtl, exSys};

    private static final String PKG = "com.feralinteractive.saintsrow3_android";
    private String prefPath, bakPath;
    private SharedPreferences launcherPrefs;
    private int nativeW = 2400, nativeH = 1080;
    private int[] resHeights;
    private RadioGroup resGroup;
    private RadioGroup hudGroup;
    private SeekBar sharpSeek;
    private TextView sharpLabel;
    private Switch swCube, swHiRes, swDynRes;
    private Spinner fpsSpinner;
    private int[] fpsValues;
    private TextView status;
    private Hw hw;
    private TextView hwText;
    private Spinner upTech;
    private SeekBar upSharpSeek;
    private TextView upSharpLabel;
    private Switch swCasPs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout setupCard, setupBody;
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    // ---------------------------------------------------------------- UI helpers

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        c.setPadding(dp(16), dp(12), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        TextView h = text(title.toUpperCase(Locale.getDefault()), 13, ACCENT, true);
        h.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(8);
        c.addView(h, hp);
        return c;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 16 : 14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? ACCENT : 0xFF2E2547);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        return b;
    }

    private void addOptRow(LinearLayout parent, Opt o) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(o.title, 15, TEXT, false));
        names.addView(text(o.hint, 11, MUTED, false));
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        Spinner sp = new Spinner(this);
        o.spinner = sp;
        row.addView(sp, new LinearLayout.LayoutParams(dp(150), -2));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8);
        parent.addView(row, rp);
    }

    // ---------------------------------------------------------------- первичная настройка

    private boolean pkgInstalled(String p) {
        try { getPackageManager().getPackageInfo(p, 0); return true; } catch (Exception e) { return false; }
    }

    private int globalInt(String key) {
        try { return Settings.Global.getInt(getContentResolver(), key, -1); } catch (Exception e) { return -1; }
    }

    private void open(Intent i) {
        try { startActivity(i); } catch (Exception e) { say("Не удалось открыть: " + e.getMessage()); }
    }

    private void openSettings(String action) {
        open(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void openShizuku() {
        Intent i = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
        if (i == null) { say("Shizuku не установлен."); return; }
        open(i);
    }

    private void downloadShizuku() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + SHIZUKU_PKG)));
        } catch (Exception e) {
            open(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases")));
        }
    }

    private void launchGameOnce() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(PKG, GAME_ACTIVITY);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        open(i);
    }

    private interface Act { void go(); }

    private void setupRow(String title, String hint, boolean ok, String btn, final Act act) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text(ok ? "✓" : "✕", 18, ok ? 0xFF5BD68A : 0xFFFF6B7A, true);
        row.addView(mark, new LinearLayout.LayoutParams(dp(26), -2));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 14, TEXT, false));
        if (!ok && hint != null) names.addView(text(hint, 11, MUTED, false));
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!ok && btn != null && act != null) {
            Button b = button(btn, false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { act.go(); } });
            row.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8);
        setupBody.addView(row, rp);
    }

    /** Пересчитывает шаги первичной настройки и прячет карточку, когда всё готово. */
    private void refreshSetup() {
        if (setupBody == null) return;
        setupBody.removeAllViews();
        boolean shInst = pkgInstalled(SHIZUKU_PKG);
        boolean running = Fs.running();
        boolean perm = Fs.ready();
        boolean dev = globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1;
        boolean adb = globalInt(Settings.Global.ADB_ENABLED) == 1 || globalInt("adb_wifi_enabled") == 1;
        boolean gameInst = pkgInstalled(PKG);
        boolean prefs = perm && gameInst && Fs.exists(prefPath);

        if (perm && gameInst && prefs) { setupCard.setVisibility(View.GONE); return; }
        setupCard.setVisibility(View.VISIBLE);
        if (!perm && !Fs.rootChecking()) showAccessBanner();

        boolean viaRoot = Fs.rootOk();
        setupBody.addView(text("Лаунчеру нужен доступ к папке игры: через Shizuku или через root. Пройдите шаги сверху вниз.", 11, MUTED, false));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, dp(8));
        setupBody.addView(new View(this), gap);

        if (viaRoot) {
            setupRow("Root-доступ получен", null, true, null, null);
        } else {
            setupRow("Есть root? Выдайте его лаунчеру",
                Fs.rootChecking() ? "Проверяю root…" : "В Magisk/KernelSU/APatch разрешите root для «SR3 Лаунчер» и нажмите «Проверить». Тогда Shizuku не нужен",
                false, "Проверить", new Act() { public void go() { Fs.retryRoot(); refreshSetup(); } });
            setupRow("Или Shizuku установлен", "Скачайте приложение Shizuku (Google Play или GitHub)", shInst, "Скачать", new Act() { public void go() { downloadShizuku(); } });
            if (!running) {
                setupRow("Режим разработчика включён",
                    "Настройки → О телефоне → «Номер сборки», нажмите 7 раз", dev, "О телефоне",
                    new Act() { public void go() { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS); } });
                setupRow("Отладка включена (беспроводная или по USB)",
                    "Для разработчиков → «Беспроводная отладка» (нужен Wi‑Fi) или «Отладка по USB»", adb, "Для разработчиков",
                    new Act() { public void go() { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS); } });
            }
            setupRow("Shizuku запущен",
                "В Shizuku: «Запуск через беспроводную отладку» → «Сопряжение» → «Запустить». После перезагрузки телефона запускать снова",
                running, "Открыть Shizuku", new Act() { public void go() { openShizuku(); } });
            setupRow("Доступ лаунчеру выдан",
                running ? "Нажмите «Разрешить» в окне Shizuku. Если окна нет, включите лаунчер в списке приложений Shizuku" : "Сначала запустите Shizuku",
                perm, running ? "Запросить" : null, new Act() { public void go() { Fs.requestAccess(); refreshSetup(); } });
        }
        setupRow("Игра установлена", "Установите Saints Row: The Third", gameInst, null, null);
        setupRow("Файл настроек игры создан",
            "Запустите игру один раз до главного меню и закройте её", prefs, gameInst && perm ? "Запустить игру" : null,
            new Act() { public void go() { launchGameOnce(); } });
    }

    private void addExCard(LinearLayout parent, String title, Ex[] list) {
        LinearLayout c = card(parent, title);
        for (Ex e : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            names.addView(text(e.title, 15, TEXT, false));
            if (e.hint.length() > 0) names.addView(text(e.hint, 11, MUTED, false));
            row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
            Spinner sp = new Spinner(this);
            e.spinner = sp;
            row.addView(sp, new LinearLayout.LayoutParams(dp(150), -2));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(8);
            c.addView(row, rp);
        }
    }

    private void loadExtras(String xml) {
        for (Ex[] grp : exAll) for (Ex e : grp) {
            String cur = null;
            if (xml != null) cur = PrefsEditor.getInKey(xml, e.key, e.presets ? GP : e.path);
            e.present = cur != null;
            e.vals.clear();
            List<String> labels = new ArrayList<String>();
            for (int i = 0; i < e.values.length; i++) { e.vals.add(e.values[i]); labels.add(e.labels[i]); }
            int idx = e.defIndex;
            if (cur != null) {
                idx = -1;
                for (int i = 0; i < e.values.length; i++) if (sameValue(e.values[i], cur, e.type)) { idx = i; break; }
                if (idx < 0) {
                    boolean full = false;
                    try { full = e.special && Double.parseDouble(cur) >= 0.999; } catch (Exception ignored) { }
                    if (full) idx = e.defIndex;
                    else { e.vals.add(cur); labels.add("Текущее: " + cur); idx = labels.size() - 1; }
                }
            }
            fillSpinner(e.spinner, labels, idx);
        }
    }

    private String exValue(Ex e) {
        int si = e.spinner.getSelectedItemPosition();
        if (si < 0 || si >= e.vals.size()) return null;
        return e.vals.get(si);
    }

    private String applyExtras(String xml) {
        for (Ex[] grp : exAll) for (Ex e : grp) {
            if (e.special) continue;
            String v = exValue(e);
            if (v == null) continue;
            // ключ, которого нет в файле, не создаём, пока значение не изменено
            if (!e.present && sameValue(e.values[e.defIndex], v, e.type)) continue;
            if (e.presets) {
                for (String blk : PRESET_BLOCKS) xml = PrefsEditor.setInKey(xml, e.key, e.type, v, join(SETUP, blk));
            } else {
                xml = PrefsEditor.setInKey(xml, e.key, e.type, v, e.path);
            }
        }
        return xml;
    }

    private void fillSpinner(Spinner sp, List<String> labels, int sel) {
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(Math.max(0, Math.min(sel, labels.size() - 1)));
    }

    private Switch toggle(LinearLayout parent, String title, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 15, TEXT, false));
        names.addView(text(hint, 11, MUTED, false));
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8);
        parent.addView(row, rp);
        return sw;
    }

    private RadioButton radio(RadioGroup g, String label, int id) {
        RadioButton r = new RadioButton(this);
        r.setText(label);
        r.setId(id);
        r.setTextColor(TEXT);
        r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        g.addView(r);
        return r;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        launcherPrefs = getSharedPreferences("sr3launcher", Context.MODE_PRIVATE);
        String dir = "/sdcard/Android/data/" + PKG + "/files/feral_app_support";
        prefPath = dir + "/preferences";
        bakPath = dir + "/preferences.sr3launcher.bak";
        Fs.init(new Runnable() { @Override public void run() { handler.post(new Runnable() { @Override public void run() { loadFromFile(); refreshSetup(); } }); } });

        try {
            android.graphics.Rect r = getWindowManager().getMaximumWindowMetrics().getBounds();
            nativeW = Math.max(r.width(), r.height());
            nativeH = Math.min(r.width(), r.height());
        } catch (Throwable ignored) { }

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        loadFromFile();
        refreshSetup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resGroup != null && Fs.ready() && !fsWasReady) { fsWasReady = true; loadFromFile(); }
        if (setupBody != null) {
            if (!Fs.ready()) Fs.init(new Runnable() { @Override public void run() { handler.post(new Runnable() { @Override public void run() { loadFromFile(); refreshSetup(); } }); } });
            refreshSetup();
        }
    }

    private boolean fsWasReady = false;

    @Override
    protected void onDestroy() {
        Fs.release();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // шапка
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(24), dp(10), dp(24), dp(4));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lgp.rightMargin = dp(14);
        head.addView(logo, lgp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("SAINTS ROW: THE THIRD", 22, Color.WHITE, true));
        titles.addView(text("Лаунчер: разрешение, графика, счётчик FPS и скрытые параметры игры", 13, MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout credit = new LinearLayout(this);
        credit.setOrientation(LinearLayout.VERTICAL);
        credit.setGravity(Gravity.END);
        credit.addView(text("Автор: Tsifrokayf", 13, TEXT, false));
        TextView gh = text("github.com/Tsifrokayf", 13, ACCENT, true);
        gh.setPaintFlags(gh.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        gh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Tsifrokayf")));
                } catch (Exception e) { say("Не удалось открыть ссылку"); }
            }
        });
        credit.addView(gh);
        head.addView(credit, new LinearLayout.LayoutParams(-2, -2));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(dp(20), dp(8), dp(20), dp(8));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(0, -2, 1f);
        lpl.rightMargin = dp(8);
        LinearLayout.LayoutParams lpr = new LinearLayout.LayoutParams(0, -2, 1.25f);
        lpr.leftMargin = dp(8);
        cols.addView(left, lpl);
        cols.addView(right, lpr);
        scroll.addView(cols, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // --- первичная настройка: проверка Shizuku, отладки и игры
        setupCard = card(left, "Первичная настройка");
        setupBody = new LinearLayout(this);
        setupBody.setOrientation(LinearLayout.VERTICAL);
        setupCard.addView(setupBody);

        // --- авто-подбор по железу
        hw = Hw.detect(this);
        LinearLayout cAuto = card(left, "Подбор под ваше железо");
        hwText = text(hw.describe(), 12, MUTED, false);
        cAuto.addView(hwText);
        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        Button a60 = button("Авто · 60 FPS", false);
        Button a30 = button("Авто · 30 FPS", false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
        ap.topMargin = dp(8);
        ap.rightMargin = dp(6);
        autoRow.addView(a60, ap);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(0, -2, 1f);
        ap2.topMargin = dp(8);
        autoRow.addView(a30, ap2);
        cAuto.addView(autoRow);
        cAuto.addView(text("Стартовые значения по классу GPU. Это не замер: после подбора проверьте в игре со счётчиком FPS и поправьте вручную.", 11, MUTED, false));
        cAuto.addView(text("Пресеты графики (FPS-лимит не меняют):", 12, TEXT, false));
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int pi = i;
            Button pb = button(PRESET_NAMES[i], false);
            pb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pb.setPadding(dp(4), dp(8), dp(4), dp(8));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -2, 1f);
            pp.topMargin = dp(6);
            if (i < PRESET_NAMES.length - 1) pp.rightMargin = dp(4);
            presetRow.addView(pb, pp);
            pb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyPreset(pi); } });
        }
        cAuto.addView(presetRow);
        a60.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { autoTune(60); } });
        a30.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { autoTune(30); } });

        // --- разрешение
        LinearLayout cRes = card(left, "Разрешение рендера");
        resHeights = new int[] {nativeH, 1280, 1080, 720, 540};
        resGroup = new RadioGroup(this);
        resGroup.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"Нативное", "1280p", "1080p", "720p", "540p"};
        for (int i = 0; i < resHeights.length; i++) {
            int w = widthFor(resHeights[i]);
            String label = names[i] + "   " + w + "×" + resHeights[i];
            if (i == 0) label = "Нативное (экран)   " + nativeW + "×" + nativeH;
            radio(resGroup, label, 100 + i);
        }
        cRes.addView(resGroup);
        cRes.addView(text("Чем ниже разрешение, тем выше FPS и холоднее телефон. Нативное совпадает с 1280p, если экран 1280 пикселей по высоте.", 11, MUTED, false));
        swDynRes = toggle(cRes, "Динамическое разрешение", "Игра сама снижает до 60–80% при просадках FPS");

        // --- FPS
        LinearLayout cFps = card(left, "Частота кадров и счётчик");
        List<Integer> rates = new ArrayList<Integer>();
        float maxHz = 60f;
        try {
            Display d = getDisplay();
            for (Display.Mode m : d.getSupportedModes()) maxHz = Math.max(maxHz, m.getRefreshRate());
        } catch (Throwable ignored) { }
        for (int hz : new int[] {30, 40, 60, 90, 120}) if (hz <= maxHz + 1f) rates.add(hz);
        fpsValues = new int[rates.size()];
        List<String> fpsLabels = new ArrayList<String>();
        for (int i = 0; i < rates.size(); i++) { fpsValues[i] = rates.get(i); fpsLabels.add(rates.get(i) + " FPS"); }
        LinearLayout fpsRow = new LinearLayout(this);
        fpsRow.setOrientation(LinearLayout.HORIZONTAL);
        fpsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout fn = new LinearLayout(this);
        fn.setOrientation(LinearLayout.VERTICAL);
        fn.addView(text("Лимит кадров", 15, TEXT, false));
        fn.addView(text("Выше 60 может греть телефон", 11, MUTED, false));
        fpsRow.addView(fn, new LinearLayout.LayoutParams(0, -2, 1f));
        fpsSpinner = new Spinner(this);
        fillSpinner(fpsSpinner, fpsLabels, 2);
        fpsRow.addView(fpsSpinner, new LinearLayout.LayoutParams(dp(150), -2));
        LinearLayout.LayoutParams frp = new LinearLayout.LayoutParams(-1, -2);
        frp.bottomMargin = dp(10);
        cFps.addView(fpsRow, frp);
        cFps.addView(text("Счётчик FPS в игре", 15, TEXT, false));
        hudGroup = new RadioGroup(this);
        hudGroup.setOrientation(RadioGroup.HORIZONTAL);
        radio(hudGroup, "Выключен", 200);
        radio(hudGroup, "Компактный", 201);
        radio(hudGroup, "Полный", 202);
        cFps.addView(hudGroup);
        cFps.addView(text("Встроенная статистика движка: FPS, время кадра, нагрузка.", 11, MUTED, false));

        // --- апскейлер движка
        LinearLayout cUp = card(left, "Апскейлер игры");
        LinearLayout upRow = new LinearLayout(this);
        upRow.setOrientation(LinearLayout.HORIZONTAL);
        upRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout un = new LinearLayout(this);
        un.setOrientation(LinearLayout.VERTICAL);
        un.addView(text("Метод", 15, TEXT, false));
        un.addView(text("Как игра растягивает картинку на экран при пониженном разрешении", 11, MUTED, false));
        upRow.addView(un, new LinearLayout.LayoutParams(0, -2, 1f));
        upTech = new Spinner(this);
        List<String> tl = new ArrayList<String>();
        tl.add("0 (по умолчанию)"); tl.add("1"); tl.add("2"); tl.add("3"); tl.add("4");
        fillSpinner(upTech, tl, 0);
        upRow.addView(upTech, new LinearLayout.LayoutParams(dp(150), -2));
        cUp.addView(upRow);
        cUp.addView(text("В движке есть билинейный, CAS и EASU/RCAS (как FSR 1). Какой номер какой метод, надёжно определить нельзя: сравните картинку на 540p по глазам.", 11, MUTED, false));
        upSharpLabel = text("Резкость апскейлера: 50%", 15, TEXT, false);
        cUp.addView(upSharpLabel);
        upSharpSeek = new SeekBar(this);
        upSharpSeek.setMax(100);
        upSharpSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s2, int p, boolean u) { upSharpLabel.setText("Резкость апскейлера: " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar s2) { }
            @Override public void onStopTrackingTouch(SeekBar s2) { }
        });
        cUp.addView(upSharpSeek);
        swCasPs = toggle(cUp, "CAS через пиксельный шейдер", "Альтернативный режим резкости, может быть быстрее или медленнее");

        // --- графика
        LinearLayout cGfx = card(right, "Графика");
        for (Opt o : opts) addOptRow(cGfx, o);

        LinearLayout cMore = card(right, "Резкость и текстуры");
        sharpLabel = text("Резкость (CAS): 5%", 15, TEXT, false);
        cMore.addView(sharpLabel);
        sharpSeek = new SeekBar(this);
        sharpSeek.setMax(100);
        sharpSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { sharpLabel.setText("Резкость (CAS): " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.bottomMargin = dp(10);
        cMore.addView(sharpSeek, sp);
        swCube = toggle(cMore, "Кубические отражения", "Отражения окружения на металле и стекле");
        swHiRes = toggle(cMore, "Текстуры персонажей высокого разрешения", "Чёткие лица и одежда, больше видеопамяти");

        // --- дополнительные параметры (скрытые в игре)
        exMin.special = true; exMax.special = true;
        addExCard(left, "Пост-обработка", exPost);
        addExCard(left, "Дальность прорисовки и отражения", exRange);
        addExCard(left, "Динамическое разрешение: тонко", exDrs);
        addExCard(right, "Менеджер кадров", exFrame);
        addExCard(right, "Движок и потоки (риск сбоев)", exEngine);
        addExCard(right, "Управление", exCtl);
        addExCard(right, "Система", exSys);

        // нижняя панель
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(10), dp(24), dp(12));
        status = text("", 12, MUTED, false);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        Button reset = button("Вернуть исходные", false);
        Button save = button("Сохранить", false);
        Button play = button("▶  ИГРАТЬ", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = dp(10);
        bar.addView(reset, bp);
        bar.addView(save, bp);
        bar.addView(play, bp);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        reset.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { restoreBackup(); } });
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (applyToFile()) say("Настройки сохранены"); } });
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { play(); } });

        setContentView(root);
    }

    private boolean noAccessShown;

    private void say(String s) {
        status.setText(s);
        boolean bad = s != null && s.startsWith("Нет доступа");
        status.clearAnimation();
        if (bad) {
            status.setTextColor(0xFFFF3B4E);
            status.setTypeface(Typeface.DEFAULT_BOLD);
            AlphaAnimation a = new AlphaAnimation(1f, 0.15f);
            a.setDuration(500);
            a.setRepeatMode(Animation.REVERSE);
            a.setRepeatCount(Animation.INFINITE);
            status.startAnimation(a);
        } else {
            status.setTextColor(MUTED);
            status.setTypeface(Typeface.DEFAULT);
        }
    }

    /** Баннер с предложением поставить и настроить Shizuku (или выдать root) и коротким гайдом. */
    private void showAccessBanner() {
        if (noAccessShown || isFinishing()) return;
        noAccessShown = true;
        String guide = "Лаунчер правит файл настроек игры, а Android не даёт обычным приложениям писать в папку другого приложения. Нужен один из двух способов:\n\n"
            + "ВАРИАНТ A: root\nЕсли у вас Magisk, KernelSU или APatch, выдайте root-доступ приложению «SR3 Лаунчер» и нажмите «Проверить root».\n\n"
            + "ВАРИАНТ B: Shizuku (без root)\n"
            + "1. Установите Shizuku (кнопка «Скачать Shizuku»).\n"
            + "2. Настройки → О телефоне → «Номер сборки», нажмите 7 раз (режим разработчика).\n"
            + "3. Для разработчиков → включите «Беспроводная отладка» (нужен Wi‑Fi).\n"
            + "4. В Shizuku: «Запуск через беспроводную отладку» → «Сопряжение», введите код из настроек, затем «Запустить».\n"
            + "5. Вернитесь в лаунчер и нажмите «Разрешить» в окне Shizuku.\n\n"
            + "Без root Shizuku придётся запускать заново после перезагрузки телефона.";
        new AlertDialog.Builder(this)
            .setTitle("Нет доступа к файлам игры")
            .setMessage(guide)
            .setPositiveButton("Скачать Shizuku", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { downloadShizuku(); }
            })
            .setNeutralButton("Проверить root", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { Fs.retryRoot(); refreshSetup(); }
            })
            .setNegativeButton("Позже", null)
            .show();
    }

    // ---------------------------------------------------------------- file IO

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private String readXml() {
        try {
            byte[] d = Fs.read(prefPath);
            return d == null ? null : new String(d, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private boolean writeBytes(String path, byte[] data) {
        return Fs.write(path, data);
    }

    private int widthFor(int h) {
        int w = Math.round(h * (nativeW / (float) nativeH));
        return w - (w % 2);
    }

    // ---------------------------------------------------------------- state <-> UI

    private void loadFromFile() {
        // сбрасываем возможный «нестандартный» пункт разрешения от прошлой загрузки
        View oldCustom = resGroup.findViewById(110);
        if (oldCustom != null) resGroup.removeView(oldCustom);
        if (resHeights.length > 5) {
            int[] n = new int[5];
            System.arraycopy(resHeights, 0, n, 0, 5);
            resHeights = n;
        }
        String xml = readXml();
        if (xml == null) {
            say(Fs.ready() ? "Файл настроек ещё не создан. Запустите игру один раз, затем откройте лаунчер снова." : "Нет доступа к файлам игры (нужен root или Shizuku). Пройдите шаги в карточке «Первичная настройка».");
            resGroup.check(100);
            hudGroup.check(200);
            for (Opt o : opts) {
                o.vals.clear();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < o.values.length; i++) { o.vals.add(o.values[i]); labels.add(o.labels[i]); }
                fillSpinner(o.spinner, labels, o.defIndex);
            }
            sharpSeek.setProgress(5);
            swCube.setChecked(true);
            loadExtras(null);
            return;
        }
        // разрешение
        String hs = PrefsEditor.getGlobal(xml, "FixedScreenHeight");
        int h = nativeH;
        try { h = Integer.parseInt(hs); } catch (Exception ignored) { }
        int choice = launcherPrefs.getInt("res_choice", -1);
        int sel = -1;
        if (choice >= 0 && choice < resHeights.length && resHeights[choice] == h) sel = choice;
        if (sel < 0) for (int i = 0; i < resHeights.length; i++) if (resHeights[i] == h) { sel = i; break; }
        if (sel < 0) {
            // нестандартная высота: добавляем отдельный пункт
            RadioButton r = radio(resGroup, "Текущее   " + widthFor(h) + "×" + h, 110);
            int[] n = new int[resHeights.length + 1];
            System.arraycopy(resHeights, 0, n, 0, resHeights.length);
            n[resHeights.length] = h;
            resHeights = n;
            sel = resHeights.length - 1;
            r.setChecked(true);
        }
        resGroup.check(sel == resHeights.length - 1 && sel >= 5 ? 110 : 100 + sel);
        String dre = PrefsEditor.getInKey(xml, "Enable", "HKEY_CURRENT_USER", "Software", "Feral Interactive", "Saints Row The Third", "Setup", "DynamicResolutionScaling");
        swDynRes.setChecked("1".equals(dre));

        // FPS
        int fps = 60;
        try { fps = Integer.parseInt(PrefsEditor.getGlobal(xml, "GfxOverrideFps")); } catch (Exception ignored) { }
        int fi = 0;
        for (int i = 0; i < fpsValues.length; i++) if (fpsValues[i] <= fps) fi = i;
        fpsSpinner.setSelection(fi);
        boolean show = "1".equals(PrefsEditor.getInKey(xml, "ShowFeralGUI", D3D_CONFIG));
        boolean compact = "1".equals(PrefsEditor.getInKey(xml, "CompactFeralGUI", D3D_CONFIG));
        hudGroup.check(!show ? 200 : (compact ? 201 : 202));

        // графика берём из активного пресета (GraphicsPreset)
        String[] gp = join(SETUP, "GraphicsPreset");
        for (Opt o : opts) {
            String cur = PrefsEditor.getInKey(xml, o.key, gp);
            o.vals.clear();
            List<String> labels = new ArrayList<String>();
            for (int i = 0; i < o.values.length; i++) { o.vals.add(o.values[i]); labels.add(o.labels[i]); }
            int idx = o.defIndex;
            if (cur != null) {
                idx = -1;
                for (int i = 0; i < o.values.length; i++) if (sameValue(o.values[i], cur, o.type)) { idx = i; break; }
                if (idx < 0) { o.vals.add(cur); labels.add("Текущее: " + cur); idx = labels.size() - 1; }
            }
            fillSpinner(o.spinner, labels, idx);
        }
        double sh = 0.05;
        try { sh = Double.parseDouble(PrefsEditor.getInKey(xml, "GfxSharpen", gp)); } catch (Exception ignored) { }
        sharpSeek.setProgress((int) Math.round(sh * 100));
        swCube.setChecked(!"0".equals(PrefsEditor.getInKey(xml, "GfxCubemapReflections", gp)));
        swHiRes.setChecked(!"0".equals(PrefsEditor.getGlobal(xml, "GfxEnableHighResCharacters")));
        String[] up = join(SETUP, "Upscaler");
        int tech = 0;
        try { tech = Integer.parseInt(PrefsEditor.getInKey(xml, "Technique", up)); } catch (Exception ignored) { }
        upTech.setSelection(Math.max(0, Math.min(4, tech)));
        double us = 0.5;
        try { us = Double.parseDouble(PrefsEditor.getInKey(xml, "SharpenAmount", up)); } catch (Exception ignored) { }
        upSharpSeek.setProgress((int) Math.round(us * 100));
        swCasPs.setChecked("1".equals(PrefsEditor.getInKey(xml, "CASUsePixelShader", up)));
        loadExtras(xml);
        say(Fs.exists(bakPath) ? "Исходные настройки сохранены, их можно вернуть." : "Настройки загружены.");
    }

    private static boolean sameValue(String a, String b, String type) {
        try { return Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) < 0.001; } catch (Exception e) { }
        return a.equals(b);
    }

    private static String[] join(String[] a, String extra) {
        String[] r = new String[a.length + 1];
        System.arraycopy(a, 0, r, 0, a.length);
        r[a.length] = extra;
        return r;
    }

    private int checkedIndex(RadioGroup g, int base, int fallback) {
        int id = g.getCheckedRadioButtonId();
        return id < 0 ? fallback : id - base;
    }

    // ---------------------------------------------------------------- apply

    private boolean applyToFile() {
        String xml = readXml();
        if (xml == null) {
            say("Файл настроек игры не найден. Запустите игру один раз.");
            return false;
        }
        if (!Fs.exists(bakPath)) {
            try { writeBytes(bakPath, xml.getBytes("UTF-8")); } catch (Exception ignored) { }
        }

        int rid = resGroup.getCheckedRadioButtonId();
        int ri = rid == 110 ? resHeights.length - 1 : rid - 100;
        if (ri < 0 || ri >= resHeights.length) ri = 1;
        int h = resHeights[ri];
        int w = widthFor(h);
        launcherPrefs.edit().putInt("res_choice", ri).apply();
        xml = PrefsEditor.setGlobal(xml, "FixedScreenHeight", String.valueOf(h));
        xml = PrefsEditor.setGlobal(xml, "ScreenH", String.valueOf(h));
        xml = PrefsEditor.setGlobal(xml, "ScreenW", String.valueOf(w));

        boolean dyn = swDynRes.isChecked();
        String minS = "1.000000", maxS = "1.000000";
        if (dyn) {
            minS = exValue(exMin); maxS = exValue(exMax);
            if (minS == null) minS = "0.600000";
            if (maxS == null) maxS = "0.800000";
            try { if (Double.parseDouble(minS) > Double.parseDouble(maxS)) minS = maxS; } catch (Exception ignored) { }
        }
        String[] drs = join(SETUP, "DynamicResolutionScaling");
        xml = PrefsEditor.setInKey(xml, "Enable", "integer", dyn ? "1" : "0", drs);
        xml = PrefsEditor.setInKey(xml, "MinScaling", "double", minS, drs);
        xml = PrefsEditor.setInKey(xml, "MaxScaling", "double", maxS, drs);

        String sharp = PrefsEditor.fmt(sharpSeek.getProgress() / 100.0);
        for (String blk : PRESET_BLOCKS) {
            String[] p = join(SETUP, blk);
            xml = PrefsEditor.setInKey(xml, "GfxResolutionScale", "double", "1.000000", p);
            xml = PrefsEditor.setInKey(xml, "GfxMinScaling", "double", minS, p);
            xml = PrefsEditor.setInKey(xml, "GfxMaxScaling", "double", maxS, p);
            for (Opt o : opts) {
                int si = o.spinner.getSelectedItemPosition();
                if (si < 0 || si >= o.vals.size()) continue;
                xml = PrefsEditor.setInKey(xml, o.key, o.type, o.vals.get(si), p);
            }
            xml = PrefsEditor.setInKey(xml, "GfxSharpen", "double", sharp, p);
            xml = PrefsEditor.setInKey(xml, "GfxCubemapReflections", "integer", swCube.isChecked() ? "1" : "0", p);
        }
        xml = PrefsEditor.setGlobal(xml, "GfxEnableHighResCharacters", swHiRes.isChecked() ? "1" : "0");
        String[] upk = join(SETUP, "Upscaler");
        xml = PrefsEditor.setInKey(xml, "Technique", "integer", String.valueOf(Math.max(0, upTech.getSelectedItemPosition())), upk);
        xml = PrefsEditor.setInKey(xml, "SharpenAmount", "double", PrefsEditor.fmt(upSharpSeek.getProgress() / 100.0), upk);
        xml = PrefsEditor.setInKey(xml, "CASUsePixelShader", "integer", swCasPs.isChecked() ? "1" : "0", upk);

        int fps = fpsValues[Math.max(0, fpsSpinner.getSelectedItemPosition())];
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < fpsValues.length; i++) { if (i > 0) list.append(','); list.append(fpsValues[i]); }
        xml = PrefsEditor.setGlobal(xml, "GfxAvailableOverrideFps", list.toString());
        xml = PrefsEditor.setGlobal(xml, "GfxOverrideFps", String.valueOf(fps));
        xml = PrefsEditor.setGlobal(xml, "GfxOverrideFpsEnabled", "1");

        xml = applyExtras(xml);

        int hud = checkedIndex(hudGroup, 200, 0);
        xml = PrefsEditor.setInKey(xml, "ShowFeralGUI", "integer", hud > 0 ? "1" : "0", D3D_CONFIG);
        xml = PrefsEditor.setInKey(xml, "CompactFeralGUI", "integer", hud == 1 ? "1" : "0", D3D_CONFIG);

        try {
            if (!writeBytes(prefPath, xml.getBytes("UTF-8"))) {
                say("Не удалось записать файл настроек.");
                return false;
            }
        } catch (Exception e) {
            say("Ошибка записи: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void restoreBackup() {
        if (!Fs.exists(bakPath)) {
            say("Исходной копии пока нет: она создаётся при первом сохранении.");
            return;
        }
        try {
            byte[] data = Fs.read(bakPath);
            if (data != null && writeBytes(prefPath, data)) {
                launcherPrefs.edit().remove("res_choice").apply();
                loadFromFile();
                say("Исходные настройки возвращены.");
            } else {
                say("Не удалось вернуть исходные настройки.");
            }
        } catch (Exception e) {
            say("Ошибка: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- авто-подбор

    // порядок: res(индекс), тени, свет, SSAO, SSLR, туман, AA, аниз., LOD, персонажи, транспорт, объекты, деревья, трава, VFX,
    //          резкость CAS %, резкость апскейлера %, динамическое разрешение
    private static final int[][] AUTO60 = {
        /* флагман */ {3, 1, 1, 0, 0, 1, 2, 2, 2, 2, 2, 2, 2, 1, 2, 20, 50, 1},
        /* мощный  */ {3, 1, 1, 0, 0, 0, 1, 2, 2, 2, 2, 2, 2, 1, 2, 25, 55, 1},
        /* средний */ {4, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1, 30, 60, 1},
        /* слабый  */ {4, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 35, 65, 1},
    };
    private static final int[][] AUTO30 = {
        /* флагман */ {0, 2, 2, 2, 1, 2, 2, 3, 3, 3, 3, 3, 3, 2, 3, 5, 40, 0},
        /* мощный  */ {2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 1, 2, 10, 45, 0},
        /* средний */ {3, 1, 1, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 1, 2, 20, 50, 1},
        /* слабый  */ {4, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1, 30, 60, 1},
    };

    private static final String[] PRESET_NAMES = {"Минимум", "Низкие", "Средние", "Высокие", "Ультра"};
    // формат тот же, что у AUTO: {разрешение, 14 параметров графики, резкость CAS, резкость апскейлера, динамическое разрешение}
    private static final int[][] PRESETS = {
        /* Минимум */ {4, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 35, 65, 1},
        /* Низкие  */ {3, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1, 30, 60, 1},
        /* Средние */ {1, 1, 1, 0, 0, 1, 2, 2, 2, 2, 2, 2, 2, 1, 2, 20, 50, 1},
        /* Высокие */ {0, 2, 2, 2, 1, 2, 2, 3, 3, 3, 3, 3, 3, 2, 3, 10, 45, 0},
        /* Ультра  */ {0, 2, 2, 3, 3, 3, 2, 3, 5, 3, 3, 3, 3, 3, 3, 0, 40, 0},
    };

    private void applyPreset(int n) {
        int[] p = PRESETS[n];
        resGroup.check(100 + Math.max(0, Math.min(resHeights.length - 1, p[0])));
        for (int i = 0; i < opts.length; i++) {
            int idx = Math.min(p[1 + i], opts[i].vals.size() - 1);
            opts[i].spinner.setSelection(Math.max(0, idx));
        }
        sharpSeek.setProgress(p[15]);
        upSharpSeek.setProgress(p[16]);
        swDynRes.setChecked(p[17] == 1);
        say("Пресет «" + PRESET_NAMES[n] + "» выбран. Нажмите «Сохранить» или «Играть». Проверьте FPS по счётчику в игре.");
    }

    private void autoTune(int fps) {
        int[] p = (fps == 60 ? AUTO60 : AUTO30)[hw.tier];
        int ri = Math.max(0, Math.min(resHeights.length - 1, p[0]));
        resGroup.check(100 + ri);
        for (int i = 0; i < opts.length; i++) {
            int idx = Math.min(p[1 + i], opts[i].vals.size() - 1);
            opts[i].spinner.setSelection(Math.max(0, idx));
        }
        sharpSeek.setProgress(p[15]);
        upSharpSeek.setProgress(p[16]);
        swDynRes.setChecked(p[17] == 1);
        for (int i = 0; i < fpsValues.length; i++) {
            if (fpsValues[i] == fps) fpsSpinner.setSelection(i);
        }
        say("Подобрано для " + fps + " FPS (класс: " + hw.tierName + "). Нажмите «Сохранить» или «Играть».");
    }

    // ---------------------------------------------------------------- launch

    private void play() {
        if (Fs.exists(prefPath) && !applyToFile()) return;
        Fs.sh("am force-stop " + PKG);
        say("Запуск игры…");
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setClassName(PKG, GAME_ACTIVITY);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    startActivity(i);
                    finishAndRemoveTask();
                } catch (Exception e) {
                    say("Не удалось запустить игру: " + e.getMessage());
                }
            }
        }, 700);
    }
}
