package ai.ibiko.intelligence.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Tiny explainable baseline for common polysemous words; abstains when context is weak. */
public final class WordSenseDisambiguator {
    public static final class Sense { public final String word,label,evidence; public final double confidence;
        Sense(String w,String l,String e,double c){word=w;label=l;evidence=e;confidence=c;}
        public String render(){return word+"="+label+"("+String.format(Locale.ROOT,"%.2f",confidence)+"; "+evidence+")";}}
    private static final String[][] RULES={
        {"bank","financial institution","money account loan deposit cash"},
        {"bank","river edge","river water stream shore fishing"},
        {"bat","flying mammal","cave wings animal nocturnal"},
        {"bat","sports equipment","baseball cricket hit swing"},
        {"java","programming language","code compiler class jvm software"},
        {"java","island or coffee","indonesia island coffee drink"},
        {"light","illumination","lamp bright shine dark"},
        {"light","low weight","heavy weight carry"},
        {"charge","electrical property","battery voltage current circuit"},
        {"charge","payment or fee","money price invoice cost"},
        {"mouse","computer device","click cursor keyboard computer"},
        {"mouse","animal","cheese rodent tail animal"}
    };
    private WordSenseDisambiguator(){}
    public static List<Sense> analyze(String text){String s=" "+(text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim())+" ";List<Sense> out=new ArrayList<Sense>();
        for(int i=0;i<RULES.length;i+=2){String word=RULES[i][0];if(!s.contains(" "+word+" "))continue;int best=-1,score=0,second=0;
            for(int j=i;j<i+2;j++){int n=0;for(String cue:RULES[j][2].split(" "))if(s.contains(" "+cue+" "))n++;if(n>score){second=score;score=n;best=j;}else if(n>second)second=n;}
            if(best>=0&&score>0&&score>second)out.add(new Sense(word,RULES[best][1],"context cues: "+RULES[best][2],Math.min(.90,.55+.10*score)));
            else out.add(new Sense(word,"ambiguous","insufficient distinguishing context",.30));
        }
        return Collections.unmodifiableList(out);
    }
}
