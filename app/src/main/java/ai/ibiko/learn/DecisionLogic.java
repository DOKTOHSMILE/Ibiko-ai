package ai.ibiko.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.brain.MiniJson;
import ai.ibiko.ibiko1.formulas.Critical;
import ai.ibiko.ibiko1.formulas.Thinking;
import ai.ibiko.suit.Comprehension;

/**
 * What a turn decided, and why, kept beside the words. The phone's model was being shown an earlier question and
 * the earlier reply and told to use them as a guide, so it copied the wording. This is the other half: the kind of
 * act, the limits the person stated, the lean those limits support, the moves the model actually made, and whether
 * its own reason agrees. A later answer is told to follow that, not the sentences.
 *
 * <p>Pure Java. Nothing is invented beyond the person's words and the reply that was stored.
 */
public final class DecisionLogic {
    public final String side;
    public final String act;
    public final String decision;
    public final String domain;
    public final String lean;
    public final String because;
    public final String constraints;
    public final String moves;
    /** A sentence in the reply that states a reason, or empty when the reply only stated a result. */
    public final String why;
    /** One short instruction for the next answer. */
    public final String lesson;

    private DecisionLogic(String side, String act, String decision, String domain, String lean, String because,
            String constraints, String moves, String why, String lesson) {
        this.side = side;
        this.act = act;
        this.decision = decision;
        this.domain = domain;
        this.lean = lean;
        this.because = because;
        this.constraints = constraints;
        this.moves = moves;
        this.why = why;
        this.lesson = lesson;
    }

    /** The last thing the person said in {@code turns} (a wrapped prompt is unwrapped at "The user says:"). */
    public static String asked(List<String[]> turns) {
        String text = "";
        if (turns != null) {
            for (int i = turns.size() - 1; i >= 0; i--) {
                String[] t = turns.get(i);
                if (t == null || t.length < 2 || "assistant".equals(t[0])) {
                    continue;
                }
                text = t[1] == null ? "" : t[1];
                break;
            }
        }
        int cut = text.lastIndexOf("The user says: ");
        if (cut >= 0) {
            text = text.substring(cut + 15);
        }
        return text.trim();
    }

    /** Online unless the source says this reply was written on the device. */
    public static String sideOf(String source) {
        String s = source == null ? "" : source.toLowerCase(Locale.US);
        return s.contains("offline") || s.contains("device") || s.contains("local") ? "offline" : "online";
    }

    public static DecisionLogic of(String user, String reply, String source) {
        String asked = user == null ? "" : user.trim();
        Comprehension c = Comprehension.of(asked);
        String why = whyOf(reply);
        String moves = movesOf(reply);
        String domain = "";
        String lean = "";
        String because = "";
        String constraints = "";
        String lesson;
        if (c.judgement) {
            Critical.Verdict v = Critical.decide(asked);
            domain = v.domain == null ? "" : v.domain;
            lean = v.lean == null ? "" : v.lean;
            because = v.because == null ? "" : v.because;
            constraints = join(v.constraints);
            lesson = judgementLesson(v, why);
        } else if (c.refused) {
            lesson = "They said not to act. Answer in words. Do not copy a reply that goes ahead and does it.";
        } else if (c.hypothetical) {
            lesson = "Hypothetical. Explain what would follow. Do not act, and do not copy a reply that acts.";
        } else if (c.want != null) {
            lesson = "They asked for " + c.want.code() + " to be done, not explained."
                    + (c.also == null ? "" : " Then " + c.also.code() + ".")
                    + " Do that kind of thing. Do not answer with a different function, and do not copy unrelated wording.";
        } else if (!moves.isEmpty()) {
            lesson = "The earlier model got there by: " + moves + "."
                    + (why.isEmpty() ? " It did not add a separate reason." : " Its reason: " + clip(why, 160))
                    + " Follow those moves. Copy the words only when the facts are the same.";
        } else if (!why.isEmpty()) {
            lesson = "The earlier model gave a reason: " + clip(why, 180)
                    + " Use the reason. Copy the rest only when the facts are the same.";
        } else {
            lesson = "The earlier model stated a result and gave no separate reason."
                    + " Use it only when the facts match. Do not treat the wording as a rule.";
        }
        return new DecisionLogic(sideOf(source), c.act.name().toLowerCase(Locale.US), c.decisionLine(),
                domain, lean, because, constraints, moves, why, clip(lesson, 480));
    }

