package ai.ibiko.intelligence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small deterministic pre-processor for the local model. It produces evidence, not answers
 * and never executes capabilities. Heuristic POS labels are intentionally marked as inferred.
 */
public final class UnderstandingEngine {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*");
    private static final Set<String> PRONOUNS = set("i me my mine we us our ours you your yours he him his she her hers it its they them their theirs this that these those");
    private static final Set<String> DETERMINERS = set("a an the this that these those some any each every");
    private static final Set<String> PREPOSITIONS = set("in on at under over beside between behind before after into from to with without by for of about across through");
    private static final Set<String> AUX = set("am is are was were be been being have has had do does did can could will would shall should may might must");
    private static final Set<String> COMMON_VERBS = set("be have do make build create use mean need want know understand see find read write run move cause contain include support connect store learn explain compare help become work");
    private static final Set<String> COMMON_NOUNS = set("engine object box person people thing system model language word sentence file phone vehicle motorcycle car book data knowledge user assistant application component function part time place country city idea question answer relationship cause effect event message request command knowledge weapon weapons battery wheel wheels brake keyboard screen door animal river bank mouse component device camera microphone speaker sensor motor robot tree plant water food person people customer doctor teacher student house building room road map book phone car cars vehicle vehicles engine engines object objects data time place language computer software hardware file folder memory conversation context pronoun verb noun adjective adverb sentence paragraph document image audio video location direction distance event meeting money account loan deposit cash light charge bat");
    private static final Set<String> ADJECTIVES = set("good bad small large fast slow lightweight offline online new old important useful relevant possible true false better best");
    private static final Set<String> NEGATORS = set("not no never without cannot can't dont don't isn't aren't won't");

    public enum PartOfSpeech { NOUN, PROPER_NOUN, VERB, ADJECTIVE, ADVERB, PRONOUN, DETERMINER, PREPOSITION, AUXILIARY, NUMBER, UNKNOWN }
    public enum RelationType { SUBJECT, OBJECT, LOCATION, TIME, CAUSE, CONTRAST, NEGATION, COREFERENCE }

    public static final class Token {
        public final String text;
        public final int start;
        public final int end;
        public final PartOfSpeech pos;
        public final boolean inferred;
        Token(String text, int start, int end, PartOfSpeech pos, boolean inferred) {
            this.text = text; this.start = start; this.end = end; this.pos = pos; this.inferred = inferred;
        }
    }

    public static final class Relation {
        public final String source, target;
        public final RelationType type;
        Relation(String source, String target, RelationType type) { this.source = source; this.target = target; this.type = type; }
    }

    public static final class Result {
        public final String languageHint, intent, resolvedReference;
        public final boolean negated, question, temporal, spatial, causal;
        public final double confidence;
        public final List<Token> tokens;
        public final List<Relation> relations;
        public final List<ClauseStructure.Frame> frames;
        public final SemanticIntelligence.Result semantics;
        Result(String languageHint, String intent, String resolvedReference, boolean negated, boolean question,
               boolean temporal, boolean spatial, boolean causal, double confidence, List<Token> tokens, List<Relation> relations, List<ClauseStructure.Frame> frames, SemanticIntelligence.Result semantics) {
            this.languageHint = languageHint; this.intent = intent; this.resolvedReference = resolvedReference;
            this.negated = negated; this.question = question; this.temporal = temporal; this.spatial = spatial;
            this.causal = causal; this.confidence = confidence;
            this.tokens = Collections.unmodifiableList(tokens); this.relations = Collections.unmodifiableList(relations);
            this.frames = Collections.unmodifiableList(frames); this.semantics = semantics;
        }
        /** Compact, explicitly heuristic evidence suitable for appending to a model prompt. */
        public String toPromptContext() {
            StringBuilder b = new StringBuilder("[LOCAL UNDERSTANDING; heuristic, verify against context] intent=").append(intent)
                    .append("; language_hint=").append(languageHint).append("; negated=").append(negated).append("; question=").append(question)
                    .append("; temporal=").append(temporal).append("; spatial=").append(spatial)
                    .append("; causal=").append(causal).append("; confidence=").append(String.format(Locale.ROOT, "%.2f", confidence));
            if (!resolvedReference.isEmpty()) b.append("; reference=").append(resolvedReference);
            b.append("; candidate_frames=");
            for (int i=0; i<frames.size(); i++) { if(i>0)b.append(" | "); b.append(frames.get(i).render()); }
            if (semantics != null) b.append("; ").append(semantics.toPromptContext());
            b.append("; tokens=");
            for (int i = 0; i < tokens.size(); i++) { if (i > 0) b.append(','); Token t = tokens.get(i); b.append(t.text).append('/').append(t.pos.name().toLowerCase(Locale.ROOT)); }
            return b.toString();
        }
    }

