package netty.websocket.websocketx.benchmarkserver;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.*;
import netty.websocket.websocketx.pub.RpcClient;
import util.FSTUtil;
import util.Request;
import util.Response;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final String WEBSOCKET_PATH = "/websocket";

    private WebSocketServerHandshaker handShaker;
    private InetSocketAddress remote;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            this.handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            this.handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        String localhost = req.headers().get("localhost");
        int localport = req.headers().getInt("localport", 8080);
        System.out.printf("localhost: %s, localport: %s\n", localhost, localport);
        remote = new InetSocketAddress(localhost, localport);
        // Handshake
        String uri = "wss://" + req.headers().get(HttpHeaderNames.HOST) + WEBSOCKET_PATH;
        System.out.println(uri);
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                uri, "diy-protocol", true, 5 * 1024 * 1024);
        handShaker = wsFactory.newHandshaker(req);
        if (handShaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handShaker.handshake(ctx.channel(), req);
            RpcClient.addConnection(remote, ctx.channel());
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            handShaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof TextWebSocketFrame) {
            // Echo the frame
            ctx.write(frame.retain());
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            byte[] array = textFrame.retain().duplicate().content().array();
            System.out.println("WebSocket Client received message: " + new String(array));
            Object o = FSTUtil.getConf().asObject(array);
            if (o instanceof Response) {
                RpcClient.addResponse(((Response) o).requestId, (Response) o);
            } else if (o instanceof Request) {
                if (WebSocketServer.PORT == 8443) {
                    RpcClient.doRequest(new InetSocketAddress("127.0.0.1", 8444), new Request() {
                    });
                }
                System.out.println(WebSocketServer.PORT + ": receive message from: " + remote.getPort());
            }
            return;
        }
        if (frame instanceof BinaryWebSocketFrame) {
            // Echo the frame
            ctx.write(frame.retain());
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
            byte[] bytes = new byte[binaryFrame.duplicate().retain().content().readableBytes()];
            System.out.println(bytes.length);
            binaryFrame.duplicate().content().readBytes(bytes);
            System.out.println("WebSocket Client received message: " + new String(bytes));
            Object o = FSTUtil.getConf().asObject(binaryFrame.copy().content().array());
            if (o instanceof Response) {
                RpcClient.addResponse(((Response) o).requestId, (Response) o);
            } else if (o instanceof Request) {
                // todo logic
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
