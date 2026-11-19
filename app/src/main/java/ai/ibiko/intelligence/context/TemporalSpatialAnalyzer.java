package ai.ibiko.intelligence.context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts explicit temporal/spatial cues. Relative dates are resolved only against a supplied reference time. */
public final class TemporalSpatialAnalyzer {
    public static final class Cue { public final String kind,expression,normalised; public final double confidence;
        Cue(String k,String e,String n,double c){kind=k;expression=e;normalised=n;confidence=c;}
        public String render(){return kind+":"+expression+(normalised.isEmpty()?"":" -> "+normalised)+" ("+String.format(Locale.ROOT,"%.2f",confidence)+")";}}
    private static final Pattern TIME=Pattern.compile("\\b(today|tomorrow|yesterday|tonight|this morning|this afternoon|this evening|next week|last week|next month|last month|next year|last year|in \\d+ (?:seconds?|minutes?|hours?|days?|weeks?|months?|years?)|\\d+ (?:seconds?|minutes?|hours?|days?|weeks?|months?|years?) ago|\\d+ (?:seconds?|minutes?|hours?|days?|weeks?|months?|years?))\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE=Pattern.compile("\\b(above|below|under|over|inside|outside|beside|next to|behind|in front of|between|left of|right of|north of|south of|east of|west of|near|far from|inside|around|through|across)\\b",Pattern.CASE_INSENSITIVE);
    private TemporalSpatialAnalyzer(){}
    public static List<Cue> analyze(String text,long referenceTimeMillis){if(text==null||text.trim().isEmpty())return Collections.emptyList();List<Cue> out=new ArrayList<Cue>();Matcher t=TIME.matcher(text);
        while(t.find()){String e=t.group().toLowerCase(Locale.ROOT);out.add(new Cue("time",e,normalise(e,referenceTimeMillis),e.matches("\\d+ (?:seconds?|minutes?|hours?|days?|weeks?|months?|years?)")?.70:.88));}
        Matcher s=SPACE.matcher(text);while(s.find())out.add(new Cue("space",s.group().toLowerCase(Locale.ROOT),"",.78));return Collections.unmodifiableList(out);}
    private static String normalise(String e,long millis){Calendar c=Calendar.getInstance(TimeZone.getDefault(),Locale.ROOT);c.setTimeInMillis(millis);String today=date(c);
        if(e.equals("tomorrow"))c.add(Calendar.DAY_OF_MONTH,1);else if(e.equals("yesterday"))c.add(Calendar.DAY_OF_MONTH,-1);
        else if(e.equals("next week"))c.add(Calendar.WEEK_OF_YEAR,1);else if(e.equals("last week"))c.add(Calendar.WEEK_OF_YEAR,-1);
        else if(e.equals("next month"))c.add(Calendar.MONTH,1);else if(e.equals("last month"))c.add(Calendar.MONTH,-1);
        else if(e.equals("next year"))c.add(Calendar.YEAR,1);else if(e.equals("last year"))c.add(Calendar.YEAR,-1);
        else if(e.matches("in \\d+ days?")){c.add(Calendar.DAY_OF_MONTH,amount(e));}else if(e.matches("\\d+ days? ago")){c.add(Calendar.DAY_OF_MONTH,-amount(e));}
        else if(e.matches("in \\d+ weeks?")){c.add(Calendar.WEEK_OF_YEAR,amount(e));}else if(e.matches("\\d+ weeks? ago")){c.add(Calendar.WEEK_OF_YEAR,-amount(e));}
        else if(e.matches("in \\d+ months?")){c.add(Calendar.MONTH,amount(e));}else if(e.matches("\\d+ months? ago")){c.add(Calendar.MONTH,-amount(e));}
        else if(e.matches("in \\d+ years?")){c.add(Calendar.YEAR,amount(e));}else if(e.matches("\\d+ years? ago")){c.add(Calendar.YEAR,-amount(e));}
        else if(e.matches("in \\d+ hours?")){c.add(Calendar.HOUR_OF_DAY,amount(e));return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.ROOT).format(new Date(c.getTimeInMillis()));}
        else if(e.matches("in \\d+ minutes?")){c.add(Calendar.MINUTE,amount(e));return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.ROOT).format(new Date(c.getTimeInMillis()));}
        else if(e.matches("in \\d+ seconds?")){c.add(Calendar.SECOND,amount(e));return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT).format(new Date(c.getTimeInMillis()));}
        if(e.matches("\\d+ (?:seconds?|minutes?|hours?|days?|weeks?|months?|years?)"))return "duration-unspecified-direction";
        if(e.equals("today"))return today;if(e.equals("tonight")||e.contains("morning")||e.contains("afternoon")||e.contains("evening"))return today+" (time-of-day unspecified)";
        return date(c);}
    private static int amount(String s){Matcher m=Pattern.compile("\\d+").matcher(s);return m.find()&&m.group().length()<=9?Integer.parseInt(m.group()):0;} // "in 99999999999 seconds" threw
    private static String date(Calendar c){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(c.getTimeInMillis()));}
}