    /** previousSubject is optional conversational context for resolving it/they/this/that. */
    public Result analyze(String input, String previousSubject) {
        String text = input == null ? "" : input.trim();
        String low = text.toLowerCase(Locale.ROOT);
        List<Token> tokens = new ArrayList<Token>();
        Matcher m = TOKEN.matcher(text);
        int known = 0;
        while (m.find()) {
            String w = m.group();
            PartOfSpeech pos = classify(w, m.start() == 0 || Character.isUpperCase(w.charAt(0)));
            if (pos != PartOfSpeech.UNKNOWN) known++;
            tokens.add(new Token(w, m.start(), m.end(), pos, pos == PartOfSpeech.NOUN || pos == PartOfSpeech.PROPER_NOUN || pos == PartOfSpeech.VERB));
        }
        boolean negated = containsAny(low, NEGATORS);
        boolean question = text.endsWith("?") || startsWithAny(low, "who ", "what ", "when ", "where ", "why ", "how ", "which ", "is ", "are ", "can ", "do ");
        boolean temporal = containsAny(low, set("today tomorrow yesterday now later earlier before after during next last week month year hour minute"));
        boolean spatial = containsAny(low, set("above below under over inside outside beside behind between left right north south east west near far direction distance"));
        boolean causal = containsAny(low, set("because therefore consequently causes caused leads to result of due to so that why"));
        String intent = question ? "question" : startsWithAny(low, "please ", "build ", "create ", "write ", "find ", "search ", "open ", "run ", "delete ", "update ", "explain ", "summarize ", "translate ") ? "request" : "statement";
        String reference = "";
        if (containsAny(low, set("it its this that they them their he she him her")) && previousSubject != null && !previousSubject.trim().isEmpty()) {
            reference = previousSubject.trim();
        }
        List<Relation> relations = new ArrayList<Relation>();
        if (negated) relations.add(new Relation("utterance", "proposition", RelationType.NEGATION));
        if (causal) relations.add(new Relation("cause", "effect", RelationType.CAUSE));
        if (low.contains(" but ") || low.contains(" however ")) relations.add(new Relation("clause_1", "clause_2", RelationType.CONTRAST));
        if (temporal) relations.add(new Relation("event", "time_expression", RelationType.TIME));
        if (spatial) relations.add(new Relation("object", "spatial_expression", RelationType.LOCATION));
        if (!reference.isEmpty()) relations.add(new Relation(reference, "pronoun", RelationType.COREFERENCE));
        double confidence = tokens.isEmpty() ? 0.0 : Math.min(0.95, 0.35 + 0.6 * ((double) known / tokens.size()));
        List<ClauseStructure.Frame> frames = ClauseStructure.extract(tokens, 4);
        SemanticIntelligence.Result semanticEvidence = new SemanticIntelligence().analyze(text, null);
        return new Result(LanguageIdentifier.detect(text).code, intent, reference, negated, question, temporal, spatial, causal, confidence, tokens, relations, frames, semanticEvidence);
    }

    private static PartOfSpeech classify(String word, boolean sentenceInitialCapital) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.matches("\\d+(?:[.,]\\d+)?")) return PartOfSpeech.NUMBER;
        if (PRONOUNS.contains(w)) return PartOfSpeech.PRONOUN;
        if (DETERMINERS.contains(w)) return PartOfSpeech.DETERMINER;
        if (PREPOSITIONS.contains(w)) return PartOfSpeech.PREPOSITION;
        if (AUX.contains(w)) return PartOfSpeech.AUXILIARY;
        if (NEGATORS.contains(w)) return PartOfSpeech.UNKNOWN;
        if (COMMON_VERBS.contains(w) || (w.endsWith("s") && COMMON_VERBS.contains(w.substring(0, w.length() - 1))) || w.endsWith("ing") || w.endsWith("ed") || w.endsWith("ize") || w.endsWith("ise")) return PartOfSpeech.VERB;
        if (ADJECTIVES.contains(w) || (w.endsWith("er") && ADJECTIVES.contains(w.substring(0, w.length()-2))) || w.endsWith("ous") || w.endsWith("ful") || w.endsWith("able") || w.endsWith("ive") || w.endsWith("less")) return PartOfSpeech.ADJECTIVE;
        if (w.endsWith("ly")) return PartOfSpeech.ADVERB;
        if (COMMON_NOUNS.contains(w) || (w.endsWith("s") && COMMON_NOUNS.contains(w.substring(0, w.length()-1)))) return PartOfSpeech.NOUN;
        if (sentenceInitialCapital && word.length() > 1 && Character.isUpperCase(word.charAt(0))) return PartOfSpeech.PROPER_NOUN;
        if (w.endsWith("tion") || w.endsWith("ment") || w.endsWith("ness") || w.endsWith("ity") || w.endsWith("er") || w.endsWith("or")) return PartOfSpeech.NOUN;
        return PartOfSpeech.UNKNOWN;
    }
    private static boolean containsAny(String text, Set<String> words) {
        for (String word : words) if (Pattern.compile("(?:^|[^\\p{L}])" + Pattern.quote(word) + "(?:$|[^\\p{L}])").matcher(text).find()) return true;
        return false;
    }
    private static boolean startsWithAny(String text, String... prefixes) { for (String p : prefixes) if (text.startsWith(p)) return true; return false; }
    private static Set<String> set(String words) { Set<String> s = new LinkedHashSet<String>(); Collections.addAll(s, words.split(" ")); return s; }
}
