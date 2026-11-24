package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Websites, understood and built: what kinds of website there are (a shop, a restaurant, a portfolio, a school…), what
 * each is for, the sections and features it needs, the design patterns that suit it and what it is usually built with
 * ({@code knowledge/web/sites.tsv}, coded); kinds explained and told apart; and a real site built for a request ("build
 * a website for my bakery called Sweet Crumbs in Lagos, WhatsApp 0803…"): one HTML file, responsive (phone first),
 * accessible, its sections chosen by the kind, the business's own name, place and number put in, working buttons (a
 * menu on phones, a form that opens WhatsApp or email). Pure Java.
 *
 * <pre>
 * W1  reads     build · explain a kind · tell two kinds apart · what any website is made of
 * W2  kind      the kind a request names (bakery → restaurant), or a business site when none does
 * W3  details   the name ("called X", "named X", in quotes), the place ("in Lagos"), a phone or WhatsApp number, an email
 * W4  builds    the page: header, the kind's sections in order, footer; its colours; the script for the menu and forms
 * </pre>
 */
public final class Sites {
    /** One kind of website, as the knowledge file has it. */
    public static final class Kind {
        public final String id, name, purpose, features, patterns, builtWith;
        final Pattern names;
        /** The sections: block and heading ("menu", "Our menu"), in order. */
        public final List<String[]> sections = new ArrayList<String[]>();
        /** Background, surface, accent, text. */
        public final String[] colours;

        Kind(String[] f) {
            id = f[0];
            names = Pattern.compile("(?i)\\b(?:" + f[1] + ")(?![\\p{L}])"); // whole words: "bar" is not "barber"
            name = f[2];
            purpose = f[3];
            for (String pair : f[4].split(";")) {
                String[] p = pair.split(":", 2);
                sections.add(new String[]{p[0].trim(), p.length > 1 ? p[1].trim() : p[0]});
            }
            features = f[5];
            patterns = f[6];
            builtWith = f[7];
            colours = f[8].split(",");
        }
    }

    private final List<Kind> kinds = new ArrayList<Kind>();

