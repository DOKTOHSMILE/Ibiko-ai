package ai.ibiko;

import java.util.List;
import java.util.Locale;

import ai.ibiko.chat.ChatBook;
import ai.ibiko.ibiko1.LearningTalk;

/**
 * The Learning chat (Settings › Ibiko v1 and learning › Learning chat with your API model): a chat like any other, with
 * one purpose. Nothing here draws messages or answers them: Ibiko types its question in the message box and taps Send,
 * and the message takes the same way as one the pilot types ({@link BrainGate}): the working steps, the reply written
 * out as it comes, the rating. In this chat the pilot's API model answers (its brain, {@link Brains#brainPick}).
 *
 * <p>When an exchange is done, whoever asked it (Ibiko or the pilot), Ibiko checks the answer against what it holds and
 * keeps it (known, unsure, or set aside), and says so in a note on the chat; the names in it it doesn't know become its
 * next questions. A message that starts with "Ibiko" is answered by Ibiko itself. While the pilot writes or a reply is
 * coming, Ibiko waits its turn. It asks only while the Learning chat is open, at most {@link #PER_DAY} a day: each
 * question uses the pilot's API tokens. "Stop learning" turns it off.
 */
final class LearningRoom {
    /** Settings: the Learning chat is on. */
    static final String ON = ai.ibiko.chat.ui.SettingsLearningActivity.LEARNING_CHAT;
    /** Settings: it was just turned on, so the Learning chat opens when the chat screen is back. */
    static final String OPEN = ai.ibiko.chat.ui.SettingsLearningActivity.LEARNING_CHAT_OPEN;
    private static final String ID = "learning_chat_id", DAY = "learning_chat_day", ASKED = "learning_chat_asked", LEARNED = "learning_chat_learned";
    static final String TITLE = "Learning";
    /** The most questions Ibiko asks the API model in a day: the tokens are the pilot's. */
    static final int PER_DAY = 60;
    /** The pause between one exchange and Ibiko's next question, so it can be read. */
    static final long BETWEEN_MS = 6000, TICK_MS = 900, TYPE_MS = 45;

