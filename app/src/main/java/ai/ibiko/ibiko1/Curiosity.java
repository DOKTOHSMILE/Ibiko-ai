package ai.ibiko.ibiko1;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.words.Morph;

/**
 * Inquisitiveness: a question it couldn't answer is a gap it wants filled, so it asks the person back. Their reply
 * isn't saved as typed: it is read against the question, the hedges and filler are taken off, it is turned into one
 * complete sentence ("Who founded Dangote Group?" + "some guy called Aliko Dangote I think" becomes "Aliko Dangote
 * founded Dangote Group."), and only that sentence is kept. An unsure reply is checked with the person first.
 */
final class Curiosity {

    /** How long a gap waits for the person's reply. */
    static final long WAIT_MS = 10 * 60 * 1000L;

    /** A question it couldn't answer and asked back about. */
    static final class Gap {
        final String question;
        final long at;
        /** Formed from an unsure reply and waiting for "yes": null until then. */
        final String formed;

        Gap(String question, long at, String formed) {
            this.question = question;
            this.at = at;
            this.formed = formed;
        }

        boolean expired(long now) {
            return now - at > WAIT_MS;
        }
    }

    /** What a reply to a gap came to. */
    static final class Reading {
        /** The well-formed sentence to keep; null when the reply didn't answer. */
        final String statement;
        /** "I think", "maybe", "probably": checked with the person before it is kept. */
        final boolean unsure;
        /** "I don't know", "no idea", "skip": the person can't fill it either. */
        final boolean declined;

        Reading(String statement, boolean unsure, boolean declined) {
            this.statement = statement;
            this.unsure = unsure;
            this.declined = declined;
        }
    }

    private static final Pattern DECLINE = Pattern.compile("(?i)^\\W*(i (really )?(don'?t|do not) know|i'?m not sure|not sure|no idea|"
            + "dunno|idk|i have no idea|no clue|nope,? no idea|skip( it)?|never ?mind|forget (it|that)|leave it|pass|"
            + "you tell me|search (it|for it)|look it up|google it)\\b.*");
    private static final Pattern HEDGE = Pattern.compile("(?i)\\b(i think|i believe|i guess|i suppose|maybe|perhaps|probably|"
            + "possibly|if i remember( right(ly)?)?|if i'?m not mistaken|not (100% |completely |totally )?sure,? but|"
            + "i'?m not sure,? but|kind of|sort of|could be|might be|i reckon|apparently)\\b[,]?");
    private static final Pattern LEAD = Pattern.compile("(?i)^(well|so|oh|um+|uh+|hmm+|ok|okay|yeah|yes|ah|actually|"
            + "honestly|lol|haha|right)\\b[,!.]?\\s*");
    private static final Pattern INTRO = Pattern.compile("(?i)^(the answer is|it'?s called|it is called|that'?d be|that would be|"
            + "that'?s|that is|it'?s|it is|it was|its|they'?re|they are|he'?s|he is|she'?s|she is|"
            + "(some|a|one) (guy|man|woman|lady|person|people|company|place|town|city|someone) (called|named)|"
            + "someone (called|named)|a (guy|man|woman) by the name( of)?|by the name( of)?|called|named)\\s+");
    private static final Pattern COPULA_Q = Pattern.compile("(?i)^(who|what|which|where|when|how old|how (?:tall|big|long|far|high|deep))"
            + "(?:'s|\\s+(is|are|was|were))\\s+(.+)$");
    private static final Pattern PASSIVE_Q = Pattern.compile("(?i)^(who|when|where|how|why)\\s+(was|were|is|are)\\s+(.+?)\\s+"
            + "(born|founded|built|made|invented|discovered|written|created|started|established|formed|named|killed|elected|"
            + "painted|designed|composed|released|published|opened|launched|produced|directed|owned|led|run|named after)"
            + "(?:\\s+(by|in|on|at))?$");
    private static final Pattern AUX_Q = Pattern.compile("(?i)^(who|what|which|where|when|how many|how much|how)\\s+(do|does|did)\\s+"
            + "(.+?)\\s+(\\w+)(?:\\s+(.+))?$");
    private static final Pattern VERB_Q = Pattern.compile("(?i)^(who|what|which \\w+)\\s+(\\w+(?:ed|s)|wrote|made|built|began|"
            + "won|led|ran|sang|drew|found|gave|took|came up with|owns|runs|leads|rules|governs|discovered|invented)\\s+(.+)$");
    private static final Pattern HOW_MANY = Pattern.compile("(?i)^how (many|much)\\s+(.+?)\\s+(are there|is there|were there|"
            + "was there|does|do|did|has|have|had)\\s*(.*)$");

