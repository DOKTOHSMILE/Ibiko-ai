package ai.ibiko.intelligence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Conservative S-V-O candidate extraction. Frames are heuristic evidence, not a full parse. */
public final class ClauseStructure {
    private ClauseStructure() {}
    public static final class Frame {
        public final String subject, predicate, object;
        public final boolean negated;
        Frame(String s, String p, String o, boolean n) { subject=s; predicate=p; object=o; negated=n; }
        public String render() { return "S="+subject+",V="+predicate+",O="+object+",negated="+negated+",source=inferred"; }
    }
    public static List<Frame> extract(List<UnderstandingEngine.Token> tokens, int maxFrames) {
        if (tokens == null || maxFrames <= 0) return Collections.emptyList();
        List<Frame> out = new ArrayList<Frame>();
        int start=0;
        for (int i=0; i<=tokens.size() && out.size()<maxFrames; i++) {
            if (i==tokens.size() || isBoundary(tokens.get(i).text)) {
                extractClause(tokens,start,i,out); start=i+1;
            }
        }
        return Collections.unmodifiableList(out);
    }
    private static void extractClause(List<UnderstandingEngine.Token> ts,int start,int end,List<Frame> out) {
        if(start>=end) return;
        int split=-1;
        for(int i=start+1;i<end-1;i++) {
            String w=ts.get(i).text.toLowerCase(Locale.ROOT);
            if (("but".equals(w)||"however".equals(w)||"then".equals(w)) && isNominal(ts.get(i+1))) { split=i; break; }
        }
        if(split>start) { extractClause(ts,start,split,out); extractClause(ts,split+1,end,out); return; }
        int verb=-1;
        // Lexical verbs outrank auxiliaries: "does not build" -> "build".
        for(int i=start;i<end;i++) if(ts.get(i).pos==UnderstandingEngine.PartOfSpeech.VERB) {verb=i;break;}
        if(verb<0) for(int i=start;i<end;i++) if(ts.get(i).pos==UnderstandingEngine.PartOfSpeech.AUXILIARY) {verb=i;break;}
        if(verb<0) return;
        int subject=-1, object=-1;
        for(int i=verb-1;i>=start;i--) if(isNominal(ts.get(i))) {subject=i;break;}
        for(int i=verb+1;i<end;i++) if(isNominal(ts.get(i))) {object=i;break;}
        boolean neg=false;
        for(int i=Math.max(start,verb-3);i<Math.min(end,verb+2);i++) {
            String w=ts.get(i).text.toLowerCase(Locale.ROOT);
            if(w.equals("not")||w.equals("never")||w.equals("no")||w.equals("don't")||w.equals("dont")||w.equals("cannot")||w.equals("can't")) neg=true;
        }
        out.add(new Frame(subject<0?"[implicit/unknown]":ts.get(subject).text,ts.get(verb).text,
                object<0?"[not identified]":ts.get(object).text,neg));
    }
    private static boolean isNominal(UnderstandingEngine.Token t) {
        UnderstandingEngine.PartOfSpeech p=t.pos;
        return p==UnderstandingEngine.PartOfSpeech.NOUN||p==UnderstandingEngine.PartOfSpeech.PROPER_NOUN||p==UnderstandingEngine.PartOfSpeech.PRONOUN||p==UnderstandingEngine.PartOfSpeech.NUMBER;
    }
    private static boolean isBoundary(String s) { return s.equals(".")||s.equals("?")||s.equals("!")||s.equals(";"); }
}
