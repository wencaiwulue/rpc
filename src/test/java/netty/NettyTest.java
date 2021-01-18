package netty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;

public class NettyTest {

    private Server server = new Server(80);

    @BeforeEach
    public void testNetty() throws InterruptedException, CertificateException, SSLException {
        server.start();
    }

    @Test
    public void testSend() throws InterruptedException, SSLException, URISyntaxException {
        Client client = new Client(81);
        client.invoke();

    }

}
