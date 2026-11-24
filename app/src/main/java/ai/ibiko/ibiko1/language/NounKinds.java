package ai.ibiko.ibiko1.language;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * What kind of thing a noun names, found by going up its broader terms in the dictionary ("dog" → canine → carnivore →
 * placental mammal → mammal → vertebrate → animal): person, animal, nature, substance, thing, place or abstract. The
 * figures of speech need it: "my boss is a dragon" says a person is an animal, so it can't be literal. Pure Java.
 */
public final class NounKinds {
    private NounKinds() {
    }

    private static final String[][] ROOTS = {
        {"person", "person|individual|someone|human|people|worker|adult|relative|leader|expert|professional|man|woman|child"},
        {"animal", "animal|beast|creature|mammal|bird|fish|reptile|insect|amphibian|vertebrate|invertebrate|canine|feline|carnivore|herbivore"},
        {"nature", "plant|tree|flower|weather|wind|atmospheric phenomenon|natural phenomenon|celestial body|star|body of water|"
                + "geological formation|natural elevation|light|precipitation|storm"},
        {"substance", "substance|matter|material|food|liquid|metal|chemical|fluid|solid|mineral|beverage|currency"},
        {"place", "location|region|area|place|building|structure|city|country|room|establishment"},
        {"thing", "artifact|artefact|device|instrument|container|vehicle|machine|tool|object|equipment|furniture|implement|covering"},
        {"abstract", "abstraction|attribute|feeling|emotion|state|concept|idea|time period|cognition|quality|event|act|activity|"
                + "communication|psychological feature|measure|relation|condition|knowledge|belief"},
    };

    /** The noun's kind, or null when its broader terms don't reach one within a few steps. */
    public static String of(String noun, Function<String, List<String>> broaderOf) {
        String start = noun.toLowerCase(Locale.ROOT).trim();
        Deque<String[]> queue = new ArrayDeque<String[]>();
        Set<String> seen = new HashSet<String>();
        queue.add(new String[]{start, "0"});
        while (!queue.isEmpty() && seen.size() < 40) {
            String[] at = queue.poll();
            String w = at[0];
            int depth = Integer.parseInt(at[1]);
            if (!seen.add(w)) {
                continue;
            }
            if (depth > 0) {
                String k = match(w);
                if (k != null) {
                    return k;
                }
            }
            if (depth >= 8) {
                continue;
            }
            List<String> up;
            try {
                up = broaderOf.apply(w);
            } catch (RuntimeException e) {
                continue;
            }
            for (String u : up) {
                queue.add(new String[]{u.toLowerCase(Locale.ROOT).trim(), String.valueOf(depth + 1)});
            }
        }
        return null;
    }

    static String match(String broader) {
        for (String[] r : ROOTS) {
            if (broader.matches("(?:.*\\s)?(?:" + r[1] + ")")) {
                return r[0];
            }
        }
        return null;
    }
}
