package serguei.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer {

    private static final int DEFAULT_TIMEOUT_MILS = 10_000;
    private static final int WAIT_FOR_PROCESSES_TO_FINISH_MILS = 10_000;

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final SocketAddress socketAddress;
    private final int timeoutMils;
    private final HttpServerRequestHandler requestHandler;
    private final Map<Long, SocketRunner> connections = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private ServerSocketRunner serverSocketRunner;
    private boolean isStopped;

    public HttpServer(HttpServerRequestHandler requestHandler, int port) {
        socketAddress = new InetSocketAddress(port);
        timeoutMils = DEFAULT_TIMEOUT_MILS;
        this.requestHandler = requestHandler;
    }

    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port) {
        socketAddress = new InetSocketAddress(inetAddress, port);
        timeoutMils = DEFAULT_TIMEOUT_MILS;
        this.requestHandler = requestHandler;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(socketAddress);
        serverSocket.setSoTimeout(timeoutMils);
        serverSocketRunner = new ServerSocketRunner();
        threadPool.execute(serverSocketRunner);
    }

    public void stop() {
        try {
            serverSocketRunner.stop();
            for (SocketRunner runner : connections.values()) {
                runner.stop();
            }
            long time = System.currentTimeMillis();
            while (connections.size() > 0 && System.currentTimeMillis() - time < WAIT_FOR_PROCESSES_TO_FINISH_MILS) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    // nothing we can do here
                }
            }
            isStopped = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getConnectionNo() {
        return connections.size();
    }

    public boolean isRunning() {
        if (serverSocketRunner != null) {
            return serverSocketRunner.isRunning();
        } else {
            return false;
        }
    }

    public boolean isStopped() {
        return isStopped;
    }

    protected HttpServerRequestHandler getRequestHandler() {
        return requestHandler;
    }

    private class ServerSocketRunner implements Runnable {

        private Long connectionNo = 0l;
        private volatile boolean finished;
        private volatile boolean started;

        @Override
        public void run() {
            while (!finished) {
                try {
                    started = true;
                    Socket socket = serverSocket.accept();
                    SocketRunner socketRunner = new SocketRunner(socket, connectionNo++);
                    threadPool.execute(socketRunner);
                } catch (IOException e) {
                    // we will get SocketException when serverSocket is closed
                }
            }
        }

        public void stop() throws IOException {
            finished = true;
            serverSocket.close();
        }

        public boolean isRunning() {
            return started && !finished;
        }
    }

    private class SocketRunner implements Runnable {

        private final Socket socket;
        private final Long connectionNo;
        private volatile boolean finished = false;

        public SocketRunner(Socket socket, long connectionNo) {
            this.socket = socket;
            this.connectionNo = connectionNo;
        }

        @Override
        public void run() {
            try {
                connections.put(connectionNo, this);
                while (!finished) {
                    HttpRequest request;
                    try {
                        request = new HttpRequest(socket.getInputStream());
                    } catch (HttpException e) {
                        // this happens when connection is closed by the client or
                        // client sends non-HTTP data
                        finished = true;
                        break;
                    }
                    try {
                        requestHandler.process(request, socket.getOutputStream());
                    } catch (IOException e) {
                        System.out.println("Error while processing request: " + e.getMessage());
                        finished = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                connections.remove(connectionNo);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finished = true;
            }
        }

        public void stop() {
            finished = true;
        }

    }

}
