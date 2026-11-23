package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small web apps handed over whole: a to-do list, a calculator, a countdown, a stopwatch, a contact form, a login page,
 * a quiz, a bill splitter. Each is one HTML file that opens in any browser and works on a phone, read from
 * knowledge/web/apps.tsv (tools/build_web_apps.py), where every app was opened in a real browser and used before it was
 * written: what that test checked is said with it. A newer file on the phone replaces the shipped one. Pure Java.
 */
public final class Apps {
    /** One app: what names it, what it is, does and is made of, how it was tested, and its whole page. */
    public static final class App {
        public final String id, name, does, how, tested, html;
        final Pattern named;

        App(String[] f) {
            id = f[0];
            named = Pattern.compile("(?i)\\b(?:" + f[1] + ")\\b");
            name = f[2];
            does = f[3];
            how = f[4];
            tested = f[5];
            html = f[6].replace("\\n", "\n");
        }
    }

    private final List<App> apps = new ArrayList<App>();

    public Apps(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                if (!line.startsWith("#") && f.length >= 7) {
                    apps.add(new App(f));
                }
            }
        } catch (IOException ignored) {
            // none: the request goes on to the rest of Ibiko
        }
    }

    public int size() {
        return apps.size();
    }

    /** Asked to be made: "make", "build", "I need"… before the app's name, not a question about one. */
    private static final Pattern MAKE = Pattern.compile("(?i)\\b(?:build|create|make|design|write|code|develop|generate|program|put together"
            + "|give me|get me|show me|send me|i (?:want|need)|can (?:you|i) (?:have|get))\\b");

    /** The app a message asks to be made, or null. The earliest named wins ("a quiz with a countdown" is a quiz). */
    public App asked(String message) {
        if (message == null) {
            return null;
        }
        Matcher make = MAKE.matcher(message);
        if (!make.find()) {
            return null;
        }
        App best = null;
        int at = Integer.MAX_VALUE;
        for (App a : apps) {
            Matcher x = a.named.matcher(message);
            if (x.find(make.end()) && x.start() < at) {
                best = a;
                at = x.start();
            }
        }
        return best;
    }

    /** The app, whole, with what it does, how it works and how it was tested. */
    public static String reply(App a) {
        return "Here's a " + a.name + ": one HTML file. Save it as " + a.id.replace('_', '-') + ".html and open it in any browser; "
                + "it works on a phone and needs no server.\n\n```html\n" + a.html + "```\n\n"
                + "What it does: " + a.does + ".\n\nHow it works: " + a.how + ".\n\n"
                + "Tested in a browser: " + a.tested + ".";
    }

    // ------------------------------------------------------------------------------------------------- changes

    /** An app as handed over, and as changed since: its page, and whether the quiz's sample questions were replaced yet. */
    public static final class Made {
        public final App app;
        public String html;
        boolean ownQuestions;

        public Made(App app) {
            this.app = app;
            this.html = app.html;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("(?i)\\b(?:whats ?app|number|phone)\\b[^0-9+]{0,20}(\\+?\\d[\\d\\s-]{6,16}\\d)");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]+@[\\w-]+\\.[\\w.]+)");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:sign[- ]?in|login|log[- ]?in|server|backend|api)\\b.{0,30}?(https://[^\\s\"'<>]+)");
    private static final Pattern NAME = Pattern.compile("(?i)\\b(?:call it|name it|rename it(?: to)?|title it|the title is|change the (?:name|title) to)\\s+[\"“']?(.{2,40}?)[\"”']?\\s*[.!]*$");
    private static final Pattern COLOUR = Pattern.compile("(?i)\\b(?:make it|colou?r it|change (?:the )?colou?rs? to|use)\\s+(blue|navy|red|green|purple|orange|pink|gold|teal|brown|black|grey|gray)\\b");
    /** "Add a question: What is 2+2? 3, 4, 5. Answer: 4" */
    private static final Pattern QUESTION = Pattern.compile("(?is)\\badd (?:a |another |this )?question\\s*:?\\s*(.+?\\?)\\s*(?:choices\\s*:?\\s*)?(.+?)[.;]?\\s*"
            + "(?:the )?(?:right |correct )?answer(?: is)?\\s*:?\\s*(.+?)\\s*[.!]*$");
    private static final String[][] COLOURS = {{"blue", "#2563eb"}, {"navy", "#1e3a8a"}, {"red", "#dc2626"}, {"green", "#16a34a"},
        {"purple", "#7c3aed"}, {"orange", "#ea580c"}, {"pink", "#db2777"}, {"gold", "#ca8a04"}, {"teal", "#0d9488"}, {"brown", "#92400e"},
        {"black", "#111111"}, {"grey", "#6b7280"}, {"gray", "#6b7280"}};

    /** Whether a message asks to change the app just made. */
    public static boolean asksChange(Made m, String message) {
        if (m == null || message == null) {
            return false;
        }
        String t = message.trim();
        return NAME.matcher(t).find() || COLOUR.matcher(t).find()
                || m.html.contains("const WHATSAPP") && NUMBER.matcher(t).find() || m.html.contains("const EMAIL") && EMAIL.matcher(t).find()
                || m.html.contains("const SIGN_IN_URL") && URL.matcher(t).find() || m.html.contains("const QUESTIONS") && QUESTION.matcher(t).find();
    }

    /** The change asked for, made to the app; what changed in words, or null when nothing could be done. */
    public static String change(Made m, String message) {
        String t = message.trim();
        List<String> done = new ArrayList<String>();
        Matcher x;
        if (m.html.contains("const QUESTIONS") && (x = QUESTION.matcher(t)).find()) {
            String said = question(m, x.group(1), x.group(2), x.group(3));
            if (said != null) {
                done.add(said);
            }
        } else {
            if ((x = NAME.matcher(t)).find()) {
                String name = text(x.group(1));
                m.html = m.html.replaceFirst("<title>[^<]*</title>", Matcher.quoteReplacement("<title>" + name + "</title>"))
                        .replaceFirst("<h1>[^<]*</h1>", Matcher.quoteReplacement("<h1>" + name + "</h1>"));
                done.add("named it " + name);
            }
            if ((x = COLOUR.matcher(t)).find()) {
                String word = x.group(1).toLowerCase(java.util.Locale.ROOT);
                for (String[] c : COLOURS) {
                    if (c[0].equals(word)) {
                        m.html = m.html.replaceFirst("--accent:#[0-9a-fA-F]{6}", "--accent:" + c[1]);
                        done.add("made its colour " + word);
                    }
                }
            }
            if (m.html.contains("const WHATSAPP") && (x = NUMBER.matcher(t)).find()) {
                String digits = x.group(1).replaceAll("[^0-9]", "");
                m.html = m.html.replaceFirst("const WHATSAPP = \"[^\"]*\"", "const WHATSAPP = \"" + digits + "\"");
                done.add("messages go to WhatsApp " + digits + (x.group(1).trim().startsWith("0")
                        ? " (WhatsApp links need the country code: for Nigeria, 234 then the number without its first 0)" : ""));
            }
            if (m.html.contains("const EMAIL") && (x = EMAIL.matcher(t)).find()) {
                m.html = m.html.replaceFirst("const EMAIL = \"[^\"]*\"", Matcher.quoteReplacement("const EMAIL = \"" + x.group(1) + "\""));
                done.add("emails go to " + x.group(1));
            }
            if (m.html.contains("const SIGN_IN_URL") && (x = URL.matcher(t)).find()) {
                m.html = m.html.replaceFirst("const SIGN_IN_URL = \"[^\"]*\"", Matcher.quoteReplacement("const SIGN_IN_URL = \"" + text(x.group(1)) + "\""));
                done.add("it signs in at " + x.group(1) + " (it sends the email and password there as JSON, and takes a 2xx answer as signed in)");
            }
        }
        return done.isEmpty() ? null : String.join("; ", done);
    }

    /** A question added to the quiz: the choices split on commas or "or", the answer found among them. */
    private static String question(Made m, String q, String choices, String answer) {
        List<String> cs = new ArrayList<String>();
        for (String c : choices.split("\\s*(?:,|;|\\bor\\b|/)\\s*")) {
            String c2 = text(c.replaceFirst("^(?:choices?\\s*:?|options?\\s*:?)\\s*", "").replaceAll("[.]+$", ""));
            if (!c2.isEmpty()) {
                cs.add(c2);
            }
        }
        String a = text(answer.replaceAll("[.]+$", ""));
        int right = -1;
        for (int i = 0; i < cs.size(); i++) {
            if (cs.get(i).equalsIgnoreCase(a)) {
                right = i;
            }
        }
        if (cs.size() < 2 || right < 0) {
            return null;
        }
        StringBuilder item = new StringBuilder("  { q: \"").append(text(q)).append("\", choices: [");
        for (int i = 0; i < cs.size(); i++) {
            item.append(i == 0 ? "" : ", ").append('"').append(cs.get(i)).append('"');
        }
        item.append("], answer: ").append(right).append(" }");
        int open = m.html.indexOf("const QUESTIONS = [\n"), close = m.html.indexOf("\n];", open);
        if (open < 0 || close < 0) {
            return null;
        }
        String head = m.html.substring(0, open) + "const QUESTIONS = [\n";
        String body = m.ownQuestions ? m.html.substring(open + "const QUESTIONS = [\n".length(), close) + ",\n" : "";
        m.html = head + body + item + m.html.substring(close);
        boolean first = !m.ownQuestions;
        m.ownQuestions = true;
        return "added \"" + text(q) + "\" (answer: " + cs.get(right) + ")" + (first ? ", in place of my sample questions" : "");
    }

    /** Text safe inside the page's script and HTML: no quotes, backslashes or tags. */
    private static String text(String s) {
        return s.replaceAll("[\"\\\\<>`]", "").trim();
    }
}
