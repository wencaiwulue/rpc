package netty;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class RegistryHandler extends ChannelInboundHandlerAdapter {
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
    System.out.println("server: " + msg);
    ctx.writeAndFlush("ok i got it, this is a test response\r\n")
        .addListener(
            (ChannelFutureListener)
                future -> {
                  boolean success = future.isSuccess();
                  if (success) {
                    System.out.println("ok");
                  } else {
                    System.out.println("not ok");
                  }
                })
        .sync();
    ctx.close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    cause.printStackTrace();
    ctx.close();
  }
}
