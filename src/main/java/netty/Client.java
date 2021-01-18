package netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

public class Client {
  public void invoke() throws InterruptedException {
    EventLoopGroup loopGroup = new NioEventLoopGroup();
    try {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap
          .group(loopGroup)
          .channel(NioSocketChannel.class)
          .option(ChannelOption.TCP_NODELAY, true)
          .handler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                  ChannelPipeline pipeline = ch.pipeline();
                  pipeline.addLast(
                      "frameDecoder",
                      new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                  pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                  pipeline.addLast("encoder", new ObjectEncoder());
                  pipeline.addLast(
                      "decoder",
                      new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)));
                  pipeline.addLast("handler", new ClientHandler());
                }
              });
      ChannelFuture f = bootstrap.connect("localhost", 80).sync();
      f.channel().writeAndFlush("this is a test request").sync();
      f.channel().closeFuture().sync();
    } finally {
      loopGroup.shutdownGracefully();
    }
  }

  public static void main(String[] args) throws InterruptedException {
    new Client().invoke();
  }
}
