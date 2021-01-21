package netty.websocket.websocketx.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;
import netty.websocket.websocketx.pub.RpcClient;
import netty.websocket.websocketx.server.HeartBeatHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class WebSocketClient {

    static final String URL = "wss://%s:%s/";
    static int port = Integer.parseInt(System.getProperty("","8080"));

    public static void doConnection(InetSocketAddress address) throws Exception {
        URI uri = new URI(String.format(URL, address.getHostName(), address.getPort()));

        final SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();


        EventLoopGroup group = new NioEventLoopGroup();
        try {

            AtomicReference<WebSocketClientHandler> ref = new AtomicReference<>();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(), uri.getPort()));
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(8192));
                            p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                            p.addLast(new WebSocket13FrameEncoder(true));
                            p.addLast(new WebSocket13FrameDecoder(false, true, 65536));
                            p.addLast(new IdleStateHandler(2, 3, 5, TimeUnit.SECONDS));
                            p.addLast(new HeartBeatHandler());
                            DefaultHttpHeaders headers = new DefaultHttpHeaders();
                            headers.add("localhost", "127.0.0.1");
                            headers.add("localport", port);
                            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                                    uri, WebSocketVersion.V13, "diy-protocol", true, headers);
                            ref.set(new WebSocketClientHandler(handshaker));
                            p.addLast(ref.get());
                        }
                    });

            Channel ch = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
            ref.get().getHandshakeFuture().sync();
            RpcClient.addConnection(new InetSocketAddress("127.0.0.1", port), ch);

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if ("bye".equalsIgnoreCase(msg)) {
                    ch.writeAndFlush(new CloseWebSocketFrame()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                    ch.closeFuture().sync();
                    break;
                } else if ("ping".equalsIgnoreCase(msg)) {
                    WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
                    ch.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    WebSocketFrame frame = new TextWebSocketFrame(msg);
                    ch.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
