package ai.ibiko.ibiko1;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everyday questions worked out exactly, offline: units ("convert 5 km to miles", "how many grams in a pound",
 * "30 C in F"), dates ("what day is it", "how many days until Christmas", "what day of the week was 1 October 1960",
 * "what's the date in 90 days", "is 2028 a leap year", "how old is someone born on 4 August 1961"), and percentages
 * ("15% of 200", "what percent is 30 of 120", "200 plus 15%"). Null for anything else. Pure Java; the clock is passed
 * in, so the answers can be checked.
 */
public final class Everyday {
    private Everyday() {
    }

    /** The answer, or null. */
    public static String answer(String t) {
        return answer(t, LocalDateTime.now());
    }

    static String answer(String t, LocalDateTime now) {
        if (t == null || t.trim().isEmpty()) {
            return null;
        }
        String q = t.trim().replaceAll("[’`]", "'").replaceAll("\\s+", " ")
                .replaceFirst("(?i)^(please\\s+)?((can|could|would|will) you (please )?(tell me|say|work out|calculate|convert)\\s+|"
                        + "(do|did) you know\\s+|tell me\\s+|i (want|need) to know\\s+)", "")
                .replaceAll("[?!.]+$", "").trim();
        String r = units(q);
        if (r == null) {
            r = dates(q, now);
        }
        if (r == null) {
            r = percent(q);
        }
        return r;
    }

    // ---------------------------------------------------------------- units

    /** A unit: its kind and how many of the kind's base unit one is. */
    private static final class Unit {
        final String kind, one, many;
        final double base;

        Unit(String kind, double base, String one, String many) {
            this.kind = kind;
            this.base = base;
            this.one = one;
            this.many = many;
        }
    }

    private static final Map<String, Unit> UNITS = new LinkedHashMap<String, Unit>();

    private static void unit(String kind, double base, String one, String many, String... names) {
        Unit u = new Unit(kind, base, one, many);
        UNITS.put(one, u);
        UNITS.put(many, u);
        for (String n : names) {
            UNITS.put(n, u);
        }
    }

