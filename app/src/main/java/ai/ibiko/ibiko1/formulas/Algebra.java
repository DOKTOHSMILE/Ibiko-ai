package ai.ibiko.ibiko1.formulas;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko v1's algebra, offline and exact: equations, inequalities and systems are solved with their working shown, the
 * way a teacher writes it out. Numbers are exact fractions throughout; a square root that doesn't come out is kept as
 * a surd ((5 ± 3√2)/4); a root that has no exact form is given to 4 decimals and said so.
 *
 * <ul>
 * <li>One unknown: linear, quadratic (the discriminant, exact roots, complex ones), cubic and quartic (rational roots
 * found exactly and divided out, the rest to 4 decimals).</li>
 * <li>Inequalities: the roots, then the sign of each stretch between them.</li>
 * <li>Systems of 2 or 3 linear equations: elimination, every unknown.</li>
 * <li>A quadratic with a parameter ("for which m does … have equal roots / two distinct real roots"): its discriminant
 * worked out as a polynomial in the parameter, then solved.</li>
 * <li>Expand and factorise.</li>
 * </ul>
 * Pure Java.
 */
public final class Algebra {
    private Algebra() {
    }

    // ------------------------------------------------------------------ exact fractions

    /** An exact fraction. */
    public static final class Q implements Comparable<Q> {
        public final BigInteger n, d;
        public static final Q ZERO = new Q(BigInteger.ZERO, BigInteger.ONE), ONE = new Q(BigInteger.ONE, BigInteger.ONE);

        Q(BigInteger n, BigInteger d) {
            if (d.signum() == 0) {
                throw new ArithmeticException("division by zero");
            }
            if (d.signum() < 0) {
                n = n.negate();
                d = d.negate();
            }
            BigInteger g = n.gcd(d);
            if (g.signum() != 0 && !g.equals(BigInteger.ONE)) {
                n = n.divide(g);
                d = d.divide(g);
            }
            this.n = n;
            this.d = d;
        }

        static Q of(long v) {
            return new Q(BigInteger.valueOf(v), BigInteger.ONE);
        }

        static Q parse(String s) {
            if (s.contains(".")) {
                String[] p = s.split("\\.", 2);
                BigInteger den = BigInteger.TEN.pow(p[1].length());
                return new Q(new BigInteger(p[0].isEmpty() ? "0" : p[0]).multiply(den).add(new BigInteger(p[1].isEmpty() ? "0" : p[1])), den);
            }
            return new Q(new BigInteger(s), BigInteger.ONE);
        }

        Q add(Q o) {
            return new Q(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d));
        }

        Q sub(Q o) {
            return add(o.neg());
        }

        Q mul(Q o) {
            return new Q(n.multiply(o.n), d.multiply(o.d));
        }

        Q div(Q o) {
            return new Q(n.multiply(o.d), d.multiply(o.n));
        }

        Q neg() {
            return new Q(n.negate(), d);
        }

        boolean zero() {
            return n.signum() == 0;
        }

        int sign() {
            return n.signum();
        }

        double value() {
            return new java.math.BigDecimal(n).divide(new java.math.BigDecimal(d), java.math.MathContext.DECIMAL64).doubleValue();
        }

        @Override
        public int compareTo(Q o) {
            return n.multiply(o.d).compareTo(o.n.multiply(d));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Q && ((Q) o).n.equals(n) && ((Q) o).d.equals(d);
        }

        @Override
        public int hashCode() {
            return n.hashCode() * 31 + d.hashCode();
        }

