package netty.websocket.websocketx.pub;

import io.netty.channel.Channel;
import netty.websocket.websocketx.client.WebSocketClient;
import util.FSTUtil;
import util.Request;
import util.Response;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author naison
 * @since 3/14/2020 15:46
 */
public class RpcClient {
    private static final Map<InetSocketAddress, Channel> CONNECTIONS =
            new ConcurrentHashMap<>(); // 主节点于各个简单的链接
    private static final Map<Integer, Response> RESPONSE_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, CountDownLatch> RESPONSE_MAP_LOCK = new ConcurrentHashMap<>();
    private static final Deque<SocketRequest> REQUEST_TASK = new ArrayDeque<>(1000 * 1000);
    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition empty = lock.newCondition();
    private static final Condition notEmpty = lock.newCondition();
    private static final CyclicBarrier barrier = new CyclicBarrier(1);

    public static void addConnection(InetSocketAddress k, Channel v) {
        CONNECTIONS.put(k, v);
    }

    public static void addResponse(int k, Response v) {
        RESPONSE_MAP.put(k, v);
        RESPONSE_MAP_LOCK.get(k).countDown();
    }

    private static Channel getConnection(InetSocketAddress remote) {
        if (remote == null) return null;

        if (!CONNECTIONS.containsKey(remote)
                || !CONNECTIONS.get(remote).isOpen()
                || !CONNECTIONS.get(remote).isActive()) {
            synchronized (remote.toString().intern()) {
                if (!CONNECTIONS.containsKey(remote)
                        || !CONNECTIONS.get(remote).isOpen()
                        || !CONNECTIONS.get(remote).isActive()) {
                    try {
                        WebSocketClient.doConnection(remote);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return CONNECTIONS.get(remote);
    }

    public static Response doRequest(InetSocketAddress remote, final Request request) {
        if (remote == null) return null;

        CountDownLatch latch = new CountDownLatch(1);
        SocketRequest request1 = new SocketRequest(remote, request);
        REQUEST_TASK.addLast(request1);
        RESPONSE_MAP_LOCK.put(request.requestId, latch);

        try {
            boolean a = latch.await(5, TimeUnit.SECONDS);
            if (!a) {
                request1.cancelled = true;
                return null;
            }
        } catch (InterruptedException e) {
            request1.cancelled = true;
            return null;
        }
        Response response = RESPONSE_MAP.remove(request.requestId);
        System.out.printf("response info: %s\n", new String(FSTUtil.getConf().asByteArray(response)));
        return response;
    }

    private static void writeRequest() {
        while (true) {
            try {
                while (!REQUEST_TASK.isEmpty()) {
                    SocketRequest socketRequest = REQUEST_TASK.poll();
                    if (socketRequest != null && !socketRequest.cancelled) {
                        boolean success = false;
                        int retry = 0;
                        while (retry++ < 3) {
                            Channel channel = getConnection(socketRequest.address);
                            if (channel != null /*&& socketRequest.address.alive*/) {
                                channel.writeAndFlush(socketRequest.request);
                                success = true;
                                break;
                            }
                        }
                    }
                }
                barrier.await();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
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
