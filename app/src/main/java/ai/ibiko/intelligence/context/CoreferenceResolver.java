package ai.ibiko.intelligence.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Recency/salience-based, deliberately abstaining coreference resolver. */
public final class CoreferenceResolver {
    public static final class Entity {
        public final String text, type; public final boolean plural; public final int turn; public final double salience;
        public Entity(String text,String type,boolean plural,int turn,double salience) { this.text=text;this.type=type;this.plural=plural;this.turn=turn;this.salience=salience; }
    }
    public static final class Resolution {
        public final String pronoun, antecedent, status; public final double confidence;
        Resolution(String p,String a,String s,double c){pronoun=p;antecedent=a;status=s;confidence=c;}
        public String render(){return pronoun+" -> "+(antecedent.isEmpty()?"unresolved":antecedent)+" ["+status+", confidence="+String.format(Locale.ROOT,"%.2f",confidence)+"]";}
    }
    private CoreferenceResolver(){}
    public static List<Resolution> resolve(List<String> pronouns,List<Entity> entities) {
        if(pronouns==null||entities==null)return Collections.emptyList();
        List<Resolution> out=new ArrayList<Resolution>();
        for(String raw:pronouns){if(raw==null||raw.trim().isEmpty())continue;String p=raw.toLowerCase(Locale.ROOT);Entity best=null;double top=-1,second=-1;
            for(Entity e:entities){ if(e==null||!compatible(p,e))continue; double score=e.salience/(1.0+Math.max(0,e.turn));
                if(score>top){second=top;top=score;best=e;}else if(score>second)second=score; }
            if(best==null)out.add(new Resolution(p,"","no compatible antecedent",0));
            else if(second>=0 && top-second<0.16)out.add(new Resolution(p,"","ambiguous antecedents",0.35));
            else out.add(new Resolution(p,best.text,"most salient compatible recent entity",Math.min(.92,.55+top*.08)));
        }
        return Collections.unmodifiableList(out);
    }
    private static boolean compatible(String p,Entity e){
        if(p.equals("he")||p.equals("him")||p.equals("his")||p.equals("she")||p.equals("her")||p.equals("hers"))return false; // entity memory has no gender signal; abstain rather than guess
        if(p.equals("they")||p.equals("them")||p.equals("their")||p.equals("theirs"))return e.plural;
        if(p.equals("it")||p.equals("its"))return !e.plural && e.type != null && !e.type.equals("person");
        return p.equals("this")||p.equals("that")||p.equals("these")||p.equals("those");
    }
}
