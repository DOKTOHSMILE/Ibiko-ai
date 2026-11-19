package ai.ibiko.linux;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** An archive can't write outside its folder through a link it unpacked, nor copy an outside file in. */
public class TarEscapeTest {
    private static void header(ByteArrayOutputStream out, String name, char type, String link, byte[] body) throws Exception {
        byte[] h = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, h, 0, n.length);
        byte[] mode = "0000644\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(mode, 0, h, 100, mode.length);
        byte[] size = String.format("%011o\0", body.length).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(size, 0, h, 124, size.length);
        h[156] = (byte) type;
        if (link != null) {
            byte[] l = link.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(l, 0, h, 157, l.length);
        }
        out.write(h);
        out.write(body);
        out.write(new byte[(512 - body.length % 512) % 512]);
    }

    @Test
    public void linksCannotLeadOut() throws Exception {
        File top = Files.createTempDirectory("tar").toFile();
        File outside = new File(top, "outside");
        outside.mkdirs();
        File secret = new File(outside, "secret.txt");
        Files.write(secret.toPath(), "keys".getBytes(StandardCharsets.UTF_8));
        File dest = new File(top, "rootfs");
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        header(tar, "evil", '2', outside.getAbsolutePath(), new byte[0]);
        header(tar, "evil/planted.txt", '0', null, "pwned".getBytes(StandardCharsets.UTF_8));
        header(tar, "copied.txt", '1', "../outside/secret.txt", new byte[0]);
        header(tar, "ok.txt", '0', null, "fine".getBytes(StandardCharsets.UTF_8));
        tar.write(new byte[1024]);
        TarGz.untar(new ByteArrayInputStream(tar.toByteArray()), dest);
        assertFalse("nothing written through the link", new File(outside, "planted.txt").exists());
        assertFalse("no outside file copied in", new File(dest, "copied.txt").exists());
        assertTrue(new File(dest, "ok.txt").isFile());
    }
}
