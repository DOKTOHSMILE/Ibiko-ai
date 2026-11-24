package ai.ibiko.ibiko1.formulas;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everyday sums said in words, worked out exactly with the working shown: half (a third, a quarter, double) of a number,
 * a percentage off a price, whether a number is prime (and if not, what divides it), the day of the week some days after
 * another, and the price of several when one's price is said. Pure Java.
 */
public final class Quick {
    private Quick() {
    }

    private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?)";
    private static final Pattern PART = Pattern.compile("(?i)^\\s*(?:what(?:'s| is)\\s+)?(half|a half|one half|a third|one third|a quarter|one quarter|"
            + "a fifth|double|twice|triple|three times)\\s+(?:of\\s+)?" + NUM + "\\s*[?.!]*\\s*$");
    private static final Pattern OFF = Pattern.compile("(?i)^\\s*(?:what(?:'s| is)\\s+)?" + NUM + "\\s*%\\s+(?:off|discount on|less than|off of)\\s+"
            + "([₦$£€]|N(?=\\d))?\\s*" + NUM + "(?:\\s*(naira|dollars?|pounds?|euros?))?\\s*[?.!]*\\s*$");
    private static final Pattern PRIME = Pattern.compile("(?i)^\\s*is\\s+" + NUM + "\\s+(?:a\\s+)?prime(?:\\s+number)?\\s*[?.!]*\\s*$");
    private static final String DAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday";
    private static final Pattern WEEKDAY = Pattern.compile("(?i)^\\s*what day (?:is|will it be|would it be|is it)\\s+(\\d{1,4}) days? (after|from|before)\\s+"
            + "(" + DAYS + "|today)\\s*[?.!]*\\s*$");
    private static final Pattern PRICE = Pattern.compile("(?i)\\b(?:a|an|one|each)\\s+([a-z]+)\\s+costs?\\s+([₦$£€]|N(?=\\d))?\\s*" + NUM
            + "(?:\\s*(naira|dollars?|pounds?|euros?|cedis?))?[^?]*?how much (?:do|does|would|will|for)\\s+" + NUM + "\\s+([a-z]+)(?:\\s+cost)?\\s*\\?");

    /** The sum worked out, or null when the message isn't one of these. */
    public static String reply(String message) {
        if (message == null || message.length() > 300) {
            return null;
        }
        Matcher m;
        if ((m = PART.matcher(message)).matches()) {
            double n = num(m.group(2));
            String w = m.group(1).toLowerCase(Locale.ROOT).replaceFirst("^(a|one) ", "");
            double f = w.equals("half") ? 0.5 : w.equals("third") ? 1.0 / 3 : w.equals("quarter") ? 0.25 : w.equals("fifth") ? 0.2
                    : w.matches("double|twice") ? 2 : 3;
            String how = f < 1 ? m.group(2) + " ÷ " + Math.round(1 / f) : m.group(2) + " × " + Math.round(f);
            return cap(m.group(1).toLowerCase(Locale.ROOT)) + " of " + m.group(2) + " is " + show(n * f) + " (" + how + ").";
        }
        if ((m = OFF.matcher(message)).matches()) {
            double pct = num(m.group(1)), price = num(m.group(3));
            String cur = m.group(2) != null ? m.group(2) : "", unit = m.group(4) != null ? " " + m.group(4) : "";
            double cut = price * pct / 100;
            return cur + show(price - cut) + unit + ": " + show(pct) + "% of " + cur + show(price) + " is " + cur + show(cut) + ", and " + cur + show(price)
                    + " − " + cur + show(cut) + " = " + cur + show(price - cut) + unit + ".";
        }
        if ((m = PRIME.matcher(message)).matches()) {
            double d = num(m.group(1));
            if (d != Math.floor(d) || d > 1e12) {
                return null;
            }
            long n = (long) d;
            if (n < 2) {
                return "No: a prime number is a whole number above 1 that only 1 and itself divide. " + n + " isn't above 1.";
            }
            for (long k = 2; k * k <= n; k++) {
                if (n % k == 0) {
                    return "No: " + n + " = " + k + " × " + (n / k) + ", so it can be divided by " + k + ".";
                }
            }
            return "Yes: " + n + " is prime. No number but 1 and " + n + " divides it (I checked every number up to its square root, "
                    + (long) Math.floor(Math.sqrt(n)) + ").";
        }
        if ((m = WEEKDAY.matcher(message)).matches()) {
            String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
            String from = m.group(3).toLowerCase(Locale.ROOT);
            int at;
            if (from.equals("today")) {
                at = (java.time.LocalDate.now().getDayOfWeek().getValue() + 6) % 7;
            } else {
                at = java.util.Arrays.asList(DAYS.split("\\|")).indexOf(from);
            }
            int n = Integer.parseInt(m.group(1)), step = m.group(2).equalsIgnoreCase("before") ? -n : n;
            int to = Math.floorMod(at + step, 7);
            return days[to] + ". " + n + " days is " + (n / 7) + (n / 7 == 1 ? " week" : " weeks") + " and " + (n % 7) + (n % 7 == 1 ? " day" : " days")
                    + ", so it's " + (n % 7) + " " + (n % 7 == 1 ? "day" : "days") + " " + (step < 0 ? "before " : "after ") + days[at] + ".";
        }
        if ((m = PRICE.matcher(message)).find()) {
            double each = num(m.group(3));
            double qty = num(m.group(5));
            String cur = m.group(2) != null ? m.group(2) : "", unit = m.group(4) != null ? " " + m.group(4) : "";
            return cur + show(each * qty) + unit + ": " + show(qty) + " × " + cur + show(each) + unit + " = " + cur + show(each * qty) + unit + ".";
        }
        return null;
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static String show(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.US, "%,d", (long) Math.rint(v));
        }
        return String.format(Locale.US, "%,.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
