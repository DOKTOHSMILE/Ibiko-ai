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
}
