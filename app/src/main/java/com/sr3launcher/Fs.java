package com.sr3launcher;

import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Доступ к папке игры (Android/data) через Shizuku: чтение, запись и команды оболочки. */
final class Fs {
    private static final int REQ = 4711;
    private static Runnable onReady;
    private static String problem = "Starting access to the game files…";
    private static boolean listening;
    /** 0 - ещё не проверяли, 1 - root выдан, -1 - root недоступен или отклонён. */
    private static volatile int root = 0;
    private static volatile boolean rootChecking;

    private static final Shizuku.OnRequestPermissionResultListener PERM = new Shizuku.OnRequestPermissionResultListener() {
        @Override public void onRequestPermissionResult(int requestCode, int grantResult) {
            if (requestCode == REQ && grantResult == PackageManager.PERMISSION_GRANTED && onReady != null) onReady.run();
        }
    };
    private static final Shizuku.OnBinderReceivedListener BINDER = new Shizuku.OnBinderReceivedListener() {
        @Override public void onBinderReceived() {
            try { check(); } catch (Throwable ignored) { }
            if (ready() && onReady != null) onReady.run();
        }
    };

    static void init(Runnable ready) {
        onReady = ready;
        try {
            if (!listening) {
                Shizuku.addRequestPermissionResultListener(PERM);
                Shizuku.addBinderReceivedListener(BINDER);
                listening = true;
            }
            check();
        } catch (Throwable t) {
            problem = "Shizuku unavailable: " + t.getMessage();
        }
        if (!shizukuReady() && root == 0) tryRoot();
    }

    /** Есть ли root-доступ (su), уже выданный лаунчеру. */
    static boolean rootOk() { return root == 1; }

    static boolean rootChecking() { return rootChecking; }

    /** Повторная проверка root (например, после того как выдали доступ в Magisk/KernelSU). */
    static void retryRoot() { if (root != 1) { root = 0; tryRoot(); } }

    private static void tryRoot() {
        if (rootChecking) return;
        rootChecking = true;
        new Thread(new Runnable() {
            @Override public void run() {
                int r = -1;
                try {
                    Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", "id"});
                    p.getOutputStream().close();
                    String out = new String(drain(p.getInputStream()));
                    if (p.waitFor() == 0 && out.contains("uid=0")) r = 1;
                } catch (Throwable ignored) { }
                root = r;
                rootChecking = false;
                if (r == 1 && onReady != null) onReady.run();
                else if (onReady != null) onReady.run();
            }
        }, "root-check").start();
    }

    static void release() {
        try {
            if (listening) {
                Shizuku.removeRequestPermissionResultListener(PERM);
                Shizuku.removeBinderReceivedListener(BINDER);
                listening = false;
            }
        } catch (Throwable ignored) { }
    }

