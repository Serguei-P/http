package serguei.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import serguei.http.utils.Utils;

/**
 * This is HTTP Server. When started it will listen on one (usually HTTP port)
 * or two (plus HTTPS port - this will do SSL handshake on the connection)
 *
 * This class provides all necessary scaffolding including listening on a port
 * and creating threads for processing requests. The actual job of processing
 * request is done in HttpServerRequestHandler object passed in a constructor
 *
 * @author Serguei Poliakov
 *
 */
public class HttpServer {

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final SocketAddress socketAddress;
    private final SocketAddress sslSocketAddress;
    private final HttpServerRequestHandler requestHandler;
    private final Map<Long, SocketRunner> connections = new ConcurrentHashMap<>();
    private final int numberOfPorts;
    private int timeoutMils;

    private HttpServerOnRequestHeadersProcess onRequestHeadersHandler;
    private HttpServerOnConnectProcess onConnectHandler;
    private TlsVersion[] enabledTlsProtocols;
    private String[] enabledCipherSuites;
    private List<ServerSocketRunner> serverSocketRunners = new ArrayList<>();
    private volatile boolean isStopped;
    private AtomicLong connectionNo = new AtomicLong(0);
    private KeyStoreData defaultKeyStore;
    private final List<KeyStoreData> keyStores = new ArrayList<>();
    private volatile boolean requireSni = false;
    private volatile boolean warnWhenSniNotMatching = false;
    private int throttlingDelayMils = 0;
    private boolean tcpNoDelay;
    private boolean needClientAuthentication;
    private final int waitForProcessesToFinishOnShutdownMs;

    /**
     * Creating an instance of HttpServer listening to one ports (this does not
     * actually start the server - call start() for that). This listens to
     * connection to all IP addresses (bind to 0.0.0.0).
     * 
     * This is the simplest way to create a server
     * 
     * @param requestHandler - an implementation of HttpServerRequestHandler that
     *                       will processes all requests
     * @param port           - port for HTTP requests
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port) {
        this(requestHandler, (new ServerOptions()).setPort(port));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that).
     * 
     * This is the most flexible way to create a server
     *
     * @param requestHandler - an implementation of HttpServerRequestHandler that
     *                       will processes all requests
     * @param options        - server options
     */
    public HttpServer(HttpServerRequestHandler requestHandler, ServerOptions options) {
        this.requestHandler = requestHandler;
        int portNo = 0;
        InetAddress inetAddress = options.getInetAddress() != null ? options.getInetAddress() : allLocalAddresses();
        if (options.getPort() > 0) {
            this.socketAddress = new InetSocketAddress(inetAddress, options.getPort());
            portNo++;
        } else {
            this.socketAddress = null;
        }
        if (options.getPort() > 0) {
            this.sslSocketAddress = new InetSocketAddress(inetAddress, options.getSslPort());
            portNo++;
        } else {
            sslSocketAddress = null;
        }
        this.numberOfPorts = portNo;
        this.timeoutMils = options.getTimeoutMs();
        this.defaultKeyStore = options.getDefaultKeyStore();
        this.tcpNoDelay = options.isTcpNoDelay();
        this.needClientAuthentication = options.isNeedClientAuthentication();
        this.waitForProcessesToFinishOnShutdownMs = options.getWaitForProcessesToFinishOnShutdownMs();
    }

