package ai.ibiko.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maths written in LaTeX by a model (\( \frac{\alpha^2}{\beta} \neq 0 \), \[ m\in(-\infty,3.32) \]) turned into
 * readable maths on the phone: Greek letters and symbols as themselves (α ≠ ∞ ∪ ≈), powers and indices raised
 * (m², x₁), fractions as a/b, roots as √. Only the maths between delimiters is touched; code stays as written. Pure
 * Java.
 */
public final class MathText {
    private MathText() {
    }

    private static final Pattern MATH = Pattern.compile("(?s)\\\\\\((.+?)\\\\\\)|\\\\\\[(.+?)\\\\\\]|\\$\\$(.+?)\\$\\$|(?<![\\\\$\\w])\\$([^$\\n]+?)\\$(?![\\w$])");
    private static final Pattern CODE = Pattern.compile("(?s)```.*?```|`[^`\\n]*`");

    /** The text with its LaTeX maths made readable; everything else as it was. */
    public static String readable(String text) {
        if (text == null || text.indexOf('\\') < 0 && text.indexOf('$') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        Matcher code = CODE.matcher(text);
        int at = 0;
        while (code.find()) {
            out.append(outsideCode(text.substring(at, code.start())));
            out.append(code.group());
            at = code.end();
        }
        out.append(outsideCode(text.substring(at)));
        return out.toString();
    }

    private static String outsideCode(String s) {
        Matcher m = MATH.matcher(s);
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (m.find()) {
            String inline = m.group(1) != null ? m.group(1) : m.group(4);
            String display = m.group(2) != null ? m.group(2) : m.group(3);
            if (m.group(4) != null && !looksLikeMath(m.group(4))) {
                continue; // "$5 and $10": money, not maths
            }
            out.append(s, at, m.start());
            if (display != null) {
                String before = out.toString();
                if (!before.isEmpty() && !before.endsWith("\n")) {
                    out.append('\n');
                }
                out.append(convert(display).trim()).append('\n');
                int next = m.end();
                while (next < s.length() && s.charAt(next) == '\n') {
                    next++; // the display's own line ends here
                }
                at = next;
                continue;
            }
            out.append(convert(inline).trim());
            at = m.end();
        }
        out.append(s.substring(at));
        return out.toString();
    }

    private static boolean looksLikeMath(String s) {
        return s.matches("(?s).*(\\\\[a-zA-Z]+|\\^|_|=|[a-zA-Z]\\s*[+\\-*/]\\s*[a-zA-Z0-9]).*") && !s.matches("\\s*\\d[\\d,.]*\\s*");
    }

    private static final Map<String, String> SYMBOLS = new HashMap<String, String>();

    static {
        String[] pairs = {
            "alpha", "α", "beta", "β", "gamma", "γ", "Gamma", "Γ", "delta", "δ", "Delta", "Δ", "epsilon", "ε", "varepsilon", "ε",
            "zeta", "ζ", "eta", "η", "theta", "θ", "Theta", "Θ", "iota", "ι", "kappa", "κ", "lambda", "λ", "Lambda", "Λ",
            "mu", "μ", "nu", "ν", "xi", "ξ", "pi", "π", "Pi", "Π", "rho", "ρ", "sigma", "σ", "Sigma", "Σ", "tau", "τ",
            "phi", "φ", "varphi", "φ", "Phi", "Φ", "chi", "χ", "psi", "ψ", "Psi", "Ψ", "omega", "ω", "Omega", "Ω",
            "infty", "∞", "neq", "≠", "ne", "≠", "leq", "≤", "le", "≤", "geq", "≥", "ge", "≥", "approx", "≈", "sim", "∼",
            "equiv", "≡", "pm", "±", "mp", "∓", "times", "×", "cdot", "·", "div", "÷", "cup", "∪", "cap", "∩", "in", "∈",
            "notin", "∉", "subset", "⊂", "subseteq", "⊆", "supset", "⊃", "to", "→", "rightarrow", "→", "leftarrow", "←",
            "Rightarrow", "⇒", "implies", "⇒", "Leftarrow", "⇐", "iff", "⇔", "Leftrightarrow", "⇔", "mapsto", "↦",
            "sum", "Σ", "prod", "∏", "int", "∫", "oint", "∮", "partial", "∂", "nabla", "∇", "forall", "∀", "exists", "∃",
            "emptyset", "∅", "varnothing", "∅", "angle", "∠", "perp", "⊥", "parallel", "∥", "circ", "∘", "degree", "°",
            "ldots", "…", "cdots", "⋯", "dots", "…", "therefore", "∴", "because", "∵", "mathbb{R}", "ℝ", "mathbb{N}", "ℕ",
            "mathbb{Z}", "ℤ", "mathbb{Q}", "ℚ", "mathbb{C}", "ℂ", "lt", "<", "gt", ">", "neg", "¬", "land", "∧", "lor", "∨",
            "propto", "∝", "ll", "≪", "gg", "≫", "star", "⋆", "prime", "′", "quad", "  ", "qquad", "    ",
            "sin", "sin", "cos", "cos", "tan", "tan", "log", "log", "ln", "ln", "exp", "exp", "lim", "lim", "max", "max",
            "min", "min", "det", "det",
        };
        for (int i = 0; i < pairs.length; i += 2) {
            SYMBOLS.put(pairs[i], pairs[i + 1]);
        }
    }

    private static final String SUP_FROM = "0123456789+-=()ni", SUP_TO = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ";
    private static final String SUB_FROM = "0123456789+-=()aeoxijkmn", SUB_TO = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₒₓᵢⱼₖₘₙ";

    /** One piece of LaTeX maths as readable text. */
    public static String convert(String tex) {
        String s = tex;
        // sizing and spacing commands say nothing on a phone
        s = s.replaceAll("\\\\(displaystyle|textstyle|scriptstyle|left|right|bigl|bigr|Bigl|Bigr|biggl|biggr|big|Big|bigg|Bigg|limits|nolimits)(?![a-zA-Z])", "");
        s = s.replaceAll("\\\\[,;:]", " ").replaceAll("\\\\!", "").replace("\\ ", " ").replace("~", " ");
        s = s.replace("\\{", "{\u0000").replace("\\}", "\u0001}"); // literal braces, kept
        s = s.replace("\\%", "%").replace("\\&", "&").replace("\\#", "#").replace("\\_", "_\u0002").replace("\\$", "$");
        s = s.replace("\\\\", "\n");
        // wrappers whose content is shown as it is
        for (int guard = 0; guard < 50; guard++) {
            String next = unwrap(s);
            if (next.equals(s)) {
                break;
            }
            s = next;
        }
        // the brace-less short form: \frac32 is \frac{3}{2}
        s = s.replaceAll("\\\\(d|t|c)?frac\\s*([0-9a-zA-Z])\\s*([0-9a-zA-Z])", "\\\\frac{$2}{$3}");
        // fractions and roots, innermost first
        for (int guard = 0; guard < 50; guard++) {
            String next = fracs(s);
            if (next.equals(s)) {
                break;
            }
            s = next;
        }
        // named symbols
        Matcher cmd = Pattern.compile("\\\\(mathbb\\{[A-Z]\\}|[a-zA-Z]+)").matcher(s);
        StringBuffer b = new StringBuffer();
        while (cmd.find()) {
            String name = cmd.group(1);
            String sym = SYMBOLS.get(name);
            String rep = sym != null ? (name.matches("sin|cos|tan|log|ln|exp|lim|max|min|det") ? sym + " " : sym)
                    : name.startsWith("mathbb") ? name.substring(7, 8) : name;
            cmd.appendReplacement(b, Matcher.quoteReplacement(rep));
        }
        cmd.appendTail(b);
        s = b.toString();
        s = scripts(s, '^', SUP_FROM, SUP_TO);
        s = scripts(s, '_', SUB_FROM, SUB_TO);
        // grouping braces go; literal ones come back
        s = s.replace("{\u0000", "\u0003").replace("\u0001}", "\u0004").replace("{", "").replace("}", "")
                .replace('\u0003', '{').replace('\u0004', '}').replace("_\u0002", "_");
        return s.replaceAll("[ \\t]{2,}", " ").replaceAll(" ([,.;)])", "$1").replaceAll("\\( ", "(");
    }

    /** \boxed{x}, \text{x}, \mathrm{x}, \mathbf{x}, \operatorname{x}, \overline{x}: shown as x. */
    private static String unwrap(String s) {
        Matcher m = Pattern.compile("\\\\(boxed|text|textrm|textbf|textit|mathrm|mathbf|mathit|mathsf|mathcal|operatorname|overline|"
                + "underline|hat|bar|vec|tilde|dot|ddot|widehat|widetilde|mbox|fbox)\\s*\\{").matcher(s);
        if (!m.find()) {
            return s;
        }
        int open = m.end() - 1, close = matching(s, open);
        if (close < 0) {
            return s.substring(0, m.start()) + s.substring(m.end());
        }
        String inner = s.substring(open + 1, close);
        String kind = m.group(1);
        String shown = kind.equals("vec") ? inner + "⃗" : kind.equals("hat") || kind.equals("widehat") ? inner + "̂"
                : kind.equals("bar") || kind.equals("overline") ? inner + "̅" : inner;
        return s.substring(0, m.start()) + shown + s.substring(close + 1);
    }

    /** One \frac{a}{b} (or \dfrac, \tfrac) as a/b, or one \sqrt as √: the first whose parts hold no other. */
    private static String fracs(String s) {
        Matcher m = Pattern.compile("\\\\(d|t|c)?frac\\s*\\{").matcher(s);
        while (m.find()) {
            int o1 = m.end() - 1, c1 = matching(s, o1);
            if (c1 < 0 || c1 + 1 >= s.length() || s.charAt(c1 + 1) != '{') {
                continue;
            }
            int c2 = matching(s, c1 + 1);
            if (c2 < 0) {
                continue;
            }
            String num = s.substring(o1 + 1, c1), den = s.substring(c1 + 2, c2);
            if (num.contains("\\frac") || den.contains("\\frac") || num.contains("\\dfrac") || den.contains("\\dfrac")) {
                continue; // an inner fraction first
            }
            return s.substring(0, m.start()) + group(num) + "/" + group(den) + s.substring(c2 + 1);
        }
        Matcher r = Pattern.compile("\\\\sqrt\\s*(\\[([^\\]]+)\\])?\\s*\\{").matcher(s);
        if (r.find()) {
            int o = r.end() - 1, c = matching(s, o);
            if (c > 0) {
                String inner = s.substring(o + 1, c);
                String index = r.group(2) == null ? "" : scripts("^{" + r.group(2) + "}", '^', SUP_FROM, SUP_TO);
                return s.substring(0, r.start()) + index + "√" + group(inner) + s.substring(c + 1);
            }
        }
        return s;
    }

    /** A part of a fraction in brackets when it is more than one term ("m^2-4m+1" → "(m^2-4m+1)"). */
    private static String group(String part) {
        String p = part.trim();
        // one number, letter or symbol (with its power or index): no brackets needed
        boolean simple = p.matches("(\\d+(\\.\\d+)?|[a-zA-Z]|\\\\[a-zA-Z]+)([\\^_](\\{[^{}]*\\}|[0-9a-zA-Z]))*");
        return simple ? p : "(" + p + ")";
    }

    private static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** x^2 → x², x^{10} → x¹⁰, a_1 → a₁; a script with no small form stays as ^(…). */
    private static String scripts(String s, char mark, String from, String to) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != mark || i + 1 >= s.length() || (mark == '_' && i + 1 < s.length() && s.charAt(i + 1) == '\u0002')) {
                out.append(c);
                continue;
            }
            String body;
            int end;
            if (s.charAt(i + 1) == '{') {
                int close = matching(s, i + 1);
                if (close < 0) {
                    out.append(c);
                    continue;
                }
                body = s.substring(i + 2, close);
                end = close;
            } else {
                body = String.valueOf(s.charAt(i + 1));
                end = i + 1;
            }
            StringBuilder small = new StringBuilder();
            boolean ok = !body.isEmpty();
            for (char ch : body.toCharArray()) {
                int k = from.indexOf(ch);
                if (k < 0) {
                    ok = false;
                    break;
                }
                small.append(to.charAt(k));
            }
            out.append(ok ? small : (body.length() == 1 ? mark + body : mark + "(" + body + ")"));
            i = end;
        }
        return out.toString();
    }
}
