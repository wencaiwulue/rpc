package netty;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

@ChannelHandler.Sharable
public class BusinessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        System.out.println("server: " + msg);
        ctx.writeAndFlush("ok i got it, this is a test response")
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
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
