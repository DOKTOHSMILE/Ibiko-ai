package ai.ibiko.intelligence;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Tiny lexical language hint for routing/evaluation only; never a substitute for a trained LID model. */
public final class LanguageIdentifier {
    public static final class Detection {
        public final String code,evidence; public final double confidence;
        Detection(String c,double s,String e){code=c;confidence=s;evidence=e;}
        public String render(){return code+"(heuristic confidence="+String.format(Locale.ROOT,"%.2f",confidence)+"; "+evidence+")";}
    }
    private static final Map<String,Set<String>> CUES=new LinkedHashMap<String,Set<String>>();
    static {
        CUES.put("en",set("the is are was were have has had this that with from what when where why how please your you i we they it can will would should not"));
        CUES.put("ha",set("sannu yaya lafiya ina wannan kuma saboda yanzu don allah na gode akwai gida mutum ruwa"));
        CUES.put("yo",set("bawo ejo se mo ni ati fun nitori kini eniyan eniyan kaabo pele daradara jowo"));
        CUES.put("ig",set("kedu ndewo anyi bụ nke maka gị unu onye kedu odi dị ezigbo daalụ"));
        CUES.put("pcm",set("wetin dey abi wahala oga dem una una no wahala how far make we na so sef don chop"));
    }
    private LanguageIdentifier(){}
    public static Detection detect(String text){String s=text==null?"":text.toLowerCase(Locale.ROOT);String[] words=s.replaceAll("[^\\p{L}\\p{N}]+"," ").trim().split("\\s+");if(s.trim().isEmpty())return new Detection("undetermined",0,"empty input");
        String best="undetermined";int bestScore=0,second=0;StringBuilder evidence=new StringBuilder();
        for(Map.Entry<String,Set<String>> e:CUES.entrySet()){int score=0;for(String w:words)if(e.getValue().contains(w))score++;if(score>bestScore){second=bestScore;bestScore=score;best=e.getKey();}else if(score>second)second=score;}
        boolean yorubaMark=s.matches(".*[ẹṣ].*");boolean igboMark=s.matches(".*[ịụṅ].*");boolean hausaMark=s.matches(".*[ƙɗɓ].*");
        if(yorubaMark){best="yo";bestScore=Math.max(bestScore,3);second=0;evidence.append("Yoruba orthographic markers");}
        else if(igboMark){best="ig";bestScore=Math.max(bestScore,3);second=0;evidence.append("Igbo orthographic markers");}
        else if(hausaMark){best="ha";bestScore=Math.max(bestScore,3);second=0;evidence.append("Hausa orthographic markers");}
        if(bestScore<2 || (bestScore==second && evidence.length()==0))return new Detection("undetermined",0,"insufficient lexical evidence");
        double confidence=Math.min(.90,.50+.12*bestScore-.08*second);
        if(evidence.length()==0)evidence.append(bestScore).append(" matching lexical cues");
        return new Detection(best,confidence,evidence.toString());
    }
    private static Set<String> set(String s){return new HashSet<String>(Arrays.asList(s.split(" ")));}
}
