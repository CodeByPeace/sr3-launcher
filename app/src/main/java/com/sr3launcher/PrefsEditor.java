package com.sr3launcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Точечная правка XML-файла настроек Feral без пересборки документа (форматирование не трогаем). */
final class PrefsEditor {
    private PrefsEditor() {}

    static String fmt(double d) {
        return String.format(Locale.US, "%.6f", d);
    }

    private static Pattern valuePattern(String name) {
        return Pattern.compile("<value name=\"" + Pattern.quote(name) + "\" type=\"([a-z]+)\">([^<]*)</value>");
    }

    /** Первое значение с таким именем в файле или null. */
    static String getGlobal(String xml, String name) {
        Matcher m = valuePattern(name).matcher(xml);
        return m.find() ? m.group(2) : null;
    }

    /** Меняет первое значение с таким именем. Если его нет, файл остаётся прежним. */
    static String setGlobal(String xml, String name, String val) {
        Matcher m = valuePattern(name).matcher(xml);
        if (!m.find()) return xml;
        String repl = "<value name=\"" + name + "\" type=\"" + m.group(1) + "\">" + val + "</value>";
        return xml.substring(0, m.start()) + repl + xml.substring(m.end());
    }

    /** Индекс закрывающего </key> для ключа, открытого в позиции openIdx, учитывая вложенность. */
    private static int matchingEnd(String xml, int openIdx) {
        int depth = 0;
        int i = openIdx;
        while (true) {
            int o = xml.indexOf("<key name=", i);
            int c = xml.indexOf("</key>", i);
            if (c < 0) return -1;
            if (o >= 0 && o < c) {
                depth++;
                i = o + 10;
            } else {
                depth--;
                if (depth == 0) return c;
                i = c + 6;
            }
        }
    }

    /** Находит ключ по пути {"IndirectX","Direct3D","Config"}; возвращает {начало содержимого, индекс </key>} или null. */
    static int[] findKey(String xml, String... path) {
        int from = 0;
        int end = xml.length();
        int contentStart = -1;
        int closeIdx = -1;
        for (String p : path) {
            String tag = "<key name=\"" + p + "\">";
            int i = xml.indexOf(tag, from);
            if (i < 0 || i >= end) return null;
            closeIdx = matchingEnd(xml, i);
            if (closeIdx < 0) return null;
            contentStart = i + tag.length();
            from = contentStart;
            end = closeIdx;
        }
        return new int[] {contentStart, closeIdx};
    }

    static String getInKey(String xml, String val, String... path) {
        int[] r = findKey(xml, path);
        if (r == null) return null;
        Matcher m = valuePattern(val).matcher(xml.substring(r[0], r[1]));
        return m.find() ? m.group(2) : null;
    }

    /** Меняет или добавляет значение в плоском ключе (в ключе не должно быть вложенных ключей с тем же именем). */
    static String setInKey(String xml, String name, String type, String val, String... path) {
        int[] r = findKey(xml, path);
        if (r == null) return xml;
        String blk = xml.substring(r[0], r[1]);
        Matcher m = valuePattern(name).matcher(blk);
        if (m.find()) {
            String repl = "<value name=\"" + name + "\" type=\"" + m.group(1) + "\">" + val + "</value>";
            blk = blk.substring(0, m.start()) + repl + blk.substring(m.end());
        } else {
            int last = blk.lastIndexOf("</value>");
            String indent = "                            ";
            int ls = blk.lastIndexOf("<value");
            if (ls >= 0) {
                int nl = blk.lastIndexOf('\n', ls);
                indent = blk.substring(nl + 1, ls);
            }
            String add = "\n" + indent + "<value name=\"" + name + "\" type=\"" + type + "\">" + val + "</value>";
            if (last >= 0) {
                int at = last + 8;
                blk = blk.substring(0, at) + add + blk.substring(at);
            } else {
                blk = add + blk;
            }
        }
        return xml.substring(0, r[0]) + blk + xml.substring(r[1]);
    }
}
