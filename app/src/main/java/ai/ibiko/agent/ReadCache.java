package ai.ibiko.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * What this task has already read, searched, listed or fetched, and what it has already written.
 *
 * <p>The work log is trimmed as it grows, so the model forgets a file it opened and reads it again. That second read
 * is the circle: the same file, the same search, the same page. This remembers the result. The first call really
 * runs. The next call does not. A file is read again only after it changes. A task carried on later uses the same
 * memory, so it does not start the reading over.
 */
final class ReadCache {
    private static final int MOST = 200;
    private static final int EXCERPT = 700;

    private static final class Item {
        long stamp;
        int times;
        String body;
    }

    private final Map<String, Item> items = new LinkedHashMap<String, Item>();
    private final LinkedHashSet<String> writes = new LinkedHashSet<String>();
    private File file;

    /** Remembers {@code where} and loads what a previous run kept there. A second call does nothing. */
    synchronized void bind(File where) {
        if (file != null || where == null) {
            return;
        }
        file = where;
        load();
    }

    /**
     * The words to give back when this call was already done and the file has not changed, or null when it should
     * really run. {@code next} is the next unticked step, so the answer points forward instead of back at the file.
     */
    synchronized String again(String tool, String key, long stamp, String next) {
        Item it = items.get(id(tool, key));
        if (it == null || it.stamp != stamp) {
            return null;
        }
        it.times++;
        save();
        String step = next == null || next.isEmpty() ? "the next unticked step" : clip(next, 90);
        if (it.times >= 2) {
            return "Cached. This was already read and it has not changed. Do not read it again. Write the fix now. Move on to " + step + ".";
        }
        return "Cached. This was already read and it has not changed. Do not read it again. Use what is below and move on to "
                + step + ".\n" + it.body;
    }

    /** Remembers a result that really ran. Only a short excerpt is kept: the full text is already in the work. */
    synchronized void keep(String tool, String key, long stamp, String body) {
        Item it = new Item();
        it.stamp = stamp;
        it.times = 1;
        it.body = excerpt(body);
        items.put(id(tool, key), it);
        trim();
        save();
    }

    /** The words when this exact write or edit was already done, or null the first time. Does not remember it. */
    synchronized String done(String kind, String sig, String next) {
        if (!writes.contains(kind + "\u0000" + sig)) {
            return null;
        }
        String step = next == null || next.isEmpty() ? "the next unticked step" : clip(next, 90);
        return "Cached. This " + kind + " was already done. Do not do it again. Tick the step if its check passed, and move on to "
                + step + ".";
    }

    /** Remembers a write or edit that succeeded, so the same one is not done again. */
    synchronized void note(String kind, String sig) {
        if (writes.add(kind + "\u0000" + sig)) {
            save();
        }
    }

    /** True when this exact write or edit was already done. The first time is remembered and returns null. */
    synchronized String repeat(String kind, String sig, String next) {
        String again = done(kind, sig, next);
        if (again != null) {
            return again;
        }
        note(kind, sig);
        return null;
    }

    /**
     * Something may have changed files without naming them (a command, an unzip, a pull): every search and listing is
     * stale. Reads keep their own stamp (the file's time and size), so they stay.
     */
    synchronized void staleSearches() {
        boolean any = items.keySet().removeIf(k -> k.startsWith("search\u0000") || k.startsWith("list_files\u0000"));
        if (any) {
            save();
        }
    }

    /** A file changed: the next read of it is a real one. Searches and listings are stale too. */
    synchronized void changed(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        String p = path.replace('\\', '/');
        Iterator<String> it = items.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith("search\u0000") || k.startsWith("list_files\u0000")
                    || k.contains("\u0000" + p + "\u0000") || k.endsWith("\u0000" + p)) {
                it.remove();
            }
        }
        save();
    }

    private static String id(String tool, String key) {
        return tool + "\u0000" + (key == null ? "" : key);
    }

    private void trim() {
        while (items.size() > MOST) {
            items.remove(items.keySet().iterator().next());
        }
        while (writes.size() > MOST) {
            writes.remove(writes.iterator().next());
        }
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("W\t")) {
                    writes.add(unesc(line.substring(2)));
                    continue;
                }
                if (!line.startsWith("R\t")) {
                    continue;
                }
                String[] p = line.substring(2).split("\t", 4);
                if (p.length < 4) {
                    continue;
                }
                Item it = new Item();
                try {
                    it.stamp = Long.parseLong(p[1]);
                    it.times = Integer.parseInt(p[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                it.body = unesc(p[3]);
                items.put(unesc(p[0]), it);
            }
        } catch (IOException ignored) {
            // a missing memory only means the first read really runs
        }
        trim();
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Item> e : items.entrySet()) {
            Item it = e.getValue();
            b.append("R\t").append(esc(e.getKey())).append('\t').append(it.stamp).append('\t').append(it.times)
                    .append('\t').append(esc(it.body)).append('\n');
        }
        for (String w : writes) {
            b.append("W\t").append(esc(w)).append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the memory is still held for this run
        }
    }

    private static String excerpt(String body) {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty()) {
            return "(nothing came back)";
        }
        if (t.length() <= EXCERPT) {
            return t;
        }
        return t.substring(0, EXCERPT) + "\n…(the rest was already read; do not read it again)";
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String esc(String s) {
        return (s == null ? "" : s).replace("\\", "\\\\").replace("\n", "\\n").replace("\t", " ");
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    b.append('\n');
                    i++;
                    continue;
                }
                if (n == '\\') {
                    b.append('\\');
                    i++;
                    continue;
                }
            }
            b.append(c);
        }
        return b.toString();
    }
}