    static {
        unit("length", 0.001, "millimetre", "millimetres", "mm", "millimeter", "millimeters");
        unit("length", 0.01, "centimetre", "centimetres", "cm", "centimeter", "centimeters");
        unit("length", 1, "metre", "metres", "m", "meter", "meters");
        unit("length", 1000, "kilometre", "kilometres", "km", "kilometer", "kilometers", "kms");
        unit("length", 0.0254, "inch", "inches", "in", "\"");
        unit("length", 0.3048, "foot", "feet", "ft", "'");
        unit("length", 0.9144, "yard", "yards", "yd", "yds");
        unit("length", 1609.344, "mile", "miles", "mi");
        unit("length", 1852, "nautical mile", "nautical miles", "nmi");
        unit("mass", 0.001, "milligram", "milligrams", "mg");
        unit("mass", 1, "gram", "grams", "g", "gm", "gms");
        unit("mass", 1000, "kilogram", "kilograms", "kg", "kgs", "kilo", "kilos");
        unit("mass", 1_000_000, "tonne", "tonnes", "t", "metric ton", "metric tons", "ton", "tons");
        unit("mass", 28.349523125, "ounce", "ounces", "oz");
        unit("mass", 453.59237, "pound", "pounds", "lb", "lbs");
        unit("mass", 6350.29318, "stone", "stones", "st");
        unit("volume", 0.001, "millilitre", "millilitres", "ml", "milliliter", "milliliters");
        unit("volume", 0.01, "centilitre", "centilitres", "cl");
        unit("volume", 1, "litre", "litres", "l", "liter", "liters", "ltr");
        unit("volume", 0.00492892159375, "teaspoon", "teaspoons", "tsp");
        unit("volume", 0.01478676478125, "tablespoon", "tablespoons", "tbsp");
        unit("volume", 0.0295735295625, "fluid ounce", "fluid ounces", "fl oz");
        unit("volume", 0.2365882365, "cup", "cups");
        unit("volume", 0.473176473, "pint", "pints", "pt", "us pint", "us pints");
        unit("volume", 0.946352946, "quart", "quarts", "qt");
        unit("volume", 3.785411784, "gallon", "gallons", "gal", "us gallon", "us gallons");
        unit("volume", 1000, "cubic metre", "cubic metres", "m3", "m³", "cubic meter", "cubic meters");
        unit("time", 1, "second", "seconds", "s", "sec", "secs");
        unit("time", 60, "minute", "minutes", "min", "mins");
        unit("time", 3600, "hour", "hours", "h", "hr", "hrs");
        unit("time", 86400, "day", "days");
        unit("time", 604800, "week", "weeks", "wk", "wks");
        unit("time", 31557600, "year", "years", "yr", "yrs");
        unit("time", 2629800, "month", "months");
        unit("speed", 1, "metre per second", "metres per second", "m/s", "meters per second");
        unit("speed", 1 / 3.6, "kilometre per hour", "kilometres per hour", "km/h", "kmh", "kph", "kilometers per hour");
        unit("speed", 0.44704, "mile per hour", "miles per hour", "mph");
        unit("speed", 0.514444, "knot", "knots", "kn", "kt");
        unit("area", 1, "square metre", "square metres", "m2", "m²", "sq m", "square meter", "square meters");
        unit("area", 1_000_000, "square kilometre", "square kilometres", "km2", "km²", "sq km", "square kilometer", "square kilometers");
        unit("area", 10_000, "hectare", "hectares", "ha");
        unit("area", 4046.8564224, "acre", "acres");
        unit("area", 0.09290304, "square foot", "square feet", "sq ft", "ft2", "ft²");
        unit("area", 2_589_988.110336, "square mile", "square miles", "sq mi", "mi2", "mi²");
        unit("data", 1, "byte", "bytes", "b");
        unit("data", 1024, "kilobyte", "kilobytes", "kb");
        unit("data", 1024.0 * 1024, "megabyte", "megabytes", "mb");
        unit("data", 1024.0 * 1024 * 1024, "gigabyte", "gigabytes", "gb");
        unit("data", 1024.0 * 1024 * 1024 * 1024, "terabyte", "terabytes", "tb");
        unit("energy", 1, "joule", "joules", "j");
        unit("energy", 1000, "kilojoule", "kilojoules", "kj");
        unit("energy", 4.184, "calorie", "calories", "cal");
        unit("energy", 4184, "kilocalorie", "kilocalories", "kcal");
        unit("energy", 3_600_000, "kilowatt hour", "kilowatt hours", "kwh");
        unit("temperature", 0, "degree Celsius", "degrees Celsius", "c", "°c", "celsius", "centigrade", "degrees c", "degree c");
        unit("temperature", 0, "degree Fahrenheit", "degrees Fahrenheit", "f", "°f", "fahrenheit", "degrees f", "degree f");
        unit("temperature", 0, "kelvin", "kelvins", "k");
    }

    private static Unit unitOf(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceFirst("^(a|an|one|the) ", "");
        Unit u = UNITS.get(n);
        if (u == null && n.startsWith("degrees ")) {
            u = UNITS.get(n.substring(8));
        }
        if (u == null) {
            for (Map.Entry<String, Unit> e : UNITS.entrySet()) {
                if (e.getKey().equalsIgnoreCase(n)) {
                    return e.getValue();
                }
            }
        }
        return u;
    }

    private static final String NUM = "(-?\\d[\\d,]*(?:\\.\\d+)?|-?\\.\\d+|a|an|one|half a|half an)";
    private static final String U = "([a-zA-Z°'\"²³/ ]{1,24}?)";
    private static final Pattern CONVERT = Pattern.compile("(?i)^(?:convert |change |turn |what is |what's |how much is |how many is )?"
            + NUM + " ?" + U + " (?:to|in|into|in to|as|equals how many|is how many|=) " + U + "$");
    private static final Pattern HOW_MANY = Pattern.compile("(?i)^how (?:many|much) " + U + " (?:are |is )?(?:there )?(?:in|make|makes|to|per) " + NUM + " ?" + U + "$");
    private static final Pattern HOW_MANY_ONE = Pattern.compile("(?i)^how (?:many|much) " + U + " (?:are |is )?(?:there )?(?:in|make|makes|to) (?:a|an|one) " + U + "$");

    static String units(String q) {
        Matcher m = HOW_MANY_ONE.matcher(q);
        if (m.matches()) {
            return convert(1, m.group(2), m.group(1));
        }
        m = HOW_MANY.matcher(q);
        if (m.matches()) {
            return convert(number(m.group(2)), m.group(3), m.group(1));
        }
        m = CONVERT.matcher(q);
        if (m.matches()) {
            return convert(number(m.group(1)), m.group(2), m.group(3));
        }
        return null;
    }

