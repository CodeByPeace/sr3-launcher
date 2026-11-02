package com.sr3launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import java.util.Locale;

/** Определение железа и класса производительности. */
final class Hw {
    String gpu = "";
    String soc = "";
    int cores;
    long ramMb;
    /** 0 = флагман, 1 = мощный, 2 = средний, 3 = слабый */
    int tier = 3;
    String tierName = "";

    static Hw detect(Context c) {
        Hw h = new Hw();
        h.cores = Runtime.getRuntime().availableProcessors();
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            h.ramMb = mi.totalMem / (1024 * 1024);
        } catch (Throwable ignored) { }
        try {
            String m = Build.SOC_MODEL;
            String mf = Build.SOC_MANUFACTURER;
            h.soc = ((mf == null ? "" : mf) + " " + (m == null ? "" : m)).trim();
        } catch (Throwable ignored) { }
        if (h.soc.length() == 0) h.soc = Build.HARDWARE;
        h.gpu = glRenderer();
        h.classify();
        return h;
    }

    private static String glRenderer() {
        try {
            EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            EGL14.eglInitialize(d, v, 0, v, 1);
            int[] cfgAttr = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            EGL14.eglChooseConfig(d, cfgAttr, 0, cfg, 0, 1, n, 0);
            EGLContext ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            EGLSurface s = EGL14.eglCreatePbufferSurface(d, cfg[0], new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            EGL14.eglMakeCurrent(d, s, s, ctx);
            String r = GLES20.glGetString(GLES20.GL_RENDERER);
            EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(d, s);
            EGL14.eglDestroyContext(d, ctx);
            EGL14.eglTerminate(d);
            return r == null ? "" : r;
        } catch (Throwable t) {
            return "";
        }
    }

    private static int firstNumber(String s) {
        int i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) i++;
        int j = i;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        try { return Integer.parseInt(s.substring(i, j)); } catch (Exception e) { return 0; }
    }

    private void classify() {
        String g = gpu.toLowerCase(Locale.ROOT);
        int t = -1;
        if (g.contains("adreno")) {
            int n = firstNumber(g);
            if (n >= 800) t = 0;
            else if (n >= 740) t = 1;
            else if (n >= 650) t = 2;
            else t = 3;
        } else if (g.contains("immortalis")) {
            int n = firstNumber(g.substring(g.indexOf("immortalis")));
            t = n >= 925 ? 0 : (n >= 720 ? 1 : 2);
        } else if (g.contains("xclipse")) {
            int n = firstNumber(g);
            t = n >= 940 ? 1 : 2;
        } else if (g.contains("mali")) {
            int n = firstNumber(g.substring(g.indexOf("mali")));
            t = n >= 720 ? 1 : (n >= 610 ? 2 : 3);
        }
        if (t < 0) t = ramMb >= 12000 && cores >= 8 ? 1 : (ramMb >= 6000 ? 2 : 3);
        if (ramMb > 0 && ramMb < 4000 && t < 3) t = Math.max(t, 2);
        tier = t;
        tierName = new String[] {"флагман", "мощный", "средний", "слабый"}[t];
    }

    String describe() {
        StringBuilder b = new StringBuilder();
        b.append(gpu.length() > 0 ? gpu : "GPU не определён");
        if (soc.length() > 0) b.append(" · ").append(soc);
        b.append(" · ").append(cores).append(" ядер");
        if (ramMb > 0) b.append(" · ").append(Math.round(ramMb / 1024.0)).append(" ГБ ОЗУ");
        b.append("\nКласс: ").append(tierName);
        return b.toString();
    }
}
