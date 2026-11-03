package com.sr3launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Железо и системные настройки производительности (ядра, приоритет, подкачка). Всё через root (su). */
final class Perf {
    private Perf() { }

    static final class Info {
        int cpus;
        int[] maxKhz = new int[0];
        long memTotalKb, memAvailKb, swapTotalKb, swapFreeKb, zramBytes;
        int swappiness = -1;
        String zramAlgo = "";
        boolean ok;

        List<Integer> clusters() {
            TreeSet<Integer> s = new TreeSet<Integer>();
            for (int f : maxKhz) if (f > 0) s.add(f);
            return new ArrayList<Integer>(s);
        }

        String describe() {
            StringBuilder b = new StringBuilder();
            List<Integer> cl = clusters();
            if (cl.isEmpty()) b.append("Ядер: ").append(cpus);
            else {
                b.append("Ядра: ");
                for (int i = 0; i < cl.size(); i++) {
                    int n = 0;
                    for (int f : maxKhz) if (f == cl.get(i)) n++;
                    if (i > 0) b.append(", ");
                    b.append(n).append(" × ").append(String.format(Locale.US, "%.2f", cl.get(i) / 1e6)).append(" ГГц");
                }
            }
            b.append("\nОЗУ: ").append(gb(memTotalKb)).append(" ГБ, доступно ").append(gb(memAvailKb)).append(" ГБ");
            if (swapTotalKb > 0) b.append("\nПодкачка: ").append(gb(swapTotalKb)).append(" ГБ, занято ").append(gb(swapTotalKb - swapFreeKb)).append(" ГБ");
            else b.append("\nПодкачка: нет");
            if (zramBytes > 0) b.append(" (zram ").append(String.format(Locale.US, "%.1f", zramBytes / 1073741824.0)).append(" ГБ)");
            if (swappiness >= 0) b.append(", swappiness ").append(swappiness);
            return b.toString();
        }

        private static String gb(long kb) { return String.format(Locale.US, "%.1f", kb / 1048576.0); }
    }

    static Info read() {
        Info in = new Info();
        String out = Fs.suOut("for c in /sys/devices/system/cpu/cpu[0-9]*; do echo \"C ${c##*/} $(cat $c/cpufreq/cpuinfo_max_freq 2>/dev/null)\"; done;"
            + "grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo;"
            + "echo \"Z $(cat /sys/block/zram0/disksize 2>/dev/null)\"; echo \"S $(cat /proc/sys/vm/swappiness 2>/dev/null)\"; echo \"A $(cat /sys/block/zram0/comp_algorithm 2>/dev/null)\"");
        if (out == null || out.length() == 0) return in;
        int maxIdx = -1;
        List<int[]> cpu = new ArrayList<int[]>();
        for (String line : out.split("\n")) {
            line = line.trim();
            try {
                if (line.startsWith("C cpu")) {
                    String[] p = line.split("\\s+");
                    int idx = Integer.parseInt(p[1].substring(3));
                    int f = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                    cpu.add(new int[] {idx, f});
                    if (idx > maxIdx) maxIdx = idx;
                } else if (line.startsWith("MemTotal:")) in.memTotalKb = kb(line);
                else if (line.startsWith("MemAvailable:")) in.memAvailKb = kb(line);
                else if (line.startsWith("SwapTotal:")) in.swapTotalKb = kb(line);
                else if (line.startsWith("SwapFree:")) in.swapFreeKb = kb(line);
                else if (line.startsWith("Z ")) in.zramBytes = Long.parseLong(line.substring(2).trim());
                else if (line.startsWith("S ")) in.swappiness = Integer.parseInt(line.substring(2).trim());
                else if (line.startsWith("A ")) in.zramAlgo = line.substring(2).trim();
            } catch (Exception ignored) { }
        }
        in.cpus = maxIdx + 1;
        in.maxKhz = new int[in.cpus];
        for (int[] c : cpu) in.maxKhz[c[0]] = c[1];
        in.ok = in.cpus > 0 && in.memTotalKb > 0;
        return in;
    }

    private static long kb(String line) {
        return Long.parseLong(line.replaceAll("[^0-9]", ""));
    }

    // ---- варианты привязки к ядрам

