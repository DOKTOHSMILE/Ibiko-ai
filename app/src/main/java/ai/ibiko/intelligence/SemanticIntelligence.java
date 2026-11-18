package ai.ibiko.intelligence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight, deterministic semantic evidence for the on-device comprehension pipeline.
 * This is deliberately conservative: lexical relations are candidates, not universal truths.
 * It does not answer questions, mutate knowledge, or execute application capabilities.
 */
public final class SemanticIntelligence {
    public enum Relation { SYNONYM_CANDIDATE, ANTONYM_CANDIDATE, HYPERNYM_CANDIDATE,
        HYPONYM_CANDIDATE, PART_OF_CANDIDATE, FUNCTION_CANDIDATE }
    public enum Inference { ENTAILMENT_CANDIDATE, CONTRADICTION_CANDIDATE, UNKNOWN }

    public static final class Edge {
        public final String subject, predicate, object, evidence;
        public final Relation relation;
        Edge(String s, Relation r, String o, String e) {
            subject=s; relation=r; object=o; predicate=r.name().toLowerCase(Locale.ROOT); evidence=e;
        }
        public String render() { return subject + " -" + predicate + "-> " + object + " [" + evidence + "]"; }
    }

    public static final class Result {
        public final List<Edge> edges;
        public final Inference inference;
        public final String inferenceReason;
        Result(List<Edge> e, Inference i, String reason) {
            edges=Collections.unmodifiableList(e); inference=i; inferenceReason=reason;
        }
        public String toPromptContext() {
            StringBuilder b=new StringBuilder("[SEMANTIC EVIDENCE; candidates only] inference=")
                    .append(inference.name().toLowerCase(Locale.ROOT));
            if (!inferenceReason.isEmpty()) b.append(" (").append(inferenceReason).append(')');
            if (!edges.isEmpty()) {
                b.append("; relations=");
                for (int i=0;i<edges.size();i++) { if(i>0)b.append(" | "); b.append(edges.get(i).render()); }
            }
            return b.toString();
        }
    }

    private static final Map<String, Set<String>> SYNONYMS = new LinkedHashMap<String, Set<String>>();
    private static final Map<String, Set<String>> ANTONYMS = new LinkedHashMap<String, Set<String>>();
    private static final Map<String, String> HYPERNYMS = new LinkedHashMap<String, String>();
    private static final Map<String, String> PARTS = new LinkedHashMap<String, String>();
    private static final Map<String, String> FUNCTIONS = new LinkedHashMap<String, String>();
    static {
        group(SYNONYMS, "big", "large", "huge"); group(SYNONYMS, "small", "little", "tiny");
        group(SYNONYMS, "fast", "quick", "rapid"); group(SYNONYMS, "begin", "start", "commence");
        group(SYNONYMS, "end", "finish", "complete"); group(SYNONYMS, "help", "assist", "support");
        group(SYNONYMS, "buy", "purchase"); group(SYNONYMS, "car", "automobile");
        pair(ANTONYMS,"hot","cold"); pair(ANTONYMS,"big","small"); pair(ANTONYMS,"fast","slow");
        pair(ANTONYMS,"open","closed"); pair(ANTONYMS,"true","false"); pair(ANTONYMS,"increase","decrease");
        pair(ANTONYMS,"enter","exit"); pair(ANTONYMS,"before","after"); pair(ANTONYMS,"inside","outside");
        HYPERNYMS.put("car","vehicle"); HYPERNYMS.put("motorcycle","vehicle"); HYPERNYMS.put("dog","animal");
        HYPERNYMS.put("cat","animal"); HYPERNYMS.put("apple","food"); HYPERNYMS.put("banana","food");
        PARTS.put("wheel","vehicle"); PARTS.put("engine","vehicle"); PARTS.put("brake","vehicle");
        PARTS.put("keyboard","computer"); PARTS.put("screen","computer"); PARTS.put("battery","device");
        FUNCTIONS.put("brake","slow or stop movement"); FUNCTIONS.put("wheel","enable rolling movement");
        FUNCTIONS.put("engine","provide mechanical power"); FUNCTIONS.put("keyboard","enter text or commands");
        FUNCTIONS.put("battery","store and supply electrical energy"); FUNCTIONS.put("door","allow entry and exit");
    }

