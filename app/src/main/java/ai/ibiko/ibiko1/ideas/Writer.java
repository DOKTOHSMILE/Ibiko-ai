package ai.ibiko.ibiko1.ideas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Ibiko's own words for what it makes: each part said from what the word means to Ibiko (its dictionary), with the
 * person's name and place, never a source's sentence. A check makes sure: a text that shares six words in a row with any
 * source is said another way. Pure Java.
 */
public final class Writer {
    private Writer() {
    }

    /** How many words in a row make a copy. */
    static final int RUN = 6;

    /** Whether a text repeats six words in a row from any source. */
    public static boolean copies(String text, Corpus c) {
        Set<String> runs = runs(text);
        if (runs.isEmpty() || c == null) {
            return false;
        }
        for (Corpus.Source s : c.sources) {
            for (String sentence : s.sentences) {
                for (String r : runs(sentence)) {
                    if (runs.contains(r)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Set<String> runs(String text) {
        Set<String> out = new HashSet<String>();
        String[] w = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}' ]", " ").trim().split("\\s+");
        for (int i = 0; i + RUN <= w.length; i++) {
            StringBuilder b = new StringBuilder();
            for (int j = i; j < i + RUN; j++) {
                b.append(w[j]).append(' ');
            }
            out.add(b.toString());
        }
        return out;
    }

    /**
     * A part's words for the page, Ibiko's own: what the part is (its meaning in the dictionary, when it has one), for
     * whom, at the named place; a plain prompt to add the details when the dictionary has nothing.
     *
     * @param meaning what a word means to Ibiko (a dictionary sense), or null
     */
    public static String say(String part, String name, String place, List<String> audience, Function<String, String> meaning, Corpus c) {
        String key = part.toLowerCase(Locale.ROOT);
        String head = key.contains(" ") ? key.substring(key.lastIndexOf(' ') + 1) : key;
        String def = meaning == null ? null : meaning.apply(key);
        if (def == null && meaning != null) {
            def = meaning.apply(head);
        }
        String who = audience == null || audience.isEmpty() ? "visitors" : join(audience);
        String at = (name == null || name.isEmpty() ? "here" : "at " + name) + (place == null || place.isEmpty() ? "" : " in " + place);
        // the frame chosen by the part itself: the same part reads the same way, different parts differently
        int frame = Math.floorMod(key.hashCode(), 3);
        String text;
        if (def != null && !def.isEmpty()) {
            String d = def.replaceAll("\\s*\\(.*?\\)", "").replaceAll("[.;:]+$", "").trim();
            d = Character.toLowerCase(d.charAt(0)) + d.substring(1);
            switch (frame) {
                case 0:
                    text = "Our " + key + " " + at + ": " + d + ". Put the details " + who + " look for first.";
                    break;
                case 1:
                    text = "What " + who + " need to know about the " + key + " " + at + ". In short, " + d + ".";
                    break;
                default:
                    text = cap(key) + " " + at + " — " + d + ". Keep it short and up to date.";
                    break;
            }
        } else {
            text = "Everything " + who + " need about the " + key + " " + at + ". Add your own details here.";
        }
        return copies(text, c) ? "Everything " + who + " need about the " + key + " " + at + ". Add your own details here." : text;
    }

    /**
     * Which of the site builder's working blocks a part is ("contact": a form that opens WhatsApp or email), by what the
     * part's words mean; "text" when none fits (Ibiko's own paragraph).
     */
    public static String block(String part) {
        String k = " " + part.toLowerCase(Locale.ROOT) + " ";
        if (k.matches(".*\\b(contact|reach us|get in touch|enquir|inquir|location|address|find us|direction)\\w*.*")) {
            return "contact";
        }
        if (k.matches(".*\\b(admission|apply|application|enrol|enroll|registration|register|booking|book|appointment|reservation)\\w*.*")) {
            return "booking";
        }
        if (k.matches(".*\\b(gallery|photo|picture|image|video|media)\\w*.*")) {
            return "gallery";
        }
        if (k.matches(".*\\b(news|blog|post|update|announcement|article|newsletter|press)\\w*.*")) {
            return "posts";
        }
        if (k.matches(".*\\b(event|calendar|timetable|schedule|term date|date)\\w*.*")) {
            return "schedule";
        }
        if (k.matches(".*\\b(question|faq|help)\\w*.*")) {
            return "faq";
        }
        if (k.matches(".*\\b(staff|team|teacher|people|leadership|faculty|board|doctor|member)\\w*.*")) {
            return "team";
        }
        if (k.matches(".*\\b(fee|price|pricing|tuition|cost|plan|rate)\\w*.*")) {
            return "pricing";
        }
        if (k.matches(".*\\b(testimonial|review|feedback|what people say)\\w*.*")) {
            return "testimonials";
        }
        if (k.matches(".*\\b(about|history|mission|vision|story|value|welcome)\\w*.*")) {
            return "about";
        }
        return "text";
    }

    static String join(List<String> l) {
        if (l.size() == 1) {
            return l.get(0);
        }
        List<String> a = new ArrayList<String>(l.subList(0, l.size() - 1));
        return String.join(", ", a) + " and " + l.get(l.size() - 1);
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
