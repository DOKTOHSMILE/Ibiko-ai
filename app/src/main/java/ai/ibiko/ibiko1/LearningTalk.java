package ai.ibiko.ibiko1;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A learning conversation with the pilot's API model, started when asked ("start a learning conversation with my API
 * model", "go and learn 20 things"): Ibiko asks, one question at a time, what it doesn't know (the questions it
 * couldn't answer first, then what the last answer made it curious about, then random subjects from its encyclopedia),
 * checks each answer against what it holds ({@link TeacherCheck}) and keeps it: known when it agrees, unsure when it is
 * new, set aside when it goes against a fact it holds. Every exchange is shown. Each question costs the pilot tokens, so
 * it asks a set number (10 unless told), and "stop learning" ends it at once. Off the main thread.
 */
public final class LearningTalk {
    /** What it is given of the brain. */
    public interface Brain {
        /** The API model's answer to a question, or null. Blocking. */
        String ask(String question);

        /** The model's name, for the transcript ("Claude"). */
        String teacher();

        /** The answer checked against what Ibiko holds, and kept by the verdict; the line saying what was done. */
        String checkAndKeep(String question, String answer, String subject);

        /** Questions it couldn't answer before, oldest first. */
        List<String> open();

        /** A subject at random from its encyclopedia, or null. */
        String randomSubject(Random r);

        /** Whether it already knows about a name (its encyclopedia or its facts). */
        boolean knows(String name);

        /** While true, nothing new is asked (the person is talking in the learning chat). */
        default boolean holding() {
            return false;
        }
    }

    /**
     * Each step of an exchange, for a chat that shows it as it happens: Ibiko typing its question, the model's answer,
     * what Ibiko kept. Called on the talk's own thread.
     */
    public interface Events {
        /** Ibiko is about to send {@code question}: shown being typed. */
        void asking(String question);

        /** The model answered (null: no answer). */
        void answered(String question, String answer);

        /** What Ibiko did with the answer ("Kept as known: …"). */
        void kept(String verdict);

        /** The talk ended: what it learned, in a line. */
        void done(String summary);
    }

    /** The pauses that let a person watch: typing a question, reading an answer, between exchanges. 0 in tests. */
    public long typeMsPerChar, readMsPerChar, betweenMs;

