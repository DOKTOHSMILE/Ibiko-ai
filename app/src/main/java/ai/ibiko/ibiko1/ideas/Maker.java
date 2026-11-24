package ai.ibiko.ibiko1.ideas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import ai.ibiko.ibiko1.formulas.Sites;

/**
 * A website made from Ibiko's own thinking ({@link Ideation.Plan}): the parts research found most likely needed, then
 * its new ideas, each shown by the builder's working block for it (a form, a gallery, a calendar) or a paragraph in its
 * own words; and the reply that says how it got there: what the sources share, how likely each part is, who it's for,
 * what it came up with and what could come later. Pure Java.
 */
public final class Maker {
    private Maker() {
    }

    /** What was made, and what to say about it. */
    public static final class Made {
        public final Sites.Design design;
        public final String said;
        public final List<String> headings;

        Made(Sites.Design design, String said, List<String> headings) {
            this.design = design;
            this.said = said;
            this.headings = headings;
        }
    }

    /**
     * The site.
     *
     * @param what    what it is ("school")
     * @param meaning what a word means to Ibiko, or null
     * @param look    a kind it knows whose colours suit, or null
     * @param learned whether the plan is what was learned before (offline), not fresh research
     */
    public static Made site(Ideation.Plan plan, String what, Sites.Brief brief, Function<String, String> meaning, Sites.Kind look, Corpus c,
            boolean learned) {
        String name = brief.name.isEmpty() ? null : brief.name;
        String place = brief.place.isEmpty() ? null : brief.place;
        List<String[]> sections = new ArrayList<String[]>();
        Set<String> ids = new HashSet<String>();
        String purpose = "A " + what + " website, made from what " + (plan.audience.isEmpty() ? "visitors" : Writer.join(plan.audience))
                + " look for";
        sections.add(new String[]{"hero", purpose});
        List<String> headings = new ArrayList<String>();
        boolean contact = false;
        for (Ideation.Part p : plan.parts) {
            if (sections.size() > 9) {
                break;
            }
            String type = Writer.block(p.key);
            // a working block once under its own name ("booking"), so the top button finds it; again, under the part's
            String id = ids.add(type) && !type.equals("text") ? type : slug(p.key, ids);
            String text = type.equals("contact") || type.equals("booking") ? "" : Writer.say(p.key, name, place, plan.audience, meaning, c);
            sections.add(new String[]{id, p.title, type, text});
            headings.add(p.title);
            contact |= type.equals("contact");
        }
        for (Ideation.Idea idea : plan.ideas) {
            String type = Writer.block(idea.title);
            String id = ids.add(type) && !type.equals("text") ? type : slug(idea.title, ids);
            sections.add(new String[]{id, idea.title, type, Writer.say(idea.title, name, place, plan.audience, meaning, c)});
            headings.add(idea.title + " (my idea)");
        }
        if (!contact) {
            sections.add(new String[]{"contact", "Contact us", "contact", ""}); // every site needs a way to reach it
            headings.add("Contact us");
        }
        Sites.Design design = Sites.researched(what, purpose, sections, look, brief);
        StringBuilder said = new StringBuilder();
        said.append(learned ? "I built this from what I researched before on " + what + " websites (I'm offline now, so I couldn't look again)."
                : "I researched " + what + " websites first: " + plan.sources + " sources.");
        said.append(" What they share, and how likely each is needed:\n");
        for (Ideation.Part p : plan.parts.subList(0, Math.min(8, plan.parts.size()))) {
            said.append("• ").append(p.title).append(" — ").append(Math.round(p.p * 100)).append("% (").append(String.join("; ", p.why)).append(")\n");
        }
        if (!plan.audience.isEmpty()) {
            said.append("Who it's for: ").append(Writer.join(plan.audience)).append(".\n");
        }
        if (!plan.ideas.isEmpty()) {
            said.append("\nIdeas of my own, joining what goes together:\n");
            for (Ideation.Idea i : plan.ideas) {
                said.append("• ").append(i.title).append(": ").append(i.from).append(".\n");
            }
        }
        if (!plan.later.isEmpty()) {
            List<String> l = new ArrayList<String>();
            for (Ideation.Part p : plan.later) {
                l.add(p.title.toLowerCase(Locale.ROOT));
            }
            said.append("\nWhat it could grow into (fewer sites have these yet): ").append(String.join(", ", l)).append(".\n");
        }
        said.append("\nThe words are mine, not copied from any source; put your own details in.");
        return new Made(design, said.toString(), headings);
    }

    static String slug(String s, Set<String> ids) {
        String base = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "part";
        }
        String id = base;
        for (int i = 2; !ids.add(id); i++) {
            id = base + "-" + i;
        }
        return id;
    }
}
