package ai.ibiko.ibiko1.words;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ibiko's inner voice: a reply is put together in the mind first, then read back before it is said. Each sentence is
 * drafted, checked against the grammar rules ({@link GrammarCheck}, in its careful form), and redrafted with what the
 * check found, again and again until a draft passes or no draft is better; the best is confirmed (read back once more)
 * and only then said. It all happens in a few milliseconds. What it never touches: code, quoted words, lists' markers,
 * numbers, names. Pure Java.
 */
public final class InnerVoice {
    private InnerVoice() {
    }

    /** The most drafts tried for one sentence before the best so far is kept. */
    public static final int DRAFTS = 4;

    /** A reply read back: what will be said, how many drafts it took, and what was put right. */
    public static final class Said {
        public final String text;
        public final int drafts;
        public final List<String> fixed;

        Said(String text, int drafts, List<String> fixed) {
            this.text = text;
            this.drafts = drafts;
            this.fixed = fixed;
        }

        /** The step that says it, or null when nothing needed changing. */
        public String step() {
            if (fixed.isEmpty()) {
                return null;
            }
            return "Read it back before saying it (" + drafts + (drafts == 1 ? " draft" : " drafts") + "), and put right: "
                    + String.join("; ", fixed);
        }
    }

    /** A reply drafted, checked, redrafted and confirmed. */
    public static Said confirm(String reply) {
        if (reply == null || reply.isEmpty()) {
            return new Said(reply, 0, new ArrayList<String>());
        }
        StringBuilder out = new StringBuilder();
        Set<String> fixed = new LinkedHashSet<String>();
        int drafts = 0;
        boolean code = false;
        String[] lines = reply.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            if (line.trim().startsWith("```")) {
                code = !code;
            }
            if (code || line.trim().startsWith("```") || line.trim().isEmpty() || line.matches("^\\s{4,}.*")) {
                out.append(line);
            } else {
                // a list's marker and the text after it
                java.util.regex.Matcher lead = java.util.regex.Pattern.compile("^(\\s*(?:[•*-]|\\d+[.)])\\s+)?(.*)$").matcher(line);
                lead.matches();
                String marker = lead.group(1) == null ? "" : lead.group(1), text = lead.group(2);
                StringBuilder said = new StringBuilder(marker);
                for (String sentence : sentences(text)) {
                    String best = sentence;
                    int bestErrors = errors(sentence);
                    String draft = sentence;
                    int tries = 1;
                    while (bestErrors > 0 && tries < DRAFTS) {
                        GrammarCheck.Judgement j = GrammarCheck.judge(protect(draft), true);
                        String next = restore(j.corrected, draft);
                        tries++;
                        if (next.equals(draft)) {
                            break; // nothing more the rules can put right
                        }
                        int e = errors(next);
                        if (e < bestErrors) {
                            for (GrammarCheck.Issue i : j.issues) {
                                if (!i.style && i.fix != null) {
                                    fixed.add("“" + i.found + "” → “" + i.fix + "”");
                                }
                            }
                            best = next;
                            bestErrors = e;
                        }
                        draft = next;
                    }
                    drafts += tries;
                    // the space or line end after the sentence stays as it was written
                    String tail = sentence.substring(sentence.replaceAll("\\s+$", "").length());
                    said.append(best.replaceAll("\\s+$", "")).append(tail);
                }
                // confirmed: the line read back as a whole once more; kept only if no worse than the original
                String confirmed = said.toString();
                out.append(errors(confirmed) <= errors(line) ? confirmed : line);
            }
            if (li < lines.length - 1) {
                out.append('\n');
            }
        }
        return new Said(out.toString(), Math.max(1, drafts), new ArrayList<String>(fixed));
    }

    /** How many errors the careful check finds. */
    static int errors(String s) {
        return GrammarCheck.judge(protect(s), true).errors();
    }

    /** A line cut into sentences, each keeping the space or punctuation that ends it. */
    static List<String> sentences(String text) {
        List<String> out = new ArrayList<String>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[^.!?]+(?:[.!?]+[\"”)]*\\s*|$)").matcher(text);
        while (m.find()) {
            if (!m.group().isEmpty()) {
                out.add(m.group());
            }
        }
        if (out.isEmpty()) {
            out.add(text);
        }
        return out;
    }

    /** Quoted words stand as written: masked while the rules run. */
    static String protect(String s) {
        return s.replaceAll("“[^”]*”|\"[^\"]*\"|`[^`]*`", "“Q”");
    }

    /** The checked sentence with the quoted words put back where they were. */
    static String restore(String checked, String original) {
        java.util.regex.Matcher q = java.util.regex.Pattern.compile("“[^”]*”|\"[^\"]*\"|`[^`]*`").matcher(original);
        List<String> quotes = new ArrayList<String>();
        while (q.find()) {
            quotes.add(q.group());
        }
        if (quotes.isEmpty()) {
            return checked;
        }
        StringBuilder b = new StringBuilder();
        int at = 0, n = 0;
        java.util.regex.Matcher mark = java.util.regex.Pattern.compile("“Q”").matcher(checked);
        while (mark.find()) {
            b.append(checked, at, mark.start()).append(n < quotes.size() ? quotes.get(n++) : mark.group());
            at = mark.end();
        }
        b.append(checked.substring(at));
        return n == quotes.size() ? b.toString() : original;
    }
}
