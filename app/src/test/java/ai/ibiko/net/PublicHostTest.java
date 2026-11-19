package ai.ibiko.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import org.junit.Test;

/** A page or the model can't send Ibiko to the phone's own network: only public addresses are fetched. */
public class PublicHostTest {
    @Test
    public void localAddressesAreRefused() throws Exception {
        for (String ip : new String[] {"127.0.0.1", "10.0.0.1", "172.16.4.2", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "::1", "fe80::1", "fd00::1"}) {
            assertFalse(ip, WebGet.publicAddress(InetAddress.getByName(ip)));
        }
        assertFalse(WebGet.publicHost("localhost"));
        assertFalse(WebGet.publicHost("printer.local"));
        assertFalse(WebGet.publicHost("192.168.0.1"));
    }

    @Test
    public void publicAddressesAreFetched() throws Exception {
        for (String ip : new String[] {"8.8.8.8", "1.1.1.1", "100.128.0.1", "2606:4700::1111"}) {
            assertTrue(ip, WebGet.publicAddress(InetAddress.getByName(ip)));
        }
    }
}
