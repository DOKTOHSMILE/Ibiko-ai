package ai.ibiko.intelligence.context;

import ai.ibiko.intelligence.UnderstandingEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded, in-process conversational context. Snapshot APIs let the app persist it in its own storage layer. */
public final class ConversationMemory {
    private static final int MAX_ENTITIES=24, MAX_TURNS=12, MAX_PROPOSITIONS=24;
    private final List<CoreferenceResolver.Entity> entities=new ArrayList<CoreferenceResolver.Entity>();
    private final List<String> turns=new ArrayList<String>();
    private final List<Proposition> propositions=new ArrayList<Proposition>();
    private int turn;
    private static final class Proposition {
        final String subject,predicate,object; final boolean negated; int age;
        Proposition(String s,String p,String o,boolean n,int a){subject=s;predicate=p;object=o;negated=n;age=a;}
    }
    public synchronized List<CoreferenceResolver.Entity> entities(){return Collections.unmodifiableList(new ArrayList<CoreferenceResolver.Entity>(entities));}
    public synchronized List<String> recentTurns(){return Collections.unmodifiableList(new ArrayList<String>(turns));}
    public synchronized List<String> findContradictions(List<ai.ibiko.intelligence.ClauseStructure.Frame> frames) {
        List<String> found=new ArrayList<String>();
        if(frames==null)return found;
        for(ai.ibiko.intelligence.ClauseStructure.Frame f:frames){
            if(f.subject.startsWith("["))continue;
            for(Proposition old:propositions){
                if(old.age>MAX_TURNS)continue;
                if(old.subject.equalsIgnoreCase(f.subject)&&old.predicate.equalsIgnoreCase(f.predicate)
                        &&old.object.equalsIgnoreCase(f.object)&&old.negated!=f.negated){
                    found.add("possible contradiction: "+f.subject+" / "+f.predicate+" / "+f.object
                            +"; earlier polarity="+(!f.negated)+", current polarity="+f.negated+" (candidate only)");
                }
            }
        }
        return Collections.unmodifiableList(found);
    }
    public synchronized List<String> relevantEntities(String query,int max) {
        if(max<=0)return Collections.emptyList();
        String q=" "+(query==null?"":query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," "))+" ";
        List<CoreferenceResolver.Entity> ranked=new ArrayList<CoreferenceResolver.Entity>(entities);
        Collections.sort(ranked,(a,b)->Double.compare(relevance(b,q),relevance(a,q)));
        List<String> out=new ArrayList<String>();for(CoreferenceResolver.Entity e:ranked){if(out.size()>=max)break;out.add(e.text);}
        return Collections.unmodifiableList(out);
    }
    private static double relevance(CoreferenceResolver.Entity e,String q){String name=" "+e.text.toLowerCase(Locale.ROOT)+" ";double match=q.contains(name)?1.0:0.0;return match+e.salience/(1.0+e.turn);}
    public synchronized void update(String text,UnderstandingEngine.Result parsed){turn++;
        if(text!=null&&!text.trim().isEmpty()){String compact=text.trim();turns.add(compact.length()>256?compact.substring(0,256):compact);while(turns.size()>MAX_TURNS)turns.remove(0);}
        if(parsed!=null){for(UnderstandingEngine.Token token:parsed.tokens){if(token.pos==UnderstandingEngine.PartOfSpeech.NOUN||token.pos==UnderstandingEngine.PartOfSpeech.PROPER_NOUN){String value=token.text.trim();if(value.length()<2)continue;boolean plural=value.toLowerCase(Locale.ROOT).endsWith("s")&&!value.toLowerCase(Locale.ROOT).endsWith("ss");String type=isPerson(value)?"person":"thing";
                    // A repeated mention refreshes salience; subject-like first entity receives a small boost.
                    int existing=-1;for(int i=0;i<entities.size();i++)if(entities.get(i).text.equalsIgnoreCase(value)){existing=i;break;}
                    double salience=(existing>=0?entities.get(existing).salience*.72:0.5)+(entities.isEmpty()?0.18:0.08);
                    CoreferenceResolver.Entity e=new CoreferenceResolver.Entity(value,type,plural,0,Math.min(1.0,salience));if(existing>=0)entities.remove(existing);entities.add(0,e);
                }} }
        for(int i=0;i<entities.size();i++){CoreferenceResolver.Entity e=entities.get(i);entities.set(i,new CoreferenceResolver.Entity(e.text,e.type,e.plural,e.turn+1,e.salience*.92));}
        while(entities.size()>MAX_ENTITIES)entities.remove(entities.size()-1);
        for(Proposition p:propositions)p.age++;
        if(parsed!=null)for(ai.ibiko.intelligence.ClauseStructure.Frame f:parsed.frames){
            if(f.subject.startsWith("["))continue;
            propositions.add(0,new Proposition(f.subject,f.predicate,f.object,f.negated,0));
        }
        while(propositions.size()>MAX_PROPOSITIONS)propositions.remove(propositions.size()-1);
    }
    private static boolean isPerson(String s){String w=s.toLowerCase(Locale.ROOT);return w.equals("person")||w.equals("people")||w.equals("user")||w.equals("man")||w.equals("woman")||w.equals("child")||w.equals("doctor")||w.equals("teacher")||w.equals("customer")||w.equals("assistant");}
    public synchronized void clear(){entities.clear();turns.clear();propositions.clear();turn=0;}
    /** Versioned Base64 binary snapshot; safe to store as an opaque preference value. */
    public synchronized String exportSnapshot(){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);d.writeInt(2);d.writeInt(turn);d.writeInt(entities.size());for(CoreferenceResolver.Entity e:entities){d.writeUTF(e.text);d.writeUTF(e.type);d.writeBoolean(e.plural);d.writeInt(e.turn);d.writeDouble(e.salience);}d.writeInt(turns.size());for(String s:turns)d.writeUTF(s);d.writeInt(propositions.size());for(Proposition p:propositions){d.writeUTF(p.subject);d.writeUTF(p.predicate);d.writeUTF(p.object);d.writeBoolean(p.negated);d.writeInt(p.age);}d.flush();return Base64.getEncoder().encodeToString(bytes.toByteArray());}catch(IOException impossible){throw new IllegalStateException(impossible);}}
    public synchronized boolean restoreSnapshot(String snapshot){if(snapshot==null||snapshot.length()>65536)return false;try{DataInputStream d=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(snapshot)));int version=d.readInt();if(version!=1&&version!=2)return false;int restoredTurn=d.readInt();int n=d.readInt();if(n<0||n>MAX_ENTITIES)return false;List<CoreferenceResolver.Entity> es=new ArrayList<CoreferenceResolver.Entity>();for(int i=0;i<n;i++){String text=d.readUTF(),type=d.readUTF();boolean plural=d.readBoolean();int age=d.readInt();double salience=d.readDouble();if(text.length()>128||age<0||Double.isNaN(salience)||salience<0||salience>1)return false;es.add(new CoreferenceResolver.Entity(text,type,plural,age,salience));}int count=d.readInt();if(count<0||count>MAX_TURNS)return false;List<String> ts=new ArrayList<String>();for(int i=0;i<count;i++){String s=d.readUTF();if(s.length()>4096)return false;ts.add(s);}List<Proposition> ps=new ArrayList<Proposition>();if(version>=2){int pc=d.readInt();if(pc<0||pc>MAX_PROPOSITIONS)return false;for(int i=0;i<pc;i++){String sub=d.readUTF(),pred=d.readUTF(),obj=d.readUTF();boolean neg=d.readBoolean();int age=d.readInt();if(sub.length()>128||pred.length()>128||obj.length()>128||age<0)return false;ps.add(new Proposition(sub,pred,obj,neg,age));}}if(d.available()!=0)return false;entities.clear();entities.addAll(es);turns.clear();turns.addAll(ts);propositions.clear();propositions.addAll(ps);turn= Math.max(0,restoredTurn);return true;}catch(Exception invalid){return false;}}
}