        @Override
        public String toString() {
            String s = n.abs() + (d.equals(BigInteger.ONE) ? "" : "/" + d);
            return n.signum() < 0 ? "−" + s : s;
        }
    }

    // ------------------------------------------------------------------ polynomials in several letters

    /** A polynomial: each monomial (its letters and powers, e.g. "m2x1") with its coefficient. */
    public static final class Poly {
        final TreeMap<String, Q> terms = new TreeMap<String, Q>();

        static Poly constant(Q q) {
            Poly p = new Poly();
            if (!q.zero()) {
                p.terms.put("", q);
            }
            return p;
        }

        static Poly letter(char v) {
            Poly p = new Poly();
            p.terms.put(v + "1", Q.ONE);
            return p;
        }

        static TreeMap<Character, Integer> powers(String key) {
            TreeMap<Character, Integer> m = new TreeMap<Character, Integer>();
            Matcher x = Pattern.compile("([a-zA-Z])(\\d+)").matcher(key);
            while (x.find()) {
                m.put(x.group(1).charAt(0), Integer.parseInt(x.group(2)));
            }
            return m;
        }

        static String key(TreeMap<Character, Integer> m) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<Character, Integer> e : m.entrySet()) {
                if (e.getValue() != 0) {
                    b.append(e.getKey()).append(e.getValue());
                }
            }
            return b.toString();
        }

        Poly add(Poly o) {
            Poly p = new Poly();
            p.terms.putAll(terms);
            for (Map.Entry<String, Q> e : o.terms.entrySet()) {
                Q sum = p.terms.getOrDefault(e.getKey(), Q.ZERO).add(e.getValue());
                if (sum.zero()) {
                    p.terms.remove(e.getKey());
                } else {
                    p.terms.put(e.getKey(), sum);
                }
            }
            return p;
        }

        Poly neg() {
            Poly p = new Poly();
            for (Map.Entry<String, Q> e : terms.entrySet()) {
                p.terms.put(e.getKey(), e.getValue().neg());
            }
            return p;
        }

        Poly sub(Poly o) {
            return add(o.neg());
        }

        Poly mul(Poly o) {
            Poly p = new Poly();
            for (Map.Entry<String, Q> a : terms.entrySet()) {
                for (Map.Entry<String, Q> b : o.terms.entrySet()) {
                    TreeMap<Character, Integer> m = powers(a.getKey());
                    for (Map.Entry<Character, Integer> e : powers(b.getKey()).entrySet()) {
                        m.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                    p = p.add(single(key(m), a.getValue().mul(b.getValue())));
                }
            }
            return p;
        }

        static Poly single(String key, Q q) {
            Poly p = new Poly();
            if (!q.zero()) {
                p.terms.put(key, q);
            }
            return p;
        }

        Poly pow(int k) {
            Poly p = constant(Q.ONE);
            for (int i = 0; i < k; i++) {
                p = p.mul(this);
            }
            return p;
        }

        boolean isConstant() {
            return terms.isEmpty() || terms.size() == 1 && terms.containsKey("");
        }

        Q constantValue() {
            return terms.getOrDefault("", Q.ZERO);
        }

        TreeSet<Character> letters() {
            TreeSet<Character> out = new TreeSet<Character>();
            for (String k : terms.keySet()) {
                out.addAll(powers(k).keySet());
            }
            return out;
        }

        int degree(char v) {
            int d = 0;
            for (String k : terms.keySet()) {
                d = Math.max(d, powers(k).getOrDefault(v, 0));
            }
            return d;
        }

        /** The coefficient of v^k: a polynomial in the other letters. */
        Poly coefficient(char v, int k) {
            Poly p = new Poly();
            for (Map.Entry<String, Q> e : terms.entrySet()) {
                TreeMap<Character, Integer> m = powers(e.getKey());
                if (m.getOrDefault(v, 0) == k) {
                    m.remove(v);
                    p = p.add(single(key(m), e.getValue()));
                }
            }
            return p;
        }

        /** As a list of rational coefficients in its one letter (index = power); null if it has other letters. */
        List<Q> univariate(char v) {
            List<Q> out = new ArrayList<Q>();
            for (int k = 0; k <= degree(v); k++) {
                Poly c = coefficient(v, k);
                if (!c.isConstant()) {
                    return null;
                }
                out.add(c.constantValue());
            }
            return out;
        }

        @Override
        public String toString() {
            if (terms.isEmpty()) {
                return "0";
            }
            // highest total degree first, then by letters
            List<Map.Entry<String, Q>> list = new ArrayList<Map.Entry<String, Q>>(terms.entrySet());
            list.sort((a, b) -> {
                int da = total(a.getKey()), db = total(b.getKey());
                return da != db ? db - da : a.getKey().compareTo(b.getKey());
            });
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Q> e : list) {
                Q q = e.getValue();
                String mono = mono(e.getKey());
                boolean neg = q.sign() < 0;
                Q abs = neg ? q.neg() : q;
                String coef = mono.isEmpty() ? abs.toString() : abs.equals(Q.ONE) ? "" : abs.d.equals(BigInteger.ONE) ? abs.toString() : "(" + abs + ")";
                b.append(b.length() == 0 ? (neg ? "−" : "") : neg ? " − " : " + ").append(coef).append(mono);
            }
            return b.toString();
        }

        static int total(String key) {
            int t = 0;
            for (int v : powers(key).values()) {
                t += v;
            }
            return t;
        }

        static String mono(String key) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<Character, Integer> e : powers(key).entrySet()) {
                b.append(e.getKey()).append(e.getValue() == 1 ? "" : sup(e.getValue()));
            }
            return b.toString();
        }
    }

    static String sup(int k) {
        String from = "0123456789", to = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        StringBuilder b = new StringBuilder();
        for (char c : String.valueOf(k).toCharArray()) {
            b.append(to.charAt(from.indexOf(c)));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ reading maths as typed

    /** Thrown when a piece isn't algebra Ibiko can read. */
    static final class NotAlgebra extends RuntimeException {
        NotAlgebra(String why) {
            super(why);
        }
    }

    private static final class Parser {
        final String s;
        int at;

        Parser(String s) {
            this.s = s;
        }

        Poly parse() {
            Poly p = expr();
            if (at != s.length()) {
                throw new NotAlgebra("unexpected " + s.charAt(at));
            }
            return p;
        }

        Poly expr() {
            Poly p = term();
            while (at < s.length() && (s.charAt(at) == '+' || s.charAt(at) == '-')) {
                char op = s.charAt(at++);
                Poly t = term();
                p = op == '+' ? p.add(t) : p.sub(t);
            }
            return p;
        }

        Poly term() {
            Poly p = factor();
            while (at < s.length()) {
                char c = s.charAt(at);
                if (c == '*') {
                    at++;
                    p = p.mul(factor());
                } else if (c == '/') {
                    at++;
                    Poly f = factor();
                    if (!f.isConstant() || f.constantValue().zero()) {
                        throw new NotAlgebra("division by a letter");
                    }
                    p = p.mul(Poly.constant(Q.ONE.div(f.constantValue())));
                } else if (Character.isLetter(c) || c == '(' || Character.isDigit(c) && at > 0 && s.charAt(at - 1) == ')') {
                    p = p.mul(factor()); // implicit: 2x, 3(x+1), x(x-2), (x+1)(x-1)
                } else {
                    break;
                }
            }
            return p;
        }

        Poly factor() {
            if (at < s.length() && (s.charAt(at) == '-' || s.charAt(at) == '+')) {
                char c = s.charAt(at++);
                Poly f = factor();
                return c == '-' ? f.neg() : f;
            }
            Poly base = atom();
            if (at < s.length() && s.charAt(at) == '^') {
                at++;
                int start = at;
                while (at < s.length() && Character.isDigit(s.charAt(at))) {
                    at++;
                }
                if (start == at) {
                    throw new NotAlgebra("a power must be a whole number");
                }
                int k = Integer.parseInt(s.substring(start, at));
                if (k > 12) {
                    throw new NotAlgebra("power too high");
                }
                return base.pow(k);
            }
            return base;
        }

        Poly atom() {
            if (at >= s.length()) {
                throw new NotAlgebra("ends too soon");
            }
            char c = s.charAt(at);
            if (c == '(') {
                at++;
                Poly p = expr();
                if (at >= s.length() || s.charAt(at) != ')') {
                    throw new NotAlgebra("a bracket isn't closed");
                }
                at++;
                return p;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = at;
                while (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) {
                    at++;
                }
                return Poly.constant(Q.parse(s.substring(start, at)));
            }
            if (Character.isLetter(c)) {
                at++;
                return Poly.letter(c);
            }
            throw new NotAlgebra("unexpected " + c);
        }
    }

    /** Typed maths made plain: unicode signs, powers written as ², × and ·, spaces gone. */
    static String plain(String s) {
        String t = s.replace('−', '-').replace('–', '-').replace('×', '*').replace('·', '*').replace('÷', '/')
                .replace("**", "^").replace("²", "^2").replace("³", "^3").replace("⁴", "^4")
                .replace("\\left", "").replace("\\right", "").replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')');
        return t.replaceAll("\\s+", "");
    }

    static Poly read(String s) {
        String p = plain(s);
        if (p.isEmpty() || !p.matches("[0-9a-zA-Z+\\-*/^().]+")) {
            throw new NotAlgebra("not an expression");
        }
        return new Parser(p).parse();
    }

    // ------------------------------------------------------------------ roots of one-letter polynomials

    /** A root: exact (a fraction, or p ± q√r over d), or to 4 decimals; real or complex. */
    public static final class Root {
        public final double re, im;
        public final String exact;

        Root(double re, double im, String exact) {
            this.re = re;
            this.im = im;
            this.exact = exact;
        }

        boolean real() {
            return Math.abs(im) < 1e-9;
        }

        @Override
        public String toString() {
            if (exact != null) {
                return exact;
            }
            if (real()) {
                return "≈ " + dec(re);
            }
            return "≈ " + dec(re) + (im < 0 ? " − " : " + ") + dec(Math.abs(im)) + "i";
        }
    }

    static String dec(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        if (s.equals("-0")) {
            s = "0";
        }
        return s.startsWith("-") ? "−" + s.substring(1) : s;
    }

    /** Steps of working, and the roots found, for c[0] + c[1]x + … = 0. */
    static final class Worked {
        final List<String> steps = new ArrayList<String>();
        final List<Root> roots = new ArrayList<Root>();
    }

    static List<Q> trim(List<Q> c) {
        List<Q> out = new ArrayList<Q>(c);
        while (!out.isEmpty() && out.get(out.size() - 1).zero()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    static Worked roots(List<Q> coeffs, char v, boolean show) {
        Worked w = new Worked();
        List<Q> c = trim(coeffs);
        int deg = c.size() - 1;
        if (deg <= 0) {
            return w;
        }
        // x = 0 as a root as often as the lowest power allows
        int zeros = 0;
        while (c.get(0).zero()) {
            c.remove(0);
            zeros++;
        }
        if (zeros > 0) {
            w.roots.add(new Root(0, 0, "0"));
            if (show) {
                w.steps.add("Take out " + v + (zeros > 1 ? sup(zeros) : "") + ": one root is " + v + " = 0.");
            }
        }
        deg = c.size() - 1;
        // exact rational roots, each divided out (a quadratic is solved whole, with its discriminant)
        for (Q r : deg <= 2 ? new ArrayList<Q>() : rationalCandidates(c)) {
            while (c.size() > 1 && evaluate(c, r).zero()) {
                w.roots.add(new Root(r.value(), 0, r.toString()));
                if (show && deg > 2) {
                    w.steps.add(v + " = " + r + " makes it zero, so (" + v + (r.sign() < 0 ? " + " + r.neg() : " − " + r) + ") is a factor: divide it out.");
                }
                c = divideOut(c, r);
            }
        }
        deg = c.size() - 1;
        if (deg == 1) {
            Q r = c.get(0).neg().div(c.get(1));
            if (!contains(w.roots, r)) {
                w.roots.add(new Root(r.value(), 0, r.toString()));
            }
        } else if (deg == 2) {
            quadratic(c, v, show, w);
        } else if (deg >= 3) {
            if (show) {
                w.steps.add("No more whole or fractional roots: the rest found numerically (to 4 decimals).");
            }
            for (double[] z : numeric(c)) {
                w.roots.add(new Root(z[0], Math.abs(z[1]) < 1e-9 ? 0 : z[1], null));
            }
        }
        return w;
    }

    static boolean contains(List<Root> roots, Q r) {
        for (Root x : roots) {
            if (r.toString().equals(x.exact)) {
                return true;
            }
        }
        return false;
    }

    /** a x² + b x + c = 0 with its discriminant, roots exact (surds kept). */
    static void quadratic(List<Q> c, char v, boolean show, Worked w) {
        Q a = c.get(2), b = c.get(1), k = c.get(0);
        Q disc = b.mul(b).sub(Q.of(4).mul(a).mul(k));
        if (show) {
            w.steps.add("It's a quadratic: a = " + a + ", b = " + b + ", c = " + k + ".");
            w.steps.add("Discriminant b² − 4ac = (" + b + ")² − 4·(" + a + ")·(" + k + ") = " + disc
                    + (disc.sign() > 0 ? ": positive, so two real roots." : disc.zero() ? ": zero, so one repeated root." : ": negative, so no real roots (two complex ones)."));
        }
        // with whole numbers: multiply through by the common denominator
        BigInteger lcm = a.d.multiply(b.d).divide(a.d.gcd(b.d));
        lcm = lcm.multiply(k.d).divide(lcm.gcd(k.d));
        BigInteger A = a.n.multiply(lcm.divide(a.d)), B = b.n.multiply(lcm.divide(b.d)), C = k.n.multiply(lcm.divide(k.d));
        BigInteger D = B.multiply(B).subtract(BigInteger.valueOf(4).multiply(A).multiply(C));
        BigInteger[] sq = squarePart(D.abs()); // |D| = s² · r
        BigInteger s = sq[0], r = sq[1];
        BigInteger twoA = A.shiftLeft(1);
        double re = -B.doubleValue() / twoA.doubleValue();
        double spread = Math.sqrt(D.abs().doubleValue()) / Math.abs(twoA.doubleValue());
        if (D.signum() == 0) {
            Q root = new Q(B.negate(), twoA);
            w.roots.add(new Root(root.value(), 0, root.toString()));
            if (show) {
                w.steps.add(v + " = −b/(2a) = " + root + ".");
            }
            return;
        }
        if (D.signum() > 0 && r.equals(BigInteger.ONE)) {
            Q r1 = new Q(B.negate().add(s), twoA), r2 = new Q(B.negate().subtract(s), twoA);
            if (show) {
                w.steps.add(v + " = (" + Q.of(0).sub(new Q(B, BigInteger.ONE)) + " ± " + s + ")/" + new Q(twoA, BigInteger.ONE) + ".");
            }
            List<Q> two = new ArrayList<Q>(List.of(r1, r2));
            Collections.sort(two);
            for (Q q : two) {
                w.roots.add(new Root(q.value(), 0, q.toString()));
            }
            return;
        }
        // a surd: (−B ± s√r)/(2A), reduced by the common factor
        BigInteger g = B.gcd(s).gcd(twoA);
        BigInteger p = B.negate().divide(g), q = s.divide(g), den = twoA.divide(g);
        if (den.signum() < 0) {
            p = p.negate();
            q = q.negate();
            den = den.negate();
        }
        String surd = r.equals(BigInteger.ONE) ? (q.abs().equals(BigInteger.ONE) ? "" : q.abs().toString())
                : (q.abs().equals(BigInteger.ONE) ? "" : q.abs().toString()) + "√" + r; // ±2i, ±i, not ±2√1i
        String unit = D.signum() < 0 ? "i" : "";
        String head = p.signum() == 0 ? "" : new Q(p, BigInteger.ONE).toString();
        String body = head.isEmpty() ? "±" + surd + unit : head + " ± " + surd + unit;
        String exact = den.equals(BigInteger.ONE) ? body : "(" + body + ")/" + den;
        if (show) {
            w.steps.add(v + " = " + exact + (D.signum() > 0 ? "" : " (i = √−1)") + ".");
        }
        if (D.signum() > 0) {
            String lo = (den.equals(BigInteger.ONE) ? (head.isEmpty() ? "−" + surd : head + " − " + surd) : "(" + (head.isEmpty() ? "−" + surd : head + " − " + surd) + ")/" + den);
            String hi = (den.equals(BigInteger.ONE) ? (head.isEmpty() ? surd : head + " + " + surd) : "(" + (head.isEmpty() ? surd : head + " + " + surd) + ")/" + den);
            w.roots.add(new Root(re - spread, 0, lo + " ≈ " + dec(re - spread)));
            w.roots.add(new Root(re + spread, 0, hi + " ≈ " + dec(re + spread)));
        } else {
            w.roots.add(new Root(re, -spread, null));
            w.roots.add(new Root(re, spread, null));
        }
    }

    /** n = s² · r with r square-free. */
    static BigInteger[] squarePart(BigInteger n) {
        BigInteger s = BigInteger.ONE, r = n;
        for (long f = 2; f * f <= Math.min(r.longValue(), Long.MAX_VALUE / 4) && f < 100000; f++) {
            BigInteger ff = BigInteger.valueOf(f * f);
            while (r.mod(ff).signum() == 0) {
                r = r.divide(ff);
                s = s.multiply(BigInteger.valueOf(f));
            }
        }
        return new BigInteger[] {s, r};
    }

    static Q evaluate(List<Q> c, Q x) {
        Q v = Q.ZERO;
        for (int i = c.size() - 1; i >= 0; i--) {
            v = v.mul(x).add(c.get(i));
        }
        return v;
    }

    static double evaluate(List<Q> c, double x) {
        double v = 0;
        for (int i = c.size() - 1; i >= 0; i--) {
            v = v * x + c.get(i).value();
        }
        return v;
    }

    /** ± p/q with p dividing the constant and q the leading coefficient (whole-number form). */
    static List<Q> rationalCandidates(List<Q> c) {
        BigInteger l = BigInteger.ONE;
        for (Q q : c) {
            l = l.multiply(q.d).divide(l.gcd(q.d));
        }
        BigInteger a0 = c.get(0).n.multiply(l.divide(c.get(0).d)).abs(), an = c.get(c.size() - 1).n.multiply(l.divide(c.get(c.size() - 1).d)).abs();
        List<Q> out = new ArrayList<Q>();
        if (a0.signum() == 0 || a0.bitLength() > 40 || an.bitLength() > 40) {
            return out;
        }
        List<Long> ps = divisors(a0.longValue()), qs = divisors(an.longValue());
        TreeSet<Q> seen = new TreeSet<Q>();
        for (long p : ps) {
            for (long q : qs) {
                seen.add(new Q(BigInteger.valueOf(p), BigInteger.valueOf(q)));
                seen.add(new Q(BigInteger.valueOf(-p), BigInteger.valueOf(q)));
            }
        }
        out.addAll(seen);
        return out;
    }

    static List<Long> divisors(long n) {
        List<Long> out = new ArrayList<Long>();
        for (long i = 1; i * i <= n && i < 2_000_000; i++) {
            if (n % i == 0) {
                out.add(i);
                if (i != n / i) {
                    out.add(n / i);
                }
            }
        }
        return out;
    }

    /** c divided by (x − r) (synthetic division; r is a root). */
    static List<Q> divideOut(List<Q> c, Q r) {
        int n = c.size() - 1;
        Q[] out = new Q[n];
        Q carry = Q.ZERO;
        for (int i = n; i >= 1; i--) {
            carry = carry.mul(r).add(c.get(i));
            out[i - 1] = carry;
        }
        List<Q> list = new ArrayList<Q>();
        Collections.addAll(list, out);
        return list;
    }

    /** All roots numerically (Durand–Kerner, then each real one polished by Newton). */
    static List<double[]> numeric(List<Q> c) {
        int n = c.size() - 1;
        double lead = c.get(n).value();
        double[] a = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            a[i] = c.get(i).value() / lead;
        }
        double[] re = new double[n], im = new double[n];
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n + 0.4;
            re[i] = 0.9 * Math.cos(ang);
            im[i] = 0.9 * Math.sin(ang);
        }
        for (int iter = 0; iter < 2000; iter++) {
            double change = 0;
            for (int i = 0; i < n; i++) {
                // p(z_i)
                double pr = 1, pi = 0;
                for (int k = n - 1; k >= 0; k--) {
                    double nr = pr * re[i] - pi * im[i] + a[k], ni = pr * im[i] + pi * re[i];
                    pr = nr;
                    pi = ni;
                }
                double dr = 1, di = 0;
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        double xr = re[i] - re[j], xi = im[i] - im[j];
                        double nr = dr * xr - di * xi, ni = dr * xi + di * xr;
                        dr = nr;
                        di = ni;
                    }
                }
                double den = dr * dr + di * di;
                if (den == 0) {
                    continue;
                }
                double qr = (pr * dr + pi * di) / den, qi = (pi * dr - pr * di) / den;
                re[i] -= qr;
                im[i] -= qi;
                change = Math.max(change, Math.abs(qr) + Math.abs(qi));
            }
            if (change < 1e-14) {
                break;
            }
        }
        List<double[]> out = new ArrayList<double[]>();
        for (int i = 0; i < n; i++) {
            double x = re[i], y = im[i];
            if (Math.abs(y) < 1e-7) {
                y = 0;
                for (int k = 0; k < 50; k++) { // Newton on the real line
                    double f = evaluate(c, x), h = 1e-7 * Math.max(1, Math.abs(x));
                    double d = (evaluate(c, x + h) - evaluate(c, x - h)) / (2 * h);
                    if (d == 0) {
                        break;
                    }
                    double nx = x - f / d;
                    if (Math.abs(nx - x) < 1e-15) {
                        break;
                    }
                    x = nx;
                }
            }
            out.add(new double[] {x, y});
        }
        out.sort((p, q) -> p[1] != q[1] && (p[1] == 0 || q[1] == 0) ? Double.compare(Math.abs(p[1]), Math.abs(q[1])) : Double.compare(p[0], q[0]));
        return out;
    }

    /** The real roots, sorted, as numbers. */
    static List<Double> realRoots(List<Q> c) {
        List<Double> out = new ArrayList<Double>();
        if (trim(c).size() < 2) {
            return out;
        }
        for (Root r : roots(c, 'x', false).roots) {
            if (r.real()) {
                boolean dup = false;
                for (double o : out) {
                    dup |= Math.abs(o - r.re) < 1e-9;
                }
                if (!dup) {
                    out.add(r.re);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    // ------------------------------------------------------------------ asking

    private static final Pattern ASK = Pattern.compile("(?is)^\\s*(?:please\\s+|can you\\s+|could you\\s+)?(solve|find|work out|"
            + "calculate|determine|factori[sz]e|factor|expand|simplify|what (?:is|are) the (?:roots?|solutions?) of|roots? of|"
            + "for (?:which|what) values? of)\\b.*");

    /** Ibiko's answer to a message that asks for algebra, with the working; null when it isn't algebra it can do. */
    public static String reply(String message) {
        if (message == null || message.length() > 2000) {
            return null;
        }
        String m = message.trim();
        try {
            String param = parametric(m);
            if (param != null) {
                return param;
            }
            List<String> eqs = equations(m);
            if (eqs.size() >= 2) {
                return system(eqs);
            }
            Matcher fx = Pattern.compile("(?is)^\\s*(?:please\\s+)?(factori[sz]e|factor|expand|simplify)\\s*:?\\s*(.+?)\\s*[.?]*$").matcher(m);
            if (fx.matches() && !fx.group(2).contains("=")) {
                return fx.group(1).toLowerCase(Locale.ROOT).startsWith("factor") ? factorise(fx.group(2)) : expand(fx.group(2));
            }
            String eq = single(m);
            if (eq == null) {
                return null;
            }
            return solve(eq);
        } catch (NotAlgebra | ArithmeticException e) {
            return null;
        }
    }

    /** The one equation or inequality in the message, or null (plain sums stay with the calculator). */
    static String single(String m) {
        Matcher x = Pattern.compile("([0-9a-zA-Z+\\-−*/^()\\[\\]²³×.\\s]+?)\\s*(<=|>=|≤|≥|=|<|>)\\s*([0-9a-zA-Z+\\-−*/^()\\[\\]²³×.\\s]+)").matcher(stripAsk(m));
        if (!x.find()) {
            Matcher r = Pattern.compile("(?is)(?:roots?|solutions?|zeros?) of\\s+(.+?)\\s*[.?]*$").matcher(m);
            return r.find() && read(r.group(1)).letters().size() == 1 ? r.group(1) + "=0" : null;
        }
        String left = x.group(1).trim(), right = x.group(3).trim().replaceAll("[.?]+$", "");
        Poly l = read(left), r = read(right);
        if (l.sub(r).letters().size() != 1 || l.sub(r).letters().isEmpty()) {
            return null;
        }
        if (!ASK.matcher(m).matches() && !m.trim().matches("(?s)^[0-9a-zA-Z+\\-−*/^()²³.\\s]+(<=|>=|≤|≥|=|<|>)[0-9a-zA-Z+\\-−*/^()²³.\\s]+[?.]?$")) {
            return null; // a sentence that only contains an equation somewhere isn't asking to solve it
        }
        return left + x.group(2) + right;
    }

    static String stripAsk(String m) {
        return m.replaceFirst("(?is)^\\s*(?:please\\s+|can you\\s+|could you\\s+)?(solve|find|work out|calculate|determine)\\s*(?:for\\s+[a-z]\\s*)?:?\\s*", "")
                .replaceFirst("(?is)^\\s*(?:the\\s+)?(?:equation|inequality)\\s*:?\\s*", "");
    }

    /** "x + y = 5, x − y = 1" (or "and", ";", new lines): the linear equations, when there are two or three. */
    static List<String> equations(String m) {
        List<String> out = new ArrayList<String>();
        String body = stripAsk(m).replaceAll("(?i)\\b(simultaneously|the system|system of equations|equations)\\b", " ");
        for (String part : body.split("\\s*(?:,|;|\\n|\\band\\b)\\s*")) {
            if (part.contains("=") && !part.matches(".*[<>].*")) {
                out.add(part.replaceAll("[.?]+$", "").trim());
            }
        }
        return out.size() >= 2 && out.size() <= 3 ? out : new ArrayList<String>();
    }

    /** One equation or inequality in one letter, solved with its working. */
    static String solve(String eq) {
        Matcher x = Pattern.compile("(.+?)(<=|>=|≤|≥|=|<|>)(.+)").matcher(eq);
        if (!x.matches()) {
            return null;
        }
        String op = x.group(2).replace("≤", "<=").replace("≥", ">=");
        Poly p = read(x.group(1)).sub(read(x.group(3)));
        char v = p.letters().first();
        List<Q> c = p.univariate(v);
        if (c == null || trim(c).size() < 2) {
            return null;
        }
        int deg = trim(c).size() - 1;
        StringBuilder b = new StringBuilder();
        b.append("Solve ").append(pretty(x.group(1))).append(' ').append(sign(op)).append(' ').append(pretty(x.group(3))).append(".\n");
        b.append("• Everything on one side: ").append(p).append(' ').append(sign(op)).append(" 0.\n");
        Worked w = roots(c, v, true);
        for (String s : w.steps) {
            b.append("• ").append(s).append('\n');
        }
        if (op.equals("=")) {
            List<Root> real = new ArrayList<Root>(), complex = new ArrayList<Root>();
            for (Root r : w.roots) {
                (r.real() ? real : complex).add(r);
            }
            if (real.isEmpty() && complex.isEmpty()) {
                return null;
            }
            List<String> rs = new ArrayList<String>();
            for (Root r : real) {
                rs.add(v + (r.toString().startsWith("\u2248") ? " " : " = ") + r);
            }
            for (Root r : complex) {
                rs.add(v + (r.toString().startsWith("\u2248") ? " " : " = ") + r);
            }
            b.append("So ").append(String.join(", or ", rs)).append('.');
            if (deg == 1 && real.size() == 1) {
                b.append(check(c, real.get(0), v));
            }
            return b.toString().trim();
        }
        // an inequality: the sign of each stretch between the real roots
        List<Double> rr = realRoots(c);
        List<String> parts = new ArrayList<String>();
        double lo = Double.NEGATIVE_INFINITY;
        List<Double> cuts = new ArrayList<Double>(rr);
        cuts.add(Double.POSITIVE_INFINITY);
        boolean strict = op.equals("<") || op.equals(">");
        for (double hi : cuts) {
            double mid = Double.isInfinite(lo) && Double.isInfinite(hi) ? 0 : Double.isInfinite(lo) ? hi - 1 : Double.isInfinite(hi) ? lo + 1 : (lo + hi) / 2;
            double val = evaluate(c, mid);
            boolean ok = op.startsWith(">") ? val > 0 : val < 0;
            if (ok) {
                parts.add((Double.isInfinite(lo) ? "(−∞" : (strict ? "(" : "[") + num(lo, c, v)) + ", "
                        + (Double.isInfinite(hi) ? "∞)" : num(hi, c, v) + (strict ? ")" : "]")));
            }
            lo = hi;
        }
        b.append("• The roots cut the number line into stretches; the sign of each decides it.\n");
        if (parts.isEmpty()) {
            if (!strict && !rr.isEmpty()) {
                List<String> pts = new ArrayList<String>();
                for (double r : rr) {
                    pts.add(v + " = " + num(r, c, v));
                }
                b.append("So only ").append(String.join(", ", pts)).append('.');
            } else {
                b.append("So no real ").append(v).append(" makes it true.");
            }
        } else {
            b.append("So ").append(v).append(" ∈ ").append(String.join(" ∪ ", parts)).append('.');
        }
        return b.toString().trim();
    }

    /** A root shown exactly when it is a fraction, else to 4 decimals. */
    static String num(double r, List<Q> c, char v) {
        for (Root x : roots(c, v, false).roots) {
            if (x.real() && Math.abs(x.re - r) < 1e-9 && x.exact != null) {
                return x.exact.replaceAll(" ≈ .*$", "");
            }
        }
        return dec(r);
    }

    static String check(List<Q> c, Root r, char v) {
        return "\nCheck: putting " + v + " = " + r + " back in gives 0. ✓";
    }

    static String sign(String op) {
        switch (op) {
            case "<=": return "≤";
            case ">=": return "≥";
            default: return op;
        }
    }

    static String pretty(String s) {
        String t = plain(s).replaceAll("\\^(\\d+)", "^$1");
        Matcher m = Pattern.compile("\\^(\\d+)").matcher(t);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(b, sup(Integer.parseInt(m.group(1))));
        }
        m.appendTail(b);
        return b.toString().replace("*", "·").replace("-", " − ").replace("+", " + ").replace("=", " = ").replaceAll("\\s+", " ")
                .replaceAll("^ − ", "−").replaceAll("\\( − ", "(−").trim();
    }

    // ------------------------------------------------------------------ systems

    static String system(List<String> eqs) {
        List<Poly> rows = new ArrayList<Poly>();
        TreeSet<Character> vars = new TreeSet<Character>();
        for (String e : eqs) {
            String[] s = e.split("=", 2);
            Poly p = read(s[0]).sub(read(s[1]));
            for (char v : p.letters()) {
                if (p.degree(v) > 1) {
                    return null; // only linear systems
                }
            }
            for (String k : p.terms.keySet()) {
                if (Poly.total(k) > 1) {
                    return null;
                }
            }
            vars.addAll(p.letters());
            rows.add(p);
        }
        if (vars.size() != rows.size()) {
            return null;
        }
        List<Character> vs = new ArrayList<Character>(vars);
        int n = vs.size();
        Q[][] a = new Q[n][n + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = rows.get(i).terms.getOrDefault(vs.get(j) + "1", Q.ZERO);
            }
            a[i][n] = rows.get(i).constantValue().neg();
        }
        StringBuilder b = new StringBuilder("Solve the " + (n == 2 ? "two" : "three") + " equations together:\n");
        for (String e : eqs) {
            b.append("• ").append(pretty(e)).append('\n');
        }
        b.append("By elimination:\n");
        for (int col = 0; col < n; col++) {
            int pivot = -1;
            for (int r = col; r < n; r++) {
                if (!a[r][col].zero()) {
                    pivot = r;
                    break;
                }
            }
            if (pivot < 0) {
                return b.append("The equations don't fix a single answer: they are dependent or contradict each other.").toString();
            }
            Q[] t = a[col];
            a[col] = a[pivot];
            a[pivot] = t;
            for (int r = 0; r < n; r++) {
                if (r != col && !a[r][col].zero()) {
                    Q f = a[r][col].div(a[col][col]);
                    for (int k = col; k <= n; k++) {
                        a[r][k] = a[r][k].sub(f.mul(a[col][k]));
                    }
                }
            }
            b.append("• Use equation ").append(col + 1).append(" to take ").append(vs.get(col)).append(" out of the others.\n");
        }
        List<String> ans = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            ans.add(vs.get(i) + " = " + a[i][n].div(a[i][i]));
        }
        return b.append("So ").append(String.join(", ", ans)).append('.').toString();
    }

    // ------------------------------------------------------------------ expand and factorise

    static String expand(String s) {
        Poly p = read(s);
        return pretty(s) + " = " + p + ".";
    }

    static String factorise(String s) {
        Poly p = read(s);
        if (p.letters().size() != 1) {
            return null;
        }
        char v = p.letters().first();
        List<Q> c = trim(p.univariate(v));
        if (c == null || c.size() < 3) {
            return null;
        }
        Q lead = c.get(c.size() - 1);
        List<String> factors = new ArrayList<String>();
        List<Q> rest = new ArrayList<Q>(c);
        while (rest.size() > 1 && rest.get(0).zero()) {
            factors.add(String.valueOf(v));
            rest.remove(0);
        }
        for (Q r : rationalCandidates(rest.size() > 1 ? rest : c)) {
            while (rest.size() > 1 && evaluate(rest, r).zero()) {
                // (q·v − p) for r = p/q, keeping whole numbers
                String f = r.d.equals(BigInteger.ONE) ? v + (r.sign() < 0 ? " + " + r.neg() : " − " + r)
                        : r.d + "" + v + (r.sign() < 0 ? " + " + r.n.negate() : " − " + r.n);
                factors.add("(" + f + ")");
                lead = lead.div(new Q(r.d, BigInteger.ONE));
                rest = divideOut(rest, r);
            }
        }
        String left = rest.size() > 1 ? "(" + polyOf(rest, v) + ")" : "";
        Q k = rest.size() > 1 ? Q.ONE : rest.get(0);
        if (rest.size() > 1) {
            k = Q.ONE;
        }
        if (factors.isEmpty()) {
            return pretty(s) + " doesn't factorise over the whole numbers or fractions.";
        }
        String coef = rest.size() <= 1 ? (k.equals(Q.ONE) ? "" : k.equals(Q.ONE.neg()) ? "−" : k.toString()) : "";
        return pretty(s) + " = " + coef + String.join("", factors) + left + ".";
    }

    static String polyOf(List<Q> c, char v) {
        Poly p = new Poly();
        for (int i = 0; i < c.size(); i++) {
            p = p.add(Poly.single(i == 0 ? "" : v + "" + i, c.get(i)));
        }
        return p.toString();
    }

    // ------------------------------------------------------------------ a quadratic with a parameter

    private static final Pattern PARAM = Pattern.compile("(?is).*(?:values? of|for which|for what|find)\\s+([a-z])\\b.*");

    /**
     * "(2m−3)x² − 2(m²−4m+1)x + (m²−5m+6) = 0 … for which m are the roots equal / two distinct real roots / real":
     * the discriminant as a polynomial in the parameter, then its roots and sign.
     */
    static String parametric(String m) {
        Matcher pm = PARAM.matcher(m);
        if (!pm.matches()) {
            return null;
        }
        char param = pm.group(1).charAt(0);
        Matcher eq = Pattern.compile("([0-9a-zA-Z+\\-−*/^()\\[\\]²³.\\s]{6,}?)\\s*=\\s*([0-9a-zA-Z+\\-−*/^()²³.\\s]+?)(?=$|[,;:]|\\s+(?:where|has|for|find|determine|and)\\b)")
                .matcher(m.replaceAll("\\\\[()\\[\\]]", " "));
        Poly p = null;
        char x = 0;
        while (eq.find()) {
            try {
                String left = eq.group(1).replaceFirst("(?is)^.*?(?=[0-9(])", "");
                Poly cand = read(left).sub(read(eq.group(2)));
                for (char v : cand.letters()) {
                    if (v != param && cand.degree(v) == 2) {
                        p = cand;
                        x = v;
                    }
                }
                if (p != null) {
                    break;
                }
            } catch (NotAlgebra e) {
                // not this one
            }
        }
        if (p == null || !p.letters().contains(param)) {
            return null;
        }
        Poly a = p.coefficient(x, 2), b = p.coefficient(x, 1), c = p.coefficient(x, 0);
        if (a.letters().size() > 1 || b.letters().size() > 1 || c.letters().size() > 1) {
            return null;
        }
        Poly disc = b.mul(b).sub(Poly.constant(Q.of(4)).mul(a).mul(c));
        List<Q> dq = disc.univariate(param), aq = a.univariate(param);
        if (dq == null || aq == null) {
            return null;
        }
        String low = m.toLowerCase(Locale.ROOT);
        boolean none = low.matches("(?s).*\\b(no real roots?|roots? (?:are|is) (?:not real|complex|imaginary)|complex roots?|imaginary roots?).*");
        boolean equal = low.matches("(?s).*((equal|repeated|coincident|same|double) roots?|roots? (?:are|be|is) (?:equal|the same|repeated|coincident)"
                + "|one (?:repeated )?(?:real )?root|exactly one (?:real )?(?:root|solution)).*");
        boolean distinct = !none && low.matches("(?s).*((distinct|different|unequal)(?: real)? roots?|two (?:distinct |different |unequal )?real roots?"
                + "|roots? (?:are|be) (?:distinct|different|unequal|real and (?:distinct|different|unequal))).*");
        boolean real = !distinct && !none && low.matches("(?s).*\\breal roots?.*");
        if (!equal && !distinct && !real && !none) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        out.append("Treat it as a quadratic in ").append(x).append(":\n");
        out.append("• a = ").append(a).append(", b = ").append(b).append(", c = ").append(c).append(".\n");
        out.append("• Discriminant D = b² − 4ac = ").append(disc).append(".\n");
        List<Double> aRoots = realRoots(aq);
        if (!aRoots.isEmpty()) {
            List<String> bad = new ArrayList<String>();
            for (double r : aRoots) {
                bad.add(param + " = " + num(r, aq, param));
            }
            out.append("• When ").append(String.join(" or ", bad)).append(", a = 0 and it isn't a quadratic, so that value is left out.\n");
        }
        List<Double> dr = realRoots(dq);
        if (equal) {
            List<String> eqs = new ArrayList<String>();
            for (double r : dr) {
                boolean bad = false;
                for (double z : aRoots) {
                    bad |= Math.abs(z - r) < 1e-9;
                }
                if (!bad) {
                    String val = num(r, dq, param);
                    eqs.add(param + (val.matches(".*\\d\\.\\d.*") ? " ≈ " : " = ") + val);
                }
            }
            out.append("Equal roots need D = 0. ");
            out.append(eqs.isEmpty() ? "D is never 0 for a real " + param + ", so the roots are never equal."
                    : "So the roots are equal when " + String.join(" or ", eqs) + ".");
            return out.toString();
        }
        boolean wantPositive = distinct || none ? !none : true;
        String want = none ? "D < 0" : distinct ? "D > 0" : "D ≥ 0";
        List<String> parts = new ArrayList<String>();
        double lo = Double.NEGATIVE_INFINITY;
        List<Double> cuts = new ArrayList<Double>(dr);
        cuts.add(Double.POSITIVE_INFINITY);
        for (double hi : cuts) {
            double mid = Double.isInfinite(lo) && Double.isInfinite(hi) ? 0 : Double.isInfinite(lo) ? hi - 1 : Double.isInfinite(hi) ? lo + 1 : (lo + hi) / 2;
            double val = evaluate(dq, mid);
            if (wantPositive ? val > 0 : val < 0) {
                parts.add((Double.isInfinite(lo) ? "(−∞" : (real ? "[" : "(") + num(lo, dq, param)) + ", "
                        + (Double.isInfinite(hi) ? "∞)" : num(hi, dq, param) + (real ? "]" : ")")));
            }
            lo = hi;
        }
        out.append("It needs ").append(want).append(". ");
        if (parts.isEmpty()) {
            out.append("That never happens for a real ").append(param).append('.');
        } else {
            out.append("So ").append(param).append(" ∈ ").append(String.join(" ∪ ", parts));
            List<String> excl = new ArrayList<String>();
            for (double z : aRoots) {
                excl.add(num(z, aq, param));
            }
            out.append(excl.isEmpty() ? "." : ", with " + param + " ≠ " + String.join(", ", excl) + ".");
        }
        if (!dr.isEmpty() && dr.stream().anyMatch(r -> num(r, dq, param).matches(".*\\d\\.\\d.*"))) {
            out.append("\n(The boundaries are roots of D = 0 with no exact simple form; they are given to 4 decimals.)");
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ a problem in lettered parts

    /** A fraction of two polynomials (in the parameter, or in α and β). */
    static final class Frac {
        final Poly num, den;

        Frac(Poly num, Poly den) {
            this.num = num;
            this.den = den;
        }

        static Frac of(Poly p) {
            return new Frac(p, Poly.constant(Q.ONE));
        }

        Frac add(Frac o) {
            return den.terms.equals(o.den.terms) ? new Frac(num.add(o.num), den) : new Frac(num.mul(o.den).add(o.num.mul(den)), den.mul(o.den));
        }

        Frac sub(Frac o) {
            return add(new Frac(o.num.neg(), o.den));
        }

        Frac mul(Frac o) {
            return new Frac(num.mul(o.num), den.mul(o.den));
        }

        Frac div(Frac o) {
            if (o.num.terms.isEmpty()) {
                throw new NotAlgebra("division by zero");
            }
            return new Frac(num.mul(o.den), den.mul(o.num));
        }

        Frac pow(int k) {
            return new Frac(num.pow(k), den.pow(k));
        }
    }

    /** Reads an expression in α (A) and β (B) as a fraction of polynomials. */
    private static final class FracParser {
        final String s;
        int at;

        FracParser(String s) {
            this.s = s;
        }

        Frac parse() {
            Frac f = expr();
            if (at != s.length()) {
                throw new NotAlgebra("unexpected " + s.charAt(at));
            }
            return f;
        }

        Frac expr() {
            Frac f = term();
            while (at < s.length() && (s.charAt(at) == '+' || s.charAt(at) == '-')) {
                char op = s.charAt(at++);
                Frac t = term();
                f = op == '+' ? f.add(t) : f.sub(t);
            }
            return f;
        }

        Frac term() {
            Frac f = factor();
            while (at < s.length()) {
                char c = s.charAt(at);
                if (c == '*') {
                    at++;
                    f = f.mul(factor());
                } else if (c == '/') {
                    at++;
                    f = f.div(factor());
                } else if (Character.isLetter(c) || c == '(') {
                    f = f.mul(factor());
                } else {
                    break;
                }
            }
            return f;
        }

        Frac factor() {
            if (at < s.length() && (s.charAt(at) == '-' || s.charAt(at) == '+')) {
                char c = s.charAt(at++);
                Frac f = factor();
                return c == '-' ? new Frac(f.num.neg(), f.den) : f;
            }
            Frac base;
            char c = at < s.length() ? s.charAt(at) : ' ';
            if (c == '(') {
                at++;
                base = expr();
                if (at >= s.length() || s.charAt(at) != ')') {
                    throw new NotAlgebra("bracket");
                }
                at++;
            } else if (Character.isDigit(c)) {
                int start = at;
                while (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) {
                    at++;
                }
                base = Frac.of(Poly.constant(Q.parse(s.substring(start, at))));
            } else if (c == 'A' || c == 'B') {
                at++;
                base = Frac.of(Poly.letter(c));
            } else {
                throw new NotAlgebra("not α/β");
            }
            if (at < s.length() && s.charAt(at) == '^') {
                at++;
                int start = at;
                while (at < s.length() && Character.isDigit(s.charAt(at))) {
                    at++;
                }
                if (start == at) {
                    throw new NotAlgebra("power");
                }
                base = base.pow(Integer.parseInt(s.substring(start, at)));
            }
            return base;
        }
    }

    /** LaTeX or typed maths about the roots, as A/B plain text: \frac{a}{b} → ((a)/(b)), α → A, β → B. */
    static String rootsText(String t) {
        String s = t.replace("\\alpha", "A").replace("\\beta", "B").replace("α", "A").replace("β", "B")
                .replaceAll("(?i)\\balpha\\b", "A").replaceAll("(?i)\\bbeta\\b", "B")
                .replace("\\left", "").replace("\\right", "").replace("\\,", "").replace("\\;", "").replace("\\displaystyle", "")
                .replace("\\cdot", "*").replace("\\times", "*").replace("²", "^2").replace("³", "^3");
        s = s.replaceAll("\\\\(boxed|text|mathrm|mbox)\\s*", ""); // \\boxed{…}: its content
        for (int guard = 0; guard < 20 && s.contains("\\frac"); guard++) {
            Matcher m = Pattern.compile("\\\\frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}").matcher(s);
            if (!m.find()) {
                break;
            }
            s = s.substring(0, m.start()) + "((" + m.group(1) + ")/(" + m.group(2) + "))" + s.substring(m.end());
        }
        return s.replace("{", "(").replace("}", ")").replaceAll("\\\\[a-zA-Z]+", "").replaceAll("\\s+", "");
    }

    /** A symmetric polynomial in A and B written in S = A + B and P = AB (the leading term taken off each time). */
    static Poly toSP(Poly f) {
        Poly rest = f, out = new Poly();
        Poly S = Poly.letter('A').add(Poly.letter('B')), P = Poly.letter('A').mul(Poly.letter('B'));
        for (int guard = 0; guard < 400 && !rest.terms.isEmpty(); guard++) {
            String best = null;
            int bi = -1, bj = -1;
            for (String k : rest.terms.keySet()) {
                TreeMap<Character, Integer> pw = Poly.powers(k);
                int i = pw.getOrDefault('A', 0), j = pw.getOrDefault('B', 0);
                if (i >= j && (i + j > bi + bj || i + j == bi + bj && i > bi)) {
                    best = k;
                    bi = i;
                    bj = j;
                }
            }
            if (best == null) {
                throw new NotAlgebra("not symmetric");
            }
            Q c = rest.terms.get(best);
            Poly step = Poly.constant(c).mul(S.pow(bi - bj)).mul(P.pow(bj));
            rest = rest.sub(step);
            TreeMap<Character, Integer> key = new TreeMap<Character, Integer>();
            if (bi - bj > 0) {
                key.put('S', bi - bj);
            }
            if (bj > 0) {
                key.put('P', bj);
            }
            out = out.add(Poly.single(Poly.key(key), c));
        }
        if (!rest.terms.isEmpty()) {
            throw new NotAlgebra("not symmetric");
        }
        return out;
    }

    /** A polynomial in S and P with S, P put in as fractions in the parameter. */
    static Frac substitute(Poly sp, Frac S, Frac P) {
        Frac out = Frac.of(new Poly());
        for (Map.Entry<String, Q> e : sp.terms.entrySet()) {
            TreeMap<Character, Integer> pw = Poly.powers(e.getKey());
            Frac t = Frac.of(Poly.constant(e.getValue()));
            t = t.mul(S.pow(pw.getOrDefault('S', 0))).mul(P.pow(pw.getOrDefault('P', 0)));
            out = out.add(t);
        }
        return out;
    }

    /** The real values of the parameter where the polynomial (in it) is zero. */
    static List<Double> zeros(Poly p, char param) {
        List<Q> c = p.univariate(param);
        return c == null ? new ArrayList<Double>() : realRoots(c);
    }

    static double value(Poly p, char param, double x) {
        List<Q> c = p.univariate(param);
        return c == null ? Double.NaN : evaluate(c, x);
    }

    static double value(Frac f, char param, double x) {
        return value(f.num, param, x) / value(f.den, param, x);
    }

    private static final Pattern PART = Pattern.compile("(?m)^\\s*\\(([a-z])\\)\\s*");

    /**
     * A pasted problem with parts "(a) … (b) …" about one quadratic with a parameter: each part worked out (the
     * discriminant; Vieta's α + β = −b/a, αβ = c/a for conditions on the roots), or said plainly when it can't be.
     */
    public static String parts(String message) {
        if (message == null || message.length() > 8000) {
            return null;
        }
        String m = message.replace("\r", "");
        Matcher pm = Pattern.compile("(?is)where\\s+\\\\?\\(?\\s*([a-z])\\s*\\\\?\\)?\\s+is\\s+a\\s+(?:real\\s+)?parameter|values? of\\s+\\\\?\\(?\\s*([a-z])\\b").matcher(m);
        Matcher eqm = Pattern.compile("([0-9a-zA-Z+\\-−*/^()\\s]{8,}=\\s*0)").matcher(m.replaceAll("\\\\[\\[\\]()]", " "));
        if (!pm.find()) {
            return null;
        }
        char param = (pm.group(1) != null ? pm.group(1) : pm.group(2)).charAt(0);
        Poly eq = null;
        char x = 0;
        while (eqm.find() && eq == null) {
            try {
                Poly p = read(eqm.group(1).replaceFirst("(?s)^.*?(?=[0-9(])", "").replaceFirst("=\\s*0$", ""));
                for (char v : p.letters()) {
                    if (v != param && p.degree(v) == 2 && p.letters().contains(param)) {
                        eq = p;
                        x = v;
                    }
                }
            } catch (NotAlgebra e) {
                // the next
            }
        }
        if (eq == null) {
            return null;
        }
        Poly a = eq.coefficient(x, 2), b = eq.coefficient(x, 1), c = eq.coefficient(x, 0);
        Poly disc = b.mul(b).sub(Poly.constant(Q.of(4)).mul(a).mul(c));
        Frac S = new Frac(b.neg(), a), P = new Frac(c, a), D = Frac.of(disc);
        // the parts, each with its own lines, up to the next part (or "Final Challenge")
        List<String[]> parts = new ArrayList<String[]>();
        Matcher part = PART.matcher(m);
        List<int[]> at = new ArrayList<int[]>();
        while (part.find()) {
            at.add(new int[] {part.start(), part.end(), part.group(1).charAt(0)});
        }
        for (int i = 0; i < at.size(); i++) {
            int end = i + 1 < at.size() ? at.get(i + 1)[0] : m.length();
            String body = m.substring(at.get(i)[1], end);
            int fin = body.toLowerCase(Locale.ROOT).indexOf("final challenge");
            parts.add(new String[] {String.valueOf((char) at.get(i)[2]), fin >= 0 ? body.substring(0, fin) : body});
        }
        int fc = m.toLowerCase(Locale.ROOT).indexOf("final challenge");
        if (fc >= 0) {
            String rest = m.substring(fc + "final challenge".length());
            int stop = rest.toLowerCase(Locale.ROOT).indexOf("instruction");
            parts.add(new String[] {"final", stop >= 0 ? rest.substring(0, stop) : rest});
        }
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        out.append("Quadratic in ").append(x).append(": a = ").append(a).append(", b = ").append(b).append(", c = ").append(c).append(".\n")
                .append("Discriminant D = b² − 4ac = ").append(disc).append(".\n")
                .append("Vieta: α + β = −b/a = (").append(b.neg()).append(")/(").append(a).append("), αβ = c/a = (")
                .append(c).append(")/(").append(a).append(").");
        int done = 0;
        for (String[] pt : parts) {
            String label = pt[0].equals("final") ? "Final challenge" : "(" + pt[0] + ")";
            String said;
            try {
                said = part(pt[1], param, a, D, S, P);
            } catch (NotAlgebra | ArithmeticException e) {
                said = null;
            }
            out.append("\n\n").append(label).append(' ');
            if (said != null) {
                out.append(said);
                done++;
            } else {
                out.append("Not worked out: I couldn't read this part as a condition on the roots. I won't guess it.");
            }
        }
        return done == 0 ? null : out.toString();
    }

    /** One part: equal roots, distinct real roots, a sign condition, or an equation in α and β. */
    static String part(String body, char p, Poly a, Frac D, Frac S, Frac P) {
        String low = body.toLowerCase(Locale.ROOT);
        String text = rootsText(body);
        boolean distinct = low.matches("(?s).*(distinct|different|unequal) (?:real )?roots?.*");
        // an equation in α and β: "α²/β + β²/α = 7"
        Matcher e = Pattern.compile("([0-9AB+\\-*/^()]*[AB][0-9AB+\\-*/^()]*)=(-?[0-9.]+)").matcher(text);
        if (e.find()) {
            String lhsText = e.group(1);
            // a bracket opened around the whole equation (\\boxed{…}, "(… = 13)") is left over in front: dropped
            while (lhsText.startsWith("(") && count(lhsText, '(') > count(lhsText, ')')) {
                lhsText = lhsText.substring(1);
            }
            Frac f = new FracParser(lhsText).parse();
            Poly num = toSP(f.num), den = toSP(f.den);
            Frac lhs = substitute(num, S, P).div(substitute(den, S, P));
            Q k = Q.parse(e.group(2));
            Poly zero = lhs.num.sub(Poly.constant(k).mul(lhs.den));
            List<String> good = new ArrayList<String>();
            List<String> notReal = new ArrayList<String>();
            for (double r : zeros(zero, p)) {
                if (Math.abs(value(a, p, r)) < 1e-9 || Math.abs(value(lhs.den, p, r)) < 1e-9 || Math.abs(value(P, p, r)) < 1e-12) {
                    continue; // not a quadratic there, or the expression isn't defined
                }
                double d = value(D, p, r);
                String v = shown(zero, p, r);
                if (d > 1e-9 || !distinct && d >= -1e-9) {
                    good.add(v);
                } else {
                    notReal.add(v);
                }
            }
            String expr = prettyRoots(lhsText) + " = " + k;
            StringBuilder b = new StringBuilder("Write " + expr + " with α + β and αβ, put in Vieta's values, and clear the fractions: a polynomial equation in "
                    + p + ". ");
            if (good.isEmpty()) {
                b.append(notReal.isEmpty() ? "It has no admissible real solution." : "Its real solutions (" + String.join(", ", notReal)
                        + ") make the discriminant negative, so the roots wouldn't be real" + (distinct ? " and distinct." : "."));
            } else {
                b.append("Keeping only values where a ≠ 0, αβ ≠ 0 and the roots are real" + (distinct ? " and distinct" : "") + ": ")
                        .append(p).append(' ').append(String.join(" or ", good).replaceFirst("^", good.get(0).startsWith("≈") ? "" : "= ")).append('.');
                if (!notReal.isEmpty()) {
                    b.append(" (").append(String.join(", ", notReal)).append(" also solve it, but there the roots aren't real.)");
                }
            }
            return b.toString();
        }
        // conditions on the roots as signs
        List<Frac> conds = new ArrayList<Frac>();
        List<String> said = new ArrayList<String>();
        boolean strictD = true;
        if (low.matches("(?s).*\\b(equal|repeated|coincident) roots?.*|.*roots? (?:are|be) equal.*")) {
            List<String> eq = new ArrayList<String>();
            for (double r : zeros(D.num, p)) {
                if (Math.abs(value(a, p, r)) > 1e-9) {
                    eq.add(shown(D.num, p, r));
                }
            }
            return "Equal roots need D = 0: " + p + " " + String.join(" or ", eq).replaceFirst("^(?!≈)", "= ") + ".";
        }
        boolean negative = low.matches("(?s).*\\b(both|two) (?:roots? )?(?:are )?negative.*|.*negative roots?.*");
        boolean positive = low.matches("(?s).*\\b(both|two) (?:roots? )?(?:are )?positive.*|.*positive roots?.*");
        Matcher gap = Pattern.compile("(?s)difference between the roots(?: is)? (greater|more|less) than (\\d+(?:\\.\\d+)?)").matcher(low);
        if (distinct || negative || positive || low.matches("(?s).*\\breal roots?.*")) {
            conds.add(D);
            strictD = distinct || positive && gap.find(0);
            said.add(strictD ? "D > 0" : "D ≥ 0");
        }
        if (negative) {
            conds.add(new Frac(S.num.neg(), S.den));
            conds.add(P);
            said.add("α + β < 0");
            said.add("αβ > 0");
        }
        if (positive) {
            conds.add(S);
            conds.add(P);
            said.add("α + β > 0");
            said.add("αβ > 0");
        }
        if (gap.find(0)) {
            Q g = Q.parse(gap.group(2));
            Frac sq = S.mul(S).sub(Frac.of(Poly.constant(Q.of(4))).mul(P)).sub(Frac.of(Poly.constant(g.mul(g))));
            conds.add(gap.group(1).equals("less") ? new Frac(sq.num.neg(), sq.den) : sq);
            said.add("(α − β)² = (α + β)² − 4αβ " + (gap.group(1).equals("less") ? "<" : ">") + " " + g.mul(g));
        }
        if (conds.isEmpty()) {
            return null;
        }
        // every boundary: where any condition's top or bottom is zero, or a = 0
        TreeSet<Double> cuts = new TreeSet<Double>(zeros(a, p));
        for (Frac f : conds) {
            cuts.addAll(zeros(f.num, p));
            cuts.addAll(zeros(f.den, p));
        }
        List<Double> cs = new ArrayList<Double>(cuts);
        List<String> pieces = new ArrayList<String>();
        double lo = Double.NEGATIVE_INFINITY;
        cs.add(Double.POSITIVE_INFINITY);
        Double openLo = null;
        String openText = null;
        for (double hi : cs) {
            double mid = Double.isInfinite(lo) ? (Double.isInfinite(hi) ? 0 : hi - 1) : Double.isInfinite(hi) ? lo + 1 : (lo + hi) / 2;
            boolean ok = true;
            for (int i = 0; i < conds.size(); i++) {
                double v = value(conds.get(i), p, mid);
                ok &= i == 0 && !strictD && conds.get(0) == D ? v >= 0 : v > 0;
            }
            if (ok) {
                if (openLo == null) {
                    openLo = lo;
                    openText = Double.isInfinite(lo) ? "(−∞" : "(" + shownPlain(lo, p, a, conds);
                }
            } else if (openLo != null) {
                pieces.add(openText + ", " + shownPlain(lo, p, a, conds) + ")");
                openLo = null;
            }
            if (openLo != null && !Double.isInfinite(hi) && Math.abs(value(a, p, hi)) < 1e-9) {
                pieces.add(openText + ", " + shownPlain(hi, p, a, conds) + ")"); // a = 0 there: the interval breaks
                openLo = null;
            }
            lo = hi;
        }
        if (openLo != null) {
            pieces.add(openText + ", ∞)");
        }
        String needs = "It needs " + String.join(", ", said) + ".";
        return needs + (pieces.isEmpty() ? " No real " + p + " meets all of them." : " So " + p + " ∈ " + String.join(" ∪ ", pieces) + ".");
    }

    static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            n += s.charAt(i) == c ? 1 : 0;
        }
        return n;
    }

    /** A boundary shown exactly when it is a fraction, else to 4 decimals. */
    static String shownPlain(double r, char p, Poly a, List<Frac> conds) {
        List<Poly> polys = new ArrayList<Poly>();
        polys.add(a);
        for (Frac f : conds) {
            polys.add(f.num);
            polys.add(f.den);
        }
        for (Poly q : polys) {
            List<Q> c = q.univariate(p);
            if (c != null && trim(c).size() > 1) {
                for (Root x : roots(c, p, false).roots) {
                    if (x.real() && Math.abs(x.re - r) < 1e-9 && x.exact != null && !x.exact.contains("≈")) {
                        return x.exact;
                    }
                }
            }
        }
        return dec(r);
    }

    static String shown(Poly zero, char p, double r) {
        String v = num(r, zero.univariate(p), p);
        return v.matches(".*\\d\\.\\d.*") ? "≈ " + v : v;
    }

    static String prettyRoots(String s) {
        String t = s;
        for (int i = 0; i < 4; i++) {
            t = t.replaceAll("\\(\\(([^()]*)\\)/\\(([^()]*)\\)\\)", "($1)/($2)").replaceAll("\\(([0-9]+|[AB](?:\\^[0-9]+)?)\\)", "$1"); // one term needs no brackets; "(AB)" keeps them
        }
        return pretty(t.replace("A", "\u03b1").replace("B", "\u03b2")).replace("\u00b7", "");
    }
}
