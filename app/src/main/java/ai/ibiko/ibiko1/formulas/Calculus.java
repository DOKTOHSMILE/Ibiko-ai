package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko v1's calculus, offline and exact, on polynomials (products and powers of brackets are expanded first):
 * derivatives (first and second) by the power rule, integrals with + C, definite integrals and areas as exact
 * fractions, stationary points with their nature, and tangent lines. Every answer shows its working. Pure Java.
 */
public final class Calculus {
    private Calculus() {
    }

    // ------------------------------------------------------------------ the rules

    /** d/dv of p: each a·vⁿ becomes n·a·vⁿ⁻¹. */
    static Algebra.Poly derivative(Algebra.Poly p, char v) {
        Algebra.Poly out = new Algebra.Poly();
        for (Map.Entry<String, Algebra.Q> e : p.terms.entrySet()) {
            TreeMap<Character, Integer> pw = Algebra.Poly.powers(e.getKey());
            int n = pw.getOrDefault(v, 0);
            if (n == 0) {
                continue;
            }
            pw.put(v, n - 1);
            out = out.add(Algebra.Poly.single(Algebra.Poly.key(pw), e.getValue().mul(Algebra.Q.of(n))));
        }
        return out;
    }

    /** ∫ p dv (no constant): each a·vⁿ becomes a/(n+1)·vⁿ⁺¹. */
    static Algebra.Poly integral(Algebra.Poly p, char v) {
        Algebra.Poly out = new Algebra.Poly();
        for (Map.Entry<String, Algebra.Q> e : p.terms.entrySet()) {
            TreeMap<Character, Integer> pw = Algebra.Poly.powers(e.getKey());
            int n = pw.getOrDefault(v, 0);
            pw.put(v, n + 1);
            out = out.add(Algebra.Poly.single(Algebra.Poly.key(pw), e.getValue().div(Algebra.Q.of(n + 1))));
        }
        return out;
    }

    static Algebra.Q at(Algebra.Poly p, char v, Algebra.Q x) {
        List<Algebra.Q> c = p.univariate(v);
        return c == null ? null : Algebra.evaluate(c, x);
    }

    static double at(Algebra.Poly p, char v, double x) {
        List<Algebra.Q> c = p.univariate(v);
        return c == null ? Double.NaN : Algebra.evaluate(c, x);
    }

    /** The power rule written out for each term ("3x⁴ → 12x³"). */
    static String termByTerm(Algebra.Poly p, char v, boolean integrate) {
        List<String> steps = new ArrayList<String>();
        List<Map.Entry<String, Algebra.Q>> list = new ArrayList<Map.Entry<String, Algebra.Q>>(p.terms.entrySet());
        list.sort((a, b) -> Algebra.Poly.total(b.getKey()) - Algebra.Poly.total(a.getKey()));
        for (Map.Entry<String, Algebra.Q> e : list) {
            Algebra.Poly one = Algebra.Poly.single(e.getKey(), e.getValue());
            Algebra.Poly done = integrate ? integral(one, v) : derivative(one, v);
            steps.add(one + " → " + done);
            if (steps.size() >= 6) {
                break;
            }
        }
        return String.join(", ", steps);
    }

    // ------------------------------------------------------------------ asking

    private static final Pattern SECOND = Pattern.compile("(?is).*\\b(second derivative|d\\s*²\\s*y\\s*/\\s*d\\s*x\\s*²|d\\^?2y/dx\\^?2|f''\\s*\\(\\s*[a-z]\\s*\\)).*");
    private static final Pattern DIFF = Pattern.compile("(?is).*\\b(differentiate|derivative of|derive|d\\s*/\\s*d[a-z]|dy\\s*/\\s*dx|f'\\s*\\(\\s*[a-z]\\s*\\)|gradient function|rate of change of).*");
    private static final Pattern INTEG = Pattern.compile("(?is).*(\\bintegrate\\b|\\bintegral of\\b|\\bantiderivative\\b|\\banti-derivative\\b|∫|\\barea under\\b|\\barea between\\b).*");
    private static final Pattern STAT = Pattern.compile("(?is).*\\b(stationary points?|turning points?|critical points?|maximum and minimum|max(?:imum)? (?:and|or) min(?:imum)?|local (?:max|min)\\w*|maxima|minima)\\b.*");
    private static final Pattern TANGENT = Pattern.compile("(?is).*\\btangent(?: line)?\\b.*\\bat\\b.*");
    private static final Pattern RANGE = Pattern.compile("(?is)\\b(?:from|between)\\s+(-?\\d+(?:\\.\\d+)?(?:/\\d+)?)\\s+(?:to|and)\\s+(-?\\d+(?:\\.\\d+)?(?:/\\d+)?)");

