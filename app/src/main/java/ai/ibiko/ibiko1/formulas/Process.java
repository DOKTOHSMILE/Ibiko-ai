package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understanding how: reading the way a thing is made, formed or done, and how it works, out of plain text, so a
 * how-question is answered from what was read or told. "Glass is made by melting sand", "Mountains are formed when
 * plates collide", "A battery works by turning chemical energy into electricity", "First mix the flour and water.
 * Then knead the dough." each become a way: the thing, the doing (made, formed, works), the means, in order where
 * the text gives steps. Pure Java.
 *
 * <pre>
 * W1  reads   "X is/are made|formed|produced|… by|from|through|using|when|in Y"     the means of the doing
 * W2  reads   "X works|operates|functions by|through|using Y"                       how it works
 * W3  reads   "To make|build|grow X, (you) Y"                                       how to do it
 * W4  reads   "First …", "Then …", "Next …", "After that …", "Finally …"             steps, in order, of the topic
 * W5  matches a how-question to the way whose thing and doing it names
 * </pre>
 */
public final class Process {
    private Process() {
    }

    /** One way: the thing, the doing ("made", "formed", "works", "make"), the means, and the steps when given. */
    public static final class Way {
        public final String thing, doing, means, sentence;
        public final List<String> steps;

        public Way(String thing, String doing, String means, List<String> steps, String sentence) {
            this.thing = thing;
            this.doing = doing;
            this.means = means;
            this.steps = steps == null ? new ArrayList<String>() : steps;
            this.sentence = sentence == null ? "" : sentence;
        }

        /** As said: "Glass is made by melting sand.", or the steps numbered. */
        public String said() {
            if (!steps.isEmpty()) {
                StringBuilder b = new StringBuilder();
                b.append(means.isEmpty() ? "Step by step:" : cap(means) + (means.endsWith(":") ? "" : ":"));
                for (int i = 0; i < steps.size(); i++) {
                    b.append("\n").append(i + 1).append(". ").append(cap(steps.get(i).replaceAll("[.!]+$", ""))).append(".");
                }
                return b.toString();
            }
            return cap(sentence.trim().replaceAll("[.!]+$", "")) + ".";
        }

        @Override
        public String toString() {
            return "(" + thing + " | " + doing + " | " + means + (steps.isEmpty() ? "" : " | " + steps) + ")";
        }
    }

    private static final String DONE = "made|produced|formed|created|built|grown|caused|done|cooked|prepared|obtained|extracted|"
            + "generated|manufactured|brewed|baked|mined|refined|harvested|processed|spread|transmitted|measured|calculated|"
            + "digested|absorbed|recycled|stored|cured|treated|prevented|diagnosed|used|controlled|powered|fuelled|fueled";
    private static final Pattern W1 = Pattern.compile("(?i)^(.{2,80}?)\\s+(?:is|are|was|were|can be|is usually|are usually|is often|"
            + "are often|is mostly|are mostly)\\s+(" + DONE + ")\\s+((?:by|from|through|using|with|when|out of|via)\\s+.{3,})$");
    private static final Pattern W2 = Pattern.compile("(?i)^(.{2,80}?)\\s+(works?|operates?|functions?|runs?|moves?|flies|fly|swims?|"
            + "grows?|breathes?|reproduces?|feeds?|hunts?|survives?|communicates?)\\s+((?:by|through|using|with|when|via|because)\\s+.{3,})$");
    private static final Pattern W3 = Pattern.compile("(?i)^to\\s+(make|build|grow|cook|prepare|bake|brew|fix|repair|start|grow|plant|"
            + "clean|create|get|become|learn)\\s+(.{2,60}?),\\s+(?:you\\s+(?:need to|must|should|have to|can)?\\s*)?(.{3,})$");
    private static final Pattern W4 = Pattern.compile("(?i)^(first(?:ly)?|to begin(?: with)?|start by|begin by|then|next|after that|"
            + "afterwards|second(?:ly)?|third(?:ly)?|finally|lastly|at last|in the end)\\b[,:]?\\s*(.{3,})$");

    /** W1–W4: the ways read from a text about {@code topic}. */
    public static List<Way> read(String text, String topic) {
        List<Way> out = new ArrayList<Way>();
        if (text == null) {
            return out;
        }
        List<String> steps = new ArrayList<String>();
        String stepsFor = topic;
        String lead = "";
        for (String raw : text.trim().split("(?<=[.!?])\\s+(?=[A-Z\"“(0-9])")) {
            String s = raw.trim().replaceAll("[.!]+$", "").trim();
            if (s.endsWith("?") || s.length() < 6) {
                continue;
            }
            Matcher m;
            if ((m = W4.matcher(s)).matches()) {
                String word = m.group(1).toLowerCase(Locale.US);
                if (word.startsWith("first") || word.startsWith("to begin") || word.startsWith("start") || word.startsWith("begin")) {
                    flush(out, steps, stepsFor, lead);
                    steps = new ArrayList<String>();
                }
                steps.add(word.startsWith("start by") || word.startsWith("begin by") ? m.group(2) : m.group(2));
                continue;
            }
            if (!steps.isEmpty()) {
                flush(out, steps, stepsFor, lead);
                steps = new ArrayList<String>();
                lead = "";
            }
            // "Bread is made in four steps:" leads the steps that follow
            Matcher lead2 = Pattern.compile("(?i)^(.{2,60}?)\\s+(?:is|are)\\s+(" + DONE + ")\\s+(?:in\\s+\\w+\\s+(?:steps|stages)|like this|as follows)\\s*:?$").matcher(s);
            if (lead2.matches()) {
                stepsFor = it(tidy(lead2.group(1)), topic);
                lead = s;
                continue;
            }
            if ((m = W3.matcher(s)).matches()) {
                String thing = tidy(m.group(2));
                out.add(new Way(thing, m.group(1).toLowerCase(Locale.US), m.group(3), null, raw));
                stepsFor = thing;
                lead = "To " + m.group(1).toLowerCase(Locale.US) + " " + thing;
                continue;
            }
            if ((m = W1.matcher(s)).matches()) {
                String thing = it(tidy(m.group(1)), topic);
                if (thing != null) {
                    out.add(new Way(thing, m.group(2).toLowerCase(Locale.US), m.group(3), null, raw));
                    stepsFor = thing;
                }
                continue;
            }
            if ((m = W2.matcher(s)).matches()) {
                String thing = it(tidy(m.group(1)), topic);
                if (thing != null) {
                    out.add(new Way(thing, m.group(2).toLowerCase(Locale.US), m.group(3), null, raw));
                    stepsFor = thing;
                }
                continue;
            }
        }
        flush(out, steps, stepsFor, lead);
        return out;
    }

