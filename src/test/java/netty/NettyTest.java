package netty;

import org.junit.jupiter.api.Test;

public class NettyTest {
  @Test
  public void testNetty() throws InterruptedException {
    new Server(80).start();
    new Server(81).start();
  }
}