    public Sites(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                if (!line.startsWith("#") && f.length >= 9) {
                    kinds.add(new Kind(f));
                }
            }
        } catch (IOException ignored) {
            // none: the questions go on to the rest of Ibiko
        }
    }

    public int size() {
        return kinds.size();
    }

    /** W2: the kind a text names; null when it names none. The earliest named wins ("a shop for my salon" is a shop). */
    public Kind kind(String text) {
        Kind best = null;
        int at = Integer.MAX_VALUE;
        for (Kind k : kinds) {
            Matcher m = k.names.matcher(text);
            if (m.find() && m.start() < at) {
                best = k;
                at = m.start();
            }
        }
        return best;
    }

    Kind byId(String id) {
        for (Kind k : kinds) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------- W1: reading

    /** What a message asks about websites. */
    public static final class Ask {
        public enum Is { BUILD, EXPLAIN, COMPARE, MADE_OF }

        public final Is is;
        public final String text, a, b;

        Ask(Is is, String text, String a, String b) {
            this.is = is;
            this.text = text;
            this.a = a;
            this.b = b;
        }
    }

    private static final String SITE = "(?:web ?sites?|web ?pages?|sites?|landing pages?|online (?:shop|store)s?|blogs?|portfolios?)";
    private static final Pattern BUILD = Pattern.compile("(?i)^\\s*(?:please\\s+|can you\\s+|could you\\s+|i (?:want|need) (?:you to )?)?"
            + "(?:build|create|make|design|generate|code|develop|set up|put together)\\s+(?:me\\s+|us\\s+)?(?:a|an|the|my|our)?\\s*.{0,60}?" + SITE + "\\b.*$");
    private static final Pattern COMPARE = Pattern.compile("(?i)\\bdifference(?:s)? between (?:a |an |the )?(.{2,40}?)(?: web ?site| site)? and (?:a |an |the )?(.{2,40}?)(?: web ?sites?| sites?)?\\s*[?.!]*$"
            + "|^\\s*(.{2,30}?) (?:web ?site|site) (?:vs\\.?|versus|or) (?:a |an )?(.{2,30}?) (?:web ?site|site)\\s*[?.!]*$");
    private static final Pattern MADE_OF = Pattern.compile("(?i)^\\s*(?:what (?:is|are) (?:a )?web ?sites? made (?:of|up of)|how (?:is|are) (?:a )?web ?sites? (?:made|built)"
            + "|what (?:makes up|goes into) a web ?site|what (?:are )?the parts of a web ?site|how do web ?sites work)\\s*[?.!]*$");
    private static final Pattern EXPLAIN = Pattern.compile("(?i)^\\s*(?:what (?:is|are) (?:a |an )?(.{2,40}?) (?:web ?site|site)s?(?: made (?:of|up of))?"
            + "|what (?:does|should|do) (?:a |an )?(.{2,40}?) (?:web ?site|site)s? (?:have|need|include|contain)"
            + "|(?:what are )?the (?:features|sections|parts|pages) of (?:a |an )?(.{2,40}?) (?:web ?site|site)"
            + "|how (?:do i|to|should i) (?:design|plan|structure) (?:a |an )?(.{2,40}?) (?:web ?site|site))\\s*[?.!]*$");

    /** W1: what a message asks about websites; null when it doesn't. */
    public static Ask read(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        if (MADE_OF.matcher(m).matches()) {
            return new Ask(Ask.Is.MADE_OF, m, null, null);
        }
        Matcher c = COMPARE.matcher(m);
        if (c.find()) {
            String a = c.group(1) != null ? c.group(1) : c.group(3), b = c.group(2) != null ? c.group(2) : c.group(4);
            if (m.toLowerCase(Locale.ROOT).matches(".*\\b(?:web ?sites?|sites?|pages?|shops?|stores?|blogs?|portfolios?)\\b.*")) {
                return new Ask(Ask.Is.COMPARE, m, a, b);
            }
        }
        Matcher e = EXPLAIN.matcher(m);
        if (e.matches()) {
            for (int i = 1; i <= e.groupCount(); i++) {
                if (e.group(i) != null) {
                    return new Ask(Ask.Is.EXPLAIN, m, e.group(i), null);
                }
            }
        }
        if (BUILD.matcher(m).matches()) {
            return new Ask(Ask.Is.BUILD, m, null, null);
        }
        return null;
    }

    private static final Pattern LOOSE_BUILD = Pattern.compile("(?i)\\b(?:build|create|make|design|generate|code|develop|write|set up|put together"
            + "|i (?:want|need)|get me)\\b.{0,60}?\\b(?:web ?sites?|web ?pages?|landing pages?|home ?pages?|online (?:shop|store)s?)\\b");
    private static final Pattern WHAT_IS = Pattern.compile("(?i)^\\s*what(?:'s| is| are) (?:a |an )?web ?sites?\\s*[?.!]*$");

    /**
     * A looser reading, when a message carries a website's words but not the usual way of asking ("Write html for a bakery
     * website", "I need a landing page for my shop", "What is a website?"): a site to build, or what one is made of.
     */
    public static Ask loose(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        if (WHAT_IS.matcher(m).matches()) {
            return new Ask(Ask.Is.MADE_OF, m, null, null);
        }
        return LOOSE_BUILD.matcher(m).find() ? new Ask(Ask.Is.BUILD, m, null, null) : null;
    }

    // ------------------------------------------------------------------------------------------------- explaining

    /** "What is a website made of?": the parts every website has. */
    public static String madeOf() {
        return "Every website is made of the same layers:\n\n"
                + "• Structure (HTML): the content and its order: headings, text, images, links, forms.\n"
                + "• Look (CSS): colours, fonts, spacing and layout, and how it rearranges itself on a phone (responsive design).\n"
                + "• Behaviour (JavaScript): what happens when you tap: menus opening, forms checking, sliders, carts.\n"
                + "• Content and media: the words, photos and videos.\n"
                + "• A backend, when it needs one: a server and a database for logins, orders, payments or a blog's posts.\n"
                + "• Hosting and a domain: where the files live (Netlify, GitHub Pages, a host) and the name people type (yourname.com).\n\n"
                + "What changes from one kind to another is the sections, the features and the design. Ask me about a kind "
                + "(\"what does a restaurant website need?\"), compare two, or tell me to build one.";
    }

    /** A kind explained: what it is for, its sections, features, patterns and what it is built with. */
    public static String explain(Kind k) {
        List<String> heads = new ArrayList<String>();
        for (String[] s : k.sections) {
            heads.add(s[1]);
        }
        return "A " + k.name + " " + k.purpose + ".\n\n"
                + "• Sections: " + String.join(" · ", heads) + ".\n"
                + "• Features it needs: " + k.features + ".\n"
                + "• Design patterns that suit it: " + k.patterns + ".\n"
                + "• Usually built with: " + k.builtWith + ".\n\n"
                + "Say \"build me a " + k.name.replaceFirst(" or .*$", "").replaceFirst(" website$", "") + " website for …\" and I'll make one.";
    }

    /** Two kinds told apart: purpose, sections, features and design, side by side. */
    public static String compare(Kind a, Kind b) {
        return "A " + a.name + " " + a.purpose + "; a " + b.name + " " + b.purpose + ".\n\n"
                + "• " + cap(a.name) + ": sections " + headings(a) + ". Needs " + a.features + ". Design: " + a.patterns + ".\n\n"
                + "• " + cap(b.name) + ": sections " + headings(b) + ". Needs " + b.features + ". Design: " + b.patterns + ".\n\n"
                + "Built with: " + a.builtWith + " (" + a.name + "); " + b.builtWith + " (" + b.name + ").";
    }

    private static String headings(Kind k) {
        List<String> heads = new ArrayList<String>();
        for (String[] s : k.sections) {
            heads.add(s[1]);
        }
        return String.join(" · ", heads);
    }

    // ------------------------------------------------------------------------------------------------- W3: details

    /** What a build request says about the business: its name, kind of business, place, phone and email. */
    public static final class Brief {
        public String name = "", trade = "", place = "", phone = "", email = "";
    }

    private static final Pattern NAMED = Pattern.compile("(?:called|named|name is|brand(?:ed)? as|for)\\s+[\"“']?((?:\\p{Lu}[\\p{L}&'’.-]*|&)(?:\\s+(?:\\p{Lu}[\\p{L}&'’.-]*|&|of|and|the))*)");
    private static final Pattern QUOTED = Pattern.compile("[\"“]([^\"”]{2,40})[\"”]");
    private static final Pattern PLACE = Pattern.compile("\\b(?:in|at|based in|located in)\\s+((?:\\p{Lu}[\\p{L}'’.-]*)(?:\\s+\\p{Lu}[\\p{L}'’.-]*){0,2})");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s-]{7,16}\\d)");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]+@[\\w-]+\\.[\\w.]+)");
    private static final Pattern TRADE = Pattern.compile("(?i)\\bfor (?:my|our|a|an|the) ([a-z][a-z -]{2,30}?)(?= called| named| in | at |,|\\.|$| business| company| shop| store)");

    /** W3: the brief read from a build request. */
    public static Brief brief(String text) {
        Brief b = new Brief();
        Matcher q = QUOTED.matcher(text);
        Matcher n = NAMED.matcher(text);
        if (q.find()) {
            b.name = q.group(1).trim();
        } else {
            while (n.find()) {
                String cand = n.group(1).trim();
                if (!cand.matches("(?i)(my|our|a|an|the|me|us|it)\\b.*") && !cand.matches("(?i)whatsapp|instagram|facebook")) {
                    b.name = cand.replaceFirst("\\s+(?:in|at|and)$", "");
                    break;
                }
            }
        }
        Matcher p = PLACE.matcher(text);
        while (p.find()) {
            String place = p.group(1).trim();
            if (!place.equalsIgnoreCase(b.name) && !b.name.contains(place)) {
                b.place = place.replaceAll("[,\\s]+$", "");
                break;
            }
        }
        Matcher ph = PHONE.matcher(text);
        if (ph.find()) {
            b.phone = ph.group(1).replaceAll("[\\s-]", "");
        }
        Matcher em = EMAIL.matcher(text);
        if (em.find()) {
            b.email = em.group(1);
        }
        Matcher t = TRADE.matcher(text);
        if (t.find()) {
            b.trade = t.group(1).trim();
        }
        return b;
    }

    // ------------------------------------------------------------------------------------------------- W4: building

    /** A site as it is being made: its kind, what the person told about the business, its sections and colours (both can change). */
    public static final class Design {
        public final Kind kind;
        public final Brief brief;
        public final List<String[]> sections = new ArrayList<String[]>();
        public String[] colours;

        Design(Kind kind, Brief brief) {
            this.kind = kind;
            this.brief = brief;
            for (String[] sec : kind.sections) {
                sections.add(sec.clone());
            }
            colours = kind.colours.clone();
        }
    }

    /**
     * A design from Ibiko's own thinking ({@code ideas/Ideation}), not from the kinds it knows: its sections are what
     * research found and the new ideas it made, each {id, heading, block, text}: the block is which working part shows it
     * ("contact": a form; "text": Ibiko's own paragraph), the text Ibiko's own words. The look is a kind's it knows, when
     * one fits, for its colours only.
     */
    public static Design researched(String what, String purpose, List<String[]> sections, Kind look, Brief b) {
        String colours = look == null ? "#fbfaf7,#ffffff,#1f6f5c,#1d2421" : String.join(",", look.colours);
        StringBuilder spec = new StringBuilder();
        for (String[] sec : sections) {
            spec.append(spec.length() == 0 ? "" : ";").append(sec[0]).append(':').append(sec[1].replace(";", ",").replace(":", " "));
        }
        Kind k = new Kind(new String[]{"researched", Pattern.quote(what), what + " website", purpose, spec.toString(), "", "", "", colours});
        Design d = new Design(k, b);
        d.sections.clear();
        d.sections.addAll(sections);
        return d;
    }

    /** A design for a kind and a brief, as the kind lays it out. */
    public static Design design(Kind k, Brief b) {
        return new Design(k, b);
    }

    /** W4: the whole site, one HTML file. */
    public static String build(Kind k, Brief b) {
        return build(new Design(k, b));
    }

    /** W4: the whole site of a design, one HTML file. */
    public static String build(Design d) {
        Kind k = d.kind;
        Brief b = d.brief;
        String name = b.name.isEmpty() ? (b.trade.isEmpty() ? "Your business" : cap(b.trade)) : b.name;
        String[] c = d.colours;
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(esc(name)).append(b.place.isEmpty() ? "" : " · " + esc(b.place)).append("</title>\n")
                .append("<meta name=\"description\" content=\"").append(esc(name)).append(": ").append(esc(k.name))
                .append(b.place.isEmpty() ? "" : " in " + esc(b.place)).append(".\">\n<style>\n").append(css(c)).append("</style>\n</head>\n<body>\n");
        // header: the name, the sections as links, a menu button on phones
        h.append("<header class=\"top\">\n  <a class=\"brand\" href=\"#top\">").append(esc(name)).append("</a>\n")
                .append("  <button class=\"menu\" aria-expanded=\"false\" aria-controls=\"nav\">Menu</button>\n  <nav id=\"nav\">\n");
        for (String[] s : d.sections) {
            if (!s[0].equals("hero")) {
                h.append("    <a href=\"#").append(s[0]).append("\">").append(esc(short_(s[1]))).append("</a>\n");
            }
        }
        h.append("  </nav>\n</header>\n<main id=\"top\">\n");
        for (String[] s : d.sections) {
            if (s.length < 4) {
                h.append(block(s[0], s[1], k, b, name));
                continue;
            }
            // a researched section: its working block (or Ibiko's own paragraph), under its own anchor
            String type = s[2], text = s[3];
            if (type.equals("text")) {
                h.append(section(s[0], esc(s[1]), "<p>" + esc(text) + "</p>"));
                continue;
            }
            String html = block(type, s[1], k, b, name).replaceFirst("id=\"" + Pattern.quote(type) + "\"", "id=\"" + s[0] + "\"");
            if (text != null && !text.isEmpty()) {
                html = html.replaceFirst("</h2>\n", "</h2>\n  <p>" + Matcher.quoteReplacement(esc(text)) + "</p>\n");
            }
            h.append(html);
        }
        h.append("</main>\n<footer>\n  <p>&copy; <span id=\"year\"></span> ").append(esc(name))
                .append(b.place.isEmpty() ? "" : " · " + esc(b.place)).append("</p>\n</footer>\n");
        if (!b.phone.isEmpty()) {
            h.append("<a class=\"chat\" href=\"https://wa.me/").append(wa(b.phone)).append("\" aria-label=\"Chat on WhatsApp\">WhatsApp</a>\n");
        }
        h.append("<script>\n").append(js(b)).append("</script>\n</body>\n</html>\n");
        return h.toString();
    }

    private static String block(String id, String heading, Kind k, Brief b, String name) {
        String hd = esc(heading);
        String place = b.place.isEmpty() ? "" : " in " + esc(b.place);
        switch (id) {
            case "hero":
                return "<section class=\"hero\" id=\"hero\">\n  <h1>" + esc(name) + "</h1>\n  <p class=\"lead\">" + hd + place + ".</p>\n"
                        + "  <p><a class=\"button\" href=\"#" + action(k) + "\">" + esc(actionLabel(k)) + "</a></p>\n</section>\n";
            case "about":
                return section(id, hd, "<p>" + esc(name) + " is a " + esc(b.trade.isEmpty() ? k.name.replaceFirst(" website$", "") : b.trade)
                        + place + ". Tell visitors who you are, what makes you different, and why they can trust you: two or three short sentences.</p>");
            case "services":
            case "features":
            case "programmes":
            case "skills":
                return section(id, hd, cards(new String[][]{{"First " + noun(id), "What it is and who it's for."},
                        {"Second " + noun(id), "A short line on why it matters."}, {"Third " + noun(id), "Keep each one to a sentence."}}));
            case "menu":
                return section(id, hd, "<ul class=\"menu-list\">\n" + item("Signature dish", "A line about it") + item("House favourite", "A line about it")
                        + item("Drinks", "Fresh and cold") + item("Sides", "Small plates to share") + "</ul>");
            case "products":
            case "listings":
                StringBuilder p = new StringBuilder("<div class=\"grid\">\n");
                for (int i = 1; i <= 6; i++) {
                    p.append("  <article class=\"card product\"><div class=\"photo\" role=\"img\" aria-label=\"Photo\"></div><h3>")
                            .append(id.equals("listings") ? "Listing " : "Product ").append(i).append("</h3><p class=\"price\">Price</p>")
                            .append("<a class=\"button small\" href=\"#contact\" data-ask=\"").append(id.equals("listings") ? "Listing " : "Product ").append(i)
                            .append("\">").append(id.equals("listings") ? "Enquire" : "Order").append("</a></article>\n");
                }
                return section(id, hd, p.append("</div>").toString());
            case "gallery":
                StringBuilder g = new StringBuilder("<div class=\"gallery\">\n");
                for (int i = 1; i <= 6; i++) {
                    g.append("  <div class=\"photo\" role=\"img\" aria-label=\"Photo ").append(i).append("\"></div>\n");
                }
                return section(id, hd, g.append("</div>").toString());
            case "testimonials":
                return section(id, hd, "<div class=\"grid\">\n  <blockquote class=\"card\">“Exactly what we needed.”<cite>A happy customer</cite></blockquote>\n"
                        + "  <blockquote class=\"card\">“Quick, friendly and good value.”<cite>A regular</cite></blockquote>\n"
                        + "  <blockquote class=\"card\">“I tell everyone about them.”<cite>A client</cite></blockquote>\n</div>");
            case "pricing":
                return section(id, hd, "<div class=\"grid\">\n" + plan("Starter", "For trying it out", false) + plan("Standard", "Most people choose this", true)
                        + plan("Premium", "Everything included", false) + "</div>");
            case "faq":
                return section(id, hd, "<details><summary>How do I order or book?</summary><p>Use the form below or message us on WhatsApp.</p></details>\n"
                        + "<details><summary>Where are you?</summary><p>" + (b.place.isEmpty() ? "Add your address here." : "We are in " + esc(b.place) + ".")
                        + "</p></details>\n<details><summary>How do I pay?</summary><p>Say how people pay you: transfer, card, cash.</p></details>");
            case "hours":
                return section(id, hd, "<table class=\"hours\"><tr><th>Monday – Friday</th><td>8:00 – 20:00</td></tr>"
                        + "<tr><th>Saturday</th><td>9:00 – 21:00</td></tr><tr><th>Sunday</th><td>12:00 – 18:00</td></tr></table>");
            case "schedule":
                return section(id, hd, "<ol class=\"steps\"><li><strong>First</strong> What happens first, and when.</li>"
                        + "<li><strong>Next</strong> The middle of it.</li><li><strong>Then</strong> How it ends.</li></ol>");
            case "how":
                return section(id, hd, "<ol class=\"steps\"><li><strong>Tell us</strong> what you need.</li><li><strong>We get to work</strong> and keep you posted.</li>"
                        + "<li><strong>You enjoy</strong> the result.</li></ol>");
            case "team":
                return section(id, hd, cards(new String[][]{{"Name", "Role · a line about them"}, {"Name", "Role · a line about them"}, {"Name", "Role · a line about them"}}));
            case "stats":
                return section(id, hd, "<div class=\"stats\"><p><strong>10+</strong> years</p><p><strong>500+</strong> customers</p><p><strong>4.9★</strong> rating</p></div>");
            case "posts":
                return section(id, hd, cards(new String[][]{{"A first post", "A line that makes people want to read it. · 3 min read"},
                        {"A second post", "Another line. · 5 min read"}, {"A third post", "And another. · 4 min read"}}));
            case "countdown":
                return section(id, hd, "<p class=\"countdown\" id=\"countdown\" data-date=\"\">Set the date in the page (data-date=\"2026-12-31T18:00\")</p>");
            case "donate":
                return section(id, hd, "<p>Every gift helps. Choose an amount, or give what you can.</p><p><a class=\"button\" href=\"#contact\" data-ask=\"I'd like to give\">Donate</a></p>");
            case "newsletter":
                return section(id, hd, "<form class=\"inline\" data-send=\"subscribe\"><label for=\"nl\">Email</label><input id=\"nl\" type=\"email\" required placeholder=\"you@example.com\"><button class=\"button\">Subscribe</button></form>");
            case "cta":
                return "<section class=\"cta\" id=\"cta\">\n  <h2>" + hd + "</h2>\n  <p><a class=\"button\" href=\"#contact\">" + esc(actionLabel(k)) + "</a></p>\n</section>\n";
            case "rsvp":
            case "booking":
            case "contact":
                return section(id, hd, form(id, b));
            default:
                return section(id, hd, "<p>Add this section's words here.</p>");
        }
    }

    private static String section(String id, String heading, String inner) {
        return "<section id=\"" + id + "\">\n  <h2>" + heading + "</h2>\n  " + inner.replace("\n", "\n  ").trim() + "\n</section>\n";
    }

    private static String cards(String[][] items) {
        StringBuilder s = new StringBuilder("<div class=\"grid\">\n");
        for (String[] i : items) {
            s.append("  <article class=\"card\"><h3>").append(esc(i[0])).append("</h3><p>").append(esc(i[1])).append("</p></article>\n");
        }
        return s.append("</div>").toString();
    }

    private static String item(String name, String about) {
        return "  <li><span><strong>" + esc(name) + "</strong><br><small>" + esc(about) + "</small></span><span class=\"price\">Price</span></li>\n";
    }

    private static String plan(String name, String about, boolean best) {
        return "  <article class=\"card plan" + (best ? " best" : "") + "\"><h3>" + name + "</h3><p class=\"price\">Price</p><p>" + about
                + "</p><a class=\"button small\" href=\"#contact\" data-ask=\"" + name + " plan\">Choose " + name + "</a></article>\n";
    }

    private static String form(String id, Brief b) {
        String extra = id.equals("booking") ? "<label for=\"f-date\">Date</label><input id=\"f-date\" name=\"date\" type=\"date\" required>\n"
                + "<label for=\"f-time\">Time</label><input id=\"f-time\" name=\"time\" type=\"time\">\n"
                : id.equals("rsvp") ? "<label for=\"f-guests\">Guests</label><input id=\"f-guests\" name=\"guests\" type=\"number\" min=\"1\" value=\"1\">\n" : "";
        String reach = (b.phone.isEmpty() ? "" : "<p>Call <a href=\"tel:" + esc(b.phone) + "\">" + esc(b.phone) + "</a>"
                + " or <a href=\"https://wa.me/" + wa(b.phone) + "\">message us on WhatsApp</a>.</p>\n")
                + (b.email.isEmpty() ? "" : "<p>Email <a href=\"mailto:" + esc(b.email) + "\">" + esc(b.email) + "</a>.</p>\n");
        return reach + "<form data-send=\"" + id + "\">\n<label for=\"f-name\">Your name</label><input id=\"f-name\" name=\"name\" required autocomplete=\"name\">\n"
                + extra + "<label for=\"f-msg\">Message</label><textarea id=\"f-msg\" name=\"message\" rows=\"4\" required></textarea>\n"
                + "<button class=\"button\" type=\"submit\">" + (id.equals("booking") ? "Book" : id.equals("rsvp") ? "Send RSVP" : "Send") + "</button>\n</form>";
    }

    private static String action(Kind k) {
        for (String[] s : k.sections) {
            if (s[0].matches("booking|rsvp|products|listings|menu|donate|pricing")) {
                return s[0];
            }
        }
        return "contact";
    }

    private static String actionLabel(Kind k) {
        switch (action(k)) {
            case "booking": return "Book now";
            case "rsvp": return "RSVP";
            case "products": return "Shop now";
            case "listings": return "See listings";
            case "menu": return "See the menu";
            case "donate": return "Donate";
            case "pricing": return "See plans";
            default: return "Contact us";
        }
    }

    private static String noun(String id) {
        switch (id) {
            case "features": return "feature";
            case "programmes": return "programme";
            case "skills": return "skill";
            default: return "service";
        }
    }

    private static String css(String[] c) {
        return ":root{--bg:" + c[0] + ";--surface:" + c[1] + ";--accent:" + c[2] + ";--text:" + c[3] + ";--radius:14px}\n"
                + "*{box-sizing:border-box}html{scroll-behavior:smooth}\n"
                + "body{margin:0;font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--text)}\n"
                + "a{color:var(--accent)}img{max-width:100%}\n"
                + ".top{position:sticky;top:0;z-index:10;display:flex;align-items:center;justify-content:space-between;gap:1rem;padding:.8rem 1.2rem;"
                + "background:var(--bg);border-bottom:1px solid color-mix(in srgb,var(--text) 12%,transparent)}\n"
                + ".brand{font-weight:800;font-size:1.15rem;text-decoration:none;color:var(--text)}\n"
                + "nav{display:flex;gap:1rem;flex-wrap:wrap}nav a{text-decoration:none;color:var(--text);opacity:.85}nav a:hover{opacity:1;color:var(--accent)}\n"
                + ".menu{display:none;background:none;border:1px solid currentColor;color:var(--text);border-radius:8px;padding:.4rem .8rem}\n"
                + "section{max-width:1080px;margin:0 auto;padding:3.5rem 1.2rem}h1,h2,h3{line-height:1.2}h2{font-size:1.8rem;margin-top:0}\n"
                + ".hero{text-align:center;padding:5rem 1.2rem}.hero h1{font-size:clamp(2.2rem,6vw,3.6rem);margin:0 0 .6rem}.lead{font-size:1.2rem;opacity:.85}\n"
                + ".button{display:inline-block;background:var(--accent);color:var(--bg);font-weight:700;text-decoration:none;border:0;border-radius:999px;"
                + "padding:.8rem 1.4rem;cursor:pointer}.button.small{padding:.5rem 1rem;font-size:.9rem}\n"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:1rem}\n"
                + ".card{background:var(--surface);border-radius:var(--radius);padding:1.2rem}.card h3{margin-top:0}\n"
                + ".plan.best{outline:2px solid var(--accent)}.price{font-weight:800;color:var(--accent)}\n"
                + ".photo{aspect-ratio:4/3;border-radius:var(--radius);background:linear-gradient(135deg,var(--surface),color-mix(in srgb,var(--accent) 35%,var(--surface)))}\n"
                + ".product .photo{margin-bottom:.8rem}.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:.6rem}\n"
                + ".menu-list{list-style:none;padding:0}.menu-list li{display:flex;justify-content:space-between;gap:1rem;padding:.8rem 0;"
                + "border-bottom:1px dashed color-mix(in srgb,var(--text) 25%,transparent)}\n"
                + "blockquote{margin:0}cite{display:block;margin-top:.6rem;opacity:.7;font-style:normal}\n"
                + ".stats{display:flex;gap:2rem;flex-wrap:wrap}.stats strong{display:block;font-size:2rem;color:var(--accent)}\n"
                + ".steps li{margin:.6rem 0}.hours th{text-align:left;padding:.3rem 1.5rem .3rem 0;font-weight:600}\n"
                + "details{background:var(--surface);border-radius:var(--radius);padding:.9rem 1.1rem;margin:.6rem 0}summary{cursor:pointer;font-weight:600}\n"
                + ".cta{text-align:center;background:var(--surface);border-radius:var(--radius)}\n"
                + "form{display:grid;gap:.5rem;max-width:520px}form.inline{grid-template-columns:1fr auto;align-items:end}form.inline label{grid-column:1/-1}\n"
                + "input,textarea{font:inherit;padding:.7rem;border-radius:10px;border:1px solid color-mix(in srgb,var(--text) 25%,transparent);"
                + "background:var(--bg);color:var(--text)}\n"
                + ":focus-visible{outline:3px solid var(--accent);outline-offset:2px}\n"
                + "footer{text-align:center;padding:2rem 1rem;opacity:.75}\n"
                + ".chat{position:fixed;right:1rem;bottom:1rem;background:#25d366;color:#fff;font-weight:700;text-decoration:none;border-radius:999px;"
                + "padding:.8rem 1.1rem;box-shadow:0 6px 20px rgba(0,0,0,.25)}\n"
                + "@media (max-width:720px){.menu{display:block}nav{display:none;position:absolute;top:100%;left:0;right:0;flex-direction:column;"
                + "padding:1rem 1.2rem;background:var(--bg)}nav.open{display:flex}}\n"
                + "@media (prefers-reduced-motion:reduce){html{scroll-behavior:auto}}\n";
    }

    private static String js(Brief b) {
        String send = !b.phone.isEmpty() ? "'https://wa.me/" + wa(b.phone) + "?text='+encodeURIComponent(text)"
                : !b.email.isEmpty() ? "'mailto:" + b.email + "?subject=Website%20message&body='+encodeURIComponent(text)" : "null";
        return "document.getElementById('year').textContent=new Date().getFullYear();\n"
                + "const menu=document.querySelector('.menu'),nav=document.getElementById('nav');\n"
                + "menu.addEventListener('click',()=>{const open=nav.classList.toggle('open');menu.setAttribute('aria-expanded',open);});\n"
                + "nav.querySelectorAll('a').forEach(a=>a.addEventListener('click',()=>{nav.classList.remove('open');menu.setAttribute('aria-expanded',false);}));\n"
                + "// a button that names what it's for (\"Product 3\") puts it in the message\n"
                + "document.querySelectorAll('[data-ask]').forEach(a=>a.addEventListener('click',()=>{const m=document.getElementById('f-msg');"
                + "if(m)m.value='About: '+a.dataset.ask+'\\n';}));\n"
                + "// forms send what was filled in to WhatsApp or email: no server needed\n"
                + "document.querySelectorAll('form[data-send]').forEach(f=>f.addEventListener('submit',e=>{e.preventDefault();"
                + "const text=[...new FormData(f).entries()].map(([k,v])=>k+': '+v).join('\\n')||f.querySelector('input').value;"
                + "const to=" + send + ";if(to){window.open(to,'_blank');}else{alert('Thank you! Add your WhatsApp number or email to this page so messages reach you.');}f.reset();}));\n"
                + "// the countdown, when the page has a date\n"
                + "const cd=document.getElementById('countdown');if(cd&&cd.dataset.date){const t=new Date(cd.dataset.date);"
                + "const tick=()=>{const s=Math.max(0,(t-new Date())/1000);cd.textContent=Math.floor(s/86400)+' days, '+Math.floor(s%86400/3600)+' hours to go';};"
                + "tick();setInterval(tick,60000);}\n";
    }

    /** A number for wa.me: digits only, a Nigerian 0… number made international (234…). */
    static String wa(String phone) {
        String d = phone.replaceAll("[^0-9]", "");
        return d.startsWith("0") && d.length() == 11 ? "234" + d.substring(1) : d;
    }

    private static String short_(String heading) {
        String h = heading.replaceFirst("(?i)^(our|the|what we|about|book an?|book your|find|get|let's|say|start a|talk to|why)\\s+", "");
        return h.isEmpty() ? heading : cap(h);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------------------------------------- W5: changes

    /** The sections that can be added, by the words people use for them: block, heading. */
    private static final String[][] ADDABLE = {
        {"pricing|prices|plans|packages", "pricing", "Prices"}, {"faq|questions|frequently asked", "faq", "Questions"},
        {"gallery|photos|pictures|portfolio", "gallery", "Gallery"}, {"testimonials?|reviews?", "testimonials", "What people say"},
        {"team|staff|people", "team", "Our team"}, {"menu", "menu", "Our menu"}, {"contact(?: form)?", "contact", "Contact us"},
        {"newsletter|mailing list|subscribe", "newsletter", "Stay in touch"}, {"booking|appointments?|reservations?", "booking", "Book now"},
        {"hours|opening hours|opening times", "hours", "Opening hours"}, {"services", "services", "What we do"},
        {"products|shop|store", "products", "Products"}, {"blog|posts|articles|news", "posts", "Latest posts"},
        {"stats|numbers|figures", "stats", "In numbers"}, {"countdown", "countdown", "Counting down"}, {"about(?: us)?", "about", "About us"},
        {"how it works|steps|process", "how", "How it works"}, {"donate|donations|give", "donate", "Give"}, {"features", "features", "Features"}};
    private static final Map<String, String> COLOURS = new java.util.LinkedHashMap<String, String>();

    static {
        String[][] c = {{"blue", "#2563eb"}, {"navy", "#1e3a8a"}, {"red", "#dc2626"}, {"green", "#16a34a"}, {"purple", "#7c3aed"},
            {"orange", "#ea580c"}, {"pink", "#db2777"}, {"gold", "#ca8a04"}, {"yellow", "#eab308"}, {"teal", "#0d9488"},
            {"brown", "#92400e"}, {"black", "#111111"}, {"grey", "#6b7280"}, {"gray", "#6b7280"}};
        for (String[] x : c) {
            COLOURS.put(x[0], x[1]);
        }
    }

    private static final Pattern ADD = Pattern.compile("(?i)\\b(?:add|include|put in|i (?:want|need)|give (?:it|me))\\s+(?:a |an |some |the |my )?(.{2,30}?)(?:\\s+(?:section|part|page|block))?(?:\\s+(?:to it|too|as well))?\\s*[.!?]*$");
    private static final Pattern REMOVE = Pattern.compile("(?i)\\b(?:remove|delete|take out|drop|get rid of|no)\\s+(?:the |a |my )?(.{2,30}?)(?:\\s+(?:section|part|page|block))?\\s*[.!?]*$");
    private static final Pattern MOVE = Pattern.compile("(?i)\\b(?:move|put)\\s+(?:the )?(.{2,30}?)(?:\\s+section)?\\s+(?:to the |at the )?(top|first|bottom|end|last)\\b");
    private static final Pattern COLOUR = Pattern.compile("(?i)\\b(?:make it|make the (?:site|page|website)|change (?:the )?colou?rs? to|colou?r it|use)\\s+(dark|light|[a-z]+)\\b");
    private static final Pattern RENAME = Pattern.compile("(?i)\\b(?:call it|name it|rename it(?: to)?|change the name to|the name is)\\s+[\"“']?(.{2,40}?)[\"”']?\\s*[.!]*$");
    private static final Pattern NUMBER = Pattern.compile("(?i)\\b(?:my (?:whatsapp|number|phone)(?: number)? is|add my (?:whatsapp|number|phone)(?: number)?|whatsapp|phone)\\s*:?\\s*(\\+?\\d[\\d\\s-]{7,16}\\d)");

    /** W5: whether a message asks to change the site just made. */
    public static boolean asksChange(String message) {
        String m = message == null ? "" : message.trim();
        Matcher x;
        if (RENAME.matcher(m).find() || NUMBER.matcher(m).find()) {
            return true;
        }
        if ((x = COLOUR.matcher(m)).find() && colourOf(x.group(1)) != null) {
            return true;
        }
        if ((x = MOVE.matcher(m)).find() && blockFor(x.group(1)) != null) {
            return true;
        }
        if ((x = REMOVE.matcher(m)).find() && blockFor(x.group(1)) != null) {
            return true;
        }
        return (x = ADD.matcher(m)).find() && blockFor(x.group(1)) != null;
    }

    private static String[] blockFor(String words) {
        String w = words == null ? "" : words.trim().toLowerCase(Locale.ROOT).replaceFirst("^(?:a|an|the|some|my)\\s+", "");
        for (String[] a : ADDABLE) {
            if (w.matches("(?:" + a[0] + ")(?:\\s+(?:section|part|page|block))?")) {
                return a;
            }
        }
        return null;
    }

    private static String colourOf(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return w.equals("dark") || w.equals("light") ? w : COLOURS.get(w);
    }

    /** W5: the change asked for, made to the design; what changed in words, or null when nothing could be done. */
    public static String change(Design d, String message) {
        String m = message.trim();
        List<String> done = new ArrayList<String>();
        Matcher x;
        if ((x = RENAME.matcher(m)).find()) {
            d.brief.name = x.group(1).trim();
            done.add("named it " + d.brief.name);
        }
        if ((x = NUMBER.matcher(m)).find()) {
            d.brief.phone = x.group(1).replaceAll("[\\s-]", "");
            done.add("put in " + d.brief.phone + " (calls and WhatsApp; the forms send to it)");
        }
        if ((x = COLOUR.matcher(m)).find()) {
            String c = colourOf(x.group(1));
            if ("dark".equals(c)) {
                d.colours = new String[]{"#0f172a", "#1e293b", d.colours[2], "#f8fafc"};
                done.add("made it dark");
            } else if ("light".equals(c)) {
                d.colours = new String[]{"#ffffff", "#f1f5f9", d.colours[2], "#0f172a"};
                done.add("made it light");
            } else if (c != null) {
                d.colours = new String[]{d.colours[0], d.colours[1], c, d.colours[3]};
                done.add("made its colour " + x.group(1).toLowerCase(Locale.ROOT));
            }
        }
        if ((x = MOVE.matcher(m)).find()) {
            String[] a = blockFor(x.group(1));
            int at = a == null ? -1 : indexOf(d, a[1]);
            if (at >= 0) {
                String[] sec = d.sections.remove(at);
                boolean top = x.group(2).toLowerCase(Locale.ROOT).matches("top|first");
                d.sections.add(top ? Math.min(1, d.sections.size()) : d.sections.size(), sec);
                done.add("moved " + sec[1] + (top ? " to the top" : " to the end"));
            }
        } else if ((x = REMOVE.matcher(m)).find()) {
            String[] a = blockFor(x.group(1));
            int at = a == null ? -1 : indexOf(d, a[1]);
            if (at > 0) { // the opening (hero) stays
                done.add("took out " + d.sections.remove(at)[1]);
            }
        } else if ((x = ADD.matcher(m)).find()) {
            String[] a = blockFor(x.group(1));
            if (a != null && indexOf(d, a[1]) < 0) {
                int end = d.sections.size(); // before the form that ends the page (contact, booking, RSVP)
                while (end > 1 && d.sections.get(end - 1)[0].matches("contact|booking|rsvp|cta")) {
                    end--;
                }
                String[] sec = {a[1], a[2]};
                d.sections.add(end, sec);
                done.add("added " + a[2]);
            } else if (a != null) {
                done.add("it already has " + d.sections.get(indexOf(d, a[1]))[1]);
            }
        }
        return done.isEmpty() ? null : String.join("; ", done);
    }

    private static int indexOf(Design d, String block) {
        for (int i = 0; i < d.sections.size(); i++) {
            if (d.sections.get(i)[0].equals(block)) {
                return i;
            }
        }
        return -1;
    }
}
