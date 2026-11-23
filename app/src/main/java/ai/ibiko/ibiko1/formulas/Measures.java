package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading how big, long, high or many a thing is, from plain text: "Lagos has about 15 million people", "The Niger
 * River is 4,180 km long", "Kilimanjaro rises 5,895 metres above sea level", "Nigeria covers 923,768 square
 * kilometres". Each becomes a measure of the thing (population, length, elevation, area) with its value and unit,
 * so what Ibiko reads can be compared and ranked, not only what it shipped with. The number is read by
 * {@link NumberSense} first: a measure is taken only from a number read as a count or a measurement. Pure Java.
 */
public final class Measures {
    private Measures() {
    }

    /** One measure read: of what, which, the value, its unit, and the sentence. */
    public static final class Measure {
        public final String subject, what, unit, sentence;
        public final double value;

        Measure(String subject, String what, double value, String unit, String sentence) {
            this.subject = subject;
            this.what = what;
            this.value = value;
            this.unit = unit;
            this.sentence = sentence;
        }

        /** The value as said: "15,000,000", "4,180 km". */
        public String said() {
            String n = value == Math.rint(value) ? String.format(Locale.US, "%,.0f", value) : String.format(Locale.US, "%,.1f", value);
            return unit.isEmpty() ? n : n + " " + unit;
        }

        @Override
        public String toString() {
            return "(" + subject + " | " + what + " | " + said() + ")";
        }
    }

    private static final String NUM = "((?:about|around|over|nearly|almost|more than|some|roughly|approximately|an estimated)\\s+)?"
            + "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(million|billion|thousand|m|bn|k)?";
    private static final String S = "([A-Z][\\p{L}'.-]*(?:\\s+(?:of|the|de|[A-Z][\\p{L}'.-]*))*|[Ii]t|[Tt]he (?:country|city|state|river|mountain|lake|town|island))";
    private static final Pattern[] POPULATION = {
        Pattern.compile(S + "\\s+(?:has|had)\\s+" + NUM + "\\s*(people|inhabitants|residents|citizens)"),
        Pattern.compile(S + "\\s+(?:has|had)\\s+a population of\\s+" + NUM),
        Pattern.compile("(?:the )?population of " + S + "\\s+(?:is|was|stands at|reached)\\s+" + NUM),
        Pattern.compile(NUM + "\\s+(?:people|inhabitants|residents)\\s+live(?:d)?\\s+in\\s+" + S),
        Pattern.compile(S + "\\s*,?\\s+(?:is home to|with)\\s+" + NUM + "\\s*(people|inhabitants|residents)")};
    private static final Pattern LENGTH = Pattern.compile(S + "\\s+(?:is|was|runs|flows|stretches)\\s+(?:for\\s+)?" + NUM
            + "\\s*(km|kilomet(?:er|re)s|miles|mi)\\s*(?:long|in length)?");
    private static final Pattern ELEVATION = Pattern.compile(S + "\\s+(?:is|was|rises|stands|reaches)\\s+(?:to\\s+)?" + NUM
            + "\\s*(m|met(?:er|re)s|ft|feet)\\s*(?:high|tall|above sea level|in height)");
    private static final Pattern AREA = Pattern.compile(S + "\\s+(?:covers|has an area of|is)\\s+(?:an area of\\s+)?" + NUM
            + "\\s*(square kilomet(?:er|re)s|sq km|km2|km²|square miles)");

    /** The measures read from a text about {@code topic} (which "it" and "the country" stand for). */
    public static List<Measure> read(String text, String topic) {
        List<Measure> out = new ArrayList<Measure>();
        if (text == null) {
            return out;
        }
        for (String sentence : text.split("(?<=[.!?])\\s+(?=[A-Z\"“(])")) {
            for (Pattern p : POPULATION) {
                take(p, sentence, topic, "population", "", out, p == POPULATION[3]);
            }
            take(LENGTH, sentence, topic, "length", "km", out, false);
            take(ELEVATION, sentence, topic, "elevation", "m", out, false);
            take(AREA, sentence, topic, "area", "square km", out, false);
        }
        return out;
    }

    private static void take(Pattern p, String sentence, String topic, String what, String unit, List<Measure> out, boolean numberFirst) {
        Matcher m = p.matcher(sentence);
        while (m.find()) {
            String subject = numberFirst ? m.group(4) : m.group(1);
            int g = numberFirst ? 1 : 2; // the "about"-group comes first, then the digits, then a scale word
            String digits = m.group(g + 1), scale = m.group(g + 2);
            int numberAt = m.start(g + 1);
            // the number must read as a count or a measurement, not a year or a code
            NumberSense.Kind kind = null;
            for (NumberSense.Found f : NumberSense.read(sentence)) {
                if (f.start <= numberAt && f.end > numberAt) {
                    kind = f.kind();
                }
            }
            if (kind == NumberSense.Kind.YEAR || kind == NumberSense.Kind.CODE || kind == NumberSense.Kind.PHONE
                    || kind == NumberSense.Kind.MONEY || kind == NumberSense.Kind.DATE || kind == NumberSense.Kind.TIME) {
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(digits.replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (scale != null) {
                String s = scale.toLowerCase(Locale.US);
                v *= s.startsWith("b") ? 1e9 : s.startsWith("m") ? 1e6 : 1e3;
            }
            String u = unit;
            String written = m.group(m.groupCount());
            if (written != null && !"population".equals(what)) {
                String w = written.toLowerCase(Locale.US);
                if (w.startsWith("mi") || w.equals("square miles")) {
                    v *= "area".equals(what) ? 2.58999 : 1.609344; // kept in km
                } else if (w.startsWith("f")) {
                    v *= 0.3048; // kept in metres
                }
            }
            String who = subject.trim().replaceAll("[.,;:]+$", "");
            if (who.matches("(?i)it|the (country|city|state|river|mountain|lake|town|island)")) {
                if (topic == null) {
                    continue;
                }
                who = topic;
            }
            if (who.length() < 2 || who.split(" ").length > 6) {
                continue;
            }
            out.add(new Measure(who, what, Math.round(v * 10) / 10.0, u, sentence));
        }
    }
}