    private static double number(String s) {
        String n = s.trim().toLowerCase(Locale.ROOT);
        if (n.matches("a|an|one")) {
            return 1;
        }
        if (n.startsWith("half")) {
            return 0.5;
        }
        return Double.parseDouble(n.replace(",", ""));
    }

    private static String convert(double amount, String fromName, String toName) {
        Unit from = unitOf(fromName), to = unitOf(toName);
        if (from == null || to == null || !from.kind.equals(to.kind) || from == to) {
            return null;
        }
        double out;
        if (from.kind.equals("temperature")) {
            double c = from.one.endsWith("Celsius") ? amount : from.one.endsWith("Fahrenheit") ? (amount - 32) * 5 / 9 : amount - 273.15;
            out = to.one.endsWith("Celsius") ? c : to.one.endsWith("Fahrenheit") ? c * 9 / 5 + 32 : c + 273.15;
        } else {
            out = amount * from.base / to.base;
        }
        return say(amount) + " " + (amount == 1 ? from.one : from.many) + " is " + (Math.abs(out - Math.round(out)) < 1e-9 ? "" : "about ")
                + say(out) + " " + (Math.abs(out - 1) < 1e-9 ? to.one : to.many) + ".";
    }

    /** A number as people write it: commas, at most a few decimals, no trailing zeros. */
    static String say(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9 && Math.abs(v) < 1e15) {
            return String.format(Locale.US, "%,d", Math.round(v));
        }
        double a = Math.abs(v);
        int places = a >= 100 ? 1 : a >= 1 ? 2 : a >= 0.01 ? 4 : 6;
        String s = String.format(Locale.US, "%,." + places + "f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    // ---------------------------------------------------------------- dates

    private static final String MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december|"
            + "jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec";
    private static final Pattern DMY = Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)? (?:of )?(" + MONTHS + ")\\.?,? (\\d{1,4})\\b");
    private static final Pattern MDY = Pattern.compile("(?i)\\b(" + MONTHS + ")\\.? (\\d{1,2})(?:st|nd|rd|th)?,? (\\d{1,4})\\b");
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DM = Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)? (?:of )?(" + MONTHS + ")\\b");
    private static final Pattern MD = Pattern.compile("(?i)\\b(" + MONTHS + ")\\.? (\\d{1,2})(?:st|nd|rd|th)?\\b");

    /** Days people count to, by name: month and day. */
    private static final Map<String, int[]> DAYS = new LinkedHashMap<String, int[]>();

    static {
        DAYS.put("christmas eve", new int[]{12, 24});
        DAYS.put("christmas", new int[]{12, 25});
        DAYS.put("boxing day", new int[]{12, 26});
        DAYS.put("new year's eve", new int[]{12, 31});
        DAYS.put("new years eve", new int[]{12, 31});
        DAYS.put("new year", new int[]{1, 1});
        DAYS.put("new year's day", new int[]{1, 1});
        DAYS.put("valentine's day", new int[]{2, 14});
        DAYS.put("valentines day", new int[]{2, 14});
        DAYS.put("valentine", new int[]{2, 14});
        DAYS.put("halloween", new int[]{10, 31});
        DAYS.put("independence day in nigeria", new int[]{10, 1});
        DAYS.put("nigeria's independence day", new int[]{10, 1});
        DAYS.put("nigerian independence day", new int[]{10, 1});
        DAYS.put("workers' day", new int[]{5, 1});
        DAYS.put("workers day", new int[]{5, 1});
        DAYS.put("labour day", new int[]{5, 1});
        DAYS.put("democracy day", new int[]{6, 12});
        DAYS.put("children's day", new int[]{5, 27});
        DAYS.put("childrens day", new int[]{5, 27});
    }

    private static int month(String m) {
        String s = m.toLowerCase(Locale.ROOT).substring(0, 3);
        String[] all = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(s)) {
                return i + 1;
            }
        }
        return 1;
    }

    /** A date in the text (with its year, or the next one without), or null. */
    static LocalDate dateIn(String q, LocalDate today) {
        try {
            Matcher m = ISO.matcher(q);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = DMY.matcher(q);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), month(m.group(2)), Integer.parseInt(m.group(1)));
            }
            m = MDY.matcher(q);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), month(m.group(1)), Integer.parseInt(m.group(2)));
            }
            m = DM.matcher(q);
            if (m.find()) {
                return next(today, month(m.group(2)), Integer.parseInt(m.group(1)));
            }
            m = MD.matcher(q);
            if (m.find()) {
                return next(today, month(m.group(1)), Integer.parseInt(m.group(2)));
            }
        } catch (java.time.DateTimeException | NumberFormatException e) {
            return null;
        }
        String low = q.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, int[]> e : DAYS.entrySet()) {
            if (low.contains(e.getKey())) {
                return next(today, e.getValue()[0], e.getValue()[1]);
            }
        }
        if (low.matches(".*\\btomorrow\\b.*")) {
            return today.plusDays(1);
        }
        if (low.matches(".*\\byesterday\\b.*")) {
            return today.minusDays(1);
        }
        return null;
    }

    /** The next such day from today (today itself counts). */
    private static LocalDate next(LocalDate today, int month, int day) {
        LocalDate d = LocalDate.of(today.getYear(), month, day);
        return d.isBefore(today) ? d.plusYears(1) : d;
    }

    static String dateText(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + ", " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.getYear();
    }

    static String dates(String q, LocalDateTime now) {
        String low = q.toLowerCase(Locale.ROOT);
        LocalDate today = now.toLocalDate();
        if (low.matches("^(what('s| is) (the )?(date|day)( is it)?( today)?|what('s| is) today('s date)?|what day is (it|today)|"
                + "today's date|what is the date today|what's the date today|what date is (it|today))$")) {
            return "Today is " + dateText(today) + ".";
        }
        if (low.matches("^(what('s| is) the time|what time is it|time now|the time|what's the time now|what time is it now)$")) {
            return String.format(Locale.US, "It's %d:%02d %s on this device's clock.", (now.getHour() + 11) % 12 + 1, now.getMinute(),
                    now.getHour() < 12 ? "am" : "pm");
        }
        if (low.matches("^(what year is it|what('s| is) the year|which year is (it|this))( now)?$")) {
            return "It's " + today.getYear() + ".";
        }
        if (low.matches("^(what month is it|which month is (it|this))( now)?$")) {
            return "It's " + today.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + today.getYear() + ".";
        }
        Matcher leap = Pattern.compile("^is (\\d{3,4}) a leap year$").matcher(low);
        if (leap.matches()) {
            int y = Integer.parseInt(leap.group(1));
            return java.time.Year.isLeap(y) ? "Yes, " + y + " is a leap year: February has 29 days." : "No, " + y + " is not a leap year.";
        }
        Matcher in = Pattern.compile("^(?:what(?:'s| is| will be)? the date|what date (?:is|will it be)|what day (?:is|will it be)) "
                + "(?:in |)(\\d+) (day|days|week|weeks|month|months|year|years) (?:from (?:now|today)|later|time)?$").matcher(low);
        if (!in.matches()) {
            in = Pattern.compile("^(?:what(?:'s| is)? the date |what day is it |what date is it )?(\\d+) (day|days|week|weeks|month|months|year|years) "
                    + "(?:from (?:now|today)|after today)$").matcher(low);
        }
        if (in.matches()) {
            int n = Integer.parseInt(in.group(1));
            String u = in.group(2);
            LocalDate d = u.startsWith("day") ? today.plusDays(n) : u.startsWith("week") ? today.plusWeeks(n)
                    : u.startsWith("month") ? today.plusMonths(n) : today.plusYears(n);
            return n + " " + u + " from today is " + dateText(d) + ".";
        }
        Matcher ago = Pattern.compile("^(?:what (?:was )?the date |what date was it |what day was it )?(\\d+) (day|days|week|weeks|month|months|year|years) ago$").matcher(low);
        if (ago.matches()) {
            int n = Integer.parseInt(ago.group(1));
            String u = ago.group(2);
            LocalDate d = u.startsWith("day") ? today.minusDays(n) : u.startsWith("week") ? today.minusWeeks(n)
                    : u.startsWith("month") ? today.minusMonths(n) : today.minusYears(n);
            return n + " " + u + " ago was " + dateText(d) + ".";
        }
        if (low.matches("^(what day( of the week)? (was|is|will be|falls on|did)|which day( of the week)? (was|is|will be)|"
                + "what day did .* fall on|what day of the week).*")) {
            LocalDate d = dateIn(q, today);
            if (d != null) {
                String verb = d.isBefore(today) ? "was" : d.isEqual(today) ? "is" : "will be";
                return d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.getYear() + " " + verb
                        + " a " + d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + ".";
            }
        }
        if (low.matches("^how (many|much) (days|weeks|months|time)( are| is)?( (there|left))? (until|till|til|before|to)( it's)? .*")
                || low.matches("^how long (until|till|before|to) .*") || low.matches("^when is .*")) {
            LocalDate d = dateIn(q, today);
            if (d != null && !low.matches("^when is .*") || d != null && low.matches("^when is (christmas|new year|valentine|halloween|boxing day).*")) {
                long days = ChronoUnit.DAYS.between(today, d);
                String name = d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.getYear();
                if (low.matches("^when is .*")) {
                    return "It's on " + dateText(d) + ", " + (days == 0 ? "today." : days == 1 ? "tomorrow." : "in " + days + " days.");
                }
                if (days == 0) {
                    return "That's today!";
                }
                if (days < 0) {
                    return name + " was " + (-days) + " days ago.";
                }
                String weeks = days >= 14 ? " (" + (days / 7) + " weeks and " + (days % 7) + (days % 7 == 1 ? " day" : " days") + ")" : "";
                return days + (days == 1 ? " day" : " days") + " until " + dateText(d) + weeks + ".";
            }
        }
        Matcher between = Pattern.compile("(?i)^how many days (?:are there )?between (.+?) and (.+)$").matcher(q);
        if (between.matches()) {
            LocalDate a = dateIn(between.group(1), today), b = dateIn(between.group(2), today);
            if (a != null && b != null) {
                long days = Math.abs(ChronoUnit.DAYS.between(a, b));
                return "There are " + String.format(Locale.US, "%,d", days) + " days between " + dateText(a) + " and " + dateText(b) + ".";
            }
        }
        Matcher born = Pattern.compile("(?i)^how old (?:am i|is (?:someone|a person|he|she|my \\w+)|would i be)(?: if (?:i was|i were|they were|he was|she was))? born (?:on |in )?(.+)$").matcher(q);
        if (born.matches()) {
            String when = born.group(1);
            LocalDate b = dateIn(when, today);
            if (b == null && when.trim().matches("\\d{4}")) {
                int years = today.getYear() - Integer.parseInt(when.trim());
                return "Born in " + when.trim() + ": " + (years - 1) + " or " + years + " years old now, depending on the birthday.";
            }
            if (b != null && b.isBefore(today)) {
                long years = ChronoUnit.YEARS.between(b, today);
                return "Born on " + dateText(b) + ": " + years + " years old today.";
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- percentages

    private static final String N = "(-?\\d[\\d,]*(?:\\.\\d+)?)";

    static String percent(String q) {
        String low = q.toLowerCase(Locale.ROOT).replace("per cent", "%").replace("percentage", "%").replace("percent", "%").replaceAll("(\\d)\\s+%", "$1%");
        Matcher m = Pattern.compile("^(?:what(?:'s| is) )?" + N + "% of " + N + "$").matcher(low);
        if (m.matches()) {
            double p = d(m.group(1)), of = d(m.group(2));
            return say(p) + "% of " + say(of) + " is " + say(p * of / 100) + ".";
        }
        m = Pattern.compile("^(?:what %|what percentage|how many %) (?:is|of) " + N + " (?:is |out )?(?:of|from) " + N + "$").matcher(low);
        if (m.matches()) {
            double a = d(m.group(1)), b = d(m.group(2));
            return b == 0 ? null : say(a) + " is " + say(a * 100 / b) + "% of " + say(b) + ".";
        }
        m = Pattern.compile("^(?:what(?:'s| is) )?" + N + " (plus|\\+|add|minus|-|less|increased by|decreased by|reduced by) " + N + "%$").matcher(low);
        if (m.matches()) {
            double a = d(m.group(1)), p = d(m.group(3));
            boolean up = m.group(2).matches("plus|\\+|add|increased by");
            double r = a * (1 + (up ? p : -p) / 100);
            return say(a) + (up ? " plus " : " minus ") + say(p) + "% is " + say(r) + ".";
        }
        m = Pattern.compile("^(?:what(?:'s| is) the )?% (?:change|increase|decrease|difference) (?:from|between) " + N + " (?:to|and) " + N + "$").matcher(low);
        if (m.matches()) {
            double a = d(m.group(1)), b = d(m.group(2));
            if (a == 0) {
                return null;
            }
            double c = (b - a) * 100 / a;
            return "From " + say(a) + " to " + say(b) + " is " + (c >= 0 ? "an increase" : "a decrease") + " of " + say(Math.abs(c)) + "%.";
        }
        return null;
    }

    private static double d(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }
}
