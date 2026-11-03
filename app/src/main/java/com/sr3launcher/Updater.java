package com.sr3launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Проверка и скачивание обновлений из GitHub Releases репозитория лаунчера. */
final class Updater {
    private Updater() { }

    static final class Release {
        String tag = "";
        String notes = "";
        String apkName = "";
        String apkUrl = "";
        String pageUrl = "";
    }

    interface Progress { void onProgress(int percent); }

    /** Последний релиз репозитория, либо бросает исключение с понятным сообщением. */
    static Release latest(String repo) throws Exception {
        HttpURLConnection c = open("https://api.github.com/repos/Tsifrokayf/" + repo + "/releases/latest");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code == 404) throw new Exception("релизов пока нет");
        if (code != 200) throw new Exception("GitHub ответил кодом " + code);
        String body = new String(readAll(c.getInputStream()), "UTF-8");
        JSONObject o = new JSONObject(body);
        Release r = new Release();
        r.tag = o.optString("tag_name");
        r.notes = o.optString("body");
        r.pageUrl = o.optString("html_url");
        JSONArray a = o.optJSONArray("assets");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject as = a.getJSONObject(i);
                String n = as.optString("name");
                if (n.toLowerCase().endsWith(".apk")) {
                    r.apkName = n;
                    r.apkUrl = as.optString("browser_download_url");
                    break;
                }
            }
        }
        return r;
    }

    /** true, если версия tag новее текущей current (сравнение по числам через точку). */
    static boolean newer(String tag, String current) {
        int[] a = nums(tag), b = nums(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] nums(String s) {
        String[] p = s.replaceAll("[^0-9.]", "").split("\\.");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try { r[i] = p[i].length() == 0 ? 0 : Integer.parseInt(p[i]); } catch (Exception e) { r[i] = 0; }
        }
        return r;
    }

    /** Скачивает APK в dir, возвращает файл. */
    static File download(String url, File dir, Progress cb) throws Exception {
        dir.mkdirs();
        File out = new File(dir, "update.apk");
        HttpURLConnection c = open(url);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("загрузка не удалась, код " + code);
        long total = c.getContentLengthLong();
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(out);
        try {
            byte[] buf = new byte[32768];
            long done = 0;
            int n, last = -1;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
                done += n;
                if (total > 0 && cb != null) {
                    int pct = (int) (done * 100 / total);
                    if (pct != last) { last = pct; cb.onProgress(pct); }
                }
            }
        } finally {
            fo.close();
            in.close();
        }
        if (out.length() < 50000) throw new Exception("скачанный файл слишком мал");
        return out;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "GameLauncher-Updater");
        return c;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }
}
