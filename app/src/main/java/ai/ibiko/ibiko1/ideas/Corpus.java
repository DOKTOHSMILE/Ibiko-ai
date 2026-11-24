package ai.ibiko.ibiko1.ideas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What research found, source by source: each page's headings and list items (the parts a page is made of, the
 * "parameters" of a thing: a school website's admissions, staff, calendar) and its sentences (what is said about them).
 * Nothing here is shown as it was found: the sources are read for what they have in common, and Ibiko writes its own.
 * Pure Java.
 */
public final class Corpus {
    /** One page or result read. */
    public static final class Source {
        public final String host;
        /** Short labels: headings and list items, as found. */
        public final List<String> labels = new ArrayList<String>();
        /** Its sentences. */
        public final List<String> sentences = new ArrayList<String>();

        public Source(String host) {
            this.host = host == null ? "" : host;
        }

        /** All its words, lower case, for counting what it mentions. */
        String all() {
            StringBuilder b = new StringBuilder(" ");
            for (String l : labels) {
                b.append(l.toLowerCase(Locale.ROOT)).append(" . ");
            }
            for (String s : sentences) {
                b.append(s.toLowerCase(Locale.ROOT)).append(' ');
            }
            return b.toString().replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ") + " ";
        }
    }

    public final List<Source> sources = new ArrayList<Source>();

    public int size() {
        return sources.size();
    }

    public Source add(String host) {
        Source s = new Source(host);
        sources.add(s);
        return s;
    }

    private static final Pattern LABEL = Pattern.compile("(?is)<(h[1-4]|li|dt|summary|strong|b)[^>]*>(.*?)</\\1>");
    private static final Pattern DROP = Pattern.compile("(?is)<(script|style|noscript|svg|nav|footer|form|aside|header)[^>]*>.*?</\\1>");

    /** A page's headings, list items and sentences, from its HTML (navigation, scripts and footers left out). */
    public Source addPage(String host, String html) {
        Source s = add(host);
        if (html == null) {
            return s;
        }
        String body = DROP.matcher(html).replaceAll(" ");
        Matcher m = LABEL.matcher(body);
        Set<String> seen = new LinkedHashSet<String>();
        while (m.find() && seen.size() < 200) {
            String t = text(m.group(2));
            if (t.length() >= 3 && t.length() <= 60 && t.split("\\s+").length <= 7 && seen.add(t.toLowerCase(Locale.ROOT))) {
                s.labels.add(t);
            }
        }
        String plain = text(body.replaceAll("(?i)<br\\s*/?>|</p>|</h[1-6]>|</li>|</div>", "\n"));
        for (String line : plain.split("\\n|(?<=[.!?])\\s+(?=[A-Z])")) {
            String l = line.trim();
            if (l.length() >= 40 && l.length() <= 400 && l.matches(".*[.!?]$")) {
                s.sentences.add(l);
            }
        }
        return s;
    }

    /** A search's results (the app's research text: "[1] title — url" lines and "From [n] host:" passages) as sources. */
    public void addResearch(String research) {
        if (research == null) {
            return;
        }
        Source titles = add("search results");
        for (String line : research.split("\n")) {
            Matcher t = Pattern.compile("^\\[\\d+\\]\\s+(.+?)\\s+—\\s+https?://").matcher(line);
            if (t.find()) {
                titles.labels.add(t.group(1).trim());
            }
        }
        Matcher from = Pattern.compile("(?s)\\nFrom \\[\\d+\\] ([^:\\n]+):\\n(.*?)(?=\\nFrom \\[\\d+\\]|$)").matcher(research);
        while (from.find()) {
            Source s = add(from.group(1).trim());
            for (String line : from.group(2).split("\\n|(?<=[.!?])\\s+(?=[A-Z])")) {
                String l = line.trim();
                if (l.length() >= 30) {
                    s.sentences.add(l);
                }
            }
        }
        if (titles.labels.isEmpty()) {
            sources.remove(titles);
        }
    }

    /** The links a research text found, in its order. */
    public static List<String> links(String research) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("—\\s+(https?://\\S+)").matcher(research == null ? "" : research);
        while (m.find()) {
            if (!out.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    static String text(String html) {
        return html.replaceAll("(?s)<[^>]+>", " ").replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("&[a-z#0-9]+;", " ").replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s+", "\n").trim();
    }
}
