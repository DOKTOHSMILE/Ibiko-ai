package ai.ibiko.ibiko1.ideas;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An open request to make something, or for ideas, read for what is to be made and what it is about: "write a business
 * plan for my bakery" (a business plan, about a bakery), "plan a birthday party for my son" (a birthday party plan),
 * "design an app for farmers" (an app, for farmers), "give me ideas for a church fundraiser". Whatever it is, it is
 * thought through the same way ({@link Ideation}): nothing about any one thing is built in. Pure Java.
 */
public final class Ambition {
    /** What is asked: something made, or ideas for it. */
    public enum Kind { MAKE, IDEAS }

    /** A request, read. */
    public static final class Ask {
        public final Kind kind;
        /** What is made, as said ("business plan", "birthday party", "app"). */
        public final String goal;
        /** What it is about or for ("bakery", "my son", "farmers"); empty when the goal says it all. */
        public final String about;
        /** How the result is given: a page ("app", "website", "landing page") or a document (everything else). */
        public final boolean page;

        Ask(Kind kind, String goal, String about, boolean page) {
            this.kind = kind;
            this.goal = goal;
            this.about = about;
            this.page = page;
        }

        /** The whole thing, for research: "business plan for a bakery", "birthday party". */
        public String whole() {
            return about.isEmpty() ? goal : goal + " for " + about.replaceFirst("^(?:my|our|your)\\s+", "a ");
        }
    }

    private static final Pattern MAKE = Pattern.compile("(?i)^\\s*(?:please\\s+|hey,?\\s+|ibiko,?\\s+)?(?:can you\\s+|could you\\s+|would you\\s+|i (?:want|need) (?:you to\\s+)?|"
            + "help me\\s+|i'd like (?:you to\\s+)?|let's\\s+)?(?:please\\s+)?"
            + "(create|make|build|design|plan|write|draft|develop|come up with|put together|prepare|organi[sz]e|compose|produce|set up|outline|craft)"
            + "\\s+(?:me\\s+|us\\s+|for me\\s+)?(?:a|an|the|some|my|our)?\\s*(.{3,80}?)\\s*[.!?]*\\s*$");
    private static final Pattern IDEAS = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:can you\\s+|could you\\s+)?(?:give me|suggest|share|brainstorm|think of|come up with|"
            + "i need|what are (?:some|good))\\s+(?:some\\s+|a few\\s+|good\\s+|new\\s+|fresh\\s+|creative\\s+)*(?:ideas?|suggestions?|ways?|concepts?)\\s+"
            + "(?:for|about|on|to)\\s+(?:a|an|the|my|our)?\\s*(.{3,80}?)\\s*[.!?]*\\s*$|^\\s*brainstorm\\s+(?:a|an|the|my|our)?\\s*(.{3,80}?)\\s*[.!?]*\\s*$");
    /** Code is the code readers' and the coding agent's: "write a function…", "build a REST API". */
    private static final Pattern CODE = Pattern.compile("(?i)\\b(?:function|script|program|code|class|method|regex|sql|query|api|endpoint|algorithm|loop|"
            + "variable|array|database schema|unit test|bug|compiler|terminal|command)\\b");
    /** Too small to need thinking: a sentence, a word, a name, a list. */
    private static final Pattern SMALL = Pattern.compile("(?i)^(?:sentence|word|name|list|note|reminder|alarm|call|message|text|sms|email to|"
            + "post on|tweet|caption|joke|haiku|password|username|summary of|copy of|backup|folder|file|pdf|day|week|morning|evening|night|time|"
            + "cup of|glass of|plate of|bowl of|it|that|this|these|those|them|one|main)\\b");
    /** Pictures, drawings and 3D: the drawing and modelling tools' work. Letters: the letter writer's. */
    private static final Pattern ELSEWHERE = Pattern.compile("(?i)\\b(?:logo|picture|image|photo|drawing|draw|icon|poster|flyer|banner|sketch|diagram|chart|graph|"
            + "video|animation|3d|three d|model of|chair|table|desk|building|house|room|door|cake|tea|coffee|letter|note to|card for|"
            + "screen|page|form|button|login|todo|stopwatch|calculator|quiz|game)\\b|\\b\\d+\\s*(?:cm|mm|m|ft|foot|feet|inch|inches)\\b");
    private static final Pattern PAGE = Pattern.compile("(?i)\\b(?:app|application|mobile app|web app|website|web site|site|landing page|portal|dashboard|platform)\\b");

    private Ambition() {
    }

    /** The request, read; null when it isn't an open request to make something or for ideas. */
    public static Ask read(String message) {
        if (message == null || message.length() > 300 || message.contains("\n") || CODE.matcher(message).find() || ELSEWHERE.matcher(message).find()) {
            return null;
        }
        Matcher i = IDEAS.matcher(message);
        if (i.matches()) {
            String what = clean(i.group(1) != null ? i.group(1) : i.group(2));
            return what == null ? null : split(Kind.IDEAS, what);
        }
        Matcher m = MAKE.matcher(message);
        if (!m.matches()) {
            return null;
        }
        String what = clean(m.group(2));
        if (what == null || SMALL.matcher(what).find()) {
            return null;
        }
        return split(Kind.MAKE, what);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("[.!?]+$", "");
        if (t.matches("(?i)^(?:the|this|that|these|those|it|them)\\b.*")) {
            return null; // "build the screen", "make it bigger": about something already there
        }
        t = t.replaceFirst("(?i)^(?:a|an|some|my|our)\\s+", "");
        // details after a comma or "called" are the person's, not part of what is made
        t = t.replaceFirst("(?i)\\s*(?:,|\\bcalled\\b|\\bnamed\\b|\\bwith (?:the )?(?:name|title)\\b).*$", "").trim();
        return t.length() < 3 || t.split("\\s+").length > 10 ? null : t;
    }

    private static Ask split(Kind kind, String what) {
        String low = what.toLowerCase(Locale.ROOT);
        Matcher f = Pattern.compile("^(.+?)\\s+(?:for|about|on|to help|to teach|that helps|which helps)\\s+(.+)$").matcher(low);
        String goal, about;
        if (f.matches()) {
            goal = f.group(1).trim();
            about = f.group(2).trim();
        } else {
            goal = low;
            about = "";
        }
        return new Ask(kind, goal, about, PAGE.matcher(goal).find());
    }
}
