package ai.ibiko.ibiko1.cognition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine 3, multi-step and causal reasoning (priority 3). A problem is a graph: the question, the evidence, the
 * assumptions, the steps worked out from them and the conclusion, joined by what supports, contradicts, depends on,
 * causes or merely goes along with what. A conclusion keeps its links to what produced it, so it can say why it holds,
 * which assumption would change it, and whether a "cause" is only a correlation. The working in an answer is done again,
 * step by step ("24 − 16 − 3 = 5"), and a wrong step is found and put right. Pure Java.
 *
 * <pre>
 * R1  graph        nodes QUESTION, EVIDENCE, ASSUMPTION, STEP, CONCLUSION; edges SUPPORTS, CONTRADICTS,
 *                  DEPENDS_ON, CAUSES, CORRELATES
 * R2  grounds      what a conclusion rests on: every node reached back along SUPPORTS and DEPENDS_ON
 * R3  pivotal      an assumption a conclusion rests on with no evidence beside it: change it, and the result changes
 * R4  follows      a conclusion follows when it has grounds and nothing it rests on is contradicted
 * R5  causal       a CAUSES link with only CORRELATES evidence beneath it is overreach
 * R6  working      every "a + b − c × d = r" in a text is worked again (× and ÷ first); a wrong result is found and put right
 * R7  overreach    a sentence that says one thing causes another on the strength of a link ("linked to", "associated
 *                  with", "people who … tend to") is flagged: a link is not a cause
 * </pre>
 */
public final class Reasoning {
    public enum Node { QUESTION, EVIDENCE, ASSUMPTION, STEP, CONCLUSION }

    public enum Edge { SUPPORTS, CONTRADICTS, DEPENDS_ON, CAUSES, CORRELATES }

    private final Map<String, Node> nodes = new LinkedHashMap<String, Node>();
    private final Map<String, String> words = new LinkedHashMap<String, String>();
    private final List<String[]> edges = new ArrayList<String[]>(); // {from, edge, to}

    /** R1: a node, by its id, with what it says. */
    public Reasoning node(String id, Node kind, String says) {
        nodes.put(id, kind);
        words.put(id, says);
        return this;
    }

    /** R1: {@code from} relates to {@code to} ("ev1 SUPPORTS step1": the evidence supports the step). */
    public Reasoning link(String from, Edge e, String to) {
        edges.add(new String[]{from, e.name(), to});
        return this;
    }

    /** R2: everything a node rests on, through what supports it and what it depends on. */
    public Set<String> grounds(String id) {
        Set<String> seen = new LinkedHashSet<String>();
        List<String> todo = new ArrayList<String>();
        todo.add(id);
        while (!todo.isEmpty()) {
            String n = todo.remove(0);
            for (String[] e : edges) {
                boolean supports = e[1].equals(Edge.SUPPORTS.name()) && e[2].equals(n) || e[1].equals(Edge.CAUSES.name()) && e[2].equals(n)
                        || e[1].equals(Edge.CORRELATES.name()) && e[2].equals(n);
                boolean depends = e[1].equals(Edge.DEPENDS_ON.name()) && e[0].equals(n);
                String next = supports ? e[0] : depends ? e[2] : null;
                if (next != null && seen.add(next)) {
                    todo.add(next);
                }
            }
        }
        return seen;
    }

    /** R3: the assumptions a conclusion rests on that no evidence backs: the ones that would change the result. */
    public List<String> pivotal(String conclusion) {
        List<String> out = new ArrayList<String>();
        for (String g : grounds(conclusion)) {
            if (nodes.get(g) == Node.ASSUMPTION) {
                boolean backed = false;
                for (String[] e : edges) {
                    backed |= e[2].equals(g) && e[1].equals(Edge.SUPPORTS.name()) && nodes.get(e[0]) == Node.EVIDENCE;
                }
                if (!backed) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** R4: whether a conclusion follows: it rests on evidence, and nothing it rests on is contradicted. */
    public boolean follows(String conclusion) {
        Set<String> g = grounds(conclusion);
        boolean evidence = false;
        for (String n : g) {
            evidence |= nodes.get(n) == Node.EVIDENCE;
        }
        for (String[] e : edges) {
            if (e[1].equals(Edge.CONTRADICTS.name()) && (g.contains(e[2]) || e[2].equals(conclusion))) {
                return false;
            }
        }
        return evidence;
    }

    /** R5: the CAUSES links whose cause is backed only by correlation: causal overreach. */
    public List<String> causalOverreach() {
        List<String> out = new ArrayList<String>();
        for (String[] e : edges) {
            if (!e[1].equals(Edge.CAUSES.name())) {
                continue;
            }
            boolean mechanism = false, correlation = false;
            for (String[] f : edges) {
                if (f[2].equals(e[0]) || f[2].equals(e[2])) {
                    mechanism |= f[1].equals(Edge.SUPPORTS.name()) && nodes.get(f[0]) == Node.EVIDENCE && !correlational(words.get(f[0]));
                    correlation |= f[1].equals(Edge.CORRELATES.name()) || nodes.get(f[0]) == Node.EVIDENCE && correlational(words.get(f[0]));
                }
            }
            if (correlation && !mechanism) {
                out.add(words.getOrDefault(e[0], e[0]) + " → " + words.getOrDefault(e[2], e[2]));
            }
        }
        return out;
    }

    /** What a node says. */
    public String says(String id) {
        return words.get(id);
    }

    // ------------------------------------------------------------------ R6 working

    /** One step of working that doesn't add up: as written, and what it comes to. */
    public static final class Wrong {
        public final String written;
        public final double said, is;

        Wrong(String written, double said, double is) {
            this.written = written;
            this.said = said;
            this.is = is;
        }
    }

    private static final String N = "\\d[\\d,]*(?:\\.\\d+)?";
    /** "40 − 12 sold + 10 bought (Musa) = 38", "4 × 18,000 naira = 72,000 naira": numbers, operators, words between. */
    private static final String OP = "(?:[+\\-−–×*÷/]|(?<![A-Za-z])x(?![A-Za-z]))";
    private static final Pattern WORKING = Pattern.compile("(" + N + ")((?:\\s*[^\\d=\\n.;:]{0,30}?\\s*" + OP + "\\s*(?:[₦$£€])?\\s*" + N + "){1,8})"
            + "[^=\\n\\d]{0,30}?=\\s*(?:[₦$£€])?\\s*(-?" + N + ")");
    private static final Pattern STEP = Pattern.compile("(" + OP + ")\\s*(?:[₦$£€])?\\s*(" + N + ")");

    /** R6: every worked line in a text done again; the ones that don't add up. */
    public static List<Wrong> checkWorking(String text) {
        List<Wrong> out = new ArrayList<Wrong>();
        Matcher m = WORKING.matcher(text == null ? "" : text);
        while (m.find()) {
            List<Double> nums = new ArrayList<Double>();
            List<Character> ops = new ArrayList<Character>();
            nums.add(num(m.group(1)));
            Matcher s = STEP.matcher(m.group(2));
            while (s.find()) {
                char op = s.group(1).charAt(0);
                ops.add(op == '−' || op == '–' ? '-' : op == 'x' || op == '×' ? '*' : op == '÷' ? '/' : op);
                nums.add(num(s.group(2)));
            }
            if (ops.isEmpty() || m.group(2).matches("(?s).*\\d\\s*/\\s*\\d.*") && ops.contains('/') && nums.size() == 2 && nums.get(0) < 13) {
                continue; // "3/4" is a fraction, not a division to check
            }
            Double is = evaluate(nums, ops);
            double said = num(m.group(3));
            if (is != null && Math.abs(is - said) > 0.005 * Math.max(1, Math.abs(is)) + 1e-9) {
                out.add(new Wrong(m.group().trim(), said, is));
            }
        }
        return out;
    }

    /** R6: the text with each wrong result put right. */
    public static String repairWorking(String text) {
        String out = text;
        for (Wrong w : checkWorking(text)) {
            String fixed = w.written.replaceFirst("=\\s*([₦$£€])?\\s*-?" + N + "(?!.*=)", "= $1" + show(w.is));
            out = out.replace(w.written, fixed.replace("= null", "= "));
        }
        return out;
    }

    /** The numbers worked out, × and ÷ before + and −. */
    static Double evaluate(List<Double> nums, List<Character> ops) {
        List<Double> terms = new ArrayList<Double>();
        List<Character> adds = new ArrayList<Character>();
        double cur = nums.get(0);
        for (int i = 0; i < ops.size(); i++) {
            char op = ops.get(i);
            double next = nums.get(i + 1);
            if (op == '*') {
                cur *= next;
            } else if (op == '/') {
                if (next == 0) {
                    return null;
                }
                cur /= next;
            } else {
                terms.add(cur);
                adds.add(op);
                cur = next;
            }
        }
        terms.add(cur);
        double r = terms.get(0);
        for (int i = 0; i < adds.size(); i++) {
            r = adds.get(i) == '+' ? r + terms.get(i + 1) : r - terms.get(i + 1);
        }
        return r;
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    static String show(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.US, "%,d", (long) Math.rint(v));
        }
        return String.format(Locale.US, "%,.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ------------------------------------------------------------------ R7 overreach in words

    private static final Pattern CORRELATIONAL = Pattern.compile("(?i)\\b(?:linked (?:to|with)|associated with|correlat\\w*|tend to|tends to|more likely|"
            + "less likely|in (?:countries|places|cities|people|areas) (?:where|with|that)|people who|those who|studies (?:show|found|suggest)|"
            + "at the same time|went up together|rose together|coincid\\w*)\\b");
    private static final Pattern CAUSAL = Pattern.compile("(?i)\\b(?:causes?|caused|leads? to|led to|results? in|resulted in|makes? (?:people|you|them)|"
            + "is the reason|because of|due to|responsible for|produces?)\\b");
    private static final Pattern MECHANISM = Pattern.compile("(?i)\\b(?:by (?:\\w+ing)|through|mechanism|which (?:damages|blocks|raises|lowers|triggers|releases)|"
            + "randomi[sz]ed|controlled (?:trial|experiment)|experiment shows|in a trial)\\b");

    static boolean correlational(String s) {
        return s != null && CORRELATIONAL.matcher(s).find();
    }

    /**
     * R7: the sentences of a text that state a cause on the strength of a correlation (in the same sentence, or the one
     * just before it), with no mechanism or experiment named.
     */
    public static List<String> overreach(String text) {
        List<String> out = new ArrayList<String>();
        List<String> s = ai.ibiko.ibiko1.formulas.Truth.sentences(text == null ? "" : text);
        for (int i = 0; i < s.size(); i++) {
            String here = s.get(i);
            if (!CAUSAL.matcher(here).find() || MECHANISM.matcher(here).find() || here.trim().endsWith("?")
                    || here.matches("(?is).*\\b(?:not necessarily|doesn't (?:mean|prove)|does not (?:mean|prove)|isn't proof|not proof|may|might|could)\\b.*")) {
                continue;
            }
            boolean link = correlational(here) || i > 0 && correlational(s.get(i - 1)) && here.matches("(?is)^\\s*(?:so|therefore|thus|this means|which means|clearly|hence)\\b.*");
            if (link) {
                out.add(here.trim());
            }
        }
        return out;
    }
}