    /** Ibiko's answer to a calculus question on a polynomial, with the working; null when it isn't one. */
    public static String reply(String message) {
        if (message == null || message.length() > 1500) {
            return null;
        }
        String m = Algebra.spoken(message.trim()).replace('−', '-');
        try {
            if (TANGENT.matcher(m).matches()) {
                return tangent(m);
            }
            if (STAT.matcher(m).matches()) {
                return stationary(m);
            }
            if (INTEG.matcher(m).matches()) {
                return integrate(m);
            }
            if (SECOND.matcher(m).matches()) {
                return differentiate(m, true);
            }
            if (DIFF.matcher(m).matches()) {
                return differentiate(m, false);
            }
        } catch (Algebra.NotAlgebra | ArithmeticException e) {
            return null;
        }
        return null;
    }

    /** The function in the message: after "y =", "f(x) =", or after the asking words; null when there's none. */
    static String function(String m) {
        String t = m.replaceAll(RANGE.pattern(), " ").replaceAll("(?is)\\s+(?:with respect to|w\\.?r\\.?t\\.?)\\s+[a-z]\\b", " ")
                .replaceAll("(?is)\\s+at\\s+[a-z]\\s*=\\s*-?[\\d./]+.*$", " ").replaceAll("(?i)\\bd[a-z]\\s*[?.!]*\\s*$", " ")
                .replace("∫", " ");
        Matcher eq = Pattern.compile("(?is)(?:\\by\\s*=|\\b[a-z]\\s*\\(\\s*[a-z]\\s*\\)\\s*=)\\s*(.+)$").matcher(t);
        String f;
        if (eq.find()) {
            f = eq.group(1);
        } else {
            Matcher after = Pattern.compile("(?is)(?:differentiate|derivative of|derive|d\\s*/\\s*d[a-z]|integrate|integral of|anti-?derivative of|"
                    + "area under(?: the curve)?|second derivative of|rate of change of|gradient function of|of)\\s*(.+)$").matcher(t);
            if (!after.find()) {
                return null;
            }
            f = after.group(1);
        }
        f = f.replaceAll("(?is)\\s*(?:,|;|\\band\\b|\\bfind\\b|\\bwhat\\b|\\bshow\\b|\\bgive\\b).*$", "").replaceAll("[?.!:]+\\s*$", "").trim();
        f = f.replaceAll("(?i)\\s*\\bd[a-z]$", "").trim();
        return f.isEmpty() ? null : f;
    }

    static char letter(Algebra.Poly p) {
        if (p.letters().size() != 1) {
            throw new Algebra.NotAlgebra("one letter only");
        }
        return p.letters().first();
    }

    static String differentiate(String m, boolean second) {
        String f = function(m);
        if (f == null) {
            return null;
        }
        Algebra.Poly p = Algebra.read(f);
        char v = letter(p);
        Algebra.Poly d = derivative(p, v);
        StringBuilder b = new StringBuilder();
        b.append("Differentiate ").append(p).append(" with respect to ").append(v).append(".\n");
        if (!Algebra.plain(f).matches("[0-9a-zA-Z+\\-^.]+")) {
            b.append("• Expand it first: ").append(p).append(".\n");
        }
        b.append("• Power rule, term by term (a·").append(v).append("ⁿ → n·a·").append(v).append("ⁿ⁻¹): ")
                .append(termByTerm(p, v, false)).append(".\n");
        if (!second) {
            return b.append("So d/d").append(v).append(" = ").append(d).append('.').toString();
        }
        Algebra.Poly d2 = derivative(d, v);
        b.append("• First derivative: ").append(d).append(".\n");
        b.append("• Again: ").append(termByTerm(d, v, false)).append(".\n");
        return b.append("So the second derivative is ").append(d2).append('.').toString();
    }

