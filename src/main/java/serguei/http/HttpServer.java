package serguei.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

import serguei.http.utils.Utils;

/**
 * This is HTTP Server. When started it will listen on one (usually HTTP port) or two (plus HTTPS port - this will do
 * SSL handshake on the connection)
 * 
 * This class provides all necessary scaffolding including listening on a port and creating threads for processing
 * requests. The actual job of processing request is done in HttpServerRequestHandler object passed in a constructor
 * 
 * @author Serguei Poliakov
 *
 */
public class HttpServer {

    private static final int DEFAULT_TIMEOUT_MILS = 60_000;
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

    /**
     * Creating an instance of HttpServer listening to one ports (this does not actually start the server - call start()
     * for that). This listens to connection to all IP addresses (bind to 0.0.0.0).
     * 
     * @param requestHandler
     *            - an implementation of HttpServerRequestHandler that will processes all requests
     * @param port
     *            - port for HTTP requests
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port) {
        this(requestHandler, allLocalAddresses(), port, -1, null, null, null);
    }

    /**
     * Creating an instance of HttpServer listening to one ports (this does not actually start the server - call start()
     * for that).
     * 
     * @param requestHandler
     *            - an implementation of HttpServerRequestHandler that will processes all requests
     * @param inetAddress
     *            - an IP address to which the server is bound. You can use this constructor if you wish to limit your
     *            server to a particular local interface, if it is null, then we binding on 0.0.0.0
     * @param port
     *            - port for HTTP requests
     */
    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port) {
        this(requestHandler, inetAddress, port, -1, null, null, null);
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not actually start the server - call start()
     * for that). This listens to connection to all IP addresses (bind to 0.0.0.0).
     * 
     * @param requestHandler
     *            - an implementation of HttpServerRequestHandler that will processes all requests
     * @param port
     *            - port for HTTP requests
     * @param sslPort
     *            - port for HTTPS requests (on connection to this port the server will expect the client to start SSL
     *            handshake)
     * @param keyStorePath
     *            - path to Java keystore file (JKS file)
     * @param keyStorePassword
     *            - password for Java keystore file
     * @param certificatePassword
     *            - password for certificates in Java keystore
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port, int sslPort, String keyStorePath,
            String keyStorePassword, String certificatePassword) {
        this(requestHandler, allLocalAddresses(), port, sslPort, keyStorePath, keyStorePassword, certificatePassword);
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not actually start the server - call start()
     * for that).
     * 
     * @param requestHandler
     *            - an implementation of HttpServerRequestHandler that will processes all requests
     * @param inetAddress
     *            - an IP address to which the server is bound. You can use this constructor if you wish to limit your
     *            server to a particular local interface, if it is null, then we binding on 0.0.0.0
     * @param port
     *            - port for HTTP requests
     * @param sslPort
     *            - port for HTTPS requests (on connection to this port the server will expect the client to start SSL
     *            handshake)
     * @param keyStorePath
     *            - path to Java keystore file (JKS file)
     * @param keyStorePassword
     *            - password for Java keystore file
     * @param certificatePassword
     *            - password for certificates in Java keystore
     */
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

    /**
     * Starts the server. This operation will return after server sockets are bound to ports but before the server
     * actually starts listening on sockets
     * 
     * @throws IOException
     *             - often indicate that we can't bind to a port (e.g. because it is used by a different application or
     *             we don't have authority to find to it).
     */
    public void start() throws IOException {
        if (serverSocketRunners.size() > 0) {
            throw new RuntimeException("Server is already running");
        }
        isStopped = false;
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

    /**
     * Stops the server. This will stop listening to ports and attempt to wait until all current requests finish
     * execution (subject to a timeout)
     */
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

    /**
     * @return Current number of client connections to the server
     */
    public int getConnectionNo() {
        return connections.size();
    }

    /**
     * @return true if the server started successfully and accepting incoming connections
     */
    public boolean isRunning() {
        if (isStopped) {
            return false;
        }
        synchronized (serverSocketRunners) {
            if (serverSocketRunners.size() >= numberOfPorts) {
                return serverSocketRunners.get(numberOfPorts - 1).isRunning();
            } else {
                return false;
            }
        }
    }

    /**
     * @return true if the server stopped (after waiting for active requests to finish or timeout)
     */
    public boolean isStopped() {
        return isStopped;
    }

    protected HttpServerRequestHandler getRequestHandler() {
        return requestHandler;
    }

    private ServerSocket createServerSocket(SocketAddress socketAddress) throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(socketAddress);
        serverSocket.setSoTimeout(0);
        return serverSocket;
    }

    private ServerSocket createSslServerSocket(SocketAddress socketAddress) throws IOException, GeneralSecurityException {
        ServerSocket serverSocket = createSSLServerSocket();
        serverSocket.bind(socketAddress);
        serverSocket.setSoTimeout(0);
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
            started = true;
            while (!finished) {
                try {
                    Socket socket = serverSocket.accept();
                    SocketRunner socketRunner = new SocketRunner(socket);
                    threadPool.execute(socketRunner);
                } catch (IOException e) {
                    // we will get SocketException when serverSocket is closed, meaning it is not an error
                    // if we get this exception after initiating closing serverSocket
                    if (!finished) {
                        e.printStackTrace();
                    }
                    finished = true;
                } catch (Exception e) {
                    e.printStackTrace();
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

        private final ConnectionContext connectionContext;
        private volatile boolean finished = false;

        public SocketRunner(Socket socket) throws IOException {
            this.connectionContext = new ConnectionContext(socket);
        }

        @Override
        public void run() {
            Long connNo = connectionNo.incrementAndGet();
            connections.put(connNo, this);
            InputStream inputStream = null;
            OutputStream outputStream = null;
            PostponedCloseOutputStream postponedCloseOutputStream = null;
            try {
                connectionContext.getSocket().setSoTimeout(timeoutMils);
                inputStream = new BufferedInputStream(connectionContext.getSocket().getInputStream());
                postponedCloseOutputStream = new PostponedCloseOutputStream(connectionContext.getSocket().getOutputStream());
                outputStream = new BufferedOutputStream(postponedCloseOutputStream);
                while (!finished) {
                    HttpRequest request;
                    try {
                        request = new HttpRequest(inputStream);
                    } catch (HttpException | SocketTimeoutException | SocketException e) {
                        // this happens when connection is closed by the client or
                        // client sends non-HTTP data
                        finished = true;
                        break;
                    }
                    try {
                        requestHandler.process(connectionContext, request, outputStream);
                        if (postponedCloseOutputStream.shouldClose()) {
                            // this will cause the connection to close abnormally
                            finished = true;
                        } else {
                            outputStream.flush();
                        }
                    } catch (IOException e) {
                        finished = true;
                    }
                    if (connectionContext.getCloseAction() == ConnectionContext.CloseAction.RESET) {
                        connectionContext.getSocket().setSoLinger(true, 0);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (postponedCloseOutputStream != null) {
                    postponedCloseOutputStream.setClosing();
                }
                connections.remove(connNo);
                Utils.closeQuietly(inputStream);
                Utils.closeQuietly(outputStream);
                Utils.closeQuietly(connectionContext.getSocket());
                finished = true;
            }
        }

        public void stop() {
            finished = true;
        }

    }

    private class PostponedCloseOutputStream extends OutputStream {

        private final OutputStream output;

        private boolean toClose = false;
        private boolean closing = false;

        public PostponedCloseOutputStream(OutputStream output) throws IOException {
            this.output = output;
        }

        @Override
        public void write(int b) throws IOException {
            output.write(b);
        }

        public void write(byte b[], int off, int len) throws IOException {
            output.write(b, off, len);
        }

        public void flush() throws IOException {
            output.flush();
        }

        public void close() throws IOException {
            if (closing) {
                output.close();
            } else {
                toClose = true;
            }
        }

        public void setClosing() {
            closing = true;
        }

        public boolean shouldClose() {
            return toClose;
        }

    }

}
