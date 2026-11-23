package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.NumberSense;
import ai.ibiko.ibiko1.formulas.NumberSense.Kind;

/** A number is read for what it is from its shape and its sentence, never assumed: four figures are not always a year. */
public class NumberSenseTest {
    private static Kind kind(String sentence, String number) {
        for (NumberSense.Found f : NumberSense.read(sentence)) {
            if (f.text.equals(number)) {
                System.out.println("N " + sentence + "  [" + number + "] -> " + f.readings + " : " + f.why());
                return f.kind();
            }
        }
        System.out.println("N " + sentence + "  [" + number + "] not found in " + NumberSense.read(sentence));
        return null;
    }

    @Test
    public void fourFiguresAreAYearOnlyWhenTheSentenceSaysSo() {
        assertEquals(Kind.YEAR, kind("Nigeria became independent in 1960.", "1960"));
        assertEquals(Kind.YEAR, kind("Chinua Achebe was born in 1930.", "1930"));
        assertEquals(Kind.YEAR, kind("The pyramids were built around 2560 BC.", "2560"));
        assertEquals(Kind.YEAR, kind("I was born in nineteen sixty.", "nineteen sixty"));
        assertEquals(Kind.CODE, kind("My PIN is 1960.", "1960"));
        assertEquals(Kind.CODE, kind("I live in room 1960.", "1960"));
        assertEquals(Kind.QUANTITY, kind("About 1200 people live there.", "1200"));
        assertEquals(Kind.MONEY, kind("He paid ₦1500 for it.", "1500"));
        assertEquals(Kind.MONEY, kind("The shoes cost 2000 naira.", "2000"));
        assertEquals(Kind.MEASURE, kind("The car did 1960 km this week.", "1960"));
        assertEquals(Kind.TIME, kind("The meeting is at 1430 hours.", "1430"));
        assertEquals(Kind.NUMBER, kind("Pick a number: 1960.", "1960"));
    }

    @Test
    public void phoneNumbersDatesAndTimes() {
        assertEquals(Kind.PHONE, kind("Call me on 0803 123 4567 tomorrow.", "0803 123 4567"));
        assertEquals(Kind.PHONE, kind("My WhatsApp is +2348031234567.", "+2348031234567"));
        assertEquals(Kind.PHONE, kind("Reach us on 08031234567.", "08031234567"));
        assertEquals(Kind.DATE, kind("The invoice is dated 12/05/2024.", "12/05/2024"));
        assertEquals(Kind.DATE, kind("It happened on 9 November 1989.", "9"));
        assertEquals(Kind.TIME, kind("See you at 14:30.", "14:30"));
        assertEquals(Kind.TIME, kind("Wake me at 7 am.", "7"));
    }

    @Test
    public void otherKinds() {
        assertEquals(Kind.PERCENT, kind("Sales rose by 15 percent.", "15"));
        assertEquals(Kind.AGE, kind("She is 30 years old.", "30"));
        assertEquals(Kind.ORDINAL, kind("He came 3rd in the race.", "3"));
        assertEquals(Kind.QUANTITY, kind("Lagos has 15,070,000 people.", "15,070,000"));
        assertEquals(Kind.CODE, kind("Your OTP is 0451.", "0451"));
        List<NumberSense.Found> two = NumberSense.read("It gained independence in 1960 and became a republic in 1963.");
        assertEquals(2, two.size());
        assertTrue(two.get(0).kind() == Kind.YEAR && two.get(1).kind() == Kind.YEAR);
        assertTrue(NumberSense.explain("My PIN is 1960.").contains("a code or label"));
    }

    @Test
    public void thousandsMillionsAndWhichWayADateReads() {
        List<NumberSense.Found> k = NumberSense.read("I paid 50k for the phone.");
        assertEquals(Kind.MONEY, k.get(0).kind());
        assertEquals(50000.0, k.get(0).value, 0.1);
        assertEquals(Kind.QUANTITY, NumberSense.read("The page has 12k followers.").get(0).kind());
        assertEquals(2500000.0, NumberSense.read("They raised 2.5m naira.").get(0).value, 0.1);
        assertTrue(NumberSense.explain("Due 25/12/2024.").contains("day first (25 can't be a month)"));
        assertTrue(NumberSense.explain("Due 12/25/2024.").contains("month first (25 can't be a month)"));
        assertTrue(NumberSense.explain("Due 05/06/2024.").contains("could be month first"));
    }

    @Test
    public void whatIsPrivateIsNeverKept() {
        assertEquals("My PIN is [a code] and my OTP is [a code].", NumberSense.redact("My PIN is 1960 and my OTP is 0451."));
        assertEquals("Call me on [a phone number] tomorrow.", NumberSense.redact("Call me on 0803 123 4567 tomorrow."));
        assertEquals("My password is [a code] ok", NumberSense.redact("My password is hunter2 ok"));
        assertEquals("Nigeria became independent in 1960.", NumberSense.redact("Nigeria became independent in 1960."));
        assertEquals("About 1200 people live there.", NumberSense.redact("About 1200 people live there."));
        assertEquals("Card [a code] expired.", NumberSense.redact("Card 4111 1111 1111 1111 expired."));
        Facts f = new Facts(null);
        f.learn("My PIN is 1960. Nigeria gained independence on 1 October 1960.", null, 2, 1L);
        for (ai.ibiko.ibiko1.formulas.Grammar.Triple t : f.all()) {
            assertTrue("no PIN kept: " + t, !(t.object + t.surface).contains("1960") || t.toString().contains("October"));
        }
    }

    @Test
    public void askedToReadTheNumbers() {
        WordTalk talk = new WordTalk(null);
        assertTrue(WordTalk.asks("Read the numbers in: Call 0803 123 4567 at 1430 hours, PIN 1960"));
        String said = talk.reply("Read the numbers in: Call 0803 123 4567 at 1430 hours, PIN 1960");
        System.out.println(said);
        assertTrue(said, said.contains("“0803 123 4567”: a phone number") && said.contains("“1430”: a time of day")
                && said.contains("“1960”: a code or label"));
    }
}
