package netty.websocket.websocketx.server;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Locale;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
    /**
     * ping and pong frames already handled {@link
     * io.netty.handler.codec.http.websocketx.WebSocketProtocolHandler#decode(io.netty.channel.ChannelHandlerContext,
     * io.netty.handler.codec.http.websocketx.WebSocketFrame, java.util.List)} *
     */
    if (frame instanceof TextWebSocketFrame) {
      // Send the uppercase string back.
      String request = ((TextWebSocketFrame) frame).text();
      ctx.channel()
          .writeAndFlush(new TextWebSocketFrame(request.toUpperCase(Locale.US)))
          .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    } else {
      String message = "unsupported frame type: " + frame.getClass().getName();
      throw new UnsupportedOperationException(message);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    System.out.printf("disconnected from client: %s\n", ctx.channel().remoteAddress());
    ctx.close();
  }
}