    public Result analyze(String premise, String hypothesis) {
        String p=normal(premise), h=normal(hypothesis);
        List<Edge> edges=extractEdges(p);
        Inference inference=Inference.UNKNOWN; String reason="insufficient lexical evidence";
        if (!p.isEmpty() && !h.isEmpty()) {
            if (p.equals(h)) { inference=Inference.ENTAILMENT_CANDIDATE; reason="normalised statements match"; }
            else if (samePropositionOppositePolarity(p,h)) { inference=Inference.CONTRADICTION_CANDIDATE; reason="same proposition with opposite explicit polarity"; }
            else if (containsNegatedPair(p,h)) { inference=Inference.CONTRADICTION_CANDIDATE; reason="explicit antonym or negation conflict"; }
            else if (containsAllContentWords(p,h) && !hasNegation(p) && !hasNegation(h)) {
                inference=Inference.ENTAILMENT_CANDIDATE; reason="hypothesis content words occur in premise";
            }
        }
        return new Result(edges,inference,reason);
    }

    public List<Edge> extractEdges(String text) {
        String[] words=normal(text).split(" ");
        Set<String> unique=new LinkedHashSet<String>(Arrays.asList(words));
        List<Edge> out=new ArrayList<Edge>();
        for (String w:unique) {
            Set<String> syn=SYNONYMS.get(w);
            if (syn!=null) for(String s:syn) if(!s.equals(w)) out.add(new Edge(w,Relation.SYNONYM_CANDIDATE,s,"small built-in lexical map"));
            Set<String> ant=ANTONYMS.get(w);
            if (ant!=null) for(String a:ant) out.add(new Edge(w,Relation.ANTONYM_CANDIDATE,a,"small built-in lexical map"));
            String parent=HYPERNYMS.get(w);
            if(parent!=null) out.add(new Edge(w,Relation.HYPERNYM_CANDIDATE,parent,"small built-in taxonomy"));
            String partOf=PARTS.get(w);
            if(partOf!=null) out.add(new Edge(w,Relation.PART_OF_CANDIDATE,partOf,"small built-in component map"));
            String function=FUNCTIONS.get(w);
            if(function!=null) out.add(new Edge(w,Relation.FUNCTION_CANDIDATE,function,"small built-in affordance map"));
        }
        return out;
    }

    private static boolean samePropositionOppositePolarity(String a,String b) {
        return stripNegation(a).equals(stripNegation(b)) && hasNegation(a)!=hasNegation(b);
    }
    private static boolean containsNegatedPair(String a,String b) {
        for(Map.Entry<String,Set<String>> e:ANTONYMS.entrySet()) for(String x:e.getValue()) {
            if(a.contains(" "+e.getKey()+" ") && b.contains(" "+x+" ") || a.contains(" "+x+" ") && b.contains(" "+e.getKey()+" ")) return true;
        }
        return false;
    }
    private static boolean containsAllContentWords(String p,String h) {
        Set<String> ps=new LinkedHashSet<String>(Arrays.asList(p.split(" ")));
        for(String w:h.split(" ")) if(w.length()>2 && !isStop(w) && !ps.contains(w)) return false;
        return true;
    }
    private static boolean hasNegation(String s) { return s.matches(".*\\b(not|no|never|cannot|can't|without)\\b.*"); }
    private static String stripNegation(String s) { return s.replaceAll("\\b(not|no|never|cannot|can't|without|do|does|did)\\b"," ").replaceAll("\\s+"," ").trim(); }
    private static boolean isStop(String w) { return Arrays.asList("the","and","for","with","that","this","are","was","were","has","have","had","from","into").contains(w); }
    private static String normal(String s) { return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}']+"," ").replaceAll("\\s+"," ").trim(); }
    private static void group(Map<String,Set<String>> map,String... words) { for(String w:words) { Set<String> s=new LinkedHashSet<String>(); s.addAll(Arrays.asList(words)); map.put(w,s); } }
    private static void pair(Map<String,Set<String>> map,String a,String b) { map.put(a,new LinkedHashSet<String>(Collections.singletonList(b))); map.put(b,new LinkedHashSet<String>(Collections.singletonList(a))); }
}
