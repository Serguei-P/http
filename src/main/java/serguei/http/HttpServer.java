package serguei.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class HttpServer {

    private static final int DEFAULT_TIMEOUT_MILS = 10_000;
    private static final int WAIT_FOR_PROCESSES_TO_FINISH_MILS = 10_000;

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final SocketAddress socketAddress;
    private final SocketAddress sslSocketAddress;
    private final int timeoutMils;
    private final HttpServerRequestHandler requestHandler;
    private final Map<Long, SocketRunner> connections = new ConcurrentHashMap<>();
    private final String keyStorePath;
    private final String keyStorePassword;
    private final String certificatePassword;
    private final int numberOfPorts;

    private List<ServerSocketRunner> serverSocketRunners = new ArrayList<>();
    private volatile boolean isStopped;
    private AtomicLong connectionNo = new AtomicLong(0);

    public HttpServer(HttpServerRequestHandler requestHandler, int port) {
        this(requestHandler, allLocalAddresses(), port, -1, null, null, null);
    }

    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port) {
        this(requestHandler, inetAddress, port, -1, null, null, null);
    }

    public HttpServer(HttpServerRequestHandler requestHandler, int port, int sslPort, String keyStorePath,
            String keyStorePassword, String certificatePassword) {
        this(requestHandler, allLocalAddresses(), port, sslPort, keyStorePath, keyStorePassword, certificatePassword);
    }

    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port, int sslPort,
            String keyStorePath, String keyStorePassword, String certificatePassword) {
        socketAddress = new InetSocketAddress(inetAddress, port);
        if (sslPort > 0) {
            sslSocketAddress = new InetSocketAddress(inetAddress, sslPort);
            numberOfPorts = 2;
        } else {
            sslSocketAddress = null;
            numberOfPorts = 1;
        }
        timeoutMils = DEFAULT_TIMEOUT_MILS;
        this.requestHandler = requestHandler;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.certificatePassword = certificatePassword;
    }

    public void start() throws IOException {
        List<ServerSocket> serverSockets = new ArrayList<>();
        try {
            ServerSocket serverSocket = createServerSocket(socketAddress);
            serverSockets.add(serverSocket);
            if (sslSocketAddress != null) {
                serverSocket = createSslServerSocket(sslSocketAddress);
                serverSockets.add(serverSocket);
            }
        } catch (IOException | GeneralSecurityException e) {
            for (ServerSocket serverSocket : serverSockets) {
                Utils.closeQuietly(serverSocket);
            }
            throw new IOException(e.getMessage(), e);
        }
        synchronized (serverSocketRunners) {
            for (ServerSocket serverSocket : serverSockets) {
                ServerSocketRunner serverSocketRunner = new ServerSocketRunner(serverSocket);
                serverSocketRunners.add(serverSocketRunner);
                threadPool.execute(serverSocketRunner);
            }
        }
    }

    public void stop() {
        try {
            synchronized (serverSocketRunners) {
                for (ServerSocketRunner serverSocketRunner : serverSocketRunners) {
                    serverSocketRunner.stop();
                }
                serverSocketRunners.clear();
            }
            for (SocketRunner runner : connections.values()) {
                runner.stop();
            }
            long time = System.currentTimeMillis();
            while (connections.size() > 0 && System.currentTimeMillis() - time < WAIT_FOR_PROCESSES_TO_FINISH_MILS) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
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
        synchronized (serverSocketRunners) {
            if (serverSocketRunners.size() >= numberOfPorts) {
                return serverSocketRunners.get(numberOfPorts - 1).isRunning();
            } else {
                return false;
            }
        }
    }

    public boolean isStopped() {
        return isStopped;
    }

    protected HttpServerRequestHandler getRequestHandler() {
        return requestHandler;
    }

    private ServerSocket createServerSocket(SocketAddress socketAddress) throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(socketAddress);
        serverSocket.setSoTimeout(timeoutMils);
        return serverSocket;
    }

    private ServerSocket createSslServerSocket(SocketAddress socketAddress) throws IOException, GeneralSecurityException {
        ServerSocket serverSocket = createSSLServerSocket();
        serverSocket.bind(socketAddress);
        serverSocket.setSoTimeout(timeoutMils);
        return serverSocket;
    }

    private SSLServerSocket createSSLServerSocket() throws IOException, GeneralSecurityException {
        KeyStore keyStore = loadKeyStore();
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, certificatePassword.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        trustManagerFactory.init(keyStore);
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        SSLServerSocketFactory ssocketFactory = sslContext.getServerSocketFactory();
        return (SSLServerSocket)ssocketFactory.createServerSocket();
    }

    private KeyStore loadKeyStore() throws IOException, GeneralSecurityException {
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
        return keystore;
    }

    private static InetAddress allLocalAddresses() {
        byte[] address = {0, 0, 0, 0};
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private class ServerSocketRunner implements Runnable {

        private final ServerSocket serverSocket;
        private volatile boolean finished;
        private volatile boolean started;

        public ServerSocketRunner(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            while (!finished) {
                try {
                    started = true;
                    Socket socket = serverSocket.accept();
                    SocketRunner socketRunner = new SocketRunner(socket);
                    threadPool.execute(socketRunner);
                } catch (IOException e) {
                    // we will get SocketException when serverSocket is closed
                    finished = true;
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
        private volatile boolean finished = false;

        public SocketRunner(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            Long connNo = connectionNo.incrementAndGet();
            try {
                connections.put(connNo, this);
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
                connections.remove(connNo);
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
