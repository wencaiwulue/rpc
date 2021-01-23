package netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
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
import netty.websocket.client.WebSocketClientHandler;
import util.FSTUtil;
import util.Request;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;

public class NettyTest {

    static final String URL = "wss://127.0.0.1:8443/";

    public static void main(String[] args) throws Exception {
        URI uri = new URI(URL);

        SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
            httpHeaders.add("localhost", "127.0.0.1");
            httpHeaders.add("localport", 8445);
            WebSocketClientHandshaker handshake = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, "diy-protocol", true, httpHeaders);
            WebSocketClientHandler handler = new WebSocketClientHandler(handshake, new InetSocketAddress(uri.getHost(), uri.getPort()));

            Bootstrap bootstrap = new Bootstrap();
            bootstrap
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(
                            new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel ch) {
                                    ChannelPipeline p = ch.pipeline();
                                    p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(), uri.getPort()));
                                    p.addLast(new HttpClientCodec());
                                    p.addLast(new HttpObjectAggregator(8192));
                                    p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                                    p.addLast(handler);
                                }
                            });

            Channel ch = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
            handler.getHandshakeFuture().sync();
            //      ch.closeFuture().sync();
            String json = FSTUtil.getConf().asJsonString(new Request());
            ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(json.getBytes())))
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if ("bye".equalsIgnoreCase(msg)) {
                    ch.writeAndFlush(new CloseWebSocketFrame());
                    ch.closeFuture().sync();
                    break;
                } else if ("ping".equalsIgnoreCase(msg)) {
                    WebSocketFrame frame =
                            new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
                    ch.writeAndFlush(frame);
                } else {
                    Request request = new Request();
                    String jsonString = FSTUtil.getConf().asJsonString(request);
                    WebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer(jsonString.getBytes()));
                    ch.writeAndFlush(frame);
                }
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
