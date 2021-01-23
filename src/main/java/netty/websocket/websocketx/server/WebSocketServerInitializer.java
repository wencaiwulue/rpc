package netty.websocket.websocketx.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final String WEBSOCKET_PATH = "/";

    private final SslContext sslCtx;

    public WebSocketServerInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(8192));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        //  this handler will handleShake with client
        pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, "diy-protocol", true));
        pipeline.addLast(new WebSocket13FrameDecoder(true, true, 65536));
        pipeline.addLast(new WebSocket13FrameEncoder(false));
        pipeline.addLast(new IdleStateHandler(2, 3, 5, TimeUnit.SECONDS));
        pipeline.addLast(new HeartBeatHandler());
        pipeline.addLast(new WebSocketFrameHandler());
    }
}