    private static final Pattern START = Pattern.compile("(?i)\\b(?:start|begin|have|enter|open|do|run)s?\\s+(?:into\\s+|in\\s+)?(?:a|an|the|your)?\\s*learning\\s+(?:conversation|session|chat|talk)\\b"
            + "|\\blearn(?:ing)?\\s+(?:(\\d{1,2})\\s+)?(?:some\\s+|random\\s+|new\\s+|more\\s+)*(?:things|knowledge|facts|stuff)\\s+(?:from|with)\\s+(?:my|the)\\s+(?:api\\s+)?(?:model|teacher|ai|llm|claude|gpt|gemini)\\b"
            + "|\\b(?:go\\s+(?:and\\s+)?)?learn\\s+(\\d{1,2})\\s+(?:new\\s+|random\\s+)*(?:things|facts)\\b"
            + "|\\bstart(?:s|ing)?\\s+learning\\s+(?:random\\s+|new\\s+)*(?:knowledge|things|facts)\\b"
            + "|\\b(?:talk|chat)\\s+(?:to|with)\\s+(?:my|the)\\s+api\\s+model\\s+(?:and|to)\\s+learn\\b");
    private static final Pattern STOP = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:stop|end|finish|pause|enough)\\s+(?:the\\s+|your\\s+)?(?:learning|lessons?|learning conversation)"
            + "|^\\s*that'?s enough learning\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,2})\\b");

    /** The most a session asks: tokens are the pilot's. */
    public static final int MOST = 50, USUAL = 10;

    private final Brain brain;
    private final Events events;
    private final Random random = new Random();
    private volatile int run;
    private volatile boolean going;

    /** A talk shown as one block per exchange (in the chat it was asked from). */
    public LearningTalk(final Brain brain, final Consumer<String> show) {
        this(brain, new Events() {
            private String asked;

            @Override
            public void asking(String question) {
                asked = question;
            }

            @Override
            public void answered(String question, String answer) {
                if (answer == null) {
                    show.accept("Ibiko → " + brain.teacher() + ": " + question + "\n" + brain.teacher() + ": (no answer)");
                    asked = null;
                } else {
                    asked = "Ibiko → " + brain.teacher() + ": " + question + "\n" + brain.teacher() + ": " + clip(answer, 420);
                }
            }

            @Override
            public void kept(String verdict) {
                show.accept((asked == null ? "" : asked + "\n") + "Ibiko: " + verdict);
                asked = null;
            }

            @Override
            public void done(String summary) {
                show.accept(summary);
            }
        });
    }

    /** A talk shown step by step ({@link Events}), as a chat of its own. */
    public LearningTalk(Brain brain, Events events) {
        this.brain = brain;
        this.events = events;
    }

    /** How many questions a message asks to learn, or 0 when it doesn't ask for a learning conversation. */
    public static int asked(String message) {
        if (message == null || !START.matcher(message).find()) {
            return 0;
        }
        Matcher n = NUMBER.matcher(message);
        int want = n.find() ? Integer.parseInt(n.group(1)) : USUAL;
        return Math.max(1, Math.min(MOST, want));
    }

    /** Whether a message asks to stop a learning conversation. */
    public static boolean stops(String message) {
        return message != null && STOP.matcher(message).find();
    }

    public boolean going() {
        return going;
    }

    /** Ends the conversation: the question being asked is let go, nothing more is asked. */
    public void stop() {
        run++;
        going = false;
    }

    /** Starts a learning conversation of {@code count} questions, in the background. */
    public void start(final int count) {
        final int mine = ++run;
        going = true;
        new Thread(() -> {
            Deque<String> curious = new ArrayDeque<String>();
            Set<String> asked = new LinkedHashSet<String>();
            List<String> open = new ArrayList<String>(brain.open());
            int known = 0, unsure = 0, setAside = 0, none = 0;
            for (int i = 0; i < count && mine == run; i++) {
                while (brain.holding() && mine == run) {
                    nap(400); // the person is talking: the talk waits its turn
                }
                if (i > 0 && !nap(betweenMs) || mine != run) {
                    return;
                }
                String subject = null, question = null;
                while (question == null && !open.isEmpty()) {
                    String q = open.remove(0);
                    if (asked.add(q.toLowerCase(Locale.ROOT))) {
                        question = q;
                    }
                }
                while (question == null && !curious.isEmpty()) {
                    String c = curious.poll();
                    if (asked.add(c.toLowerCase(Locale.ROOT))) {
                        subject = c;
                        question = "Who or what is " + c + "?";
                    }
                }
                for (int tries = 0; question == null && tries < 6; tries++) {
                    String s = brain.randomSubject(random);
                    if (s != null && asked.add(s.toLowerCase(Locale.ROOT))) {
                        subject = s;
                        question = pick(s);
                    }
                }
                if (question == null) {
                    break;
                }
                events.asking(question);
                if (!nap(typeMsPerChar * question.length()) || mine != run) {
                    return;
                }
                String answer;
                try {
                    answer = brain.ask(question);
                } catch (RuntimeException e) {
                    answer = null;
                }
                if (mine != run) {
                    return; // stopped while it was asking
                }
                if (answer == null || answer.trim().isEmpty()) {
                    none++;
                    events.answered(question, null);
                    continue;
                }
                String a = answer.trim();
                events.answered(question, a);
                if (!nap(Math.min(9000, readMsPerChar * a.length())) || mine != run) {
                    return;
                }
                String verdict = brain.checkAndKeep(question, a, subject);
                if (verdict.startsWith("Kept as known")) {
                    known++;
                } else if (verdict.startsWith("Set aside")) {
                    setAside++;
                } else {
                    unsure++;
                }
                events.kept(verdict);
                // what the answer named that Ibiko doesn't know yet: the next questions
                for (String name : names(a)) {
                    if (curious.size() < 6 && !name.equalsIgnoreCase(subject == null ? "" : subject) && !brain.knows(name)
                            && !asked.contains(name.toLowerCase(Locale.ROOT))) {
                        curious.add(name);
                    }
                }
            }
            if (mine == run) {
                going = false;
                events.done("Learning conversation done: " + (known + unsure + setAside) + " answers learned from " + brain.teacher() + ". " + known
                        + " agreed with what I know and are kept as known; " + unsure + " are new to me, kept as unsure until you confirm them"
                        + (setAside > 0 ? "; " + setAside + " went against facts I hold, so I set them aside" : "")
                        + (none > 0 ? "; " + none + " got no answer" : "") + ". Ask me about any of it.");
            }
        }, "ibiko1-learning-talk").start();
    }

    /** Waits {@code ms}; false when the wait was cut short. */
    private static boolean nap(long ms) {
        if (ms <= 0) {
            return true;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** A question about a subject, in one of the ways a curious person asks. */
    private String pick(String subject) {
        String[] ways = {"What is " + subject + "?", "What is " + subject + " best known for?", "Why does " + subject + " matter?",
            "What are the key facts about " + subject + "?"};
        return ways[random.nextInt(ways.length)];
    }

    private static final Pattern NAME = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+(?:of\\s+|the\\s+)?[A-Z][a-z]+){0,3})\\b");
    private static final Set<String> SMALL = new java.util.HashSet<String>(java.util.Arrays.asList("The", "This", "That", "It", "In", "On", "At",
            "He", "She", "They", "Its", "His", "Her", "Their", "A", "An", "And", "But", "As", "For", "Today", "However", "Also", "There"));

    /** The names an answer holds ("the Benue River", "Lake Chad"), first seen first. */
    static List<String> names(String text) {
        List<String> out = new ArrayList<String>();
        Matcher m = NAME.matcher(text);
        while (m.find()) {
            String n = m.group(1).trim();
            if (!SMALL.contains(n) && n.length() > 3 && !out.contains(n) && m.start() > 0) {
                out.add(n);
            }
        }
        return out;
    }

    static String clip(String s, int n) {
        String t = s.replaceAll("\\s+", " ");
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }

    static Function<String, String> none() {
        return x -> null;
    }
}
