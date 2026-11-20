package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-world problems turned into their formulas and worked out, the way a person thinks them through: what is
 * known, which formula joins it to what is asked, the numbers put in, the answer, and what it means. Money (break-even,
 * the units for a target profit, margin and markup, simple and compound interest, loan repayments), travel (speed,
 * distance, time), percentage change, and growth or depreciation over time. Pure Java.
 */
public final class WordProblems {
    private WordProblems() {
    }

    private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|m|million|thousand|bn|billion)?\\b";
    private static final String MONEY = "([₦$£€]|NGN|USD|N(?=\\d))?\\s*" + NUM;

    /** A number as written: commas, k, million. */
    static double number(String digits, String scale) {
        double v = Double.parseDouble(digits.replace(",", ""));
        if (scale == null) {
            return v;
        }
        switch (scale.toLowerCase(Locale.ROOT)) {
            case "k": case "thousand": return v * 1e3;
            case "m": case "million": return v * 1e6;
            case "bn": case "billion": return v * 1e9;
            default: return v;
        }
    }

    /** The currency the problem uses, or "". */
    static String currency(String m) {
        Matcher c = Pattern.compile("[₦$£€]|\\bNGN\\b|\\bnaira\\b|\\bdollars?\\b|\\bpounds?\\b|\\beuros?\\b", Pattern.CASE_INSENSITIVE).matcher(m);
        if (!c.find()) {
            return "";
        }
        String s = c.group().toLowerCase(Locale.ROOT);
        return s.startsWith("naira") || s.equals("ngn") || s.equals("₦") ? "₦" : s.startsWith("dollar") || s.equals("$") ? "$"
                : s.startsWith("pound") || s.equals("£") ? "£" : s.startsWith("euro") || s.equals("€") ? "€" : c.group();
    }

    static String money(String cur, double v) {
        boolean whole = Math.abs(v - Math.rint(v)) < 0.005;
        String n = whole ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%,.2f", v);
        return (v < 0 ? "−" : "") + cur + n.replace("-", "");
    }

    static String plain(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%,.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** A working value to 4 decimals (the steps stay checkable by hand). */
    static String four(double v) {
        return String.format(Locale.US, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** The first amount after one of the words, or NaN. */
    static double after(String m, String words) {
        Matcher x = Pattern.compile("(?i)\\b(?:" + words + ")\\b[^\\d₦$£€.]{0,30}?" + MONEY).matcher(m);
        return x.find() ? number(x.group(2), x.group(3)) : Double.NaN;
    }

    /** Ibiko's working for a real-world problem; null when it isn't one it knows. */
    public static String reply(String message) {
        if (message == null || message.length() > 2000) {
            return null;
        }
        String m = message.replace('−', '-');
        String low = m.toLowerCase(Locale.ROOT);
        try {
            if (low.matches("(?s).*\\b(break[- ]?even|how many .{0,60}(?:to|before|until) (?:i |we |you )?(?:make|get|earn|cover|reach|start making)|"
                    + "how many .{0,40}sell)\\b.*")) {
                String s = breakEven(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.matches("(?s).*\\b(loan|mortgage|borrow)\\b.*") && low.matches("(?s).*\\b(monthly|repayment|installment|instalment|pay (?:back|each))\\b.*")) {
                String s = loan(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.matches("(?s).*\\b(simple interest|compound|interest)\\b.*")) {
                String s = interest(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.matches("(?s).*\\b(double|triple)\\b.*") && low.contains("%")) {
                String s = doubling(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.matches("(?s).*\\b(depreciat\\w*|appreciat\\w*|grows?|growth|increases? by|loses?|decreases? by)\\b.*") && low.contains("%")
                    && low.matches("(?s).*\\b(year|years|month|months)\\b.*")) {
                String s = growth(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.matches("(?s).*\\b(km|kilomet\\w*|miles?|met(?:er|re)s?|m/s|km/h|kmph|mph)\\b.*")) {
                String s = travel(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.matches("(?s).*\\b(margin|markup|mark-up|profit)\\b.*") && low.matches("(?s).*\\b(cost|buy|bought)\\b.*")
                    && low.matches("(?s).*\\b(sell|sold|price)\\b.*")) {
                String s = margin(m, low);
                if (s != null) {
                    return s;
                }
            }
            if (low.contains("%") || low.contains("percent")) {
                String s = percent(m, low);
                if (s != null) {
                    return s;
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    // ------------------------------------------------------------------ money

    static String breakEven(String m, String low) {
        String cur = currency(m);
        double cost = after(m, "buy (?:it|them|each|one|a \\w+)?|buys?|bought|costs? (?:me|us)?|purchase|cost price");
        double price = after(m, "sell (?:it|them|each|one|a \\w+)?|sells?|sold|selling price|price");
        if (Double.isNaN(cost) || Double.isNaN(price)) {
            return null;
        }
        double fixed = 0;
        List<String> fixedSaid = new ArrayList<String>();
        Matcher f = Pattern.compile("(?i)\\b(rent|fixed costs?|overheads?|salar(?:y|ies)|wages?|expenses|transport|fuel|levy|bills?)\\b[^\\d₦$£€]{0,25}?" + MONEY).matcher(m);
        while (f.find()) {
            double v = number(f.group(3), f.group(4));
            fixed += v;
            fixedSaid.add(f.group(1).toLowerCase(Locale.ROOT) + " " + money(cur, v));
        }
        double target = 0;
        Matcher t = Pattern.compile("(?i)\\b(?:make|earn|get|reach)\\s+(?:a\\s+)?(?:profit\\s+of\\s+)?" + MONEY + "\\s*(?:profit)?").matcher(m);
        if (t.find() && low.contains("profit")) {
            target = number(t.group(2), t.group(3));
        }
        double perUnit = price - cost;
        StringBuilder b = new StringBuilder();
        b.append("What's known: each costs ").append(money(cur, cost)).append(" and sells for ").append(money(cur, price));
        if (!fixedSaid.isEmpty()) {
            b.append("; fixed costs ").append(String.join(" + ", fixedSaid)).append(fixedSaid.size() > 1 ? " = " + money(cur, fixed) : "");
        }
        b.append(".\n");
        b.append("• Profit on each one (contribution): ").append(money(cur, price)).append(" − ").append(money(cur, cost)).append(" = ")
                .append(money(cur, perUnit)).append(".\n");
        if (perUnit <= 0) {
            return b.append("Each one sells for no more than it costs, so selling more only loses more: the price has to rise above ")
                    .append(money(cur, cost)).append(" (or the cost fall) before it can ever break even.").toString();
        }
        double need = fixed + target;
        double units = need / perUnit;
        long whole = (long) Math.ceil(units - 1e-9);
        b.append("• Formula: units = (fixed costs").append(target > 0 ? " + target profit" : "").append(") ÷ profit per unit = ")
                .append(money(cur, need)).append(" ÷ ").append(money(cur, perUnit)).append(" = ").append(plain(units)).append(".\n");
        if (fixed == 0 && target == 0) {
            return b.append("With no fixed costs, every sale is already ").append(money(cur, perUnit)).append(" profit: there's nothing to break even on.").toString();
        }
        b.append("So ").append(target > 0 ? "to make " + money(cur, target) + " profit" : "to break even").append(", sell ").append(whole)
                .append(units != whole ? " (you can't sell part of one, so round up)" : "").append(". Revenue then: ").append(money(cur, whole * price))
                .append(", costs: ").append(money(cur, whole * cost + fixed)).append(".");
        return b.toString();
    }

    static String margin(String m, String low) {
        String cur = currency(m);
        double cost = after(m, "buy (?:it|them|each|one|a \\w+)?|buys?|bought|costs?|cost price");
        double price = after(m, "sell (?:it|them|each|one|a \\w+)?|sells?|sold|selling price|price");
        if (Double.isNaN(cost) || Double.isNaN(price) || price <= 0 || cost <= 0) {
            return null;
        }
        double profit = price - cost;
        return "What's known: cost " + money(cur, cost) + ", selling price " + money(cur, price) + ".\n"
                + "• Profit = price − cost = " + money(cur, profit) + ".\n"
                + "• Margin = profit ÷ price = " + plain(profit / price * 100) + "%.\n"
                + "• Markup = profit ÷ cost = " + plain(profit / cost * 100) + "%.\n"
                + "So each one makes " + money(cur, profit) + ": a " + plain(profit / price * 100) + "% margin on the price, a "
                + plain(profit / cost * 100) + "% markup on the cost.";
    }

    /** The rate (% per year), the time in years, and how often it compounds. */
    private static double rate(String m) {
        Matcher r = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%").matcher(m);
        return r.find() ? Double.parseDouble(r.group(1)) : Double.NaN;
    }

    private static double years(String low) {
        Matcher y = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(years?|yrs?|months?)\\b").matcher(low);
        if (!y.find()) {
            return Double.NaN;
        }
        double v = Double.parseDouble(y.group(1));
        return y.group(2).startsWith("month") ? v / 12 : v;
    }

    private static double principal(String m) {
        Matcher p = Pattern.compile(MONEY).matcher(m.replaceAll("\\d+(?:\\.\\d+)?\\s*%", " ").replaceAll("(?i)\\d+(?:\\.\\d+)?\\s*(?:years?|yrs?|months?)", " "));
        double best = Double.NaN;
        while (p.find()) {
            double v = number(p.group(2), p.group(3));
            if (Double.isNaN(best) || v > best) {
                best = v;
            }
        }
        return best;
    }

    static String interest(String m, String low) {
        String cur = currency(m);
        double P = principal(m), r = rate(m), t = years(low);
        if (Double.isNaN(P) || Double.isNaN(r) || Double.isNaN(t)) {
            return null;
        }
        boolean monthlyRate = low.matches("(?s).*%\\s*(?:per|a|each)\\s+month.*");
        double annual = monthlyRate ? r * 12 : r;
        if (low.contains("compound")) {
            int n = low.matches("(?s).*compound\\w*\\s+(?:monthly|every month).*") ? 12 : low.matches("(?s).*compound\\w*\\s+(?:quarterly).*") ? 4
                    : low.matches("(?s).*compound\\w*\\s+(?:daily).*") ? 365 : low.matches("(?s).*compound\\w*\\s+(?:half-yearly|semi-annually).*") ? 2 : 1;
            double A = P * Math.pow(1 + annual / 100 / n, n * t);
            return "What's known: principal P = " + money(cur, P) + ", rate " + plain(annual) + "% a year, compounded " + (n == 1 ? "yearly" : n + " times a year")
                    + ", for " + plain(t) + (t == 1 ? " year" : " years") + ".\n"
                    + "• Formula: A = P(1 + r/n)^(nt).\n"
                    + "• A = " + money(cur, P) + " × (1 + " + plain(annual / 100) + "/" + n + ")^" + plain(n * t) + " = " + money(cur, A) + ".\n"
                    + "So it grows to " + money(cur, A) + ": " + money(cur, A - P) + " interest. (Simple interest would give "
                    + money(cur, P * annual / 100 * t) + ".)";
        }
        double I = P * annual / 100 * t;
        return "What's known: principal P = " + money(cur, P) + ", rate R = " + plain(annual) + "% a year, time T = " + plain(t) + (t == 1 ? " year" : " years") + ".\n"
                + "• Formula: simple interest I = P × R × T ÷ 100.\n"
                + "• I = " + money(cur, P) + " × " + plain(annual) + " × " + plain(t) + " ÷ 100 = " + money(cur, I) + ".\n"
                + "So the interest is " + money(cur, I) + ", and the total is " + money(cur, P + I) + ".";
    }

    static String loan(String m, String low) {
        String cur = currency(m);
        double P = principal(m), r = rate(m), t = years(low);
        if (Double.isNaN(P) || Double.isNaN(r) || Double.isNaN(t) || t <= 0) {
            return null;
        }
        boolean monthlyRate = low.matches("(?s).*%\\s*(?:per|a|each)\\s+month.*");
        double i = monthlyRate ? r / 100 : r / 100 / 12;
        int n = (int) Math.round(t * 12);
        double M = i == 0 ? P / n : P * i * Math.pow(1 + i, n) / (Math.pow(1 + i, n) - 1);
        return "What's known: loan P = " + money(cur, P) + ", " + plain(monthlyRate ? r : r) + "% " + (monthlyRate ? "a month" : "a year")
                + " (" + plain(i * 100) + "% a month), over " + n + " months.\n"
                + "• Formula (equal monthly repayments): M = P·i(1 + i)ⁿ ÷ ((1 + i)ⁿ − 1).\n"
                + "• M = " + money(cur, P) + " × " + four(i) + " × " + four(Math.pow(1 + i, n)) + " ÷ " + four(Math.pow(1 + i, n) - 1) + " = " + money(cur, M) + ".\n"
                + "So the repayment is " + money(cur, M) + " a month; in all " + money(cur, M * n) + ", of which " + money(cur, M * n - P) + " is interest.";
    }

    static String doubling(String m, String low) {
        double r = rate(m);
        if (Double.isNaN(r) || r <= 0) {
            return null;
        }
        double k = low.contains("triple") ? 3 : 2;
        double years = Math.log(k) / Math.log(1 + r / 100);
        return "What's known: it grows " + plain(r) + "% a year, and the question is when it is " + (k == 2 ? "double" : "triple") + ".\n"
                + "• Formula: (1 + r)ᵗ = " + plain(k) + ", so t = ln " + plain(k) + " ÷ ln(1 + r).\n"
                + "• t = " + four(Math.log(k)) + " ÷ " + four(Math.log(1 + r / 100)) + " = " + plain(years) + ".\n"
                + "So it takes about " + plain(years) + " years (" + (int) Math.ceil(years - 1e-9) + " whole years)."
                + (k == 2 ? " (The rule of 72 gives about " + plain(72 / r) + ".)" : "");
    }

    static String growth(String m, String low) {
        String cur = currency(m);
        double P = principal(m), r = rate(m), t = years(low);
        if (Double.isNaN(P) || Double.isNaN(r) || Double.isNaN(t)) {
            return null;
        }
        boolean down = low.matches("(?s).*\\b(depreciat\\w*|loses?|decreases? by|falls?|drops?)\\b.*");
        double factor = down ? 1 - r / 100 : 1 + r / 100;
        double V = P * Math.pow(factor, t);
        return "What's known: starting value " + money(cur, P) + ", " + (down ? "losing " : "growing ") + plain(r) + "% each year, for " + plain(t)
                + (t == 1 ? " year" : " years") + ".\n"
                + "• Formula: V = P(1 " + (down ? "−" : "+") + " r)ᵗ (each year's change is on the year before's value).\n"
                + "• V = " + money(cur, P) + " × " + plain(factor) + "^" + plain(t) + " = " + money(cur, V) + ".\n"
                + "So after " + plain(t) + (t == 1 ? " year" : " years") + " it is worth " + money(cur, V) + " (" + (down ? "down " : "up ")
                + money(cur, Math.abs(V - P)) + ").";
    }

    // ------------------------------------------------------------------ travel

    static String travel(String m, String low) {
        Matcher d = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(km|kilomet\\w*|miles?|mi|m|met(?:er|re)s?)\\b(?!\\s*/|\\s*per)").matcher(low);
        Matcher s = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(km/h|kmph|km per hour|kilomet\\w* per hour|mph|miles per hour|m/s|met(?:er|re)s per second)").matcher(low);
        Matcher t = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h\\b|minutes?|mins?|seconds?|secs?)").matcher(low);
        boolean hasD = d.find(), hasS = s.find(), hasT = t.find();
        if (hasS && hasD && d.start() == s.start()) {
            hasD = d.find(); // the number belonged to the speed
        }
        String speedUnit = hasS ? (s.group(2).contains("m/s") || s.group(2).contains("second") ? "m/s" : s.group(2).contains("mi") || s.group(2).equals("mph") ? "mph" : "km/h") : "km/h";
        double hours = hasT ? Double.parseDouble(t.group(1)) / (t.group(2).startsWith("min") ? 60 : t.group(2).startsWith("sec") ? 3600 : 1) : Double.NaN;
        if (hasD && hasT && !hasS) {
            double dist = Double.parseDouble(d.group(1));
            String du = d.group(2).startsWith("mi") ? "miles" : d.group(2).startsWith("k") ? "km" : "m";
            double v = dist / hours;
            return "What's known: distance " + plain(dist) + " " + du + ", time " + t.group(1) + " " + t.group(2) + " (" + plain(hours) + " h).\n"
                    + "• Formula: speed = distance ÷ time.\n"
                    + "• " + plain(dist) + " ÷ " + plain(hours) + " = " + plain(v) + ".\n"
                    + "So the average speed is " + plain(v) + (du.equals("km") ? " km/h" : du.equals("miles") ? " mph" : " m per hour") + ".";
        }
        if (hasS && hasT && !hasD) {
            double v = Double.parseDouble(s.group(1));
            double h = speedUnit.equals("m/s") ? hours * 3600 : hours;
            double dist = v * h;
            return "What's known: speed " + plain(v) + " " + speedUnit + ", time " + t.group(1) + " " + t.group(2) + ".\n"
                    + "• Formula: distance = speed × time.\n"
                    + "• " + plain(v) + " × " + plain(h) + (speedUnit.equals("m/s") ? " s" : " h") + " = " + plain(dist) + ".\n"
                    + "So it covers " + plain(dist) + (speedUnit.equals("m/s") ? " m" : speedUnit.equals("mph") ? " miles" : " km") + ".";
        }
        if (hasS && hasD) {
            double v = Double.parseDouble(s.group(1)), dist = Double.parseDouble(d.group(1));
            if (speedUnit.equals("km/h") && !d.group(2).startsWith("k") && !d.group(2).startsWith("mi")) {
                dist /= 1000;
            }
            double h = dist / v;
            int hh = (int) Math.floor(h), mm = (int) Math.round((h - hh) * 60);
            if (mm == 60) {
                hh++;
                mm = 0;
            }
            return "What's known: distance " + plain(Double.parseDouble(d.group(1))) + " " + d.group(2) + ", speed " + plain(v) + " " + speedUnit + ".\n"
                    + "• Formula: time = distance ÷ speed.\n"
                    + "• " + plain(dist) + " ÷ " + plain(v) + " = " + plain(h) + (speedUnit.equals("m/s") ? " s" : " h") + ".\n"
                    + "So it takes " + (speedUnit.equals("m/s") ? plain(h) + " seconds" : (hh > 0 ? hh + " h " : "") + mm + " min") + ".";
        }
        return null;
    }

    // ------------------------------------------------------------------ percentages

    static String percent(String m, String low) {
        Matcher ch = Pattern.compile("(?i)(?:from|was)\\s+" + MONEY + "\\s+(?:to|now|became|is now)\\s+" + MONEY).matcher(m);
        if (low.matches("(?s).*\\b(percent(?:age)? (?:change|increase|decrease|rise|fall|drop)|by what percent|what percent(?:age)?|how much percent)\\b.*") && ch.find()) {
            String cur = ch.group(1) != null ? ch.group(1) : currency(m);
            double a = number(ch.group(2), ch.group(3)), b2 = number(ch.group(5), ch.group(6));
            double pc = (b2 - a) / a * 100;
            return "What's known: from " + money(cur, a) + " to " + money(cur, b2) + ".\n"
                    + "• Formula: % change = (new − old) ÷ old × 100.\n"
                    + "• (" + money(cur, b2) + " − " + money(cur, a) + ") ÷ " + money(cur, a) + " × 100 = " + plain(pc) + "%.\n"
                    + "So it " + (pc >= 0 ? "rose" : "fell") + " by " + plain(Math.abs(pc)) + "%.";
        }
        Matcher inc = Pattern.compile("(?i)\\b(increase|raise|add|decrease|reduce|cut|discount|take)\\s+" + MONEY + "\\s+by\\s+(\\d+(?:\\.\\d+)?)\\s*%").matcher(m);
        if (inc.find()) {
            String cur = inc.group(2) != null ? inc.group(2) : currency(m);
            double a = number(inc.group(3), inc.group(4)), p = Double.parseDouble(inc.group(5));
            boolean up = inc.group(1).toLowerCase(Locale.ROOT).matches("increase|raise|add");
            double v = a * (up ? 1 + p / 100 : 1 - p / 100);
            return "• " + plain(p) + "% of " + money(cur, a) + " = " + money(cur, a * p / 100) + ".\n"
                    + "• " + money(cur, a) + (up ? " + " : " − ") + money(cur, a * p / 100) + " = " + money(cur, v) + " (the same as × "
                    + plain(up ? 1 + p / 100 : 1 - p / 100) + ").\n"
                    + "So it becomes " + money(cur, v) + ".";
        }
        Matcher of = Pattern.compile("(?i)what\\s+(?:percent(?:age)?|%)\\s+(?:of\\s+" + MONEY + "\\s+is\\s+" + MONEY + "|is\\s+" + MONEY + "\\s+of\\s+" + MONEY + ")").matcher(m);
        if (of.find()) {
            double part, whole;
            if (of.group(2) != null) {
                whole = number(of.group(2), of.group(3));
                part = number(of.group(5), of.group(6));
            } else {
                part = number(of.group(8), of.group(9));
                whole = number(of.group(11), of.group(12));
            }
            return "• Formula: percentage = part ÷ whole × 100.\n• " + plain(part) + " ÷ " + plain(whole) + " × 100 = "
                    + plain(part / whole * 100) + "%.\nSo it is " + plain(part / whole * 100) + "%.";
        }
        return null;
    }
}