    /** From a stored line, or null when that line has no logic (it is worked out again from the words). */
    public static DecisionLogic from(Map<String, Object> record) {
        if (record == null) {
            return null;
        }
        Map<String, Object> o = MiniJson.obj(record.get("logic"));
        if (o.isEmpty()) {
            return null;
        }
        return new DecisionLogic(
                MiniJson.str(o.get("side"), ""),
                MiniJson.str(o.get("act"), ""),
                MiniJson.str(o.get("decision"), ""),
                MiniJson.str(o.get("domain"), ""),
                MiniJson.str(o.get("lean"), ""),
                MiniJson.str(o.get("because"), ""),
                MiniJson.str(o.get("constraints"), ""),
                MiniJson.str(o.get("moves"), ""),
                MiniJson.str(o.get("why"), ""),
                MiniJson.str(o.get("lesson"), ""));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("side", side);
        o.put("act", act);
        o.put("decision", decision);
        o.put("domain", domain);
        o.put("lean", lean);
        o.put("because", because);
        o.put("constraints", constraints);
        o.put("moves", moves);
        o.put("why", why);
        o.put("lesson", lesson);
        return o;
    }

    /** True when {@code reply} states the lean, and does not only name it in order to reject it. */
    public boolean agrees(String reply) {
        return states(lean, reply);
    }

    /**
     * True when {@code text} takes {@code lean}'s side. "Don't rent" and "can't afford to rent" do not. Naming the
     * option once, without a rejection in front of it, does.
     */
    public static boolean states(String lean, String text) {
        if (lean == null || lean.trim().isEmpty() || text == null) {
            return false;
        }
        String needle = lean.toLowerCase(Locale.US).trim();
        String low = text.toLowerCase(Locale.US);
        Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])").matcher(low);
        while (m.find()) {
            String before = low.substring(Math.max(0, m.start() - 48), m.start());
            if (before.matches(".*(?:\\b(?:do not|don't|dont|never|not|no|avoid|against|without|stop)"
                    + "|\\b(?:instead of|rather than|can'?t afford(?: to)?|cannot afford(?: to)?))\\s+(?:\\w+\\s+){0,3}$")) {
                continue;
            }
            String after = low.substring(m.end(), Math.min(low.length(), m.end() + 32));
            if (after.matches("(?s)^\\s+is\\s+(?:not|worse|bad|wrong|a mistake)\\b.*")) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static String judgementLesson(Critical.Verdict v, String why) {
        StringBuilder b = new StringBuilder();
        b.append("Judgement");
        if (v.domain != null && !v.domain.isEmpty() && !"general".equals(v.domain)) {
            b.append(" about ").append(v.domain);
        }
        b.append(". ");
        if (v.constraints != null && !v.constraints.isEmpty()) {
            b.append("Limits they stated: ").append(clip(join(v.constraints), 160)).append(". ");
        }
        if (v.lean != null) {
            b.append("Their words support ").append(v.lean);
            if (v.because != null && !v.because.isEmpty()) {
                b.append(" because ").append(clip(v.because, 180));
            }
            b.append(". ");
        } else {
            b.append(v.because == null ? "Nothing they said separates the options. " : clip(v.because, 180)).append(" Do not pick a side. ");
        }
        if (why != null && !why.isEmpty()) {
            boolean agrees = states(v.lean, why);
            if (agrees) {
                b.append("The model's reason matches that. ");
            } else {
                b.append("The model's own reason does not settle it: \"").append(clip(why, 140))
                        .append("\". Follow the limits they stated, not that wording. ");
            }
        }
        if (v.flip != null && !v.flip.isEmpty() && v.lean != null) {
            b.append("What would flip it: ").append(clip(v.flip, 120));
        }
        return b.toString().trim();
    }

    /** The first sentence that says why, or empty. A bare result ("Abuja.") is not a reason. */
    static String whyOf(String reply) {
        if (reply == null) {
            return "";
        }
        for (String part : reply.split("(?<=[.!?])\\s+|\\n+")) {
            String s = part.trim();
            if (s.length() < 12 || s.length() > 240) {
                continue;
            }
            if (s.toLowerCase(Locale.US).matches(".*\\b(because|so that|therefore|the reason|which means|since you)\\b.*")) {
                return s;
            }
        }
        return "";
    }

    private static String movesOf(String reply) {
        StringBuilder b = new StringBuilder();
        for (Thinking.Move m : Thinking.moves(reply)) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(m.said);
        }
        return b.toString();
    }

    private static String join(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        List<String> kept = new ArrayList<String>();
        for (String p : parts) {
            if (p != null && !p.trim().isEmpty()) {
                kept.add(p.trim());
            }
        }
        return String.join("; ", kept);
    }

    private static String clip(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n).trim() + "…";
    }
}
