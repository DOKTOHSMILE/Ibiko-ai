package ai.ibiko.intelligence.context;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative intent labelling for prompt context only. It never dispatches tools or app capabilities. */
public final class IntentClassifier {
    public enum Kind { QUESTION, EXPLANATION, CREATION, SEARCH, EDIT, SUMMARIZATION, TRANSLATION,
        COMPARISON, PLANNING, CONTINUATION, DELETION, GREETING, THANKS, FEEDBACK, STATEMENT, UNKNOWN }
    public static final class Result {
        public final Kind kind; public final double confidence; public final boolean actionRequested;
        Result(Kind k, double c, boolean a) { kind=k; confidence=c; actionRequested=a; }
        public String render() { return kind.name().toLowerCase(Locale.ROOT)+"(confidence="+String.format(Locale.ROOT,"%.2f",confidence)+", action_requested="+actionRequested+")"; }
    }
    private static final Pattern QUESTION = Pattern.compile("^(who|what|when|where|why|how|which|whose|is|are|am|was|were|do|does|did|can|could|would|will|should|have|has)\\b|\\?$", Pattern.CASE_INSENSITIVE);
    private IntentClassifier() {}
    public static Result classify(String input) {
        String s = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return new Result(Kind.UNKNOWN,0,false);
        if (s.matches("^(hi|hello|hey|good morning|good afternoon|good evening)[.! ]*$")) return new Result(Kind.GREETING,.99,false);
        if (s.matches("^(thanks|thank you|thx)[.! ]*$")) return new Result(Kind.THANKS,.99,false);
        if (s.matches(".*\\b(continue|carry on|keep going|next)\\b.*")) return new Result(Kind.CONTINUATION,.88,false);
        if (s.matches(".*\\b(summarize|sum up|give me a summary)\\b.*")) return new Result(Kind.SUMMARIZATION,.91,false);
        if (s.matches(".*\\b(translate|in [a-z]+ language)\\b.*")) return new Result(Kind.TRANSLATION,.86,false);
        if (s.matches(".*\\b(compare|difference between|versus| vs )\\b.*")) return new Result(Kind.COMPARISON,.86,false);
        if (s.matches(".*\\b(plan|roadmap|strategy|schedule)\\b.*")) return new Result(Kind.PLANNING,.83,false);
        if (s.matches("^(what if|what would happen if|what happens if|suppose)\\b.*")) return new Result(Kind.QUESTION,.86,false);
        if (s.matches(".*\\b(?:do not|don't|dont|never|must not|mustn't|cannot|can't)(?:\\s+\\w+){0,2}\\s+(?:delete|remove|erase|wipe|clear)\\b.*")) return new Result(Kind.DELETION,.92,false);
        if (s.matches(".*\\b(delete|remove|erase|wipe|clear)\\b.*")) return new Result(Kind.DELETION,.90,true);
        if (s.matches(".*\\b(search|find|look up|research|investigate)\\b.*")) return new Result(Kind.SEARCH,.84,true);
        if (s.matches(".*\\b(explain|what does .* mean|how does .* work|teach me)\\b.*")) return new Result(Kind.EXPLANATION,.84,false);
        if (s.matches(".*\\b(fix|edit|rewrite|correct|improve|update|modify)\\b.*")) return new Result(Kind.EDIT,.84,true);
        if (QUESTION.matcher(s).find()) return new Result(Kind.QUESTION,.78,false);
        if (s.matches(".*\\b(write|create|build|generate|design|implement|make|draft)\\b.*")) return new Result(Kind.CREATION,.82,true);
        if (s.matches(".*\\b(i like|i dislike|i prefer|this is wrong|that is good|bad experience)\\b.*")) return new Result(Kind.FEEDBACK,.70,false);
        return new Result(Kind.STATEMENT,.60,false);
    }
}