    private final MainActivity a;
    private LearningTalk talk;
    /** Ibiko is typing its next question now. */
    private boolean typing;
    /** When the last exchange ended (or the pilot last wrote): the next question waits a little after it. */
    private long quietSince;
    /** The brain for the turn being answered now, when it isn't the chat's own ("Ibiko, …": Ibiko answers). */
    volatile String brainForTurn;
    private boolean ticking;
    /** The subject of the question Ibiko sent, for its follow-ups. */
    private String askedAbout;
    private final java.util.concurrent.ExecutorService learner = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ibiko-learning-chat");
        t.setDaemon(true);
        return t;
    });

    LearningRoom(MainActivity a) {
        this.a = a;
    }

    /** Whether the chat on screen is the Learning chat. */
    boolean onScreen() {
        String id = a.prefs == null ? null : a.prefs.getString(ID, null);
        return id != null && a.chats != null && a.chats.current() != null && id.equals(a.chats.current().id);
    }

    /** The brain that answers in the Learning chat: the API model, unless this turn is for Ibiko. Null elsewhere. */
    String brain() {
        if (!onScreen()) {
            return null;
        }
        String once = brainForTurn;
        return once != null ? once : "api";
    }

    /** Back on the chat screen: the Learning chat opens when it was just turned on, and the talk goes on while it is on. */
    void sync() {
        if (a.prefs == null || a.chats == null) {
            return;
        }
        if (!a.prefs.getBoolean(ON, false)) {
            return;
        }
        String id = chatId();
        if (a.prefs.getBoolean(OPEN, false)) {
            a.prefs.edit().putBoolean(OPEN, false).apply();
            if (a.chatPane != null) {
                a.chatPane.openChatById(id);
            } else {
                a.chats.use(id);
            }
            if (a.chats.current() != null && id.equals(a.chats.current().id) && a.chats.current().messages.isEmpty()) {
                note("This is the Learning chat. Ibiko asks " + teacher() + " what it doesn't know, the way you would, and checks every"
                        + " answer before it keeps it. Write here any time: " + teacher() + " answers you too and Ibiko learns from it."
                        + " Start with “Ibiko” to talk to Ibiko. Say “stop learning” to end it.");
            }
        }
        if (!ticking) {
            ticking = true;
            quietSince = System.currentTimeMillis();
            a.handler.postDelayed(tick, TICK_MS);
        }
    }

    /** The app went to the background: nothing is asked until it is back. */
    void pause() {
        ticking = false;
        a.handler.removeCallbacks(tick);
    }

    /** Opens the Learning chat from a message in another chat ("start a learning conversation with my API model"). */
    boolean openFromChat() {
        if (a.prefs == null) {
            return false;
        }
        a.prefs.edit().putBoolean(ON, true).putBoolean(OPEN, true).apply();
        a.handler.postDelayed(this::sync, 1200); // after the reply that says so
        return true;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!ticking || a.isFinishing()) {
                return;
            }
            try {
                step();
            } catch (RuntimeException e) {
                // a step that went wrong waits for the next tick
            }
            a.handler.postDelayed(this, TICK_MS);
        }
    };

    /** One look at the Learning chat: learn from what was answered, then ask the next question when it is quiet. */
    private void step() {
        if (!a.prefs.getBoolean(ON, false)) {
            pause();
            return;
        }
        if (!onScreen() || typing) {
            return;
        }
        if (a.busy()) {
            quietSince = System.currentTimeMillis();
            return;
        }
        brainForTurn = null; // the turn is over: the chat's own brain again
        learnFromDone();
        android.widget.EditText box = a.findViewById(R.id.et_message);
        if (box != null && (box.hasFocus() && box.getText().length() > 0 || box.getText().toString().trim().length() > 0)) {
            quietSince = System.currentTimeMillis(); // the pilot is writing: Ibiko waits its turn
            return;
        }
        if (System.currentTimeMillis() - quietSince < BETWEEN_MS || !a.ibikoScreen.teacherReady() || leftToday() == 0) {
            return;
        }
        String[] next = talk().next();
        if (next == null) {
            return;
        }
        askedAbout = next[1];
        a.prefs.edit().putInt(ASKED, a.prefs.getInt(ASKED, 0) + 1).apply();
        typeThenSend(next[0]);
    }

    private LearningTalk talk() {
        if (talk == null) {
            talk = new LearningTalk(a.ibiko1().learningBrain(), text -> { });
        }
        return talk;
    }

    /**
     * Every exchange in the Learning chat that is done and not yet learned from: the question (Ibiko's or the pilot's),
     * and the reply under it. The check runs off the main thread; what was kept is a note on the chat.
     */
    private void learnFromDone() {
        String id = a.prefs.getString(ID, null);
        ChatBook.Section s = section(id);
        if (s == null) {
            return;
        }
        int done = a.prefs.getInt(LEARNED + "_" + id, 0);
        List<ChatBook.Message> m = s.messages;
        for (int i = done; i < m.size(); i++) {
            ChatBook.Message q = m.get(i);
            if (!"user".equals(q.role) || !"text".equals(q.kind)) {
                continue;
            }
            StringBuilder answer = new StringBuilder();
            int j = i + 1;
            for (; j < m.size() && !"user".equals(m.get(j).role); j++) {
                if ("assistant".equals(m.get(j).role) && "text".equals(m.get(j).kind) && !ours(m.get(j).body)) {
                    answer.append(answer.length() == 0 ? "" : "\n").append(m.get(j).body);
                }
            }
            if (answer.length() == 0) {
                if (j >= m.size()) {
                    break; // not answered yet
                }
                continue;
            }
            a.prefs.edit().putInt(LEARNED + "_" + id, j).apply();
            final String question = q.body.trim(), said = ai.ibiko.chat.ReplyButtons.split(answer.toString()).text.trim();
            final String about = askedAbout;
            askedAbout = null;
            if (question.matches("(?is)^\\s*@?ibiko\\b.*")) {
                i = j - 1;
                continue; // Ibiko answered that one itself
            }
            learner.execute(() -> {
                String verdict = LearningTalk.notAnAnswer(said) ? "Not kept: that isn't an answer, so there's nothing to learn from it."
                        : a.ibiko1().learningBrain().checkAndKeep(question, said, about);
                talk().heard(said, about);
                a.handler.post(() -> note("Ibiko: " + verdict));
            });
            i = j - 1;
        }
    }

    /**
     * Ibiko's question typed in the message box, letter by letter, then sent with the Send button: the same way as the
     * pilot's own message.
     */
    private void typeThenSend(final String question) {
        final android.widget.EditText box = a.findViewById(R.id.et_message);
        if (box == null) {
            return;
        }
        typing = true;
        box.setHint("Ibiko is typing…");
        for (int i = 1; i <= question.length(); i++) {
            final int n = i;
            a.handler.postDelayed(() -> {
                if (typing && !box.hasFocus() && onScreen()) {
                    box.setText(question.substring(0, n));
                    box.setSelection(n);
                }
            }, TYPE_MS * i);
        }
        a.handler.postDelayed(() -> {
            typing = false;
            box.setHint(null);
            if (onScreen() && question.equals(box.getText().toString())) {
                a.sendComposer(); // as the pilot sends: through the one way every message takes
            }
        }, TYPE_MS * question.length() + 200);
    }

    /**
     * A message in the Learning chat, before it takes the usual way: "stop learning" turns it off (true: done here),
     * and "Ibiko, …" is answered by Ibiko itself this turn. False for everything else.
     */
    boolean control(String text) {
        if (text == null || !onScreen()) {
            return false;
        }
        String said = text.trim();
        if (LearningTalk.stops(said) || said.matches("(?i)^\\s*(stop|pause|end)\\s*[.!]*\\s*$")) {
            a.prefs.edit().putBoolean(ON, false).apply();
            pause();
            a.play(ai.ibiko.turn.LocalTurn.speakAs(a.stage.agent(), "Stopped. What I learned is kept. Turn the Learning chat on again in"
                    + " Settings › Ibiko v1 and learning, or ask me to start a learning conversation."), null);
            return true;
        }
        if (said.matches("(?is)^\\s*@?ibiko\\b[\\s,:;!-]+\\S.*")) {
            brainForTurn = "ibiko"; // "Ibiko, what did you learn about Lake Chad?": Ibiko answers, through the same way
        }
        quietSince = System.currentTimeMillis();
        return false;
    }

    /** The Learning chat's id, made the first time. */
    private String chatId() {
        String id = a.prefs.getString(ID, null);
        if (section(id) != null) {
            return id;
        }
        ChatBook.Section s = a.chats.openOwn(TITLE, "ibiko", false);
        a.prefs.edit().putString(ID, s.id).putInt(LEARNED + "_" + s.id, 0).apply();
        return s.id;
    }

    private ChatBook.Section section(String id) {
        if (id == null || a.chats == null) {
            return null;
        }
        for (ChatBook.Section s : a.chats.sections()) {
            if (s.id.equals(id) && !s.archived) {
                return s;
            }
        }
        return null;
    }

    /** Ibiko's own notes on the Learning chat (what it kept, the welcome): not part of an answer. */
    static boolean ours(String body) {
        return body != null && (body.startsWith("Ibiko: ") || body.startsWith("This is the Learning chat."));
    }

    private String teacher() {
        String t = a.ibikoScreen.teacherName();
        return t == null || t.trim().isEmpty() ? "the API model" : t;
    }

    private int leftToday() {
        String today = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(new java.util.Date());
        if (!today.equals(a.prefs.getString(DAY, ""))) {
            a.prefs.edit().putString(DAY, today).putInt(ASKED, 0).apply();
        }
        return Math.max(0, PER_DAY - a.prefs.getInt(ASKED, 0));
    }

    /** A note on the Learning chat, the way the chat shows its notes. */
    private void note(String text) {
        String id = a.prefs.getString(ID, null);
        if (id == null || a.chats == null) {
            return;
        }
        a.chats.addTo(id, "assistant", "text", text, "ibiko");
        if (onScreen() && a.chatPane != null) {
            a.chatPane.show();
        }
    }
}
