package netty.websocket;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import netty.websocket.client.WebSocketClient;
import util.FSTUtil;
import util.Request;
import util.Response;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author naison
 * @since 3/14/2020 15:46
 */
public class RpcClient {
    private static final Map<InetSocketAddress, Channel> CONNECTIONS = new ConcurrentHashMap<>();
    private static final ArrayBlockingQueue<SocketRequest> REQUEST_TASK = new ArrayBlockingQueue<>(10 * 1000 * 1000);
    private static final Map<Integer, Response> RESPONSE_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, CountDownLatch> RESPONSE_MAP_LOCK = new ConcurrentHashMap<>();
    private static final Map<Integer, Consumer<Response>> RESPONSE_CONSUMER = new ConcurrentHashMap<>();


    static {
        new Thread(RpcClient::writeRequest).start();
    }

    public static void addConnection(InetSocketAddress k, Channel v) {
        CONNECTIONS.put(k, v);
    }

    public static Map<InetSocketAddress, Channel> getConnection() {
        return ImmutableMap.copyOf(CONNECTIONS);
    }

    public static void addResponse(int requestId, Response response) {
        RESPONSE_MAP.put(requestId, response);
        CountDownLatch latch = RESPONSE_MAP_LOCK.get(requestId);
        if (latch != null) {
            latch.countDown();
        } else {
            Consumer<Response> consumer = RESPONSE_CONSUMER.remove(requestId);
            if (consumer != null) {
                consumer.accept(response);
            }
        }
    }

    private static Channel getConnection(InetSocketAddress remote) {
        if (remote == null) return null;
        Supplier<Boolean> supplier = () -> !CONNECTIONS.containsKey(remote)
                || !CONNECTIONS.get(remote).isOpen()
                || !CONNECTIONS.get(remote).isActive()
                || !CONNECTIONS.get(remote).isRegistered();

        if (supplier.get()) {
            synchronized (remote.toString().intern()) {
                if (supplier.get()) {
                    WebSocketClient.doConnection(remote);
                }
            }
        }
        return CONNECTIONS.get(remote);
    }

    public static Response doRequest(InetSocketAddress remote, final Request request) {
        if (remote == null) return null;

        CountDownLatch latch = new CountDownLatch(1);
        SocketRequest socketRequest = new SocketRequest(remote, request);
        try {
            REQUEST_TASK.put(socketRequest);
            RESPONSE_MAP_LOCK.put(request.requestId, latch);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            boolean a = latch.await(5, TimeUnit.SECONDS);
            if (!a) {
                socketRequest.cancelled = true;
                return null;
            }
        } catch (InterruptedException e) {
            socketRequest.cancelled = true;
            return null;
        }
        Response response = RESPONSE_MAP.remove(request.requestId);
        System.out.printf("response info: %s\n", FSTUtil.getConf().asJsonString(response));
        return response;
    }

    public static void doRequestAsync(InetSocketAddress remote, Request request, Consumer<Response> nextTodo) {
        if (remote == null) {
            return;
        }
        SocketRequest socketRequest = new SocketRequest(remote, request);
        try {
            REQUEST_TASK.put(socketRequest);
            RESPONSE_CONSUMER.put(request.requestId, nextTodo);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void writeRequest() {
        while (true) {
            SocketRequest socketRequest = null;
            try {
                socketRequest = REQUEST_TASK.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (socketRequest != null && !socketRequest.cancelled) {
                boolean success = false;
                int retry = 0;
                while (retry++ < 3) {
                    Channel channel = getConnection(socketRequest.address);
                    if (channel != null) {
                        String json = FSTUtil.getConf().asJsonString(socketRequest.request);
                        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(json.getBytes())))
                                .addListeners(ChannelFutureListener.CLOSE_ON_FAILURE);
                        success = true;
                        break;
                    }
                }
            }
        }
    }

    private static class SocketRequest {
        public InetSocketAddress address;
        public Request request;
        public boolean cancelled;

        private SocketRequest(InetSocketAddress address, Request request) {
            this.address = address;
            this.request = request;
            this.cancelled = false;
        }
    }
}
