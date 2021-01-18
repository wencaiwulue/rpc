package netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

public class Server {
  public void start() throws InterruptedException {
    EventLoopGroup boss = new NioEventLoopGroup();
    EventLoopGroup worker = new NioEventLoopGroup();
    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap
          .group(boss, worker)
          .channel(NioServerSocketChannel.class)
          .childHandler(
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
                  pipeline.addLast(new RegistryHandler());
                }
              })
          .option(ChannelOption.SO_BACKLOG, 128)
          .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);
      ChannelFuture f = bootstrap.bind(80).sync();
      f.channel().closeFuture().sync();
    } finally {
      boss.shutdownGracefully();
      worker.shutdownGracefully();
    }
  }

  public static void main(String[] args) throws InterruptedException {
    new Server().start();
  }
}
