package ai.ibiko.main;

import java.io.File;
import java.io.IOException;

/**
 * Each chat's own folder, and nothing of another chat's: {@code files/ibiko/chats/<chat>/}. Inside it,
 * {@code repos/} holds what was pulled (cloned repositories), {@code memory/} holds what Ibiko keeps about this chat
 * (its project record, plans, task states), and {@code images/} the pictures made in it. The file tools, the agent and
 * the Linux commands of a chat see only this folder (in Linux it is {@code /workspace}); the workspace screen shows
 * only it; deleting the chat deletes it.
 */
public final class ChatSpace {
    /** Where the chats' folders are, under the coding workspace {@code files/ibiko}. */
    public static final String CHATS = "chats";
    /** Pulled data (cloned repositories), apart from memories. */
    public static final String REPOS = "repos";
    /** What Ibiko keeps about this chat. */
    public static final String MEMORY = "memory";
    public static final String IMAGES = "images";

    private ChatSpace() {
    }

    /** {@code files/ibiko}, the folder all chats' folders are in. */
    public static File root(File filesDir) {
        return new File(filesDir, "ibiko");
    }

    /** A chat's own folder (made with its parts when missing). {@code chat} is the conversation's id. */
    public static File of(File filesDir, String chat) {
        File d = new File(new File(root(filesDir), CHATS), safe(chat));
        new File(d, REPOS).mkdirs();
        new File(d, MEMORY).mkdirs();
        return d;
    }

    /** True when {@code file} is {@code root} or inside it. A path that cannot be resolved is not inside. */
    public static boolean holds(File root, File file) {
        if (root == null || file == null) {
            return false;
        }
        try {
            String r = root.getCanonicalPath();
            String f = file.getCanonicalPath();
            return f.equals(r) || f.startsWith(r + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The other chat's folder, when {@code file} sits in a chat that is not {@code mine}. Null when the file is in
     * this chat, or not in a chat at all. This is the hard line: a chat's machine does not open another chat's files.
     */
    public static File otherChat(File mine, File file) {
        File theirs = chatOf(file);
        if (theirs == null) {
            return null;
        }
        File own = mine == null ? null : chatOf(mine);
        if (own == null && mine != null && mine.isDirectory()) {
            own = chatOf(new File(mine, "x"));
        }
        if (own != null && own.getName().equals(theirs.getName())) {
            return null;
        }
        return theirs;
    }

    /**
     * The folder a command may mount. A chat's own folder when {@code dir} is in one. Never the folder that holds
     * every chat: that would let one machine read the others. Outside a chat, only {@code bench}, which has no chats.
     */
    public static File visible(File dir) {
        File chat = chatOf(dir);
        if (chat != null) {
            return chat;
        }
        File cur = dir;
        while (cur != null) {
            if ("ibiko".equals(cur.getName())) {
                if (new File(cur, CHATS).isDirectory()) {
                    File bench = new File(cur, "bench");
                    bench.mkdirs();
                    return bench;
                }
                return cur;
            }
            cur = cur.getParentFile();
        }
        return null;
    }
    public static File chatOf(File dir) {
        File cur = dir;
        while (cur != null && cur.getParentFile() != null) {
            File p = cur.getParentFile();
            if (CHATS.equals(p.getName()) && p.getParentFile() != null && "ibiko".equals(p.getParentFile().getName())) {
                return cur;
            }
            cur = p;
        }
        return null;
    }

    static String safe(String chat) {
        String s = chat == null ? "" : chat.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() || s.startsWith(".") ? "chat" : s;
    }

    /** Deletes a chat's folder and everything in it (its repositories, memories and pictures). */
    public static boolean delete(File filesDir, String chat) {
        File d = new File(new File(root(filesDir), CHATS), safe(chat));
        return !d.exists() || wipe(d);
    }

    private static boolean wipe(File f) {
        File[] kids = f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath()) ? f.listFiles() : null;
        if (kids != null) {
            for (File k : kids) {
                wipe(k);
            }
        }
        return f.delete() || !f.exists();
    }

    /**
     * Files from before chats had their own folders ({@code files/ibiko/memory/projects/<repo>} and the rest) are moved
     * once into {@code chat}'s folder: repositories (folders with {@code .git}) into {@code repos/}, records and task
     * states into {@code memory/}, anything else as it was. Returns how many were moved.
     */
    public static int adoptOld(File filesDir, String chat) {
        File ws = root(filesDir);
        File[] top = ws.listFiles();
        if (top == null) {
            return 0;
        }
        File mine = of(filesDir, chat);
        int moved = 0;
        File oldProjects = new File(ws, "memory/projects");
        File[] projects = oldProjects.listFiles();
        if (projects != null) {
            for (File p : projects) {
                File to = new File(new File(mine, new File(p, ".git").exists() ? REPOS : MEMORY + "/projects"), p.getName());
                to.getParentFile().mkdirs();
                if (!to.exists() && p.renameTo(to)) {
                    moved++;
                }
            }
        }
        for (File f : top) {
            if (CHATS.equals(f.getName())) {
                continue;
            }
            if (MEMORY.equals(f.getName()) && f.isDirectory()) {
                File[] kids = f.listFiles();
                for (File k : kids == null ? new File[0] : kids) {
                    if ("projects".equals(k.getName()) && (k.list() == null || k.list().length == 0)) {
                        k.delete();
                        continue;
                    }
                    File to = new File(new File(mine, MEMORY), k.getName());
                    if (!to.exists() && k.renameTo(to)) {
                        moved++;
                    }
                }
                f.delete();
                continue;
            }
            File to = new File(mine, f.getName());
            if (!to.exists() && f.renameTo(to)) {
                moved++;
            }
        }
        return moved;
    }
}