    /**
     * Creating an instance of HttpServer listening to one ports (this does not
     * actually start the server - call start() for that).
     * 
     * @param requestHandler - an implementation of HttpServerRequestHandler that
     *                       will processes all requests
     * @param inetAddress    - an IP address to which the server is bound. You can
     *                       use this constructor if you wish to limit your server
     *                       to a particular local interface, if it is null, then we
     *                       binding on 0.0.0.0
     * @param port           - port for HTTP requests
     */
    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port) {
        this(requestHandler, (new ServerOptions()).setPort(port).setInetAddress(inetAddress));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that). This listens to
     * connection to all IP addresses (bind to 0.0.0.0).
     * 
     * @param requestHandler      - an implementation of HttpServerRequestHandler
     *                            that will processes all requests
     * @param port                - port for HTTP requests
     * @param sslPort             - port for HTTPS requests (on connection to this
     *                            port the server will expect the client to start
     *                            SSL handshake)
     * @param keyStorePath        - path to Java keystore file (JKS file)
     * @param keyStorePassword    - password for Java keystore file
     * @param certificatePassword - password for certificates in Java keystore
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port, int sslPort, String keyStorePath,
            String keyStorePassword, String certificatePassword) {
        this(requestHandler, (new ServerOptions()).setPort(port).setSslPort(sslPort).setTlsParameters(keyStorePath,
                keyStorePassword, certificatePassword, null));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that). This listens to
     * connection to all IP addresses (bind to 0.0.0.0).
     * 
     * @param requestHandler         - an implementation of HttpServerRequestHandler
     *                               that will processes all requests
     * @param port                   - port for HTTP requests
     * @param sslPort                - port for HTTPS requests (on connection to
     *                               this port the server will expect the client to
     *                               start SSL handshake)
     * @param keyStorePath           - path to Java keystore file (JKS file)
     * @param keyStorePassword       - password for Java keystore file
     * @param certificatePassword    - password for certificates in Java keystore
     * @param clientAuthTrustManager - trust manager for client authentication (when
     *                               requested)
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port, int sslPort, String keyStorePath,
            String keyStorePassword, String certificatePassword, TrustManager clientAuthTrustManager) {
        this(requestHandler, (new ServerOptions()).setPort(port).setSslPort(sslPort).setTlsParameters(keyStorePath,
                keyStorePassword, certificatePassword, clientAuthTrustManager));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that). This listens to
     * connection to all IP addresses (bind to 0.0.0.0).
     * 
     * @param requestHandler      - an implementation of HttpServerRequestHandler
     *                            that will processes all requests
     * @param port                - port for HTTP requests
     * @param sslPort             - port for HTTPS requests (on connection to this
     *                            port the server will expect the client to start
     *                            SSL handshake)
     * @param keyStore            - Java keystore
     * @param certificatePassword - password for certificates in Java keystore
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port, int sslPort, KeyStore keyStore,
            String certificatePassword, TrustManager clientAuthTrustManager) {
        this(requestHandler, (new ServerOptions()).setPort(port).setSslPort(sslPort).setTlsParameters(keyStore,
                certificatePassword, clientAuthTrustManager));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that). This listens to
     * connection to all IP addresses (bind to 0.0.0.0).
     * 
     * @param requestHandler      - an implementation of HttpServerRequestHandler
     *                            that will processes all requests
     * @param port                - port for HTTP requests
     * @param sslPort             - port for HTTPS requests (on connection to this
     *                            port the server will expect the client to start
     *                            SSL handshake)
     * @param keyStore            - Java keystore
     * @param certificatePassword - password for certificates in Java keystore
     */
    public HttpServer(HttpServerRequestHandler requestHandler, int port, int sslPort, KeyStore keyStore,
            String certificatePassword) {
        this(requestHandler, (new ServerOptions()).setPort(port).setSslPort(sslPort).setTlsParameters(keyStore,
                certificatePassword, null));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that).
     * 
     * @param requestHandler      - an implementation of HttpServerRequestHandler
     *                            that will processes all requests
     * @param inetAddress         - an IP address to which the server is bound. You
     *                            can use this constructor if you wish to limit your
     *                            server to a particular local interface, if it is
     *                            null, then we binding on 0.0.0.0
     * @param port                - port for HTTP requests
     * @param sslPort             - port for HTTPS requests (on connection to this
     *                            port the server will expect the client to start
     *                            SSL handshake)
     * @param keyStorePath        - path to Java keystore file (JKS file)
     * @param keyStorePassword    - password for Java keystore file
     * @param certificatePassword - password for certificates in Java keystore
     */
    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port, int sslPort,
            String keyStorePath, String keyStorePassword, String certificatePassword) {
        this(requestHandler, (new ServerOptions()).setInetAddress(inetAddress).setPort(port).setSslPort(sslPort)
                .setTlsParameters(keyStorePath, keyStorePassword, certificatePassword, null));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that).
     * 
     * @param requestHandler      - an implementation of HttpServerRequestHandler
     *                            that will processes all requests
     * @param inetAddress         - an IP address to which the server is bound. You
     *                            can use this constructor if you wish to limit your
     *                            server to a particular local interface, if it is
     *                            null, then we binding on 0.0.0.0
     * @param port                - port for HTTP requests
     * @param sslPort             - port for HTTPS requests (on connection to this
     *                            port the server will expect the client to start
     *                            SSL handshake)
     * @param keyStore            - Java keystore
     * @param certificatePassword - password for certificates in Java keystore
     */
    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port, int sslPort,
            KeyStore keyStore, String certificatePassword) {
        this(requestHandler, (new ServerOptions()).setInetAddress(inetAddress).setPort(port).setSslPort(sslPort)
                .setTlsParameters(keyStore, certificatePassword, null));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that).
     *
     * @param requestHandler         - an implementation of HttpServerRequestHandler
     *                               that will processes all requests
     * @param inetAddress            - an IP address to which the server is bound.
     *                               You can use this constructor if you wish to
     *                               limit your server to a particular local
     *                               interface, if it is null, then we binding on
     *                               0.0.0.0
     * @param port                   - port for HTTP requests
     * @param sslPort                - port for HTTPS requests (on connection to
     *                               this port the server will expect the client to
     *                               start SSL handshake)
     * @param keyStorePath           - path to Java keystore file (JKS file)
     * @param keyStorePassword       - password for Java keystore file
     * @param certificatePassword    - password for certificates in Java keystore
     * @param clientAuthTrustManager - trust manager for client authentication (when
     *                               requested)
     */
    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port, int sslPort,
            String keyStorePath, String keyStorePassword, String certificatePassword,
            TrustManager clientAuthTrustManager) {
        this(requestHandler, (new ServerOptions()).setInetAddress(inetAddress).setPort(port).setSslPort(sslPort)
                .setTlsParameters(keyStorePath, keyStorePassword, certificatePassword, clientAuthTrustManager));
    }

    /**
     * Creating an instance of HttpServer listening to two ports (this does not
     * actually start the server - call start() for that).
     *
     * @param requestHandler         - an implementation of HttpServerRequestHandler
     *                               that will processes all requests
     * @param inetAddress            - an IP address to which the server is bound.
     *                               You can use this constructor if you wish to
     *                               limit your server to a particular local
     *                               interface, if it is null, then we binding on
     *                               0.0.0.0
     * @param port                   - port for HTTP requests
     * @param sslPort                - port for HTTPS requests (on connection to
     *                               this port the server will expect the client to
     *                               start SSL handshake)
     * @param keyStore               - Java keystore
     * @param certificatePassword    - password for certificates in Java keystore
     * @param clientAuthTrustManager - trust manager for client authentication (when
     *                               requested)
     */
    public HttpServer(HttpServerRequestHandler requestHandler, InetAddress inetAddress, int port, int sslPort,
            KeyStore keyStore, String certificatePassword, TrustManager clientAuthTrustManager) {
        this(requestHandler, (new ServerOptions()).setInetAddress(inetAddress).setPort(port).setSslPort(sslPort)
                .setTlsParameters(keyStore, certificatePassword, clientAuthTrustManager));
    }

    /**
     * Starts the server. This operation will return after server sockets are bound
     * to ports but before the server actually starts listening on sockets
     *
     * @throws IOException - often indicate that we can't bind to a port (e.g.
     *                     because it is used by a different application or we don't
     *                     have authority to find to it).
     */
    public void start() throws IOException {
        start(1, 0, -1);
    }

    /**
     * Starts the server making several attempts. This operation will return after
     * server sockets are bound to ports but before the server actually starts
     * listening on sockets
     *
     * This method of starting a server can be useful when this server is used in
     * testing as on some OSs a socket closed by previous test might linger for a
     * bit resulting in the next test to fail
     *
     * @param attempts      - number of attempts
     * @param timeoutMillis - pause between two attempts in milliseconds
     * @throws IOException - often indicate that we can't bind to a port (e.g.
     *                     because it is used by a different application or we don't
     *                     have authority to find to it).
     */
    public void start(int attempts, int timeoutMillis) throws IOException {
        start(attempts, timeoutMillis, -1);
    }

    /**
     * Starts the server making several attempts. This operation will return after
     * server sockets are bound to ports but before the server actually starts
     * listening on sockets
     * 
     * This method of starting a server can be useful when this server is used in
     * testing as on some OSs a socket closed by previous test might linger for a
     * bit resulting in the next test to fail
     * 
     * @param attempts      - number of attempts
     * @param timeoutMillis - pause between two attempts in milliseconds
     * @param backlog       - requested maximum length of the queue of incoming
     *                      connections
     * @throws IOException - often indicate that we can't bind to a port (e.g.
     *                     because it is used by a different application or we don't
     *                     have authority to find to it).
     */
    public void start(int attempts, int timeoutMillis, int backlog) throws IOException {
        if (serverSocketRunners.size() > 0) {
            throw new IllegalStateException("Server is already running");
        }
        isStopped = false;
        List<ServerSocket> serverSockets = new ArrayList<>();
        List<Boolean> ssl = new ArrayList<>();
        try {
            if (socketAddress != null) {
                ServerSocket serverSocket = createServerSocket(socketAddress, attempts, timeoutMillis, backlog);
                serverSockets.add(serverSocket);
                ssl.add(false);
            }
            if (sslSocketAddress != null) {
                ServerSocket serverSocket = createServerSocket(sslSocketAddress, attempts, timeoutMillis, backlog);
                serverSockets.add(serverSocket);
                ssl.add(true);
            }
        } catch (IOException e) {
            for (ServerSocket serverSocket : serverSockets) {
                Utils.closeQuietly(serverSocket);
            }
            throw new IOException(e.getMessage(), e);
        }
        synchronized (serverSocketRunners) {
            for (int i = 0; i < serverSockets.size(); i++) {
                ServerSocketRunner serverSocketRunner = new ServerSocketRunner(serverSockets.get(i), ssl.get(i));
                serverSocketRunners.add(serverSocketRunner);
                threadPool.execute(serverSocketRunner);
            }
        }
    }

    /**
     * Stops the server. This will stop listening to ports and attempt to wait until
     * all current requests finish execution (subject to a timeout).
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
            threadPool.shutdown();
            long time = System.currentTimeMillis();
            while (connections.size() > 0 && System.currentTimeMillis() - time < waitForProcessesToFinishOnShutdownMs) {
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
     * Stops the server. This will stop listening to ports and close all sockets
     * immediately.
     */
    public void stopNow() {
        try {
            synchronized (serverSocketRunners) {
                for (ServerSocketRunner serverSocketRunner : serverSocketRunners) {
                    serverSocketRunner.stop();
                }
                serverSocketRunners.clear();
            }
            threadPool.shutdown();
            for (SocketRunner runner : connections.values()) {
                runner.abort();
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
     * @return Total number of created client connections
     */
    public long getConnectionsCreated() {
        return connectionNo.get();
    }

    /**
     * @return true if the server started successfully and accepting incoming
     *         connections
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
     * @return true if the server stopped (after waiting for active requests to
     *         finish or timeout)
     */
    public boolean isStopped() {
        return isStopped;
    }

    /**
     * @param enabledTlsProtocols - list of allowed TLS protocols
     */
    public void setTlsProtocol(TlsVersion... enabledTlsProtocols) {
        this.enabledTlsProtocols = enabledTlsProtocols;
    }

    public void useJdkDefaultTlsProtocols() {
        enabledTlsProtocols = null;
    }

    /**
     * @param enabledCipherSuites - list of allowed Cipher Suites
     */
    public void setCipherSuites(String... enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public void useJdkDefaultCipherSuites() {
        this.enabledCipherSuites = null;
    }

    /**
     * This requests a client to provide authentication certificate (TLS only)
     * 
     * @param needClientAuthentication - true if authentication required, false if
     *                                 not
     */
    public void setNeedClientAuthentication(boolean needClientAuthentication) {
        this.needClientAuthentication = needClientAuthentication;
    }

    /**
     * Add additional keyStore with keys for a specific host name Choice will be
     * made based on SNI, if nothing matches, the default specified in the
     * constructor will be used
     * 
     * @param serverName          - server name received in SNI during TLS handshake
     * @param keyStorePath        - path to Java keystore file (JKS file)
     * @param keyStorePassword    - password for Java keystore file
     * @param certificatePassword - password for certificates in Java keystore
     */
    public void addHostSpecificKeystore(String serverName, String keyStorePath, String keyStorePassword,
            String certificatePassword) {
        KeyStoreData keyStore = new KeyStoreData(serverName, keyStorePath, keyStorePassword, certificatePassword, null);
        keyStores.add(keyStore);
        if (serverName.equals("*")) {
            defaultKeyStore = keyStore;
        }
    }

    /**
     * Add additional keyStore with keys for a specific host name Choice will be
     * made based on SNI, if nothing matches, the default specified in the
     * constructor will be used
     * 
     * @param serverName          - server name received in SNI during TLS handshake
     * @param keyStore            - Java keystore
     * @param certificatePassword - password for certificates in Java keystore
     */
    public void addHostSpecificKeystore(String serverName, KeyStore keyStore, String certificatePassword) {
        KeyStoreData store = new KeyStoreData(serverName, keyStore, certificatePassword, null);
        keyStores.add(store);
        if (serverName.equals("*")) {
            defaultKeyStore = store;
        }
    }

    /**
     * Closes all connections immediately without waiting for the processing to
     * finish The server will continue to accept new connections
     */
    public void closeAllConnection() {
        for (SocketRunner runner : connections.values()) {
            runner.abort();
        }
    }

    /**
     * @param shouldFail - when set to TRUE, server will fail handshake if
     *                   ClientHello does not include Server Name Indication
     *                   extension.
     */
    public void shouldFailWhenNoSni(boolean shouldFail) {
        requireSni = shouldFail;
    }

    /**
     * @param warnWhenSniNotMatching - when set to TRUE, server will send
     *                               unrecognized_name alert if ClientHello includes
     *                               Server Name Indication extension, but it does
     *                               not match any host-specific keystores.
     */
    public void shouldWarnWhenSniNotMatching(boolean warnWhenSniNotMatching) {
        this.warnWhenSniNotMatching = warnWhenSniNotMatching;
    }

    /**
     * Set timeout on client connection. This will change the timeout only for new
     * connections.
     * 
     * @param timeoutMils - timeout in milliseconds
     */
    public void setTimeoutMils(int timeoutMils) {
        this.timeoutMils = timeoutMils;
    }

    /**
     * If set to a value of more then zero, this introduces a delay between every
     * 100 bytes read from input stream.
     * 
     * This is when this library is used in testing. This will affect only new
     * client connections.
     * 
     * @param throttlingDelayMils - delay in milliseconds, when zero - no delay.
     */
    public void setThrottlingDelay(int throttlingDelayMils) {
        this.throttlingDelayMils = throttlingDelayMils;
    }

    /**
     * Switch TCP_NODELAY
     * 
     * When it is TCP_NODELAY is on, Nagle's algorithm is switched off
     * 
     * This will affect new connections only
     * 
     * @param tcpNoDelay - when true then Nagle's algorithm is off (default - Nagle
     *                   algorithm is on)
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Sets a handler that will be called immediately after the request headers are
     * read but before reading the request body (if any)
     * 
     * @param onRequestHeadersHandler - the handler, if null (default) - no
     *                                processing will take place
     */
    public void setOnRequestHeadersHandler(HttpServerOnRequestHeadersProcess onRequestHeadersHandler) {
        this.onRequestHeadersHandler = onRequestHeadersHandler;
    }

    /**
     * Sets a handler that will be called immediately after the connection to the
     * server is made, in case of TLS connection, also after the handshake took
     * place
     * 
     * @param onConnectHandler - the handler, if null (default) - no processing will
     *                         take place
     */
    public void setOnConnectHandler(HttpServerOnConnectProcess onConnectHandler) {
        this.onConnectHandler = onConnectHandler;
    }

    protected HttpServerRequestHandler getRequestHandler() {
        return requestHandler;
    }

    private static ServerSocket createServerSocket(SocketAddress socketAddress, int attempts, int timeoutMillis,
            int backlog) throws IOException {
        int count = 0;
        while (true) {
            try {
                return createServerSocket(socketAddress, backlog);
            } catch (IOException e) {
                count++;
                if (count >= attempts) {
                    throw e;
                }
                try {
                    Thread.sleep(timeoutMillis);
                } catch (InterruptedException e1) {
                    throw new IOException("Wait interrupted", e1);
                }
            }
        }
    }

    private static ServerSocket createServerSocket(SocketAddress socketAddress, int backlog) throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(socketAddress, backlog);
        serverSocket.setSoTimeout(0);
        return serverSocket;
    }

    private static InetAddress allLocalAddresses() {
        byte[] address = { 0, 0, 0, 0 };
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private class ServerSocketRunner implements Runnable {

        private final ServerSocket serverSocket;
        private final boolean ssl;
        private volatile boolean finished;
        private volatile boolean started;

        public ServerSocketRunner(ServerSocket serverSocket, boolean ssl) {
            this.serverSocket = serverSocket;
            this.ssl = ssl;
        }

        @Override
        public void run() {
            started = true;
            while (!finished) {
                try {
                    Socket socket = serverSocket.accept();
                    SocketRunner socketRunner = new SocketRunner(socket, ssl);
                    threadPool.execute(socketRunner);
                } catch (IOException e) {
                    // we will get SocketException when serverSocket is closed, meaning it is not an
                    // error
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

    private static class SslConnection {

        private SSLSocket socket;
        private ClientHello clientHello;
    }

    private class SocketRunner implements Runnable {

        private final boolean ssl;
        private ConnectionContext connectionContext;
        private Socket socket;
        private volatile boolean finished = false;

        public SocketRunner(Socket socket, boolean ssl) throws IOException {
            this.socket = socket;
            this.ssl = ssl;
        }

        @Override
        public void run() {
            Long connNo = connectionNo.incrementAndGet();
            connections.put(connNo, this);
            InputStream inputStream = null;
            OutputStream outputStream = null;
            PostponedCloseOutputStream postponedCloseOutputStream = null;
            SslConnection sslConnection;
            try {
                socket.setTcpNoDelay(tcpNoDelay);
                socket.setSoTimeout(timeoutMils);
                if (ssl) {
                    sslConnection = setupSsl(socket);
                    if (sslConnection == null) {
                        connections.remove(connNo);
                        socket.close();
                        finished = true;
                        return;
                    }
                    socket = sslConnection.socket;
                } else {
                    sslConnection = null;
                }
                if (onConnectHandler != null) {
                    if (!onConnectHandler.process(socket, sslConnection != null ? sslConnection.clientHello : null)) {
                        connections.remove(connNo);
                        socket.close();
                        finished = true;
                        return;
                    }
                }
                inputStream = socket.getInputStream();
                if (throttlingDelayMils > 0) {
                    inputStream = new ThrottlingInputStream(inputStream, 1000, throttlingDelayMils);
                }
                inputStream = new BufferedInputStream(inputStream);
                postponedCloseOutputStream = new PostponedCloseOutputStream(socket.getOutputStream());
                outputStream = new BufferedOutputStream(postponedCloseOutputStream);
                connectionContext = new ConnectionContext(socket,
                        sslConnection != null ? sslConnection.clientHello : null);
                while (!finished) {
                    HttpRequest request;
                    try {
                        HttpRequestHeaders requestHeaders = new HttpRequestHeaders(inputStream);
                        if (onRequestHeadersHandler != null) {
                            if (!onRequestHeadersHandler.process(connectionContext, requestHeaders,
                                    postponedCloseOutputStream)) {
                                break;
                            }
                        }
                        request = new HttpRequest(requestHeaders, inputStream);
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
                            if (connectionContext.getCloseAction() != ConnectionContext.CloseAction.NONE) {
                                finished = true;
                            }
                        }
                    } catch (IOException e) {
                        finished = true;
                    }
                }
                if (connectionContext.getCloseAction() == ConnectionContext.CloseAction.RESET) {
                    connectionContext.getSocket().setSoLinger(true, 0);
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
                Utils.closeQuietly(socket);
                finished = true;
            }
        }

        public void abort() {
            finished = true;
            Utils.closeQuietly(socket);
        }

        public void stop() {
            finished = true;
        }

        private SslConnection setupSsl(Socket socket) throws IOException {
            MarkAndResetInputStream inputStream = new MarkAndResetInputStream(socket.getInputStream());
            socket = new SocketWrapper(socket, inputStream, socket.getOutputStream());
            ClientHello clientHello = ClientHello.read(inputStream);
            if (requireSni && clientHello.getSniHostName().isEmpty()) {
                writeHandshakeFailureAlert(socket.getOutputStream());
                return null;
            }
            SSLSocketFactory socketFactory = getSslSocketFactory(clientHello.getSniHostName(), warnWhenSniNotMatching,
                    socket.getOutputStream());
            socket = socketFactory.createSocket(socket, socket.getLocalSocketAddress().toString(), socket.getPort(),
                    true);
            SSLSocket sslSocket = (SSLSocket) socket;
            sslSocket.setUseClientMode(false);
            if (enabledTlsProtocols != null) {
                sslSocket.setEnabledProtocols(TlsVersion.toJdkStrings(enabledTlsProtocols));
            }
            if (enabledCipherSuites != null) {
                sslSocket.setEnabledCipherSuites(enabledCipherSuites);
            }
            if (needClientAuthentication) {
                sslSocket.setNeedClientAuth(true);
            }
            sslSocket.startHandshake();
            SslConnection result = new SslConnection();
            result.socket = sslSocket;
            result.clientHello = clientHello;
            return result;
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

    SSLSocketFactory getSslSocketFactory(String serverName, boolean warnWhenSniNotMatching, OutputStream outputStream)
            throws IOException {
        if (!serverName.isEmpty()) {
            for (KeyStoreData keyStore : keyStores) {
                if (keyStore.getServerName().equals(serverName)) {
                    return keyStore.getSslSocketFactory();
                }
            }
            if (warnWhenSniNotMatching) {
                writeUnrecognizedNameAlert(outputStream);
            }
        }
        return defaultKeyStore.getSslSocketFactory();
    }

    private static void writeUnrecognizedNameAlert(OutputStream outputStream) throws IOException {
        byte[] data = { 0x15, 0x03, 0x00, 0x00, 0x02, 0x01, 0x70 };
        outputStream.write(data);
    }

    private static void writeHandshakeFailureAlert(OutputStream outputStream) throws IOException {
        byte[] data = { 0x15, 0x03, 0x00, 0x00, 0x02, 0x02, 0x28 };
        outputStream.write(data);
        outputStream.flush();
    }
}