    private static void flush(List<Way> out, List<String> steps, String thing, String lead) {
        if (steps.size() >= 2 && thing != null && !thing.isEmpty()) {
            out.add(new Way(thing, "steps", lead, new ArrayList<String>(steps), String.join(". ", steps)));
        }
    }

    private static String it(String thing, String topic) {
        // "Glass is a hard material that (is made …)": the thing is glass
        thing = thing.replaceFirst("(?i)^(.{2,60}?)\\s+(?:is|are|was|were)\\s+(?:a|an|the)\\s+.*\\b(?:that|which|who)$", "$1");
        if (thing.matches("(?i)it|this|they|these|that")) {
            return topic == null || topic.isEmpty() ? null : topic;
        }
        if (thing.matches("(?i)^(once|when|after|before|if|while|because|although|since|as|so|but|and|then)\\b.*")
                || thing.matches("(?i).*(,|\\b(that|which|who|whose|where)\\b).*")) {
            return null; // a clause, not a thing
        }
        if (thing.split("\\s+").length > 8) {
            return null;
        }
        return thing;
    }

    private static String tidy(String s) {
        return s.trim().replaceAll("^[,;:\\s]+|[,;:.!\\s]+$", "").replaceAll("\\s+", " ");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The doing a how-question asks about: "made", "works", "formed", "make" (how to), or "" for any. */
    static String doing(String question) {
        String q = question.toLowerCase(Locale.US);
        Matcher m = Pattern.compile("\\b(" + DONE + "|works?|operates?|functions?|runs?|moves?|fly|flies|swims?|grows?|breathes?|"
                + "reproduces?|feeds?|hunts?|survives?|communicates?)\\b").matcher(q);
        if (m.find()) {
            return m.group(1);
        }
        Matcher to = Pattern.compile("\\bhow (?:do (?:you|i|we|people)|can (?:you|i|we)|to|should i)\\s+(\\w+)").matcher(q);
        return to.find() ? to.group(1) : "";
    }

    /** One doing as compared: "works"/"work", "made"/"make" are one. */
    private static String family(String d) {
        String s = d.toLowerCase(Locale.US);
        if (s.matches("made|make|makes|making|formed|form|forms|created|create|creates|produced|produce|produces|generated|generate|"
                + "generates|manufactured|manufacture")) {
            return "make"; // how a thing comes to be: made, formed, created, produced are one question
        }
        if (s.matches("grown|grow|grows")) {
            return "grow";
        }
        if (s.matches("built|build|builds")) {
            return "build";
        }
        if (s.matches("flies|fly")) {
            return "fly";
        }
        return Causes.stem(s.replaceAll("s$", ""));
    }

    /** W5: the way a how-question asks for, or null. Steps answer "how to"; the thing's words must all be there. */
    public static Way how(String question, List<Way> ways) {
        if (question == null || !question.trim().toLowerCase(Locale.US).matches("^(please\\s+)?((can|could) you\\s+)?(tell me|explain|show me)?\\s*how\\b.*")) {
            return null;
        }
        String d = doing(question);
        Set<String> asked = Causes.words(question.replaceAll("(?i)\\b(how|you|i|we|people|tell|me|explain|show|please|exactly|"
                + "actually|really)\\b", " "));
        if (!d.isEmpty()) {
            asked.remove(Causes.stem(d));
            asked.remove(family(d));
        }
        if (asked.isEmpty()) {
            return null;
        }
        Way best = null;
        double bestScore = 0;
        for (Way w : ways) {
            Set<String> thing = Causes.words(w.thing);
            if (thing.isEmpty()) {
                continue;
            }
            int shared = 0;
            for (String a : asked) {
                shared += thing.contains(a) ? 1 : 0;
            }
            // the very thing asked: all its words, and none more ("coloured glass" is not glass)
            if (shared < asked.size() || shared < thing.size()) {
                continue;
            }
            boolean sameDoing = d.isEmpty() || family(d).equals(family(w.doing)) || w.doing.equals("steps");
            if (!sameDoing) {
                continue;
            }
            double score = shared - 0.05 * Math.abs(thing.size() - shared) + (w.doing.equals("steps") ? 0.5 : 0);
            if (best == null || score > bestScore) {
                best = w;
                bestScore = score;
            }
        }
        return best;
    }
}
