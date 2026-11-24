package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A story followed the way a careful reader follows it: who has what and how many, what is sold, bought or given and by
 * whom, what each thing costs, discounts, the hours something is open, and when in the story each thing happened ("by
 * noon", "before closing"). Questions about it are worked out, with the working shown: "how many tomatoes were left at
 * noon?", "how much did Tunde pay for the garri?", "how much did Ngozi pay after the discount?", "how many hours is the
 * stall open?". Nothing is guessed: what the story doesn't give is left unanswered. Pure Java.
 */
public final class Story {
    /** One thing that happened to a count. */
    static final class Event {
        final int sentence;
        final String item, who;
        final double amount;
        double discount;
        /** What happened, as the story says it ("sold", "repaired", "dropped", "bought"); a customer's job is "job". */
        String verb = "";

        Event(int sentence, String item, double amount, String who, double discount) {
            this.sentence = sentence;
            this.item = item;
            this.amount = amount;
            this.who = who;
            this.discount = discount;
        }

        Event as(String verb) {
            this.verb = verb == null ? "" : verb.toLowerCase(Locale.ROOT);
            return this;
        }

        /** Stock that came in (bought to restock the shelf), not a sale. */
        boolean in() {
            return amount > 0;
        }
    }

    final List<String> sentences;
    /** The one the story is about: the name it uses most. */
    final String main;
    final Map<String, Double> start = new LinkedHashMap<String, Double>();
    final Map<String, String> said = new LinkedHashMap<String, String>();
    final List<Event> events = new ArrayList<Event>();
    final Map<String, Double> price = new LinkedHashMap<String, Double>();
    String currency = "";
    int opens = -1, closes = -1;
    /** A break in the working day ("a 45-minute lunch break"), in minutes, and what it is called. */
    int breakMins;
    String breakName = "break";
    /** Who works with the one the story is about ("his apprentice, Musa"): their name and role. */
    final Map<String, String> staff = new LinkedHashMap<String, String>();
    private String staffPronoun = "their";
    /** The thing named last, for "and sold 15". */
    private String lastItem;
    /** The sentence each time of day is reached by ("noon" → 1). */
    final Map<String, Integer> marks = new LinkedHashMap<String, Integer>();

    private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|fifty|hundred)";
    private static final String UNIT = "(?:(?:bags?|sacks?|crates?|baskets?|bottles?|boxes|box|packs?|packets?|kilos?|kg|tins?|cartons?|loaves|loaf|pieces?|bunches|bunch|tubers?|heads?|dozens?|liters?|litres?|cups?|plates?|bowls?)\\s+of\\s+)?";
    private static final String ITEM = "([a-z][a-z-]*(?:\\s+[a-z][a-z-]*)?)";
    /** A word that says what state a thing is in, not what it is ("24 broken smartphones", "10 new cables"). */
    static final String ADJ = "(?:(?:new|broken|unrepaired|repaired|damaged|faulty|used|fresh|old|ripe|spare|extra|empty|full|working|unsold|remaining|other)\\s+)?";
    private static final Pattern AMOUNT = Pattern.compile("(?i)\\b" + NUM + "\\s+(?:more\\s+)?" + "(" + UNIT + ")" + ADJ + ITEM);
    /** "3 of the remaining unrepaired smartphones": a number out of what there is. */
    private static final Pattern PART = Pattern.compile("(?i)(?<![/\\d])\\b" + NUM + "\\s+of\\s+(?:the|her|his|their|its|my)\\s+" + ADJ + ADJ + "(" + UNIT + ")" + ITEM);
    /** Repairing, fixing: out of the stock still to be worked on. */
    private static final Pattern WORKED = Pattern.compile("(?i)\\b(repaired|repairing|repairs|fixed|fixing|fixes|mended|mending|serviced|servicing)\\b");
    /** Damaged or lost: gone from the stock. */
    private static final Pattern LOSES = Pattern.compile("(?i)\\b(dropped(?!\\s+off)|broke|damaged|destroyed|ruined|lost|spoiled|spoilt|stolen|smashed|cracked|burnt|burned|wasted)\\b");
    /** Left with the one the story is about, by a customer: work to be done for them. */
    private static final Pattern BRINGS = Pattern.compile("(?i)\\b(brought(?:\\s+in)?|brings(?:\\s+in)?|dropped off|sent|left)\\b");
    /** "taking a 45-minute lunch break", "a one-hour break": the time in a working day that isn't work. */
    private static final Pattern BREAK = Pattern.compile("(?i)\\b(\\d{1,3}|one|two|half)(?:-|\\s+)(minute|min|hour)s?(?:'s)?\\s+((?:lunch|tea|prayer|rest|coffee|dinner)\\s+)?break\\b"
            + "|\\b(?:an|one)\\s+(hour)(?:'s|-long)?\\s+((?:lunch|tea|prayer|rest)\\s+)?break\\b");
    /** "his apprentice, Musa", "Musa, her assistant": who works for the one the story is about. */
    private static final Pattern STAFF = Pattern.compile("\\b([Hh]is|[Hh]er|[Tt]heir)\\s+(apprentice|assistant|worker|staff|employee|helper|son|daughter|wife|husband|brother|sister|"
            + "manager|cashier|driver|partner|nephew|niece)s?,?\\s+([A-Z][a-z]+)|\\b([A-Z][a-z]+),\\s+([Hh]is|[Hh]er|[Tt]heir)\\s+(apprentice|assistant|worker|employee|helper|"
            + "manager|cashier|driver|partner)\\b");
    private static final Pattern FRACTION = Pattern.compile("(?i)\\b(\\d+)\\s*/\\s*(\\d+)\\s+of\\s+(?:the|her|his|their|its|my)\\s+" + "(" + UNIT + ")" + ITEM
            + "|\\b(half|a third|a quarter|two-thirds|three-quarters|one-third|one-quarter)\\s+of\\s+(?:the|her|his|their|its|my)\\s+(" + UNIT + ")" + ITEM);
    private static final Pattern HAS = Pattern.compile("(?i)\\b(?:brought|had|has|have|carried|started with|took|packed|harvested|baked|made|received|got|stocked|"
            + "prepared|loaded|collected|keeps|kept|keep|owns|owned|grows|grew|bakes|makes|picked|caught|bought)\\b");
    /** "and sold 15": a number given away with its thing left unsaid (the one just named). */
    private static final Pattern GIVES_BARE = Pattern.compile("(?i)\\b(?:sold|gave away|gave|used|ate|lost|spoiled|delivered|donated)\\s+(\\d[\\d,]*|"
            + "one|two|three|four|five|six|seven|eight|nine|ten|twelve|fifteen|twenty|thirty|forty|fifty|half)\\b(?!\\s*/)(?!\\s+(?:more\\s+)?[a-z]+)");
    private static final Pattern GIVES = Pattern.compile("(?i)\\b(?:sold|sells|gave away|gave|used|ate|lost|spoiled|delivered|donated|threw away)\\b");
    private static final Pattern BUYS = Pattern.compile("(?i)\\b(?:bought|buys|buy|buying|purchased|purchase|took home|paid for|ordered|restocked|restock)\\b");
    private static final String CUR = "([₦$£€]|N(?=\\d))?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(naira|dollars?|pounds?|euros?|cedis?)?";
    /** "sold 12 charging cables at 1,500 naira each": the price with the sale. */
    private static final Pattern PRICE_AT = Pattern.compile("(?i)\\b" + NUM + "\\s+(" + UNIT + ")" + ADJ + ITEM + "\\s+at\\s+" + CUR + "\\s*(each|apiece|a piece|per\\s+[a-z]+)?");
    /** "screen replacement costs 18,000 naira per tablet": the price of each one. */
    private static final Pattern PRICE_PER = Pattern.compile("(?i)\\b(?:costs?|is|are|charges?|charged)\\s+" + CUR + "\\s+(?:per|a|an|each|for each|for one)\\s+([a-z]+)");
    /** "a 15% loyalty discount on the total bill": off what the last customer pays. */
    private static final Pattern DISCOUNT_BILL = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*%\\s+(?:[a-z]+\\s+)?(?:discount|off|reduction)\\b");
    private static final Pattern PRICE = Pattern.compile("(?i)\\beach\\s+(" + UNIT + ")" + ITEM + "\\s+(?:costs?|is|sells for|goes for)\\s+([₦$£€]|N(?=\\d))?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(naira|dollars?|pounds?|euros?|cedis?)?"
            + "|\\b(?:a|one)\\s+(" + UNIT + ")" + ITEM + "\\s+(?:costs?|is|sells for)\\s+([₦$£€]|N(?=\\d))?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(naira|dollars?|pounds?|euros?|cedis?)?"
            + "|\\b(" + UNIT + ")" + ITEM + "\\s+(?:costs?|sells? for|is)\\s+([₦$£€]|N(?=\\d))?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(naira|dollars?|pounds?|euros?|cedis?)?\\s+(?:each|apiece|a piece|per)");
    private static final Pattern DISCOUNT = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*%\\s+(?:discount|off|reduction)\\s+(?:on|for)\\s+" + NUM + "\\s+(" + UNIT + ")" + ITEM);
    private static final Pattern CLOCK = Pattern.compile("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)");
    private static final Pattern NAME = Pattern.compile("\\b[A-Z][a-z]{2,}\\b");
    private static final java.util.Set<String> NOT_NAMES = new java.util.HashSet<String>(java.util.Arrays.asList("The", "This", "That", "Every",
            "Each", "Her", "His", "Their", "She", "Later", "Then", "After", "Before", "When", "In", "On", "By", "At", "It", "All", "Some", "Saturday",
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December", "No", "Yes", "Tasks", "Task", "Questions", "Amen", "Our", "We", "They", "There"));

    /** Words written with a capital somewhere other than a sentence's start: names, not "Today" or "Later". */
    final java.util.Set<String> named = new java.util.HashSet<String>();

    Story(String passage) {
        sentences = Passage.split(passage);
        for (String s : sentences) {
            Matcher n = NAME.matcher(s);
            while (n.find()) {
                if (n.start() > 0 && !s.substring(0, n.start()).matches("(?s).*[.!?\"“]\\s*$")) {
                    named.add(n.group());
                }
            }
        }
        // a word that opens two sentences or more is a name too ("Kemi runs…", "Kemi agreed"); "Today", once, isn't
        Map<String, Integer> opens = new LinkedHashMap<String, Integer>();
        for (String s : sentences) {
            Matcher n = NAME.matcher(s);
            if (n.find() && n.start() == 0) {
                opens.merge(n.group(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : opens.entrySet()) {
            if (e.getValue() >= 2) {
                named.add(e.getKey());
            }
        }
        Map<String, Integer> names = new LinkedHashMap<String, Integer>();
        for (String s : sentences) {
            Matcher n = NAME.matcher(s);
            while (n.find()) {
                if (isName(n.group())) {
                    names.merge(n.group(), 1, Integer::sum);
                }
            }
        }
        String best = null;
        for (Map.Entry<String, Integer> e : names.entrySet()) {
            if (best == null || e.getValue() > names.get(best)) {
                best = e.getKey();
            }
        }
        main = best;
        for (String sentence : sentences) {
            Matcher st = STAFF.matcher(sentence);
            while (st.find()) {
                String name = st.group(3) != null ? st.group(3) : st.group(4);
                String role = (st.group(2) != null ? st.group(2) : st.group(6)).toLowerCase(Locale.ROOT);
                if (main != null && !name.equals(main)) {
                    staff.put(name, role);
                    staffPronoun = (st.group(1) != null ? st.group(1) : st.group(5)).toLowerCase(Locale.ROOT);
                }
            }
            Matcher br = BREAK.matcher(sentence);
            if (br.find() && breakMins == 0) {
                if (br.group(4) != null) {
                    breakMins = 60;
                    breakName = br.group(5) == null ? "break" : br.group(5).trim().toLowerCase(Locale.ROOT) + " break";
                } else {
                    String n = br.group(1).toLowerCase(Locale.ROOT);
                    double v = n.equals("half") ? 0.5 : number(n);
                    breakMins = (int) Math.round(br.group(2).toLowerCase(Locale.ROOT).startsWith("h") ? v * 60 : v);
                    breakName = br.group(3) == null ? "break" : br.group(3).trim().toLowerCase(Locale.ROOT) + " break";
                }
            }
        }
        for (int i = 0; i < sentences.size(); i++) {
            read(i, sentences.get(i));
        }
    }

    private void read(int i, String s) {
        String low = s.toLowerCase(Locale.ROOT);
        for (String mark : new String[]{"noon", "midday", "morning", "afternoon", "evening", "closing", "night"}) {
            if (low.matches("(?s).*\\b(?:by|at|before|until|till|in the)\\s+" + mark + "\\b.*") && !marks.containsKey(mark)) {
                marks.put(mark, i);
            }
        }
        // opening hours: "opens … at 7am and closes … at 4pm"
        Matcher c = CLOCK.matcher(s);
        List<Integer> clocks = new ArrayList<Integer>();
        while (c.find()) {
            int h = Integer.parseInt(c.group(1)) % 12 + (c.group(3).toLowerCase(Locale.ROOT).startsWith("p") ? 12 : 0);
            clocks.add(h * 60 + (c.group(2) == null ? 0 : Integer.parseInt(c.group(2))));
        }
        if (clocks.size() >= 2 && low.matches("(?s).*\\b(open|opens|opened|start|starts|starts work|begins?)\\b.*")) {
            opens = clocks.get(0);
            closes = clocks.get(1);
        }
        // prices
        Matcher p = PRICE.matcher(s);
        while (p.find()) {
            int g = p.group(2) != null ? 1 : p.group(7) != null ? 6 : 11;
            String item = key(p.group(g + 1));
            price.put(item, num(p.group(g + 3)));
            String cur = p.group(g + 2) != null ? p.group(g + 2) : p.group(g + 4);
            if (cur != null && currency.isEmpty()) {
                currency = cur.matches("(?i)naira") ? " naira" : cur.matches("[₦$£€N]") ? cur : " " + cur;
            }
        }
        Matcher pa = PRICE_AT.matcher(s);
        while (pa.find()) {
            if (pa.group(5) != null || pa.group(6) != null || pa.group(7) != null) {
                price.put(key(pa.group(3)), num(pa.group(5)));
                currencyFrom(pa.group(4), pa.group(6));
            }
        }
        Matcher pp = PRICE_PER.matcher(s);
        while (pp.find()) {
            price.put(key(pp.group(4)), num(pp.group(2)));
            currencyFrom(pp.group(1), pp.group(3));
        }
        String who = subject(s);
        boolean byMain = who == null || who.equals(main) || staff.containsKey(who);
        // a discount agreed: "Ngozi asked for a 20% discount on 10 yams. Amina agreed."
        Matcher d = DISCOUNT.matcher(s);
        if (d.find()) {
            String item = key(d.group(4));
            events.add(new Event(i, item, -number(d.group(2)), who != null && !who.equals(main) ? who : null, Double.parseDouble(d.group(1)) / 100).as("bought"));
            return;
        }
        Matcher bill = DISCOUNT_BILL.matcher(s);
        if (bill.find()) {
            // "gave her a 15% discount on the total bill": the last customer's
            for (int e = events.size() - 1; e >= 0; e--) {
                Event ev = events.get(e);
                if (ev.who != null && !ev.in() && !staff.containsKey(ev.who) && ev.discount == 0) {
                    ev.discount = Double.parseDouble(bill.group(1)) / 100;
                    break;
                }
            }
        }
        // fractions sold: "sold 3/4 of the tomatoes"
        if (byMain) {
            Matcher f = FRACTION.matcher(s);
            while (f.find()) {
                String verb = takenBy(s.substring(0, f.start()));
                if (verb == null) {
                    continue;
                }
                double part = f.group(1) != null ? Double.parseDouble(f.group(1)) / Double.parseDouble(f.group(2)) : fraction(f.group(5));
                String item = key(f.group(1) != null ? f.group(4) : f.group(7));
                double have = count(item, i);
                events.add(new Event(i, item, -part * have, null, 0).as(verb));
            }
            Matcher part = PART.matcher(s);
            while (part.find()) {
                String verb = takenBy(s.substring(0, part.start()));
                String item = key(part.group(3));
                if (verb != null && start.containsKey(item)) {
                    events.add(new Event(i, item, -number(part.group(1)), staff.containsKey(who) ? who : null, 0).as(verb));
                }
            }
        }
        Matcher a = AMOUNT.matcher(s);
        while (a.find()) {
            String item = key(a.group(3));
            if (item.isEmpty() || item.matches("(?i)(am|pm|hours?|minutes?|days?|years?|naira|dollars?|percent|times?|cedis?|pounds?|euros?|kobo|per|each)\\b.*")) {
                continue;
            }
            double n = number(a.group(1));
            if (FRACTION.matcher(s).find() && s.substring(0, a.start()).matches("(?s).*\\bof\\s+(?:the|her|his)\\s*$")) {
                continue;
            }
            if (item.matches("of\\b.*")) {
                continue; // "3 of the remaining smartphones": read as a part, above
            }
            String before = s.substring(0, a.start());
            // what is done to it is the verb nearest before it ("dropped 3 …, then went out to buy 10 new cables": bought)
            int buys = last(BUYS, before), gives = last(GIVES, before), has = last(HAS, before), brings = last(BRINGS, before);
            String taken = takenBy(before);
            int takes = taken == null ? -1 : before.toLowerCase(Locale.ROOT).lastIndexOf(taken.toLowerCase(Locale.ROOT)) + taken.length();
            int near = Math.max(Math.max(buys, gives), Math.max(Math.max(has, brings), takes));
            if (near < 0) {
                continue;
            }
            if (!byMain && brings == near && brings > buys) {
                events.add(new Event(i, item, -n, who, 0).as("job")); // a customer's things left to be worked on
                said.putIfAbsent(item, a.group(2) + a.group(3));
            } else if (buys == near && !byMain) {
                events.add(new Event(i, item, -n, who, 0).as("bought")); // someone bought from the one the story is about
                said.putIfAbsent(item, a.group(2) + a.group(3));
            } else if (buys == near && start.containsKey(item)) {
                events.add(new Event(i, item, n, staff.containsKey(who) ? who : null, 0).as("bought")); // more stock bought in
            } else if (takes == near && byMain) {
                events.add(new Event(i, item, -n, staff.containsKey(who) ? who : null, 0).as(taken));
            } else if (gives == near && byMain) {
                events.add(new Event(i, item, -n, null, 0).as("sold"));
            } else if ((has == near || buys == near) && byMain) {
                start.merge(item, n, Double::sum);
                said.putIfAbsent(item, a.group(2) + a.group(3));
                lastItem = item;
            }
        }
        Matcher bare = GIVES_BARE.matcher(s);
        if (bare.find() && byMain && lastItem != null && start.containsKey(lastItem)) {
            String q = bare.group(1).toLowerCase(Locale.ROOT);
            double n = q.equals("half") ? count(lastItem, i) / 2 : number(q);
            events.add(new Event(i, lastItem, -n, null, 0).as("sold"));
        }
    }

    /** Where the last match of a pattern ends in a text, or -1. */
    private static int last(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int at = -1;
        while (m.find()) {
            at = m.end();
        }
        return at;
    }

    /**
     * The verb nearest the end of {@code before} that takes things out of the stock: sold, repaired, dropped; null when
     * the nearest verb is none of those.
     */
    private static String takenBy(String before) {
        String best = null;
        int at = -1;
        for (Pattern p : new Pattern[]{GIVES, WORKED, LOSES}) {
            Matcher m = p.matcher(before);
            while (m.find()) {
                if (m.end() > at) {
                    at = m.end();
                    best = m.group().toLowerCase(Locale.ROOT);
                }
            }
        }
        // a later verb that adds ("had", "bought") means the nearest isn't one that takes
        if (best != null && (last(HAS, before) > at && !before.substring(at).matches("(?is)\\s*(?:had|has|have)\\s*$") || last(BUYS, before) > at)) {
            return null;
        }
        return best;
    }

    private void currencyFrom(String symbol, String word) {
        if (!currency.isEmpty()) {
            return;
        }
        if (word != null) {
            currency = word.matches("(?i)naira") ? " naira" : " " + word.toLowerCase(Locale.ROOT);
        } else if (symbol != null) {
            currency = symbol;
        }
    }

    /** Who does what in a sentence: its first name, or the one the story is about for "she", "he". */
    private boolean isName(String w) {
        return !NOT_NAMES.contains(w) && named.contains(w);
    }

    private String subject(String s) {
        Matcher n = NAME.matcher(s);
        while (n.find()) {
            if (isName(n.group())) {
                // "Her friend Tunde, who …, bought": the name before the verb
                return n.group();
            }
        }
        return main;
    }

    /** How many of a thing there are just after a sentence (-1: all of the story). */
    double count(String item, int upTo) {
        double c = start.getOrDefault(item, 0.0);
        for (Event e : events) {
            if (e.item.equals(item) && (upTo < 0 || e.sentence < upTo || e.sentence == upTo)) {
                c += e.amount;
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ questions

    private static final Pattern HOURS = Pattern.compile("(?i)how many hours\\b.*\\b(open|work|stay|run|last|spend|trade)");
    private static final Pattern LEFT = Pattern.compile("(?i)how many\\s+(" + UNIT + ")(?<adj>(?:new|broken|unrepaired|repaired|damaged|faulty|used|fresh|old|ripe|spare|"
            + "unsold|remaining|working)\\s+)?" + ITEM + "\\s+(?:were|are|was|is|did .{1,20} have)\\s+(?:still\\s+)?(?:left|remaining|remain|unsold|in stock|on the shelf|"
            + "available|salvageable|usable|repairable|fixable|there|unrepaired|in the shop)"
            + "(?:\\s+(?:at|by|before|after)\\s+(?<when>noon|midday|closing(?: time)?|the end|the end of the day|night|evening|morning|afternoon))?");
    /** "How much money did Chinedu make from selling charging cables?": what the sales brought in. */
    private static final Pattern EARNED = Pattern.compile("(?i)how much(?: money)? did ([A-Z][a-z]+|she|he|they) (?:make|earn|get|take in|receive|collect|bring in)"
            + "(?:\\s+(?:in total|altogether))?(?:\\s+(?:from|by|on))?(?:\\s+(?:selling|the sale of|sales of|the sales of|the))?(?:\\s+(?:the|his|her|their))?"
            + "(?:\\s+(?!on\\b|in\\b|at\\b|today\\b|that\\b)(" + UNIT + ")" + ITEM + ")?");
    private static final Pattern PAID = Pattern.compile("(?i)how much(?: money)? did ([A-Z][a-z]+) (?:pay|spend)(?: for| on)?(?: the)?(?:\\s+(\\d[\\d,]*))?(?:\\s+(?!after\\b|with\\b|before\\b|in\\b|at\\b)(" + UNIT + ")" + ITEM
            + ")?(?:\\s+(after|with|before) the discount)?");
    private static final Pattern BOUGHT = Pattern.compile("(?i)how many\\s+(" + UNIT + ")" + ITEM + "\\s+did ([A-Z][a-z]+) (?:buy|get|take)");
    /** "How many cows does Tunde keep?", "how many cakes did Kemi bake?": what someone had. */
    private static final Pattern HAD = Pattern.compile("(?i)how many\\s+(" + UNIT + ")" + ITEM + "\\s+(?:does|did|do)\\s+([A-Z][a-z]+|she|he|they)\\s+"
            + "(?:have|keep|own|bring|carry|bake|make|grow|harvest|start with|get|pack)");
    private static final Pattern SOLD = Pattern.compile("(?i)how many\\s+(" + UNIT + ")" + ITEM + "\\s+did ([A-Z][a-z]+|she|he) sell");

    /** The question worked out from the story, with the working; null when it isn't one the story can answer this way. */
    public String answer(String question) {
        String q = question.trim();
        Matcher m;
        if ((m = HOURS.matcher(q)).find() && opens >= 0 && closes > opens) {
            int mins = closes - opens;
            String low = q.toLowerCase(Locale.ROOT);
            // working time is the day less its break; "how long is it open?" is the whole day
            boolean less = breakMins > 0 && breakMins < mins && (low.matches("(?s).*\\b(?:excluding|without|minus|less|not counting|not including|apart from|"
                    + "except)\\b.*\\bbreak\\b.*") || low.matches("(?s).*\\b(?:work|working|worked|actually)\\b.*") && !low.matches("(?s).*\\b(?:open|including)\\b.*"));
            if (less) {
                return hours(mins - breakMins) + ": from " + clock(opens) + " to " + clock(closes) + " is " + hours(mins) + ", less the " + breakMins
                        + "-minute " + breakName + ".";
            }
            return hours(mins) + ": from " + clock(opens) + " to " + clock(closes) + ".";
        }
        if ((m = LEFT.matcher(q)).find()) {
            String item = key(m.group(3));
            if (!start.containsKey(item)) {
                return null;
            }
            String adj = m.group("adj") == null ? "" : m.group("adj").trim().toLowerCase(Locale.ROOT) + " ";
            String when = m.group("when") == null ? null : m.group("when").toLowerCase(Locale.ROOT).replaceAll(" time$", "").replaceAll("^the end.*", "closing");
            int upTo = when == null || when.equals("closing") || when.equals("night") ? -1 : marks.getOrDefault(when, -1);
            if (when != null && !when.equals("closing") && !when.equals("night") && !marks.containsKey(when)) {
                return null;
            }
            double left = count(item, upTo);
            StringBuilder w = new StringBuilder(fmt(start.get(item)));
            for (Event e : events) {
                if (e.item.equals(item) && (upTo < 0 || e.sentence <= upTo)) {
                    String did = e.verb.isEmpty() || e.verb.equals("job") ? "" : " " + e.verb;
                    w.append(e.in() ? " + " : " − ").append(fmt(Math.abs(e.amount))).append(did).append(e.who == null ? "" : " (" + e.who + ")");
                }
            }
            return fmt(left) + " " + adj + shown(item, left) + (when == null ? "" : " at " + (when.equals("closing") ? "closing time" : when)) + ": " + w + " = " + fmt(left) + ".";
        }
        if ((m = EARNED.matcher(q)).find() && (m.group(1).matches("(?i)she|he|they") || m.group(1).equals(main) || staff.containsKey(m.group(1)))) {
            String item = m.group(3) == null ? null : key(m.group(3));
            if (item != null && !price.containsKey(item)) {
                item = null;
            }
            double total = 0;
            List<String> w = new ArrayList<String>();
            for (Event e : events) {
                boolean sale = e.verb.equals("sold") || e.who != null && !staff.containsKey(e.who) && !e.in();
                if (sale && (item == null || e.item.equals(item)) && price.containsKey(e.item)) {
                    double qty = -e.amount, full = qty * price.get(e.item), got = full * (1 - e.discount);
                    total += got;
                    w.add(fmt(qty) + " × " + money(price.get(e.item)) + " = " + money(full)
                            + (e.discount > 0 ? ", less " + fmt(e.discount * 100) + "% = " + money(got) : "") + (e.who == null ? "" : " (" + e.who + ")"));
                }
            }
            if (!w.isEmpty()) {
                String who = m.group(1).matches("(?i)she|he|they") ? main : m.group(1);
                return who + " made " + money(total) + (item == null ? "" : " from " + shown(item, 2)) + ": " + String.join("; ", w) + ".";
            }
        }
        if ((m = PAID.matcher(q)).find()) {
            String who = m.group(1);
            String item = m.group(4) == null ? null : key(m.group(4));
            double total = 0;
            List<String> w = new ArrayList<String>();
            for (Event e : events) {
                if (who.equals(e.who) && !e.in() && (item == null || e.item.equals(item)) && price.containsKey(e.item)) {
                    double each = price.get(e.item), qty = -e.amount;
                    double full = qty * each, paid = full * (1 - e.discount);
                    total += paid;
                    w.add(fmt(qty) + " × " + money(each) + " = " + money(full)
                            + (e.discount > 0 ? ", less " + fmt(e.discount * 100) + "% (" + money(full * e.discount) + ") = " + money(paid) : ""));
                }
            }
            if (w.isEmpty()) {
                return null;
            }
            return who + " paid " + money(total) + ": " + String.join("; ", w) + ".";
        }
        if ((m = BOUGHT.matcher(q)).find()) {
            String item = key(m.group(2)), who = m.group(3);
            double n = 0;
            for (Event e : events) {
                if (who.equals(e.who) && e.item.equals(item)) {
                    n += Math.abs(e.amount);
                }
            }
            return n == 0 ? null : who + " bought " + fmt(n) + " " + shown(item, n) + ".";
        }
        if ((m = HAD.matcher(q)).find()) {
            String item = key(m.group(2));
            if (!start.containsKey(item)) {
                return null;
            }
            double now = count(item, -1), had = start.get(item);
            return fmt(had) + " " + shown(item, had) + (now == had ? "." : " to start with; " + fmt(now) + " now.");
        }
        if ((m = SOLD.matcher(q)).find()) {
            String item = key(m.group(2));
            if (!start.containsKey(item)) {
                return null;
            }
            double sold = start.get(item) - count(item, -1);
            return fmt(sold) + " " + shown(item, sold) + " sold in all: " + fmt(start.get(item)) + " − " + fmt(count(item, -1)) + " left.";
        }
        return null;
    }

    /** The story in one sentence: who, where, and what happened, from its own facts. */
    public String gist() {
        if (main == null) {
            return null;
        }
        String role = null, place = null;
        Matcher r = Pattern.compile(main + "\\s+(?:is|was)\\s+an?\\s+([a-z]+(?:\\s+(?!in\\b|at\\b|from\\b|who\\b|and\\b)[a-z]+)?)(?:\\s+(?:in|at|from)\\s+([A-Z][a-z]+))?")
                .matcher(String.join(" ", sentences));
        if (r.find()) {
            role = r.group(1);
            place = r.group(2);
        }
        String runs = null;
        Matcher o = Pattern.compile(main + "\\s+(?:runs|owns|manages|operates|keeps|has)\\s+(an?\\s+[a-z]+(?:\\s+[a-z]+){0,3}?)\\s+(?:in|at)\\s+([A-Z][a-z]+)")
                .matcher(String.join(" ", sentences));
        if (role == null && o.find()) {
            runs = o.group(1);
            place = o.group(2);
        }
        List<String> sold = new ArrayList<String>();
        for (String item : start.keySet()) {
            if (count(item, -1) < start.get(item)) {
                sold.add(shown(item, 2));
            }
        }
        List<String> buyers = new ArrayList<String>();
        for (Event e : events) {
            if (e.who != null && !buyers.contains(e.who)) {
                buyers.add(e.who);
            }
        }
        // what was done, by its verb: repaired, sold
        List<String> fixed = new ArrayList<String>(), soldOnly = new ArrayList<String>();
        List<String> clients = new ArrayList<String>();
        for (Event e : events) {
            if (e.verb.matches("repair.*|fix.*|mend.*|servic.*") && !fixed.contains(shown(e.item, 2))) {
                fixed.add(shown(e.item, 2));
            }
            if (e.verb.equals("sold") && !soldOnly.contains(shown(e.item, 2))) {
                soldOnly.add(shown(e.item, 2));
            }
            if (e.verb.equals("job") && !clients.contains(e.who)) {
                clients.add(e.who);
            }
        }
        buyers.removeAll(staff.keySet());
        buyers.removeAll(clients);
        StringBuilder b = new StringBuilder(main);
        if (role != null) {
            b.append(", a ").append(role).append(place == null ? "" : " in " + place).append(",");
        } else if (runs != null) {
            b.append(", who runs ").append(runs).append(" in ").append(place).append(",");
        }
        if (!fixed.isEmpty()) {
            b.append(" repairs ").append(and(fixed)).append(soldOnly.isEmpty() ? "" : " and sells " + and(soldOnly));
        } else {
            b.append(sold.isEmpty() ? " has a busy day" : " sells " + and(sold));
        }
        if (!buyers.isEmpty()) {
            b.append(", including to ").append(and(buyers));
        }
        for (Map.Entry<String, String> st : staff.entrySet()) {
            b.append(", helped by ").append(staffPronoun).append(" ").append(st.getValue()).append(" ").append(st.getKey());
        }
        for (String c : clients) {
            boolean off = false;
            for (Event e : events) {
                off |= c.equals(e.who) && e.discount > 0;
            }
            b.append(", and ").append(off ? "gives a discount to " : "serves ").append(staff.isEmpty() ? "" : "the client ").append(c);
        }
        String all = String.join(" ", sentences).toLowerCase(Locale.ROOT);
        if (all.matches("(?s).*\\b(?:power|light|electricity)\\s+(?:went|goes|was)\\s+(?:out|off)\\b.*") && all.matches("(?s).*\\b(?:gen|generator)\\b.*")) {
            b.append(", and keeps working on a generator when the power goes out");
        }
        if (marks.containsKey("afternoon") || String.join(" ", sentences).toLowerCase(Locale.ROOT).contains("rain")) {
            b.append(String.join(" ", sentences).toLowerCase(Locale.ROOT).contains("rain") ? ", and keeps trading through the rain" : "");
        }
        return b.append(".").toString();
    }

    // ------------------------------------------------------------------ words and numbers

    static String key(String item) {
        String k = item == null ? "" : item.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+(for|to|from|at|in|on|and|before|after|by|with)\\b.*$", "")
                .replaceAll("^(?:the|her|his|their|more)\\s+", "");
        String irregular = IRREGULAR.get(k);
        if (irregular != null) {
            return irregular;
        }
        if (k.matches(".*(?:oes)$")) {
            return k.substring(0, k.length() - 2);
        }
        if (k.matches(".*[^s]ies$")) {
            return k.substring(0, k.length() - 3) + "y";
        }
        return k.endsWith("s") && !k.endsWith("ss") && k.length() > 3 ? k.substring(0, k.length() - 1) : k;
    }

    private static final Map<String, String> IRREGULAR = new java.util.HashMap<String, String>();

    static {
        String[][] pairs = {{"loaves", "loaf"}, {"leaves", "leaf"}, {"knives", "knife"}, {"wives", "wife"}, {"halves", "half"}, {"shelves", "shelf"},
            {"calves", "calf"}, {"children", "child"}, {"people", "person"}, {"men", "man"}, {"women", "woman"}, {"mice", "mouse"},
            {"geese", "goose"}, {"teeth", "tooth"}, {"feet", "foot"}, {"sheep", "sheep"}, {"fish", "fish"}, {"potatoes", "potato"},
            {"tomatoes", "tomato"}, {"mangoes", "mango"}};
        for (String[] p : pairs) {
            IRREGULAR.put(p[0], p[1]);
        }
    }

    private String shown(String item, double n) {
        if (n != 1) {
            for (Map.Entry<String, String> e : IRREGULAR.entrySet()) {
                if (e.getValue().equals(item) && !said.containsKey(item + "#unit")) {
                    String as = said.get(item);
                    if (as == null || !as.matches("(?i).*\\bof\\s+.*")) {
                        return e.getKey();
                    }
                }
            }
        }
        String as = said.get(item);
        if (as != null && as.matches("(?i).*\\bof\\s+.*")) {
            String unit = as.replaceFirst("(?i)\\s+of\\s+.*$", "");
            String rest = as.replaceFirst("(?i)^.*?\\s+of\\s+", "of ");
            String one = unit.replaceAll("s$", "");
            return (n == 1 ? one : one.endsWith("x") ? one + "es" : one + "s") + " " + rest;
        }
        if (n == 1) {
            return item;
        }
        return item.endsWith("o") ? item + "es" : item.endsWith("y") ? item.substring(0, item.length() - 1) + "ies" : item + "s";
    }

    private String money(double v) {
        return currency.startsWith(" ") ? fmt(v) + currency : currency + fmt(v);
    }

    private static String hours(int mins) {
        int h = mins / 60, m = mins % 60;
        return h + (h == 1 ? " hour" : " hours") + (m == 0 ? "" : " " + m + " minutes");
    }

    private static String clock(int mins) {
        int h = mins / 60, m = mins % 60;
        return (h % 12 == 0 ? 12 : h % 12) + (m == 0 ? "" : String.format(Locale.US, ":%02d", m)) + (h < 12 ? "am" : "pm");
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.US, "%,d", (long) Math.rint(v));
        }
        return String.format(Locale.US, "%,.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String and(List<String> l) {
        if (l.size() == 1) {
            return l.get(0);
        }
        return String.join(", ", l.subList(0, l.size() - 1)) + " and " + l.get(l.size() - 1);
    }

    static double number(String w) {
        String x = w.toLowerCase(Locale.ROOT).replace(",", "");
        if (x.matches("\\d+(?:\\.\\d+)?")) {
            return Double.parseDouble(x);
        }
        switch (x) {
            case "a": case "an": case "one": return 1;
            case "two": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            case "seven": return 7;
            case "eight": return 8;
            case "nine": return 9;
            case "ten": return 10;
            case "eleven": return 11;
            case "twelve": return 12;
            case "fifteen": return 15;
            case "twenty": return 20;
            case "thirty": return 30;
            case "forty": return 40;
            case "fifty": return 50;
            case "hundred": return 100;
            default: return 0;
        }
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static double fraction(String f) {
        switch (f.toLowerCase(Locale.ROOT)) {
            case "half": return 0.5;
            case "a third": case "one-third": return 1.0 / 3;
            case "a quarter": case "one-quarter": return 0.25;
            case "two-thirds": return 2.0 / 3;
            case "three-quarters": return 0.75;
            default: return 0;
        }
    }
}
