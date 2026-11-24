package ai.ibiko;

import java.util.List;
import java.util.Locale;

import ai.ibiko.chat.ChatBook;
import ai.ibiko.ibiko1.LearningTalk;

/**
 * The Learning chat (Settings › Ibiko v1 and learning › Learning chat with your API model): a chat of its own, titled
 * "Learning", where Ibiko talks with the pilot's API model the way a person would. Ibiko types its question (it shows in
 * the message box, letter by letter), sends it, the model's answer is written out as it comes, and Ibiko says what it
 * kept: known when it agrees with what Ibiko holds, unsure when it is new, set aside when it goes against a fact.
 *
 * <p>The pilot can join: a message in the Learning chat goes to the model with the talk so far, and Ibiko learns from
 * the answer too; a message that starts with "Ibiko" is for Ibiko, and Ibiko answers it. While the pilot talks, Ibiko
 * waits its turn. "Stop learning" turns it off. Each question uses the pilot's API tokens, so it asks at most
 * {@link #PER_DAY} a day, and only while the app is open.
 */
final class LearningRoom {
    /** Settings: the Learning chat is on. */
    static final String ON = ai.ibiko.chat.ui.SettingsLearningActivity.LEARNING_CHAT;
    /** Settings: it was just turned on, so the Learning chat opens when the chat screen is back. */
    static final String OPEN = ai.ibiko.chat.ui.SettingsLearningActivity.LEARNING_CHAT_OPEN;
    private static final String ID = "learning_chat_id", DAY = "learning_chat_day", ASKED = "learning_chat_asked";
    static final String TITLE = "Learning";
    /** The most questions Ibiko asks the API model in a day: the tokens are the pilot's. */
    static final int PER_DAY = 60;

    /** How the model is asked when the pilot joins: one voice in a three-way chat. */
    static final String ROOM_SYSTEM = "You are in a group chat with Ibiko, a small AI that is learning, and the person who owns it. "
            + "Ibiko asks you questions to learn; now the person is speaking. Answer the person directly, in plain language, in two to "
            + "six sentences, with facts and dates where they matter. If you are not sure, say so.";

    private final MainActivity a;
    private volatile LearningTalk talk;
    private volatile boolean pilotTalking;
    private volatile long quietUntil;
    /** The words this room put on the live line, so it clears only its own. */
    private volatile boolean liveOurs;

    LearningRoom(MainActivity a) {
        this.a = a;
    }

    /** Whether the chat on screen is the Learning chat. */
    boolean onScreen() {
        String id = a.prefs == null ? null : a.prefs.getString(ID, null);
        return id != null && a.chats != null && a.chats.current() != null && id.equals(a.chats.current().id);
    }

    /** Back on the chat screen: the Learning chat opens when it was just turned on, and the talk goes on while it is on. */
    void sync() {
        if (a.prefs == null || a.chats == null) {
            return;
        }
        boolean on = a.prefs.getBoolean(ON, false);
        if (!on) {
            stop();
            return;
        }
        String id = chatId();
        if (a.prefs.getBoolean(OPEN, false)) {
            a.prefs.edit().putBoolean(OPEN, false).apply();
            if (a.chats.use(id) && a.chatPane != null) {
                a.chatPane.openChatById(id);
            }
            if (a.chats.current() != null && id.equals(a.chats.current().id) && a.chats.current().messages.isEmpty()) {
                post("assistant", "Ibiko\nThis is my learning chat. I'll ask " + teacher() + " about what I don't know, one question at a "
                        + "time, and check every answer against what I hold before I keep it. Join in any time: write to "
                        + teacher() + " and I'll learn from that too, or start with “Ibiko” to talk to me. Say “stop learning” to end it.");
            }
        }
        start();
    }

    /** The app went to the background: the talk stops (it uses the pilot's tokens), and starts again when it's back. */
    void pause() {
        stop();
    }

