package ai.ibiko.intelligence.context;

import ai.ibiko.intelligence.UnderstandingEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Stateful conversational comprehension layer. Evidence is advisory and is never an execution signal. */
public final class ContextualUnderstandingEngine {
    public static final class Result {
        public final IntentClassifier.Result intent;
        public final List<CoreferenceResolver.Resolution> coreferences;
        public final List<WordSenseDisambiguator.Sense> senses;
        public final List<TemporalSpatialAnalyzer.Cue> cues;
        public final List<String> historicalContradictions, relevantMemory;
        public final int rememberedEntities, rememberedTurns;
        Result(IntentClassifier.Result i,List<CoreferenceResolver.Resolution> c,List<WordSenseDisambiguator.Sense> s,List<TemporalSpatialAnalyzer.Cue> cues,List<String> contradictions,List<String> relevant,int e,int t){intent=i;coreferences=c;senses=s;this.cues=cues;historicalContradictions=contradictions;relevantMemory=relevant;rememberedEntities=e;rememberedTurns=t;}
        public String toPromptContext(){StringBuilder b=new StringBuilder("[CONTEXTUAL UNDERSTANDING; heuristic evidence only; never execute from this block] intent=").append(intent.render());
            if(!coreferences.isEmpty()){b.append("; coreference=");for(int i=0;i<coreferences.size();i++){if(i>0)b.append(" | ");b.append(coreferences.get(i).render());}}
            if(!senses.isEmpty()){b.append("; word_senses=");for(int i=0;i<senses.size();i++){if(i>0)b.append(" | ");b.append(senses.get(i).render());}}
            if(!cues.isEmpty()){b.append("; temporal_spatial=");for(int i=0;i<cues.size();i++){if(i>0)b.append(" | ");b.append(cues.get(i).render());}}
            if(!relevantMemory.isEmpty())b.append("; relevant_memory_entities=").append(relevantMemory);
            if(!historicalContradictions.isEmpty())b.append("; historical_contradiction_candidates=").append(historicalContradictions);
            b.append("; memory_entities=").append(rememberedEntities).append("; recent_turns=").append(rememberedTurns);return b.toString();}
    }
    private final ConversationMemory memory;
    public ContextualUnderstandingEngine(){this(new ConversationMemory());}
    public ContextualUnderstandingEngine(ConversationMemory memory){if(memory==null)throw new IllegalArgumentException("memory required");this.memory=memory;}
    public synchronized Result analyze(String message){return analyze(message,System.currentTimeMillis());}
    public synchronized Result analyze(String message,long referenceTimeMillis){String text=message==null?"":message.trim();
        IntentClassifier.Result intent=IntentClassifier.classify(text);
        List<String> pronouns=extractPronouns(text);
        List<CoreferenceResolver.Resolution> refs=CoreferenceResolver.resolve(pronouns,memory.entities());
        List<WordSenseDisambiguator.Sense> senses=WordSenseDisambiguator.analyze(text);
        List<TemporalSpatialAnalyzer.Cue> cues=TemporalSpatialAnalyzer.analyze(text,referenceTimeMillis);
        UnderstandingEngine.Result parsed=new UnderstandingEngine().analyze(text,null);
        List<String> contradictions=memory.findContradictions(parsed.frames);
        List<String> relevant=memory.relevantEntities(text,5);
        memory.update(text,parsed);
        return new Result(intent,refs,senses,cues,contradictions,relevant,memory.entities().size(),memory.recentTurns().size());
    }
    public synchronized void reset(){memory.clear();}
    public synchronized String exportSnapshot(){return memory.exportSnapshot();}
    public synchronized boolean restoreSnapshot(String snapshot){return memory.restoreSnapshot(snapshot);}
    public synchronized List<String> recentTurns(){return memory.recentTurns();}
    private static List<String> extractPronouns(String text){if(text==null)return Collections.emptyList();String[] words=text.toLowerCase(Locale.ROOT).split("[^\\p{L}']+");List<String> out=new ArrayList<String>();for(String w:words)if(w.equals("it")||w.equals("its")||w.equals("they")||w.equals("them")||w.equals("their")||w.equals("he")||w.equals("him")||w.equals("his")||w.equals("she")||w.equals("her")||w.equals("hers")||w.equals("this")||w.equals("that")||w.equals("these")||w.equals("those"))out.add(w);return out;}
}
