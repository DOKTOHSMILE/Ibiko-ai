package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** .gitignore read as git reads it, and ignored files never pushed. */
public class GitIgnoreTest {
    @Test
    public void rulesWorkAsInGit() {
        GitIgnore g = new GitIgnore();
        g.add("", "# deps\nnode_modules/\n*.log\n/build\n.env\n!keep.log\ndocs/**/*.tmp\n");
        g.add("app", "secret.txt\n");
        assertTrue(g.ignored("node_modules/react/index.js", false));
        assertTrue(g.ignored("web/node_modules/x.js", false));
        assertTrue(g.ignored("debug.log", false));
        assertTrue(g.ignored("src/deep/error.log", false));
        assertFalse("brought back with !", g.ignored("keep.log", false));
        assertTrue(g.ignored("build/out.apk", false));
        assertFalse("anchored to the root", g.ignored("src/build/x.java", false));
        assertTrue(g.ignored(".env", false));
        assertTrue(g.ignored("docs/a/b/c.tmp", false));
        assertTrue("a rule in its own folder", g.ignored("app/secret.txt", false));
        assertFalse("not outside it", g.ignored("secret.txt", false));
        assertFalse(g.ignored("src/main.py", false));
        assertFalse("a file named like a folder rule", g.ignored("node_modules", false));
    }

    @Test
    public void ignoredFilesAreNotPushed() throws Exception {
        GitHubGitTest.FakeHub hub = new GitHubGitTest.FakeHub();
        Map<String, String> files = new HashMap<String, String>();
        files.put(".gitignore", "node_modules/\n.env\n");
        files.put("main.js", "1\n");
        hub.refs.put("main", hub.commit(files, null, "first"));
        GitHubGit git = new GitHubGit(hub, "tok");
        File dir = new File(Files.createTempDirectory("git").toFile(), "app");
        git.clone("me/app", "", dir);
        new File(dir, "node_modules/react").mkdirs();
        Files.write(new File(dir, "node_modules/react/index.js").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, ".env").toPath(), "API_KEY=secret".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "main.js").toPath(), "2\n".getBytes(StandardCharsets.UTF_8));
        String st = git.status(dir);
        assertFalse(st, st.contains("node_modules") || st.contains(".env"));
        git.push(dir, "edit", "");
        assertEquals("only the real change went up", 1, hub.sentModes.size());
        assertTrue(hub.sentModes.containsKey("main.js"));
    }
}