    static String integrate(String m) {
        String f = function(m);
        if (f == null) {
            return null;
        }
        Algebra.Poly p = Algebra.read(f);
        char v = letter(p);
        Algebra.Poly F = integral(p, v);
        boolean area = m.toLowerCase(Locale.ROOT).contains("area");
        StringBuilder b = new StringBuilder();
        b.append(area ? "Area under y = " : "Integrate ").append(p).append(area ? "" : " with respect to " + v).append(".\n");
        b.append("• Power rule backwards, term by term (a·").append(v).append("ⁿ → a·").append(v)
                .append("ⁿ⁺¹/(n+1)): ").append(termByTerm(p, v, true)).append(".\n");
        Matcher r = RANGE.matcher(m);
        if (!r.find()) {
            return b.append("So ∫ ").append(p).append(" d").append(v).append(" = ").append(F).append(" + C.").toString();
        }
        Algebra.Q lo = Algebra.Q.parse(r.group(1).contains("/") ? r.group(1).split("/")[0] : r.group(1)), hi = Algebra.Q.parse(r.group(2).contains("/") ? r.group(2).split("/")[0] : r.group(2));
        if (r.group(1).contains("/")) {
            lo = lo.div(Algebra.Q.parse(r.group(1).split("/")[1]));
        }
        if (r.group(2).contains("/")) {
            hi = hi.div(Algebra.Q.parse(r.group(2).split("/")[1]));
        }
        Algebra.Q Fhi = at(F, v, hi), Flo = at(F, v, lo), value = Fhi.sub(Flo);
        b.append("• Antiderivative F(").append(v).append(") = ").append(F).append(".\n");
        b.append("• F(").append(hi).append(") − F(").append(lo).append(") = ").append(Fhi).append(" − (").append(Flo).append(") = ").append(value).append(".\n");
        if (!area) {
            return b.append("So ∫ from ").append(lo).append(" to ").append(hi).append(" of ").append(p).append(" d").append(v).append(" = ")
                    .append(value).append(decimal(value)).append('.').toString();
        }
        // an area counts every piece as positive: split where the curve crosses the axis
        List<Algebra.Q> pieces = new ArrayList<Algebra.Q>();
        List<Double> cuts = new ArrayList<Double>();
        for (double z : Algebra.realRoots(p.univariate(v))) {
            if (z > lo.value() + 1e-12 && z < hi.value() - 1e-12) {
                cuts.add(z);
            }
        }
        if (cuts.isEmpty()) {
            Algebra.Q area1 = value.sign() < 0 ? value.neg() : value;
            return b.append("So the area is ").append(area1).append(decimal(area1)).append(value.sign() < 0 ? " (the curve is below the axis there, so the integral was negative)." : ".").toString();
        }
        double total = 0;
        double prev = lo.value();
        List<String> parts = new ArrayList<String>();
        cuts.add(hi.value());
        for (double c : cuts) {
            double piece = Math.abs(at(F, v, c) - at(F, v, prev));
            parts.add(Algebra.dec(piece));
            total += piece;
            prev = c;
        }
        return b.append("• The curve crosses the axis inside the range, so each piece is taken as positive: ").append(String.join(" + ", parts))
                .append(".\nSo the area is ≈ ").append(Algebra.dec(total)).append(" (the plain integral, ").append(value).append(", lets the pieces below the axis cancel).").toString();
    }

    static String decimal(Algebra.Q q) {
        return q.d.equals(java.math.BigInteger.ONE) ? "" : " ≈ " + Algebra.dec(q.value());
    }

