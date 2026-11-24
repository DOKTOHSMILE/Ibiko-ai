package ai.ibiko.ibiko1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko's picture of the person it talks with, from what they say about themselves: "I'm a man", "my name is Ada",
 * "I'm 30", "I live in Lagos", "I'm from Enugu", "I'm a nurse", "I like football", "I'm tired". Each is understood
 * (not copied), kept (a file in the brain's folder), said back in a word so the person knows it was taken in, and used
 * when they ask "who am I?", "what's my name?", "what do you know about me?". Pure Java.
 */
public final class Person {
    private final File file;
    private final Properties p = new Properties();
    /** How they feel right now (said this chat, not kept). */
    private String mood;

    public Person(File dir) {
        this.file = dir == null ? null : new File(dir, "person.properties");
        if (file != null && file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (IOException ignored) {
                // a damaged file: start again
            }
        }
    }

    public synchronized String get(String key) {
        return p.getProperty(key);
    }

    private void set(String key, String value) {
        p.setProperty(key, value);
        save();
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                p.store(out, "What the person told Ibiko about themselves");
            }
        } catch (IOException ignored) {
            // kept for this run at least
        }
    }

    private List<String> list(String key) {
        List<String> out = new ArrayList<String>();
        String v = p.getProperty(key);
        if (v != null) {
            for (String x : v.split("\\|")) {
                if (!x.isEmpty()) {
                    out.add(x);
                }
            }
        }
        return out;
    }

    private void add(String key, String value) {
        Set<String> all = new LinkedHashSet<String>(list(key));
        all.add(value);
        set(key, String.join("|", all));
    }

    private void drop(String key, String value) {
        List<String> all = list(key);
        all.removeIf(x -> x.equalsIgnoreCase(value));
        set(key, String.join("|", all));
    }

    // ------------------------------------------------------------------ what they say about themselves

    private static final String END = "\\s*[.!]*$";
    private static final Pattern NAME = Pattern.compile("(?i)^(?:my name is|my name's|call me|i am called|i'm called|people call me)\\s+([\\p{L}'-]+(?:\\s+[\\p{L}'-]+)?)" + END);
    private static final Pattern NAME_IM = Pattern.compile("^(?:I'm|I am|Im|It's|This is)\\s+(\\p{Lu}[\\p{L}'-]+(?:\\s+\\p{Lu}[\\p{L}'-]+)?)" + END);
    private static final Pattern GENDER = Pattern.compile("(?i)^(?:i'm|i am|im)\\s+(?:a\\s+)?(man|woman|boy|girl|guy|lady|male|female|gentleman)" + END);
    private static final Pattern AGE = Pattern.compile("(?i)^(?:i'm|i am|im)\\s+(\\d{1,3})(?:\\s*(?:years? old|yrs?(?: old)?|years))?" + END);
    private static final Pattern FROM = Pattern.compile("(?i)^(?:i'm|i am|im)\\s+from\\s+(.{2,40}?)" + END);
    private static final Pattern LIVE = Pattern.compile("(?i)^(?:i live|i stay|i'm living|i am living|i reside)\\s+(?:in|at)\\s+(.{2,40}?)" + END);
    private static final Pattern JOB = Pattern.compile("(?i)^(?:i'm|i am|im|i work as)\\s+(?:a|an)\\s+([a-z][a-z -]{2,40}?)(?:\\s+by profession)?" + END);
    private static final Pattern LIKE = Pattern.compile("(?i)^i\\s+(?:really\\s+)?(like|love|enjoy|adore)\\s+(.{2,60}?)" + END);
    private static final Pattern DISLIKE = Pattern.compile("(?i)^i\\s+(?:don't like|do not like|hate|dislike|can't stand)\\s+(.{2,60}?)" + END);
    private static final Pattern FEEL = Pattern.compile("(?i)^(?:i'm|i am|im|i feel|i'm feeling|i am feeling)\\s+(?:so\\s+|very\\s+|really\\s+|a bit\\s+|quite\\s+)?"
            + "(tired|happy|sad|sick|ill|bored|hungry|stressed|fine|good|great|ok|okay|angry|lonely|excited|worried|anxious|sleepy|cold|hot|well|unwell|down|upset)" + END);
    private static final Pattern STUDY = Pattern.compile("(?i)^i\\s+(?:study|am studying|'m studying)\\s+(.{2,50}?)" + END);

    private static final Pattern NICK = Pattern.compile("(?i)^(?:i'm|i am|im|my name is|my name's|it's|this is)\\s+([\\p{L}'-]+(?:\\s+[\\p{L}'-]+)?)"
            + "[,;.]?\\s+(?:but\\s+|and\\s+|so\\s+)?(?:you\\s+(?:can|may|could)\\s+|just\\s+|please\\s+)?call\\s+me\\s+([\\p{L}'-]+)\\s*[.!]*$");

    /** A clause about the person themselves: understood, kept, and said back in a word; null when it isn't one. */
    public synchronized String heard(String clause) {
        String raw = clause == null ? "" : clause.trim().replaceAll("[\u2019`]", "'");
        Matcher nick = NICK.matcher(raw.replaceFirst("(?i)^(?:hey|hi|hello|so|ok|okay)\\b[\\s,!.]*", ""));
        if (nick.matches()) {
            // "I'm Valentine, you can call me Val": the name kept, and the one to use
            set("name", cap(nick.group(1)));
            set("nickname", cap(nick.group(2)));
            return "Nice to meet you, " + cap(nick.group(1)) + ". I'll call you " + cap(nick.group(2)) + ".";
        }
        String c = Paraphrase.normalize(raw); // "im a man", "I'M A MAN!!", "so... i am a man" read alike
        Matcher m = GENDER.matcher(c);
        if (m.matches()) {
            String g = m.group(1).toLowerCase(Locale.ROOT);
            String kind = g.matches("woman|girl|lady|female") ? "woman" : "man";
            set("gender", kind);
            return "Got it, you're a " + kind + ".";
        }
        m = AGE.matcher(c);
        if (m.matches()) {
            int age = Integer.parseInt(m.group(1));
            if (age > 0 && age < 120) {
                set("age", String.valueOf(age));
                set("age_year", String.valueOf(java.time.LocalDate.now().getYear()));
                return "Noted: you're " + age + ".";
            }
        }
        m = FEEL.matcher(c);
        if (m.matches()) {
            mood = m.group(1).toLowerCase(Locale.ROOT);
            return feeling(mood);
        }
        m = FROM.matcher(c);
        if (m.matches()) {
            set("from", cap(m.group(1)));
            return "Nice, you're from " + cap(m.group(1)) + ".";
        }
        m = LIVE.matcher(c);
        if (m.matches()) {
            set("lives", cap(m.group(1)));
            return "Got it, you live in " + cap(m.group(1)) + ".";
        }
        Matcher call = Pattern.compile("(?i)^(?:just |please |you can |you may )?call me\\s+([\\p{L}'-]+)$").matcher(c);
        if (call.matches()) {
            set("nickname", cap(call.group(1)));
            return "Sure, " + cap(call.group(1)) + " it is.";
        }
        m = NAME.matcher(c);
        if (!m.matches()) {
            m = NAME_IM.matcher(raw.replaceFirst("(?i)^(?:hey|hi|hello|so|ok|okay)\\b[\\s,!.]*", "").replaceAll("[.!]+$", "").replaceFirst("^(?i)im\\b", "I'm"));
        }
        if (m.matches() && !m.group(1).matches("(?i)(fine|good|great|ok|okay|here|back|done|sorry|ready|sure|happy|tired)")) {
            set("name", cap(m.group(1)));
            return "Nice to meet you, " + cap(m.group(1)) + ".";
        }
        m = JOB.matcher(c);
        if (m.matches()) {
            String role = m.group(1).trim().toLowerCase(Locale.ROOT);
            set("role", role);
            return "Got it, you're " + Facts1.a(role) + ".";
        }
        m = STUDY.matcher(c);
        if (m.matches()) {
            set("studies", m.group(1).trim());
            return "Nice, you study " + m.group(1).trim() + ".";
        }
        m = LIKE.matcher(c);
        if (m.matches()) {
            String what = m.group(2).trim();
            add("likes", what);
            drop("dislikes", what);
            return "Good to know you " + m.group(1).toLowerCase(Locale.ROOT) + " " + what + ".";
        }
        m = DISLIKE.matcher(c);
        if (m.matches()) {
            String what = m.group(1).trim();
            add("dislikes", what);
            drop("likes", what);
            return "Noted, " + what + " isn't for you.";
        }
        return null;
    }

    private static String feeling(String mood) {
        switch (mood) {
            case "tired":
            case "sleepy":
                return "Sorry you're tired. Take it easy; I'll keep my answers short.";
            case "sad":
            case "down":
            case "upset":
            case "lonely":
                return "I'm sorry you're feeling " + mood + ". I'm here if you want to talk about it.";
            case "sick":
            case "ill":
            case "unwell":
                return "Sorry you're not well. Rest up, and I hope you feel better soon.";
            case "stressed":
            case "worried":
            case "anxious":
                return "That sounds hard. If it helps, tell me what's on your mind and we can work through it one piece at a time.";
            case "angry":
                return "I hear you. Want to tell me what happened?";
            case "hungry":
                return "Then let's get you fed! Ask me for a recipe or ideas for a quick meal.";
            case "bored":
                return "Let's fix that: ask me anything, or tell me what you're into and I'll find something interesting.";
            default:
                return "Glad to hear you're " + mood + ".";
        }
    }

    private static String cap(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.trim().split("\\s+")) {
            if (!w.isEmpty()) {
                b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ what they ask about themselves

    /** The answer to a question about the person, from what they said; null when it isn't one. */
    public synchronized String asked(String q, List<String> told) {
        String t = Paraphrase.normalize(q);
        String unknown = "You haven't told me yet. Tell me and I'll remember.";
        if (t.matches("(where am i|where am i now|what is my (current |present )?location|where is my (current )?location|where am i located|"
                + "do you know where i am|can you see where i am|where i am)( (now|right now|at the moment))?")) {
            String l = get("lives");
            return "I can't see where you are: I don't read your phone's location." + (l == null
                    ? " If you tell me where you are, I'll keep it in mind." : " You told me you live in " + l + ".");
        }
        if (t.matches("(what is|what's) my nickname|what do you call me|what should you call me")) {
            String n = get("nickname") != null ? get("nickname") : get("name");
            return n == null ? unknown.replace("yet.", "what to call you yet.") : "I call you " + n + ".";
        }
        if (t.matches("(what's|what is|do you know|do you remember|tell me) my name|who am i")) {
            String name = get("name");
            if (t.equals("who am i")) {
                String about = about(told);
                return about == null ? "I don't know much about you yet. Tell me your name, or anything about yourself, and I'll remember it." : about;
            }
            String nick = get("nickname");
            return name == null ? unknown.replace("yet.", "your name yet.")
                    : "You're " + name + (nick != null && !nick.equals(name) ? ", and I call you " + nick : "") + ".";
        }
        if (t.matches("(what|tell me what|how much) do you (know|remember) about me|what have i told you( about me(self)?)?|do you know me|do you remember me")) {
            String about = about(told);
            return about == null ? "Nothing yet: you haven't told me about yourself. Your name, where you live, what you like: tell me and I'll keep it." : about;
        }
        if (t.matches("(am i a|am i) (man|woman|boy|girl|guy|lady)|what('s| is) my gender")) {
            String g = get("gender");
            return g == null ? unknown : t.contains(g) || t.startsWith("what") ? "You told me you're a " + g + "." : "You told me you're a " + g + ", not a " + t.replaceFirst("am i a? ?", "") + ".";
        }
        if (t.matches("how old am i|what('s| is) my age")) {
            String a = get("age");
            if (a == null) {
                return unknown;
            }
            int year = java.time.LocalDate.now().getYear();
            int since = Math.max(0, year - Integer.parseInt(p.getProperty("age_year", String.valueOf(year))));
            int age = Integer.parseInt(a) + since;
            return since == 0 ? "You told me you're " + age + "." : "You told me you were " + a + ", so you're about " + age + " now.";
        }
        java.util.regex.Matcher later = java.util.regex.Pattern.compile("how old (will|would) i be in (\\d{1,3}) years?( time)?|how old was i (\\d{1,3}) years? ago")
                .matcher(t);
        if (later.matches()) {
            String a = get("age");
            if (a == null) {
                return unknown.replace("yet.", "your age yet.");
            }
            int year = java.time.LocalDate.now().getYear();
            int now = Integer.parseInt(a) + Math.max(0, year - Integer.parseInt(p.getProperty("age_year", String.valueOf(year))));
            if (later.group(2) != null) {
                int n = Integer.parseInt(later.group(2));
                return "You'll be " + (now + n) + ": you're " + now + " now, and " + now + " + " + n + " = " + (now + n) + ".";
            }
            int n = Integer.parseInt(later.group(4));
            return n > now ? "You weren't born yet " + n + " years ago: you're " + now + "."
                    : "You were " + (now - n) + ": you're " + now + " now, and " + now + " − " + n + " = " + (now - n) + ".";
        }
        if (t.matches("what ([a-z]+ )?do i (like|love|enjoy)|what ([a-z]+ )?(do i|i) (like|love|enjoy) (most|best)")) {
            List<String> l = list("likes");
            return l.isEmpty() ? unknown : "From what you told me, you like " + FactsQA.and(String.join(", ", l)) + ".";
        }
        if (t.matches("where (am i|do i come) from|where('s| is) my home ?town")) {
            String f = get("from");
            return f == null ? unknown : "You're from " + f + ".";
        }
        if (t.matches("where do i (live|stay)|where('s| is) my home")) {
            String l = get("lives");
            return l == null ? unknown : "You live in " + l + ".";
        }
        if (t.matches("what do i (do|do for (a )?living)|what('s| is) my (job|work|profession)")) {
            String r = get("role");
            return r == null ? unknown : "You're " + Facts1.a(r) + ".";
        }
        if (t.matches("what do i (like|love|enjoy)|what are my (likes|hobbies|interests)")) {
            List<String> l = list("likes");
            return l.isEmpty() ? unknown : "You like " + FactsQA.and(String.join(", ", l)) + ".";
        }
        if (t.matches("how am i( feeling| doing)?|how do i feel")) {
            return mood == null ? "You haven't said. How are you feeling?" : "You told me you're " + mood + ".";
        }
        return null;
    }

    /** All they told it, as one reply; null when nothing. */
    public synchronized String about(List<String> told) {
        List<String> parts = new ArrayList<String>();
        if (get("name") != null) {
            parts.add("your name is " + get("name") + (get("nickname") != null && !get("nickname").equals(get("name"))
                    ? " (I call you " + get("nickname") + ")" : ""));
        }
        if (get("gender") != null) {
            parts.add("you're a " + get("gender"));
        }
        if (get("age") != null) {
            parts.add("you're " + get("age"));
        }
        if (get("role") != null) {
            parts.add("you're " + Facts1.a(get("role")));
        }
        if (get("studies") != null) {
            parts.add("you study " + get("studies"));
        }
        if (get("from") != null) {
            parts.add("you're from " + get("from"));
        }
        if (get("lives") != null) {
            parts.add("you live in " + get("lives"));
        }
        if (!list("likes").isEmpty()) {
            parts.add("you like " + FactsQA.and(String.join(", ", list("likes"))));
        }
        if (!list("dislikes").isEmpty()) {
            parts.add("you don't like " + FactsQA.and(String.join(", ", list("dislikes"))));
        }
        if (mood != null) {
            parts.add("you're feeling " + mood);
        }
        if (told != null) {
            for (String x : told) {
                if (x != null && !x.trim().isEmpty() && parts.size() < 12) {
                    parts.add(x.trim().replaceAll("[.]+$", ""));
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String joined = parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
        return "Here's what you've told me: " + joined + ".";
    }

    /** What to call the person: the nickname they asked for, else their name; null when neither was given. */
    public synchronized String name() {
        return get("nickname") != null ? get("nickname") : get("name");
    }

    /** "a nurse", "an engineer". */
    static final class Facts1 {
        private Facts1() {
        }

        static String a(String noun) {
            return (noun.matches("(?i)^[aeiou].*") && !noun.matches("(?i)^(uni|eu|one).*") ? "an " : "a ") + noun;
        }
    }
}