    /** The Learning chat's id, made the first time. */
    private String chatId() {
        String id = a.prefs.getString(ID, null);
        if (id != null) {
            for (ChatBook.Section s : a.chats.sections()) {
                if (s.id.equals(id) && !s.archived) {
                    return id;
                }
            }
        }
        ChatBook.Section s = a.chats.openOwn(TITLE, "ibiko", false);
        a.prefs.edit().putString(ID, s.id).apply();
        return s.id;
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

    private void start() {
        LearningTalk going = talk;
        if (going != null && going.going()) {
            return;
        }
        if (!a.ibikoScreen.teacherReady()) {
            if (onScreen()) {
                post("assistant", "Ibiko\nI need an API model to learn from, and the internet. Add one in Settings › Models, and I'll start.");
            }
            return;
        }
        int left = leftToday();
        if (left == 0) {
            return;
        }
        final LearningTalk.Brain brain = a.ibiko1().learningBrain();
        LearningTalk t = new LearningTalk(new LearningTalk.Brain() {
            @Override
            public String ask(String question) {
                return brain.ask(question);
            }

            @Override
            public String teacher() {
                return brain.teacher();
            }

            @Override
            public String checkAndKeep(String question, String answer, String subject) {
                return brain.checkAndKeep(question, answer, subject);
            }

            @Override
            public List<String> open() {
                return brain.open();
            }

            @Override
            public String randomSubject(java.util.Random r) {
                return brain.randomSubject(r);
            }

            @Override
            public boolean knows(String name) {
                return brain.knows(name);
            }

            @Override
            public boolean holding() {
                return pilotTalking || System.currentTimeMillis() < quietUntil;
            }
        }, new LearningTalk.Events() {
            @Override
            public void asking(String question) {
                a.prefs.edit().putInt(ASKED, a.prefs.getInt(ASKED, 0) + 1).apply();
                typeThenSend(question);
            }

            @Override
            public void answered(String question, String answer) {
                if (answer == null) {
                    post("assistant", teacher() + "\n(no answer)");
                    return;
                }
                a.handler.post(() -> {
                    if (onScreen() && a.chatPane != null) {
                        liveOurs = true;
                        a.chatPane.setLive(answer); // written out as it comes, like any model's reply
                    }
                });
                pendingAnswer = answer;
            }

            @Override
            public void kept(String verdict) {
                String answer = pendingAnswer;
                pendingAnswer = null;
                a.handler.post(() -> {
                    clearLive();
                    if (answer != null) {
                        add("assistant", teacher() + "\n" + answer);
                    }
                    add("assistant", "Ibiko\n" + verdict);
                });
            }

            @Override
            public void done(String summary) {
                post("assistant", "Ibiko\n" + summary + (leftToday() == 0 ? " That's my " + PER_DAY + " questions for today; I'll carry on tomorrow." : ""));
            }
        });
        t.typeMsPerChar = 45;
        t.readMsPerChar = 22;
        t.betweenMs = 4000;
        talk = t;
        t.start(left);
    }

    private volatile String pendingAnswer;

    private void stop() {
        LearningTalk t = talk;
        if (t != null) {
            t.stop();
        }
        talk = null;
        a.handler.post(this::clearLive);
    }

    private void clearLive() {
        if (liveOurs && a.chatPane != null) {
            a.chatPane.setLive(null);
        }
        liveOurs = false;
    }

    /**
     * Ibiko's question typed in the message box, letter by letter, then sent: it shows as a message from Ibiko to the
     * model. When the pilot is writing in the box, or the Learning chat isn't on screen, it is sent without the typing.
     */
    private void typeThenSend(final String question) {
        final String said = "Ibiko → " + teacher() + "\n" + question;
        a.handler.post(() -> {
            final android.widget.EditText box = a.findViewById(R.id.et_message);
            boolean free = box != null && onScreen() && box.getText().toString().trim().isEmpty() && !box.hasFocus();
            if (!free) {
                add("user", said);
                return;
            }
            box.setHint("Ibiko is typing…");
            for (int i = 1; i <= question.length(); i++) {
                final int n = i;
                a.handler.postDelayed(() -> {
                    if (!box.hasFocus() && onScreen()) {
                        box.setText(question.substring(0, n));
                        box.setSelection(n);
                    }
                }, 45L * i);
            }
            a.handler.postDelayed(() -> {
                if (!box.hasFocus() && question.equals(box.getText().toString())) {
                    box.setText("");
                }
                box.setHint(null);
                add("user", said);
            }, 45L * question.length() + 120);
        });
    }

    /**
     * The pilot wrote in the Learning chat: true when it was taken here. "Stop learning" turns it off; "Ibiko, …" is
     * for Ibiko; anything else goes to the model with the talk so far, and Ibiko learns from the answer.
     */
    boolean join(final String text) {
        if (text == null || text.trim().isEmpty() || !onScreen()) {
            return false;
        }
        final String said = text.trim();
        if (LearningTalk.stops(said) || said.matches("(?i)^\\s*(stop|pause|end)\\s*[.!]*\\s*$")) {
            add("user", said);
            a.prefs.edit().putBoolean(ON, false).apply();
            stop();
            add("assistant", "Ibiko\nStopped. What I learned is kept. Turn the Learning chat on again in Settings › Ibiko v1 and learning.");
            return true;
        }
        quietUntil = System.currentTimeMillis() + 20000;
        java.util.regex.Matcher toIbiko = java.util.regex.Pattern.compile("(?i)^\\s*@?ibiko\\b[\\s,:;!-]*(.*)$").matcher(said);
        if (toIbiko.find() && !toIbiko.group(1).trim().isEmpty()) {
            add("user", said);
            final String rest = toIbiko.group(1).trim();
            a.handler.post(() -> {
                if (!a.isFinishing()) {
                    a.ibiko1().endpoint().chat(rest); // Ibiko answers here, in the Learning chat
                }
            });
            return true;
        }
        add("user", said);
        if (!a.ibikoScreen.teacherReady()) {
            add("assistant", "Ibiko\n" + "I can't reach " + teacher() + " right now: it needs the internet and an API model in Settings › Models.");
            return true;
        }
        pilotTalking = true;
        final String talkSoFar = transcript();
        new Thread(() -> {
            try {
                String answer = a.ibikoScreen.askTeacher(ROOM_SYSTEM, (talkSoFar.isEmpty() ? "" : "The chat so far:\n" + talkSoFar + "\n\n")
                        + "The person says: " + ai.ibiko.ibiko1.PromptReader.guard(said));
                if (answer == null || answer.trim().isEmpty()) {
                    post("assistant", teacher() + "\n(no answer)");
                    return;
                }
                final String clean = ai.ibiko.suit.FunctionCode.withoutCode(answer).trim();
                a.handler.post(() -> {
                    if (onScreen() && a.chatPane != null) {
                        liveOurs = true;
                        a.chatPane.setLive(clean);
                    }
                });
                try {
                    Thread.sleep(Math.min(9000, 22L * clean.length()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Ibiko learns from the answer too: a question the pilot asked, checked and kept the same way
                String verdict = said.matches("(?is).*\\?\\s*$|^(?:who|what|when|where|why|how|which|is|are|was|were|do|does|did|can|tell me|explain)\\b.*")
                        ? a.ibiko1().learningBrain().checkAndKeep(said, clean, null) : null;
                a.handler.post(() -> {
                    clearLive();
                    add("assistant", teacher() + "\n" + clean);
                    if (verdict != null) {
                        add("assistant", "Ibiko\n" + verdict);
                    }
                });
            } finally {
                pilotTalking = false;
                quietUntil = System.currentTimeMillis() + 8000;
            }
        }, "ibiko-learning-room").start();
        return true;
    }

    /** The last few messages of the Learning chat, for the model to follow the talk. */
    private String transcript() {
        String id = a.prefs.getString(ID, null);
        StringBuilder b = new StringBuilder();
        for (ChatBook.Section s : a.chats.sections()) {
            if (s.id.equals(id)) {
                List<ChatBook.Message> m = s.messages;
                for (int i = Math.max(0, m.size() - 8); i < m.size(); i++) {
                    ChatBook.Message msg = m.get(i);
                    if ("text".equals(msg.kind)) {
                        String body = msg.body.length() > 600 ? msg.body.substring(0, 600) + "…" : msg.body;
                        b.append("user".equals(msg.role) && !body.startsWith("Ibiko → ") ? "Person: " : "").append(body.replace("\n", ": ")).append('\n');
                    }
                }
            }
        }
        return b.toString().trim();
    }

    private void post(final String role, final String body) {
        a.handler.post(() -> add(role, body));
    }

    /** A message in the Learning chat, wherever the pilot is; the screen redraws when it is the chat on screen. */
    private void add(String role, String body) {
        String id = a.prefs.getString(ID, null);
        if (id == null || a.chats == null) {
            return;
        }
        a.chats.addTo(id, role, "text", body, "ibiko");
        if (onScreen() && a.chatPane != null) {
            a.chatPane.show();
        }
    }
}
