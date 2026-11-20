package ai.ibiko.ibiko1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/** Questions put to Ibiko itself are answered as itself, and letters are written offline. */
public class SelfTalkTest {
    @Test
    public void questionsToIbikoAreAnsweredAsItself() {
        assertTrue(SelfTalk.reply("Can you cook?").startsWith("I can't cook"));
        assertTrue(SelfTalk.reply("Can you cook?").contains("recipes"));
        assertTrue(SelfTalk.reply("Do you sleep?").startsWith("I can't sleep"));
        assertTrue(SelfTalk.reply("Where are you from?").contains("your phone"));
        assertTrue(SelfTalk.reply("Are you human?").contains("not a person"));
        assertTrue(SelfTalk.reply("What's your name?").contains("Ibiko"));
        assertNull("a question about the world", SelfTalk.reply("Where is Lagos?"));
        assertNull("a request is not about Ibiko", SelfTalk.reply("Can you write a poem?"));
        assertNull(SelfTalk.reply("How do you cook rice?"));
    }

    @Test
    public void aQuestionToIbikoIsNotForTheEncyclopedia() {
        assertTrue(SelfTalk.toMe("Can you cook?"));
        assertTrue(SelfTalk.toMe("What is your favourite food?"));
        assertFalse("a polite opening isn't the question", SelfTalk.toMe("Can you tell me when Nigeria gained independence?"));
        assertFalse(SelfTalk.toMe("Do you know who founded Microsoft?"));
    }

    @Test
    public void lettersAreWrittenWithTheRightToneAndBlanks() {
        Random r = new Random(1);
        String mum = Letters.write("Write a letter to my mum", r);
        assertTrue(mum, mum.startsWith("Dear Mum,"));
        assertTrue(mum, mum.contains("[Your name]"));
        String leave = Letters.write("Write an email to my boss asking for sick leave", r);
        assertTrue(leave, leave.startsWith("Subject: "));
        assertTrue(leave, leave.contains("Dear [Name],"));
        assertTrue(leave, leave.contains("request leave"));
        String thanks = Letters.write("Can you write a thank you note to Ada?", r);
        assertTrue(thanks, thanks.contains("Ada,"));
        assertTrue(thanks, thanks.toLowerCase().contains("thank"));
        assertTrue(Letters.write("Write me a short letter", r).contains("[Name]"));
        assertNull("not a letter", Letters.write("What is a letter?", r));
        assertNull(Letters.write("Write a function that sorts a list", r));
    }
}
