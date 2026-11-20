package ai.ibiko.ibiko1;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Syntax;

/**
 * Talking about a sentence's structure: "parse: the engineer deployed the node on the server", "draw the syntax tree of
 * …", "what are the constituents of …", "is 'on the server' a constituent in …", "why is 'I saw the user with the
 * telescope' ambiguous?". Answered with the tree drawn, its brackets, every reading and why they differ. Pure Java.
 */
public final class SyntaxTalk {
    private SyntaxTalk() {
    }

    private static final String Q = "[\"\u201c\u201d']?";
    private static final Pattern TREE = Pattern.compile("(?is)^(?:please\\s+)?(?:can you\\s+)?(?:parse|diagram|analy[sz]e|"
            + "(?:draw|show|give|make)(?: me)? (?:a|the) (?:syntax |parse |constituency |phrase[- ]structure )?tree (?:for|of)|"
            + "what(?:'s| is) the (?:syntax tree|parse tree|structure|phrase structure|constituent structure) of|syntax tree(?: for| of)?)"
            + "(?: the sentence)?\\s*[:\\-]?\\s*" + Q + "(.+?)" + Q + "[?.!]*$");
    private static final Pattern CONSTITUENTS = Pattern.compile("(?is)^(?:what|which) are the (?:constituents|phrases) (?:of|in)\\s*[:\\-]?\\s*" + Q + "(.+?)" + Q + "[?.!]*$");
    private static final Pattern IS_ONE = Pattern.compile("(?is)^is\\s+" + Q + "(.+?)" + Q + "\\s+a constituent (?:in|of)\\s*[:\\-]?\\s*" + Q + "(.+?)" + Q + "[?.!]*$");
    private static final Pattern AMBIGUOUS = Pattern.compile("(?is)^(?:why is|is|how is)\\s+" + Q + "(.+?)" + Q + "\\s+ambiguous[?.!]*$");

    /** The reply to a question about a sentence's structure, or null when it isn't one. */
    public static String reply(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        Matcher x = IS_ONE.matcher(m);
        if (x.matches()) {
            String said = Syntax.constituent(x.group(2), x.group(1));
            return said == null ? cannot(x.group(2)) : said;
        }
        x = AMBIGUOUS.matcher(m);
        if (x.matches()) {
            List<Syntax.Tree> trees = Syntax.parse(x.group(1));
            if (trees.isEmpty()) {
                return cannot(x.group(1));
            }
            if (trees.size() == 1) {
                return "By my rules it isn't: its words group only one way.\n\n" + block(trees.get(0));
            }
            return Syntax.ambiguity(x.group(1), trees) + "\n\nReading A:\n" + block(trees.get(0)) + "\n\nReading B:\n" + block(trees.get(1));
        }
        x = CONSTITUENTS.matcher(m);
        if (x.matches()) {
            List<Syntax.Tree> trees = Syntax.parse(x.group(1));
            if (trees.isEmpty()) {
                return cannot(x.group(1));
            }
            StringBuilder b = new StringBuilder("Its constituents, from the whole down:");
            for (Syntax.Tree p : trees.get(0).phrases()) {
                b.append("\n\u2022 [").append(p.words()).append("] ").append(p.label).append(", a ").append(Syntax.name(p.label));
            }
            return b.append("\n\n").append(block(trees.get(0))).toString();
        }
        x = TREE.matcher(m);
        if (x.matches()) {
            String sentence = x.group(1).trim();
            List<Syntax.Tree> trees = Syntax.parse(sentence);
            if (trees.isEmpty()) {
                return cannot(sentence);
            }
            StringBuilder b = new StringBuilder();
            if (trees.size() == 1) {
                b.append(block(trees.get(0)));
            } else {
                b.append(Syntax.ambiguity(sentence, trees)).append("\n\nReading A:\n").append(block(trees.get(0)))
                        .append("\n\nReading B:\n").append(block(trees.get(1)));
            }
            return b.toString();
        }
        return null;
    }

    private static String block(Syntax.Tree t) {
        return "```\n" + t.drawn() + "\n```\n" + t.bracketed();
    }

    private static String cannot(String sentence) {
        return "I couldn't fit \u201c" + sentence.trim() + "\u201d to my phrase structure rules yet. They cover statements and simple"
                + " questions (S \u2192 NP VP, with noun, verb, prepositional, adjective and adverb phrases); a longer or unusual"
                + " sentence may need splitting into simpler ones.";
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