    /** Ключи и подписи вариантов; ключ "all" значит «не менять». */
    static List<String[]> coreOptions(Info in) {
        List<String[]> r = new ArrayList<String[]>();
        r.add(new String[] {"all", "Все ядра (не менять)"});
        List<Integer> cl = in.clusters();
        if (cl.size() >= 2) {
            r.add(new String[] {"nolow", "Без самых слабых ядер"});
            r.add(new String[] {"top", "Только самые быстрые ядра"});
        }
        if (cl.size() >= 3) r.add(new String[] {"notop", "Без самого быстрого ядра (меньше нагрев)"});
        return r;
    }

    /** Шестнадцатеричная маска для taskset или null, если менять не нужно. */
    static String mask(Info in, String key) {
        List<Integer> cl = in.clusters();
        if (cl.size() < 2 || key == null || key.equals("all")) return null;
        int lo = cl.get(0), hi = cl.get(cl.size() - 1);
        long m = 0;
        for (int i = 0; i < in.maxKhz.length; i++) {
            int f = in.maxKhz[i];
            if (f <= 0) continue;
            boolean use = key.equals("nolow") ? f > lo : key.equals("top") ? f == hi : key.equals("notop") ? f < hi : true;
            if (use) m |= 1L << i;
        }
        return m == 0 ? null : Long.toHexString(m);
    }

    // ---- приоритет

    static final String[] PRIO_LABELS = {"Обычный (не менять)", "Повышенный (−5)", "Высокий (−10)", "Низкий (+5)"};
    static final int[] PRIO_NICE = {0, -5, -10, 5};

    /**
     * Скрипт для root: ждёт процесс игры и применяет маску ядер и приоритет ко всем его потокам.
     * Потоки игры появляются не сразу, поэтому проход повторяется.
     */
    static String script(String pkg, String mask, int nice, int waitSec, int rounds, boolean report) {
        StringBuilder s = new StringBuilder();
        s.append("P=").append(pkg).append(";i=0;pid=;");
        s.append("while [ $i -lt ").append(waitSec).append(" ];do pid=$(pidof $P);[ -n \"$pid\" ]&&break;sleep 1;i=$((i+1));done;");
        s.append("[ -z \"$pid\" ]&&{ echo NOPID;exit 0; };");
        s.append("r=0;while [ $r -lt ").append(rounds).append(" ];do ");
        s.append("for t in /proc/$pid/task/*;do tid=${t##*/};");
        if (mask != null) s.append("taskset -p ").append(mask).append(" $tid >/dev/null 2>&1;");
        // renice -n в toybox относительный, поэтому считаем разницу с текущим значением потока
        if (nice != 0) {
            s.append("st=$(cat /proc/$pid/task/$tid/stat 2>/dev/null);st=${st##*) };set -- $st;cur=${17};");
            s.append("[ -n \"$cur\" ]&&[ \"$cur\" -ne ").append(nice).append(" ]&&renice -n $((").append(nice).append("-cur)) -p $tid >/dev/null 2>&1;");
        }
        s.append("done;r=$((r+1));[ $r -lt ").append(rounds).append(" ]&&sleep 5;[ -d /proc/$pid ]||exit 0;done;");
        if (report) {
            s.append("n=0;for t in /proc/$pid/task/*;do n=$((n+1));done;");
            s.append("L=$(for t in /proc/$pid/task/*;do grep Cpus_allowed_list $t/status 2>/dev/null|cut -f2;done|sort|uniq -c|tr -s ' '|tr '\\n' ';');");
            s.append("echo \"потоков: $n; ядра (потоков): $L приоритет: $(ps -o NI -p $pid|tail -n1|tr -d ' ')\";");
        }
        return s.toString();
    }

    // ---- подкачка

    static String swapScript(long bytes, long origBytes) {
        return "D=/sys/block/zram0;Z=/dev/block/zram0;"
            + "if [ ! -e $D/disksize ];then echo NOZRAM;exit 0;fi;"
            + "if swapoff $Z 2>&1;then echo 1 > $D/reset;"
            + "if echo " + bytes + " > $D/disksize && mkswap $Z >/dev/null 2>&1 && swapon $Z 2>&1;then echo OK;"
            + "else echo 1 > $D/reset;echo " + origBytes + " > $D/disksize;mkswap $Z >/dev/null 2>&1;swapon $Z 2>&1;echo FAIL;fi;"
            + "else echo NOSWAPOFF;fi";
    }
}
