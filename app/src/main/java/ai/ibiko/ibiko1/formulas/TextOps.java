package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small jobs on the text itself, done exactly rather than looked up: how many letters a word has, a word reversed or
 * spelled backwards, words put in alphabetical order, the words of a sentence counted, a word's first or last letter.
 * Pure Java.
 */
public final class TextOps {
    private TextOps() {
    }

    private static final String Q = "[\"“'‘]?";
    private static final String QE = "[\"”'’]?";
    private static final Pattern LETTERS = Pattern.compile("(?i)^\\s*how many (letters|characters|vowels|consonants) (?:are )?(?:there )?(?:in|does) (?:the (?:word|name) )?"
            + Q + "([\\p{L}'-]{1,40})" + QE + "(?: have)?\\s*[?.!]*\\s*$");
    private static final Pattern REVERSE = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:reverse|spell)\\s+(?:the\\s+(?:word|name|text|string|sentence)\\s+)?" + Q
            + "(.{1,80}?)" + QE + "(\\s+backwards?)?\\s*[?.!]*\\s*$");
    private static final Pattern SORT = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:sort|order|arrange|put)\\s+(?:these|the following|this list(?: of)?)?\\s*"
            + "(?:words|names|items|fruits|things)?\\s*(?:in )?(?:alphabetically|in alphabetical order|alphabetical(?:ly)?|a to z|from a to z)?\\s*[:,-]?\\s*(.{3,300}?)"
            + "\\s*(?:in alphabetical order|alphabetically|from a to z|a to z)?\\s*[?.!]*\\s*$");
    private static final Pattern WORDS = Pattern.compile("(?i)^\\s*how many words (?:are )?(?:there )?in (?:the sentence |this sentence |the text )?[:,]?\\s*"
            + Q + "(.{1,300}?)" + QE + "\\s*[?.!]*\\s*$");
    private static final Pattern FIRST = Pattern.compile("(?i)^\\s*what(?:'s| is) the (first|last) letter (?:of|in) (?:the (?:word|name) )?" + Q
            + "([\\p{L}'-]{1,40})" + QE + "\\s*[?.!]*\\s*$");

    /** The job done, or null when the message isn't one of these. */
    public static String reply(String message) {
        if (message == null || message.length() > 400) {
            return null;
        }
        Matcher m;
        if ((m = LETTERS.matcher(message)).matches()) {
            String w = m.group(2);
            String kind = m.group(1).toLowerCase(Locale.ROOT);
            String letters = w.replaceAll("[^\\p{L}]", "");
            if (kind.equals("vowels") || kind.equals("consonants")) {
                StringBuilder v = new StringBuilder(), c = new StringBuilder();
                for (char ch : letters.toLowerCase(Locale.ROOT).toCharArray()) {
                    ("aeiou".indexOf(ch) >= 0 ? v : c).append(ch);
                }
                String pick = kind.equals("vowels") ? v.toString() : c.toString();
                return "“" + w + "” has " + pick.length() + " " + (pick.length() == 1 ? kind.replaceAll("s$", "") : kind)
                        + (pick.isEmpty() ? "." : ": " + String.join(", ", pick.split("")) + ".");
            }
            int n = kind.equals("characters") ? w.length() : letters.length();
            return "“" + w + "” has " + n + " " + (n == 1 ? kind.replaceAll("s$", "") : kind) + ": " + String.join("-", w.toUpperCase(Locale.ROOT).split("")) + ".";
        }
        if ((m = FIRST.matcher(message)).matches()) {
            String w = m.group(2);
            boolean first = m.group(1).equalsIgnoreCase("first");
            String letters = w.replaceAll("[^\\p{L}]", "");
            if (letters.isEmpty()) {
                return null;
            }
            char c = first ? letters.charAt(0) : letters.charAt(letters.length() - 1);
            return "The " + m.group(1).toLowerCase(Locale.ROOT) + " letter of “" + w + "” is " + Character.toUpperCase(c) + ".";
        }
        if ((m = WORDS.matcher(message)).matches()) {
            String s = m.group(1).trim();
            int n = s.isEmpty() ? 0 : s.split("\\s+").length;
            return "There " + (n == 1 ? "is 1 word" : "are " + n + " words") + " in “" + s + "”.";
        }
        if ((m = REVERSE.matcher(message)).matches()) {
            boolean spell = message.trim().toLowerCase(Locale.ROOT).startsWith("spell") || message.toLowerCase(Locale.ROOT).contains("spell");
            if (spell && m.group(2) == null) {
                return null; // "spell necessary": spelling, not reversing
            }
            String t = m.group(1).trim();
            String back = new StringBuilder(t).reverse().toString();
            return spell || t.split("\\s+").length == 1 ? "“" + t + "” backwards is “" + back + "”."
                    : "Reversed: “" + back + "”.";
        }
        if ((m = SORT.matcher(message)).matches() && message.toLowerCase(Locale.ROOT).matches("(?s).*\\b(alphabetic\\w*|a to z)\\b.*")) {
            List<String> items = new ArrayList<String>();
            for (String p : m.group(1).split("\\s*(?:,|;|\\band\\b)\\s*")) {
                String x = p.trim().replaceAll("[.!?]+$", "");
                if (!x.isEmpty()) {
                    items.add(x);
                }
            }
            if (items.size() < 2) {
                return null;
            }
            List<String> sorted = new ArrayList<String>(items);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            return "In alphabetical order: " + String.join(", ", sorted) + ".";
        }
        return null;
    }

    static List<String> list(String... s) {
        return Arrays.asList(s);
    }
}
