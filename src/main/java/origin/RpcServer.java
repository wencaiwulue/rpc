package origin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author naison
 * @since 3/25/2020 19:32
 */
public class RpcServer implements Runnable {
    private static final Logger log = LogManager.getLogManager().getLogger(RpcServer.class.getName());

    private Selector selector;

    private volatile boolean close = false;

    public RpcServer(InetSocketAddress addr) {
        try {
            this.selector = Selector.open();
            this.bind(addr);
        } catch (IOException e) {
            this.destroy();
            Runtime.getRuntime().exit(-1);
        }
    }

    private void bind(InetSocketAddress addr) throws IOException {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(addr);
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        log.info("服务已启动，已经绑定{}"+addr);
    }

    private void destroy() {
        close = true;
        selector.wakeup();
    }

    @Override
    public void run() {
        // Loop until destroy() is called
        while (true) {
            int keyCount = 0;
            try {
                if (!close) {
                    keyCount = selector.selectNow();
                }
                if (close) {
                    try {
                        selector.close();
                    } catch (IOException ioe) {
                        log.info("关闭selector出错楼"+ ioe);
                    }
                    break;
                }
            } catch (Throwable x) {
                log.info(""+ x);
                continue;
            }

            Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
            while (iterator != null && iterator.hasNext()) {
                SelectionKey sk = iterator.next();
                iterator.remove();
                processKey(sk);
            }//while
        }//while
    }

    private void processKey(SelectionKey selectionKey) {
        try {
            if (close) {
                cancelledKey(selectionKey);
            } else if (selectionKey.isValid()) {
                if (selectionKey.isAcceptable()) {
                    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel channel = serverSocketChannel.accept();
                    channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    channel.configureBlocking(false);
                    channel.register(selector, SelectionKey.OP_READ);
                    log.info("已经创建链接:{}"+channel.getRemoteAddress());
                } else if (selectionKey.isReadable()) {
                    boolean closeSocket = false;
                    if (!processRead(selectionKey)) {
                        closeSocket = true;
                    }
                    if (closeSocket) {
                        cancelledKey(selectionKey);
                    }
                }
            } else {
                cancelledKey(selectionKey);
            }
        } catch (CancelledKeyException e) {
            cancelledKey(selectionKey);
        } catch (Throwable t) {
            log.info("这次戳错啦"+ t);
        }
    }

    private void cancelledKey(SelectionKey key) {
        if (key == null) {
            return;
        }

        SocketChannel socketChannel = (SocketChannel) key.attach(null);
        if (socketChannel != null) {
            if (socketChannel.isConnected() && socketChannel.isOpen()) {
                try {
                    socketChannel.close();
                } catch (Exception e) {
                    log.info("关闭socketChannel出错啦！！！，{}"+ e.getMessage());
                }
            }
        }
        if (key.channel().isOpen()) {
            try {
                key.channel().close();
            } catch (Exception e) {
                log.info("关闭channel出错啦！！！，{}"+ e.getMessage());
            }
        }
    }

    private boolean processRead(SelectionKey key) {
        try {
            new Handler(key).run();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static class Handler implements Runnable {

        private final SelectionKey key;

        private Handler(SelectionKey key) {
            this.key = key;
        }

        @Override
        public void run() {
            SocketChannel channel = (SocketChannel) key.channel();
            if (channel != null) {
                synchronized (channel.toString().intern()) {// 一个channel不能同时被两个线程读取，不然内容回错乱，但是这里会不会有更好的方法呢？
                    try {
                        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);
                        int read = channel.read(byteBuffer);
                        if (read > 0) {// 客户端主动断开链接，也会发送一个读事件 返回值为-1
                            try {
                                byteBuffer.flip();
                                int len = byteBuffer.getInt();
                                if (len > 0) {
                                    ByteBuffer buffer = ByteBuffer.allocate(len);
                                    if (channel.read(buffer) == len) {
//                                        this.node.handle(request, channel);// handle the request
                                    }
                                }
                            } catch (OutOfMemoryError oom) {
                                log.info(oom.getLocalizedMessage());
                                log.info("length: " + byteBuffer.getInt());
                            }
                        }
                    } catch (SocketException e) {
                        key.channel();
                        try {
                            channel.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        log.info(e.getMessage());
                    } catch (ClosedChannelException e) {
                        key.channel();
                        try {
                            channel.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        log.info("channel关闭了");
                    } catch (IOException e) {
                        log.info("出错了，关闭channel"+ e);
                        try {
                            channel.close();
                        } catch (Exception ex) {
                            log.info(""+ex);
                        }
                        this.key.cancel();
                    }
                }
            } else {
                log.info("channel disconnect, should retry or not ?");
            }
        }
    }
}