    static String stationary(String m) {
        String f = function(m);
        if (f == null) {
            return null;
        }
        Algebra.Poly p = Algebra.read(f);
        char v = letter(p);
        Algebra.Poly d = derivative(p, v), d2 = derivative(d, v);
        List<Algebra.Q> dc = d.univariate(v);
        if (dc == null || Algebra.trim(dc).size() < 2) {
            return "The gradient " + d + " is never zero" + (Algebra.trim(dc).isEmpty() ? "" : "") + ", so there are no stationary points.";
        }
        StringBuilder b = new StringBuilder();
        b.append("Stationary points of y = ").append(p).append(".\n");
        b.append("• dy/d").append(v).append(" = ").append(d).append(". Set it to 0.\n");
        Algebra.Worked w = Algebra.roots(dc, v, false);
        List<String> pts = new ArrayList<String>();
        for (Algebra.Root r : w.roots) {
            if (!r.real()) {
                continue;
            }
            String xs = r.exact != null ? r.exact.replaceAll(" ≈ .*$", "") : Algebra.dec(r.re);
            String ys;
            Algebra.Q exactX = r.exact != null && r.exact.matches("−?\\d+(/\\d+)?") ? Algebra.Q.parse(r.exact.replace("−", "-").split("/")[0])
                    .div(r.exact.contains("/") ? Algebra.Q.parse(r.exact.split("/")[1]) : Algebra.Q.ONE) : null;
            if (exactX != null) {
                ys = at(p, v, exactX).toString();
            } else {
                ys = "≈ " + Algebra.dec(at(p, v, r.re));
            }
            double second = at(d2, v, r.re);
            String nature = second > 1e-9 ? "a minimum" : second < -1e-9 ? "a maximum" : "a point of inflection or flat point (the second derivative is 0 there)";
            pts.add("(" + xs + ", " + ys + "): d²y/d" + v + "² = " + Algebra.dec(second) + ", so " + nature);
        }
        if (pts.isEmpty()) {
            return b.append("dy/d").append(v).append(" = 0 has no real solution, so there are no stationary points.").toString();
        }
        b.append("• d²y/d").append(v).append("² = ").append(d2).append(" decides the nature.\n");
        for (String s : pts) {
            b.append("• ").append(s).append(".\n");
        }
        return b.toString().trim();
    }

    static String tangent(String m) {
        Matcher at = Pattern.compile("(?is)\\bat\\s+(?:the point\\s+)?(?:([a-z])\\s*=\\s*)?\\(?\\s*(-?\\d+(?:\\.\\d+)?)").matcher(m);
        if (!at.find()) {
            return null;
        }
        String f = function(m.substring(0, at.start()));
        if (f == null) {
            return null;
        }
        Algebra.Poly p = Algebra.read(f);
        char v = letter(p);
        Algebra.Q x0 = Algebra.Q.parse(at.group(2).replace("-", "")).mul(at.group(2).startsWith("-") ? Algebra.Q.ONE.neg() : Algebra.Q.ONE);
        Algebra.Poly d = derivative(p, v);
        Algebra.Q y0 = at(p, v, x0), slope = at(d, v, x0), c = y0.sub(slope.mul(x0));
        StringBuilder b = new StringBuilder();
        b.append("Tangent to y = ").append(p).append(" at ").append(v).append(" = ").append(x0).append(".\n");
        b.append("• The point: y = ").append(y0).append(", so (").append(x0).append(", ").append(y0).append(").\n");
        b.append("• The gradient: dy/d").append(v).append(" = ").append(d).append(" = ").append(slope).append(" there.\n");
        b.append("• y − ").append(paren(y0)).append(" = ").append(slope).append("(").append(v).append(" − ").append(paren(x0)).append(").\n");
        Algebra.Poly line = Algebra.Poly.single(v + "1", slope).add(Algebra.Poly.constant(c));
        return b.append("So the tangent is y = ").append(line).append('.').toString();
    }

    static String paren(Algebra.Q q) {
        return q.sign() < 0 ? "(" + q + ")" : q.toString();
    }
}
