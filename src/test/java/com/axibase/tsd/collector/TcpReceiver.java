package com.axibase.tsd.collector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Nikolay Malevanny.
 */
public class TcpReceiver {
    private ServerSocketChannel serverSocketChannel;
    private StringBuilder sb;
    private int port;

    public TcpReceiver(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        sb = new StringBuilder();
        serverSocketChannel = ServerSocketChannel.open();
        try {
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                while (sb != null && serverSocketChannel.isOpen()) {
                    try {
                        System.out.println("start TCP receiving");
                        ByteBuffer bb = ByteBuffer.allocate(TestUtils.BUFFER_SIZE);

                        SocketChannel socketChannel = serverSocketChannel.accept();
                        while (socketChannel.read(bb) > 0) {
                            bb.flip();
                            CharBuffer cb = Charset.forName(TestUtils.UTF_8).decode(bb);
                            sb.append(cb);
                            bb.clear();
                        }
                    } catch (AsynchronousCloseException e) {
                        // ok
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void stop() throws Exception {
        if (serverSocketChannel != null) {
            serverSocketChannel.close();
        }
    }

    public StringBuilder getSb() {
        return sb;
    }
}