    /** Проверяет Shizuku и при необходимости запрашивает доступ. */
    private static void check() {
        if (!Shizuku.pingBinder()) {
            problem = "Shizuku isn't running. Open Shizuku, start it, then come back to the launcher.";
            return;
        }
        if (Shizuku.isPreV11()) {
            problem = "Shizuku version is too old.";
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            problem = "Allow the launcher access in the Shizuku window (it just appeared).";
            if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQ);
            else problem = "Access denied. Enable it for the launcher in the Shizuku app.";
            return;
        }
        problem = "";
    }

    /** Запущен ли сервис Shizuku. */
    static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    /** Показывает окно запроса доступа Shizuku, если сервис запущен, а доступа ещё нет. */
    static void requestAccess() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQ);
            }
        } catch (Throwable ignored) { }
    }

    private static boolean shizukuReady() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean ready() { return shizukuReady() || root == 1; }

    static String problem() {
        try { if (!ready()) check(); } catch (Throwable ignored) { }
        if (root == 1) return "";
        return problem.length() == 0 ? "No access to the game files." : problem;
    }

    private static Process spawn(String[] cmd) throws Exception {
        if (!shizukuReady() && root == 1) {
            return Runtime.getRuntime().exec(new String[] {"su", "-c", cmd[cmd.length - 1]});
        }
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, cmd, null, null);
    }

    private static byte[] drain(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }

    private static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    /** Выполняет команду, возвращает код выхода (или -1). */
    static int sh(String cmd) {
        if (!ready()) return -1;
        try {
            Process p = spawn(new String[] {"sh", "-c", cmd});
            p.getOutputStream().close();
            drain(p.getInputStream());
            return p.waitFor();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Выполняет команду от root (su) и возвращает вывод (stdout и stderr) или null. Нужен root. */
    static String suOut(String cmd) {
        if (root != 1) return null;
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", "(" + cmd + ") 2>&1"});
            p.getOutputStream().close();
            byte[] d = drain(p.getInputStream());
            p.waitFor();
            return new String(d);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Запускает скрипт от root в фоне, он живёт дольше лаунчера. */
    static void suDetached(String script) {
        if (root != 1) return;
        try {
            Runtime.getRuntime().exec(new String[] {"su", "-c", "nohup sh -c " + q(script) + " >/dev/null 2>&1 &"});
        } catch (Throwable ignored) { }
    }

    static boolean exists(String path) {
        return sh("test -f " + q(path)) == 0;
    }

    private static volatile String lastReadError = "";
    private static volatile String lastReadVia = "";

    /** Почему последнее чтение не удалось (пусто, если удалось). */
    static String lastReadError() { return lastReadError; }

    /** Каким путём файл был прочитан в последний раз. */
    static String lastReadVia() { return lastReadVia; }

    /** Тот же файл по другим путям: Android/data через FUSE и нижележащий слой хранилища. */
    private static String[] variants(String path) {
        if (path.startsWith("/sdcard/")) {
            String rest = path.substring("/sdcard/".length());
            return new String[] {path, "/storage/emulated/0/" + rest, "/data/media/0/" + rest};
        }
        return new String[] {path};
    }

    private static String shOut(String cmd) throws Exception {
        Process p = spawn(new String[] {"sh", "-c", cmd});
        p.getOutputStream().close();
        String out = new String(drain(p.getInputStream()), "UTF-8").trim();
        p.waitFor();
        return out;
    }

    /**
     * Читает файл, пробуя несколько путей. Чтение засчитывается, только если число
     * прочитанных байт совпало с размером файла (stat): после первой настройки игры
     * часть файлов в Android/data копируется как 0 байт без ошибки.
     * Причина отказа остаётся в lastReadError().
     */
    static byte[] read(String path) {
        lastReadError = "";
        lastReadVia = "";
        if (!ready()) { lastReadError = "no access to the game files"; return null; }
        StringBuilder why = new StringBuilder();
        for (String p : variants(path)) {
            try {
                String st = shOut("stat -c %s " + q(p) + " 2>&1");
                long expect;
                try { expect = Long.parseLong(st); } catch (NumberFormatException e) {
                    why.append(p).append(": ").append(st.length() == 0 ? "no response" : st).append("\n");
                    continue;
                }
                if (expect == 0) { why.append(p).append(": file is empty (0 bytes)\n"); continue; }
                Process pr = spawn(new String[] {"sh", "-c", "cat " + q(p) + " 2>/dev/null"});
                pr.getOutputStream().close();
                byte[] d = drain(pr.getInputStream());
                pr.waitFor();
                if (d.length == expect) { lastReadVia = p; return d; }
                String err = shOut("cat " + q(p) + " 2>&1 >/dev/null");
                why.append(p).append(": read ").append(d.length).append(" of ").append(expect).append(" bytes").append(err.length() > 0 ? " (" + err + ")" : "").append("\n");
            } catch (Throwable t) {
                why.append(p).append(": ").append(t.getMessage()).append("\n");
            }
        }
        lastReadError = why.toString().trim();
        return null;
    }

    private static volatile String lastWriteError = "";

    /** Why the last write failed (empty if it worked). */
    static String lastWriteError() { return lastWriteError; }

    private static String pipe(String cmd, byte[] data, int[] rc) throws Exception {
        Process p = spawn(new String[] {"sh", "-c", cmd});
        OutputStream o = p.getOutputStream();
        try { o.write(data); o.flush(); } catch (Exception ignored) { }
        try { o.close(); } catch (Exception ignored) { }
        String out = new String(drain(p.getInputStream()), "UTF-8").trim();
        rc[0] = p.waitFor();
        return out;
    }

    /**
     * Writes over the file in place; if the game's file refuses that (it was created with
     * owner-only permissions), writes a temp file next to it and renames it over the original.
     */
    static boolean write(String path, byte[] data) {
        lastWriteError = "";
        if (!ready()) { lastWriteError = "no access to the game files"; return false; }
        StringBuilder why = new StringBuilder();
        try {
            int[] rc = new int[1];
            String out = pipe("{ cat > " + q(path) + "; } 2>&1", data, rc);
            if (rc[0] == 0) return true;
            why.append("in place: ").append(out.length() == 0 ? "exit " + rc[0] : out).append("\n");
            String tmp = path + ".sr3tmp";
            out = pipe("{ cat > " + q(tmp) + " && chmod 660 " + q(tmp) + " && mv -f " + q(tmp) + " " + q(path)
                    + "; } 2>&1 || { rm -f " + q(tmp) + "; exit 1; }", data, rc);
            if (rc[0] != 0) {
                why.append("replace: ").append(out.length() == 0 ? "exit " + rc[0] : out);
                lastWriteError = why.toString().trim();
                return false;
            }
            String sz = shOut("stat -c %s " + q(path) + " 2>&1");
            if (!sz.equals(String.valueOf(data.length))) {
                why.append("replaced, but the file size is ").append(sz).append(" instead of ").append(data.length);
                lastWriteError = why.toString().trim();
                return false;
            }
            return true;
        } catch (Throwable t) {
            why.append(t.getMessage());
            lastWriteError = why.toString().trim();
            return false;
        }
    }
}
