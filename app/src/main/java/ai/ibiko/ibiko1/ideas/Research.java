package ai.ibiko.ibiko1.ideas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import ai.ibiko.ibiko1.Plug;

/**
 * Research for thinking: the web searched in several phrasings (what a thing has, what to include, its parts, the best
 * of them), the top pages read for their headings and lists, gathered into a {@link Corpus}. What it found is kept, so
 * the next time (and offline) Ibiko starts from what it learned. Blocking: off the main thread.
 */
public final class Research {
    private Research() {
    }

    /** Pages read at most for one piece of thinking. */
    static final int PAGES = 6;

    /** Research on making something ("school" "website"). {@code step} hears each search, for the thinking shown. */
    public static Corpus forMaking(String subject, String goal, Consumer<String> step) {
        String g = goal == null || goal.isEmpty() ? "" : " " + goal;
        return gather(new String[]{subject + g + " features", "what to include on a " + subject + g, subject + g + " pages and sections",
                "best " + subject + g + " examples"}, step);
    }

    /** Research for a view on something: its pros and cons, benefits, risks. */
    public static Corpus forOpinion(String subject, Consumer<String> step) {
        return gather(new String[]{subject + " pros and cons", subject + " benefits", subject + " risks and problems"}, step);
    }

    static Corpus gather(String[] queries, Consumer<String> step) {
        Corpus c = new Corpus();
        if (Plug.web == null) {
            return c;
        }
        Set<String> hosts = new LinkedHashSet<String>();
        List<String> links = new ArrayList<String>();
        for (String q : queries) {
            if (step != null) {
                step.accept("Searching the web for: " + q);
            }
            String found;
            try {
                found = Plug.web.search(q);
            } catch (RuntimeException e) {
                found = null;
            }
            if (found == null) {
                continue;
            }
            c.addResearch(found);
            for (String l : Corpus.links(found)) {
                String h = host(l);
                if (hosts.add(h)) {
                    links.add(l); // one page a site: sources, not one site many times
                }
            }
        }
        int read = 0;
        for (String l : links) {
            if (read >= PAGES) {
                break;
            }
            String html;
            try {
                html = Plug.web.get(l);
            } catch (RuntimeException e) {
                html = null;
            }
            if (html != null && html.length() > 500) {
                if (step != null) {
                    step.accept("Reading " + host(l));
                }
                c.addPage(host(l), html);
                read++;
            }
        }
        return c;
    }

    static String host(String url) {
        return url.replaceFirst("^https?://(www\\.)?", "").replaceFirst("[/?#].*$", "").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ what was learned, kept

    /** The parts learned for a thing, kept in the brain folder: part, probability, sources. */
    public static void keep(File dir, String key, Ideation.Plan plan) {
        if (dir == null || plan == null || plan.parts.isEmpty()) {
            return;
        }
        StringBuilder b = new StringBuilder("# what research found for " + key + " (" + plan.sources + " sources)\n");
        for (Ideation.Part p : plan.parts) {
            b.append("part\t").append(p.key).append('\t').append(String.format(Locale.US, "%.3f", p.p)).append('\t').append(p.sources()).append('\n');
        }
        for (Ideation.Part p : plan.later) {
            b.append("later\t").append(p.key).append('\t').append(String.format(Locale.US, "%.3f", p.p)).append('\t').append(p.sources()).append('\n');
        }
        for (String w : plan.audience) {
            b.append("for\t").append(w).append('\n');
        }
        for (Ideation.Idea i : plan.ideas) {
            b.append("idea\t").append(i.title).append('\t').append(i.a).append('\t').append(i.b).append('\t').append(i.from).append('\n');
        }
        try {
            File f = file(dir, key);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept next time
        }
    }

    /** What was learned before for a thing, or null when nothing was. */
    public static Ideation.Plan recall(File dir, String key) {
        File f = dir == null ? null : file(dir, key);
        if (f == null || !f.isFile()) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            int n = 0;
            if (!lines.isEmpty()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d+) sources\\)").matcher(lines.get(0));
                n = m.find() ? Integer.parseInt(m.group(1)) : 0;
            }
            Ideation.Plan plan = Ideation.restored(n);
            for (String l : lines) {
                String[] c = l.split("\t", -1);
                if (c[0].equals("part") || c[0].equals("later")) {
                    Ideation.Part p = Ideation.restoredPart(c[1], Double.parseDouble(c[2]), Integer.parseInt(c[3]));
                    (c[0].equals("part") ? plan.parts : plan.later).add(p);
                } else if (c[0].equals("for")) {
                    plan.audience.add(c[1]);
                } else if (c[0].equals("idea") && c.length >= 5) {
                    plan.ideas.add(Ideation.restoredIdea(c[1], c[2], c[3], c[4]));
                }
            }
            return plan.parts.isEmpty() ? null : plan;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static File file(File dir, String key) {
        return new File(new File(dir, "ideas"), key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".tsv");
    }
}