    private Curiosity() {
    }

    /** A question it couldn't answer, kept as a gap; null for what isn't a question worth asking back about. */
    static Gap open(String question, long now) {
        if (question == null) {
            return null;
        }
        String q = question.trim();
        if (q.isEmpty() || !q.toLowerCase(Locale.US).matches("^(who|what|which|where|when|how|why|whose|is|are|was|were|does|do|did)\\b.*")) {
            return null;
        }
        return new Gap(q, now, null);
    }

    /** The line that asks the person back, said after "I don't know". */
    static String askBack(String question) {
        String q = question.trim().toLowerCase(Locale.US);
        if (q.matches("^(what|where|when|who)('s| is| are| do i| am i| was i)?\\b.*\\b(my|i|me|mine)\\b.*")) {
            return "What is it? Tell me and I'll keep it, put properly.";
        }
        return "Do you know? If you tell me, I'll put it into a proper sentence and check it with you before I keep it.";
    }

    /** True when the reply is a new question or an order, not an answer to the gap. */
    static boolean movesOn(Gap g, String reply) {
        String r = reply.trim();
        String low = r.toLowerCase(Locale.US);
        if (r.endsWith("?") && !low.matches("^(is it|isn'?t it|maybe|perhaps)\\b.*")) {
            return true;
        }
        // "I have 3 apples" after "what is the capital of Nigeria?": about the person, sharing no word with the question
        if (low.matches("^(i|i'm|i am|i've|my|we|we're|our)\\b.*") && low.split("\\s+").length >= 3) {
            boolean shares = false;
            String q = " " + g.question.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N} ]", " ") + " ";
            for (String w : low.replaceAll("[^\\p{L}\\p{N} ]", " ").split("\\s+")) {
                shares |= w.length() > 3 && q.contains(" " + w + " ");
            }
            if (!shares) {
                return true;
            }
        }
        return low.matches("^(what|who|where|when|why|how|which|can you|could you|will you|would you|please|tell me|"
                + "write|make|show|give|sing|play|draw|open|send|call|find|search|translate|explain|calculate|solve)\\b.*")
                && !low.matches("^(what|who|where|when|which)\\b.*\\b(is|was|are|were)\\b.*\\b(called|named)\\b.*");
    }

    /** Reads the reply against the gap's question and forms the sentence to keep. */
    static Reading read(Gap g, String reply) {
        String r = reply.trim();
        if (DECLINE.matcher(r).matches()) {
            return new Reading(null, false, true);
        }
        boolean unsure = HEDGE.matcher(r).find();
        String answer = clean(r);
        if (answer.isEmpty()) {
            return new Reading(null, unsure, false);
        }
        String formed = form(g.question, answer);
        return new Reading(formed, unsure, false);
    }

    /** Hedges, filler and lead-ins taken off; what is left is the answer itself. */
    static String clean(String reply) {
        String a = reply.trim();
        String before;
        do {
            before = a;
            a = HEDGE.matcher(a).replaceAll(" ");
            a = a.replaceAll("\\s+", " ").trim();
            a = a.replaceFirst("^[,;:.!\\- ]+", "");
            a = LEAD.matcher(a).replaceFirst("");
            a = INTRO.matcher(a).replaceFirst("");
        } while (!a.equals(before));
        a = a.replaceAll("(?i)[,]?\\s*\\b(lol|haha|i guess|i think|right|innit|abi|sha|o)\\s*$", "");
        a = a.replaceAll("[\\s.!,;:]+$", "").trim();
        return a;
    }

    /**
     * One complete sentence from the question and the answer it got: the answer put where the question's word was,
     * "my" said back as "your", a pronoun ("he was born in 1930") replaced by who it is about. A reply that is already a
     * sentence about the subject is kept as said, tidied.
     */
    static String form(String question, String answer) {
        String q = question.trim().replaceAll("[?.!\\s]+$", "").replaceAll("\\s+", " ");
        if (q.matches("(?i)^why\\b.*") && !answer.trim().matches("(?is).+\\s(because|due to)\\s.+")) {
            // "Why is the sky blue?" + "scattering of light": the reason joined to what it explains, kept as a cause
            String why = ai.ibiko.ibiko1.formulas.Causes.form(q, answer.trim());
            if (why != null) {
                return why;
            }
        }
        String a = names(q, answer.trim());
        String body = null;
        Matcher m;
        String subjectOf = subject(q);
        // "He was born in 1930", "It is in Lagos": a full answer with a pronoun for what the question is about
        Matcher pron = Pattern.compile("(?i)^(he|she|it|they|this|that)\\s+(is|was|are|were|has|had|did|does|\\w+ed|\\w+s)\\b(.*)$").matcher(a);
        if (subjectOf != null && pron.matches()) {
            String verb = pron.group(2);
            String plain = pron.group(1).toLowerCase(Locale.US);
            if (plain.equals("they") || !verb.matches("(?i)are|were")) {
                body = subjectOf + " " + verb + pron.group(3);
            }
        }
        if (body == null && sentence(a, subjectOf)) {
            body = a; // already says it whole
        }
        if (body == null && (m = PASSIVE_Q.matcher(q)).matches()) {
            String wh = m.group(1).toLowerCase(Locale.US);
            String prep = m.group(5);
            String what = m.group(3) + " " + m.group(2).toLowerCase(Locale.US) + " " + m.group(4);
            if (wh.equals("who")) {
                body = what + (prep != null && !prep.equalsIgnoreCase("by") ? " " + prep : "") + " " + by(a);
            } else if (wh.equals("when")) {
                body = what + " " + when(a);
            } else if (wh.equals("where")) {
                body = what + " " + where(a);
            } else {
                body = what + " " + a;
            }
        }
        if (body == null && (m = HOW_MANY.matcher(q)).matches()) {
            String count = a.replaceFirst("(?i)^(there (are|is|were|was)|about|around|roughly)\\s+", "");
            String things = m.group(2);
            String rest = m.group(4).trim();
            String aux = m.group(3).toLowerCase(Locale.US);
            if (aux.startsWith("are there") || aux.startsWith("is there") || aux.startsWith("were there") || aux.startsWith("was there")) {
                String be = aux.startsWith("were") || aux.startsWith("was") ? (one(count) ? "was" : "were") : (one(count) ? "is" : "are");
                body = "There " + be + " " + count + (count.toLowerCase(Locale.US).contains(things.toLowerCase(Locale.US)) ? "" : " " + things)
                        + (rest.isEmpty() ? "" : " " + rest);
            } else if (!rest.isEmpty()) {
                // "how many states does Nigeria have" + "36" → "Nigeria has 36 states"
                Matcher sv = Pattern.compile("(?i)^(.+?)\\s+(\\w+)$").matcher(rest);
                if (sv.matches()) {
                    String verb = aux.equals("did") ? Morph.past(sv.group(2)) : aux.equals("does") ? Morph.third(sv.group(2)) : sv.group(2);
                    body = me(sv.group(1)) + " " + verb + " " + count + (count.toLowerCase(Locale.US).contains(things.toLowerCase(Locale.US)) ? "" : " " + things);
                }
            }
        }
        if (body == null && (m = COPULA_Q.matcher(q)).matches()) {
            String wh = m.group(1).toLowerCase(Locale.US);
            String be = m.group(2) == null ? "is" : m.group(2).toLowerCase(Locale.US);
            String s = m.group(3);
            Matcher tail = Pattern.compile("(?i)^(.+?)\\s+(from|in|at|on|of|for|by|with|about|like)$").matcher(s);
            if (wh.equals("where")) {
                String from = tail.matches() ? tail.group(2).toLowerCase(Locale.US) : null;
                String who = tail.matches() ? tail.group(1) : s;
                body = me(who) + " " + verbFor(who, be) + " " + (from != null ? from + " " + a.replaceFirst("(?i)^" + from + "\\s+", "") : where(a));
            } else if (wh.equals("when")) {
                body = me(s) + " " + verbFor(s, be) + " " + when(a);
            } else if (wh.equals("how old")) {
                body = me(s) + " " + verbFor(s, be) + " " + a + (a.matches("\\d+") ? " years old" : "");
            } else if (wh.startsWith("how ")) {
                body = me(s) + " " + verbFor(s, be) + " " + a;
            } else if (tail.matches() && !tail.group(2).equalsIgnoreCase("of")) {
                // "what is Ngozi famous for" + "economics" → "Ngozi is famous for economics"
                body = me(tail.group(1)) + " " + verbFor(tail.group(1), be) + " " + tail.group(2).toLowerCase(Locale.US) + " " + a;
            } else {
                // "what is the capital of Ghana" + "Accra" → "The capital of Ghana is Accra"
                body = me(s) + " " + verbFor(s, be) + " " + a;
            }
        }
        if (body == null && (m = AUX_Q.matcher(q)).matches()) {
            String wh = m.group(1).toLowerCase(Locale.US);
            String aux = m.group(2).toLowerCase(Locale.US);
            String s = m.group(3);
            String verb = m.group(4);
            String rest = m.group(5) == null ? "" : " " + m.group(5);
            boolean you = s.matches("(?i)i|you");
            String v = aux.equals("did") ? Morph.past(verb) : aux.equals("does") && !you ? Morph.third(verb) : verb;
            if (verb.equalsIgnoreCase("do") && wh.equals("what")) {
                // "what does Ngozi do" + "she's an economist" / "economist"
                String job = a.replaceFirst("(?i)^(she|he|they|i)('s| is| are| am| was)\\s+", "");
                body = me(s) + " " + (aux.equals("did") ? "was" : verbFor(s, "is")) + " " + article(job);
            } else if (wh.equals("where")) {
                body = me(s) + " " + v + rest + " " + where(a);
            } else if (wh.equals("when")) {
                body = me(s) + " " + v + rest + " " + when(a);
            } else if (wh.equals("how")) {
                body = me(s) + " " + v + rest + " " + a;
            } else {
                body = me(s) + " " + v + " " + a + rest;
            }
        }
        if (body == null && (m = VERB_Q.matcher(q)).matches()) {
            String verb = m.group(2);
            String object = m.group(3);
            if (a.matches("(?i).+\\s+and\\s+.+") && verb.matches("(?i)\\w+[^s]s") && !verb.matches("(?i)\\w+ss")) {
                verb = verb.replaceFirst("(?i)(?<=(sh|ch|x|z|o))es$|s$", ""); // "Ada and Obi own it", not "owns"
            }
            body = a + " " + verb + " " + me(object);
        }
        if (body == null) {
            // a question of another shape: the answer said with what it answers, never as a loose word on its own
            body = a.split("\\s+").length >= 4 ? a : "The answer to “" + q + "?” is " + a;
        }
        boolean person = q.matches("(?i).*\\b(i|my|me|mine|myself)\\b.*") || a.matches("(?i)^(i|my|i'm|i am)\\b.*");
        return tidy(person ? swap(body) : body);
    }

    /**
     * Names typed in lower case set right: a word the question wrote with a capital ("ghana" → "Ghana"), and a
     * one-to-four-word answer where the question asks for a name (who, where, a capital, a city…): "accra" → "Accra".
     */
    static String names(String q, String a) {
        StringBuilder out = new StringBuilder();
        boolean asksName = q.matches("(?i)^(who|where)\\b.*") || q.matches("(?i).*\\b(capital|city|town|country|state|"
                + "village|name|president|governor|king|queen|founder|author|inventor|ceo|leader|called)\\b.*");
        boolean shortName = asksName && a.split("\\s+").length <= 4 && a.matches("[\\p{Ll}\\s'-]+")
                && !a.matches("(?i).*\\b(is|was|are|were|the|a|an|in|at|on|my|your|and|of)\\b.*");
        Matcher w = Pattern.compile("[\\p{L}][\\p{L}'-]*").matcher(a);
        int last = 0;
        while (w.find()) {
            out.append(a, last, w.start());
            String word = w.group();
            boolean capitalInQ = Pattern.compile("\\b" + Pattern.quote(Character.toUpperCase(word.charAt(0)) + word.substring(1)) + "\\b")
                    .matcher(q).find() && w.start() + word.length() <= a.length();
            boolean qStart = q.toLowerCase(Locale.US).startsWith(word.toLowerCase(Locale.US) + " ");
            if (Character.isLowerCase(word.charAt(0)) && (shortName || capitalInQ && !qStart)) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
            out.append(word);
            last = w.end();
        }
        out.append(a.substring(last));
        return out.toString();
    }

    /** The thing the question asks about ("Chinua Achebe" in "when was Chinua Achebe born"), or null. */
    static String subject(String q) {
        Matcher m = PASSIVE_Q.matcher(q);
        if (m.matches()) {
            return m.group(3);
        }
        m = COPULA_Q.matcher(q);
        if (m.matches()) {
            String s = m.group(3).replaceFirst("(?i)\\s+(from|in|at|on|for|by|with|about|like|born|founded)$", "");
            return s.matches("(?i).*\\b(my|i|me)\\b.*") ? null : s;
        }
        m = AUX_Q.matcher(q);
        if (m.matches()) {
            return m.group(3).matches("(?i)i|you") ? null : m.group(3);
        }
        return null;
    }

    /** True when the reply already says the whole thing: a verb and the subject (or most of its words) in it. */
    static boolean sentence(String a, String subject) {
        if (a.split("\\s+").length < 3 || !a.matches("(?i).*\\b(is|are|was|were|has|have|had|did|does|\\w+ed|\\w+s)\\b.*")) {
            return false;
        }
        if (subject == null) {
            return a.matches("(?i)^(my|i|i'm|i am|our|we)\\b.*") && a.split("\\s+").length >= 3;
        }
        String low = a.toLowerCase(Locale.US);
        int words = 0, found = 0;
        for (String w : subject.toLowerCase(Locale.US).split("\\W+")) {
            if (w.length() > 2 && !w.matches("the|and|of")) {
                words++;
                if (low.contains(w)) {
                    found++;
                }
            }
        }
        return words > 0 && found * 2 >= words;
    }

    private static String by(String a) {
        return a.replaceFirst("(?i)^by\\s+", "by ").startsWith("by ") ? a : "by " + a;
    }

    private static String when(String a) {
        if (a.matches("(?i)^(in|on|at|during|around|about|before|after|since|last|next|this|yesterday|today|tomorrow|ago)\\b.*")
                || a.matches("(?i).*\\bago$")) {
            return a;
        }
        if (a.matches("(?i)^\\d{1,2}(st|nd|rd|th)?\\s+\\p{L}+.*") || a.matches("(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\w*\\s+\\d{1,2}\\b.*")
                || a.matches("(?i)^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b.*")) {
            return "on " + a;
        }
        return "in " + a;
    }

    private static String where(String a) {
        if (a.matches("(?i)^(in|at|on|near|by|from|outside|inside|under|behind|beside|between|across|around|up|down|there|here|home|abroad)\\b.*")) {
            return a;
        }
        return "in " + a;
    }

    private static boolean one(String count) {
        return count.matches("(?i)(1|one|a|an|only one|just one)\\b.*");
    }

    private static String article(String job) {
        if (job.matches("(?i)^(a|an|the|my|his|her|their)\\b.*") || !job.matches("(?i)^\\p{Ll}[\\p{L}-]*$")) {
            return job;
        }
        return ("aeiou".indexOf(Character.toLowerCase(job.charAt(0))) >= 0 ? "an " : "a ") + job;
    }

    /** "is" said with "my …" as the subject stays "is"; "I" takes "are" once it is said back as "you". */
    private static String verbFor(String subject, String be) {
        if (subject.matches("(?i)i|you")) {
            return be.equals("was") || be.equals("were") ? "were" : "are";
        }
        return be;
    }

    /** The subject as said by the person, kept to be said back (the swap to "your" happens on the whole sentence). */
    private static String me(String s) {
        return s;
    }

    /** "my" said back as "your": the sentence is kept to answer the person, in Ibiko's words. */
    static String swap(String s) {
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("(?i)\\b(i'm|i am|i was|i|me|my|mine|myself|am)\\b").matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            String w = m.group(1).toLowerCase(Locale.US);
            String to;
            switch (w) {
                case "i'm":
                case "i am":
                    to = "you are";
                    break;
                case "i was":
                    to = "you were";
                    break;
                case "i":
                case "me":
                    to = "you";
                    break;
                case "my":
                    to = "your";
                    break;
                case "mine":
                    to = "yours";
                    break;
                case "myself":
                    to = "yourself";
                    break;
                default:
                    to = "are"; // "am"
            }
            out.append(to);
            last = m.end();
        }
        out.append(s.substring(last));
        return out.toString();
    }

    /** One clean sentence: single spaces, a capital first, one full stop. */
    static String tidy(String s) {
        String t = s.replaceAll("\\s+", " ").replaceAll("\\s+([,.;:!?])", "$1").trim();
        t = t.replaceAll("[\\s.!,;:]+$", "");
        if (t.isEmpty()) {
            return t;
        }
        return Character.toUpperCase(t.charAt(0)) + t.substring(1) + ".";
    }

    /** "Yes", "that's right", "correct": the formed sentence is confirmed. */
    static boolean yes(String reply) {
        return reply.trim().toLowerCase(Locale.US).matches("^(yes|yeah|yep|yup|ya|correct|right|exactly|that'?s (right|correct|it)|"
                + "sure|true|ok|okay|confirmed?|it is|absolutely|definitely|indeed)\\b.*");
    }

    /** "No", "that's wrong": what it formed is not kept. */
    static boolean no(String reply) {
        return reply.trim().toLowerCase(Locale.US).matches("^(no|nope|nah|wrong|incorrect|that'?s (wrong|not (right|it))|not quite)\\b.*");
    }

    /** "No, it's Accra": the part after the "no", or null when there's no new answer in it. */
    static String instead(String reply) {
        String r = reply.trim().replaceFirst("(?i)^(no|nope|nah|wrong|incorrect|that'?s (wrong|not (right|it))|not quite)\\b[,.!]*\\s*", "");
        r = r.replaceFirst("(?i)^(actually|rather|it'?s|it is|it was|i meant|i mean)\\s+", "");
        return r.trim().isEmpty() ? null : r;
    }
}
