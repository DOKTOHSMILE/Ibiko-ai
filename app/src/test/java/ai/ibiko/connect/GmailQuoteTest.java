package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** Search words from the model stay one IMAP string: a line break can't start a command of its own. */
public class GmailQuoteTest {
    @Test
    public void aLineBreakCannotEndTheCommand() {
        String q = Gmail.quote("invoice\"\r\na9 SELECT INBOX\r\na10 UID STORE 1:* +FLAGS (\\Deleted)");
        assertFalse(q, q.contains("\r") || q.contains("\n"));
        assertEquals("\"from:bank \\\"x\\\"\"", Gmail.quote("from:bank \"x\""));
    }
}
