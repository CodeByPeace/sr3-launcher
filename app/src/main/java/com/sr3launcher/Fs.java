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
    private static String problem = "Запускаю доступ к файлам игры…";
    private static boolean listening;

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
            problem = "Shizuku недоступен: " + t.getMessage();
        }
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
            problem = "Shizuku не запущен. Откройте Shizuku, запустите его и вернитесь в лаунчер.";
            return;
        }
        if (Shizuku.isPreV11()) {
            problem = "Версия Shizuku слишком старая.";
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            problem = "Разрешите лаунчеру доступ в окне Shizuku (оно появилось сейчас).";
            if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQ);
            else problem = "Доступ отклонён. Включите его для лаунчера в приложении Shizuku.";
            return;
        }
        problem = "";
    }

    static boolean ready() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static String problem() {
        try { if (!ready()) check(); } catch (Throwable ignored) { }
        return problem.length() == 0 ? "Нет доступа к файлам игры." : problem;
    }

    private static Process spawn(String[] cmd) throws Exception {
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

    static boolean exists(String path) {
        return sh("test -f " + q(path)) == 0;
    }

    static byte[] read(String path) {
        if (!ready()) return null;
        try {
            Process p = spawn(new String[] {"sh", "-c", "cat " + q(path)});
            p.getOutputStream().close();
            byte[] d = drain(p.getInputStream());
            int rc = p.waitFor();
            return rc == 0 && d.length > 0 ? d : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Пишет поверх существующего файла (владелец и права сохраняются, игра сможет читать). */
    static boolean write(String path, byte[] data) {
        if (!ready()) return false;
        try {
            Process p = spawn(new String[] {"sh", "-c", "cat > " + q(path)});
            OutputStream o = p.getOutputStream();
            o.write(data);
            o.flush();
            o.close();
            drain(p.getInputStream());
            return p.waitFor() == 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
