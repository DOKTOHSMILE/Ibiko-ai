package ai.ibiko.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What waits for the pilot's yes: connector calls that change something; reading goes ahead. */
public class ApprovalTest {
    @Test
    public void changesAskReadsDoNot() {
        for (String c : new String[] {"github.create_issue {\"title\":\"x\"}", "github.delete_branch main", "gmail.send to a",
                "github.merge_pull_request 4", "notion.updatePage x", "slack.post_message hi", "github.add_comment 3",
                "calendar.create_event x", "github.push_files x",
                // not named as a change, but not a read either: asked
                "gmail.draft a@b.c | Hi | text", "db.execute_sql DROP TABLE x", "ops.deploy prod", "db.insert x", "srv.restart",
                "auth.revoke token", "mcp.do_thing x"}) {
            assertTrue(c, Approval.changes(c));
        }
        for (String c : new String[] {"github.repos", "github.get_file_contents a", "github.list_issues", "gmail.search x",
                "github.search_code y", "calendar.list_events", "github.get_me", "drive.read_file a",
                "web.search cats", "docs.read py 1", "calendar.week", "social.open https://x.com/a", "db.query_rows x"}) {
            assertFalse(c, Approval.changes(c));
        }
    }
}
