package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

/** Units, dates and percentages, worked out exactly against a fixed clock. */
public class EverydayTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 14, 5);

    private static String a(String q) {
        return Everyday.answer(q, NOW);
    }

    @Test
    public void units() {
        assertEquals("5 kilometres is about 3.11 miles.", a("Convert 5 km to miles"));
        assertEquals("1 pound is about 453.6 grams.", a("How many grams in a pound?"));
        assertEquals("30 degrees Celsius is 86 degrees Fahrenheit.", a("30 C in F"));
        assertEquals("100 degrees Fahrenheit is about 37.78 degrees Celsius.", a("What is 100 F in Celsius?"));
        assertEquals("1 hour is 3,600 seconds.", a("How many seconds in an hour?"));
        assertEquals("2 gigabytes is 2,048 megabytes.", a("2 GB to MB"));
        assertEquals("1 acre is about 0.4047 hectares.", a("how many hectares in an acre"));
        assertNull("different kinds don't convert", a("convert 5 km to kg"));
        assertNull("not a conversion", a("What is the capital of Ghana?"));
    }

    @Test
    public void dates() {
        assertEquals("Today is Friday, 20 November 2026.", a("What's the date today?"));
        assertEquals("It's 2:05 pm on this device's clock.", a("What time is it?"));
        assertTrue(a("How many days until Christmas?"), a("How many days until Christmas?").startsWith("35 days until Friday, 25 December 2026"));
        assertEquals("1 October 1960 was a Saturday.", a("What day of the week was 1 October 1960?"));
        assertEquals("90 days from today is Thursday, 18 February 2027.", a("What's the date 90 days from now?"));
        assertEquals("No, 2027 is not a leap year.", a("Is 2027 a leap year?"));
        assertEquals("Yes, 2028 is a leap year: February has 29 days.", a("Is 2028 a leap year?"));
        assertEquals("Born on Friday, 4 August 1961: 65 years old today.", a("How old am I if I was born on 4 August 1961?"));
        assertTrue(a("How many days between 1 January 2026 and 1 March 2026?").startsWith("There are 59 days"));
        assertTrue(a("When is Christmas?"), a("When is Christmas?").startsWith("It's on Friday, 25 December 2026, in 35 days"));
    }

    @Test
    public void percentages() {
        assertEquals("15% of 200 is 30.", a("What is 15% of 200?"));
        assertEquals("30 is 25% of 120.", a("What percent is 30 of 120?"));
        assertEquals("200 plus 15% is 230.", a("200 plus 15%"));
        assertEquals("From 80 to 100 is an increase of 25%.", a("percentage increase from 80 to 100"));
    }
}
