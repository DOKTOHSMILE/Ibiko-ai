package ai.ibiko.learn;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ai.ibiko.ibiko1.Knowledge;
import ai.ibiko.ibiko1.formulas.Understanding;
import ai.ibiko.main.ChatSpace;

/**
 * The one place that may read across chats. A chat's own machine (its files, its shell, its workers) sees only
 * that chat's folder. What was learned from an API model or the model on the phone is written into that chat,
 * and a copy of the lesson (not the chat's project files) is kept here so the next answer can be smarter.
 * Pure Java.
 */
public final class Central {
    public static final String NAME = "central";

    private Central() {
    }

    /** {@code files/ibiko/central}. Not inside any chat. */
    public static File home(File filesDir) {
        File d = new File(ChatSpace.root(filesDir), NAME);
        d.mkdirs();
        return d;
    }

    /**
     * One lesson. Written into this chat's own memory, and into the central store. The chat's files are not copied.
     */
    public static void learn(File filesDir, String chat, String source, String asked, String reply) {
        if (filesDir == null || reply == null || reply.trim().isEmpty()) {
            return;
        }
        String line = clip(source) + "\t" + clip(asked) + "\t" + clip(reply) + "\n";
        File folder = ChatSpace.of(filesDir, chat == null || chat.isEmpty() ? "chat" : chat);
        append(new File(new File(folder, ChatSpace.MEMORY), "learned.txt"), line);
        append(new File(home(filesDir), "lessons.tsv"), (chat == null ? "" : chat) + "\t" + line);
    }

    /** The lessons gathered from every chat. This is the cross-chat read. A chat's tools do not call it. */
    public static List<String> lessons(File filesDir) {
        List<String> out = new ArrayList<String>();
        File f = new File(home(filesDir), "lessons.tsv");
        if (!f.isFile()) {
            return out;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) {
                    out.add(line);
                }
            }
        } catch (java.io.IOException ignored) {
            // the store is missing a read: the chats still have their own copies
        }
        return out;
    }

    /**
     * One lesson from any chat that shares this question's content words, as what it implies, not as words to copy
     * and not as that chat's files. Empty when nothing stored is about this question. This is the cross-chat read.
     */
    public static String recall(File filesDir, String question) {
        List<String> want = Knowledge.meaning(question);
        if (filesDir == null || want.isEmpty()) {
            return "";
        }
        String asked = null, reply = null, source = null;
        int best = 0;
        for (String line : lessons(filesDir)) {
            String[] p = line.split("\t", 4);
            if (p.length < 4) {
                continue;
            }
            List<String> have = Knowledge.meaning(p[2] + " " + p[3]);
            int n = 0;
            for (String w : want) {
                if (have.contains(w)) {
                    n++;
                }
            }
            int need = Math.min(want.size(), Math.max(1, have.size()));
            int min = need >= 3 ? 2 : 1;
            if (n >= min && n > best) {
                best = n;
                source = p[1];
                asked = p[2];
                reply = p[3];
            }
        }
        if (asked == null) {
            return "";
        }
        Understanding.Implication u = Understanding.of(asked + ". " + reply);
        return "Across chats, a similar request was decided. Follow the logic, not the wording, and not that chat's files.\n"
                + "Asked: " + clip(asked, 180) + "\n"
                + "Implies: " + u.follows + "\n"
                + "Said (" + clip(source, 40) + "): " + clip(reply, 180);
    }

    private static String clip(String s, int max) {
        String t = clip(s);
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').trim();
        return t.length() > 400 ? t.substring(0, 400) : t;
    }

    private static void append(File f, String line) {
        try {
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (java.io.IOException ignored) {
            // a lesson that could not be written is still in the chat that said it
        }
    }
}
