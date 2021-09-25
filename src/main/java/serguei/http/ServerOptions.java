package serguei.http;

import java.net.InetAddress;
import java.security.KeyStore;

import javax.net.ssl.TrustManager;

/**
 * Options for HTTP server
 * 
 * @author Serguei Poliakov
 *
 */
public class ServerOptions {

    private static final int DEFAULT_TIMEOUT_MS = 60_000;
    private static final int DEFAULT_TLS_TIMEOUT = 10_000;
    private static final int WAIT_FOR_PROCESSES_TO_FINISH_MS = 10_000;

    private InetAddress inetAddress;
    private int port;
    private int sslPort;
    private KeyStoreData defaultKeyStore;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;
    private int timeoutBetweenRequestsMs = DEFAULT_TIMEOUT_MS;
    private int timeoutDuringTlsHandshakeMs = DEFAULT_TLS_TIMEOUT;
    private boolean tcpNoDelay;
    private boolean needClientAuthentication;
    private int waitForProcessesToFinishOnShutdownMs = WAIT_FOR_PROCESSES_TO_FINISH_MS;

    InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * @param inetAddress - an IP address to which the server is bound. You can use
     *                    this constructor if you wish to limit your server to a
     *                    particular local interface, if not set we will be binding
     *                    on 0.0.0.0
     * @return this
     */
    public ServerOptions setInetAddress(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        return this;
    }

    int getPort() {
        return port;
    }

    /**
     * @param port - port for HTTP requests, if it is not set we would not except
     *             plain HTML
     * 
     *             At least one of port or sslPort need to be setup (or both when we
     *             want to listen to both plain HTTP and TLS)
     * @return this
     */
    public ServerOptions setPort(int port) {
        this.port = port;
        return this;
    }

    int getSslPort() {
        return sslPort;
    }

    /**
     * @param sslPort - port for HTTPS requests (on connection to this port the
     *                server will expect the client to start SSL handshake), if this
     *                is not set, we would not except TLS requests
     * 
     *                At least one of port or sslPort need to be setup (or both when
     *                we want to listen to both plain HTTP and TLS)
     * @return this
     */
    public ServerOptions setSslPort(int sslPort) {
        this.sslPort = sslPort;
        return this;
    }

    KeyStoreData getDefaultKeyStore() {
        return defaultKeyStore;
    }

    /**
     * This sets TLS parameters, requires sslPort to be set
     * 
     * @param keyStorePath           - path to Java keystore file (JKS file)
     * @param keyStorePassword       - password for Java keystore file
     * @param certificatePassword    - password for certificates in Java keystore
     * @param clientAuthTrustManager - trust manager for client authentication (when
     *                               requested), set to null if no client
     *                               authentication is required
     * @return this
     */
    public ServerOptions setTlsParameters(String keyStorePath, String keyStorePassword, String certificatePassword,
            TrustManager clientAuthTrustManager) {
        this.defaultKeyStore = new KeyStoreData("*", keyStorePath, keyStorePassword, certificatePassword,
                clientAuthTrustManager);
        return this;
    }

    /**
     * This sets TLS parameters, requires sslPort to be set
     * 
     * @param keyStore               - Java keystore
     * @param certificatePassword    - password for certificates in Java keystore
     * @param clientAuthTrustManager - trust manager for client authentication (when
     *                               requested), set to null if no client
     *                               authentication is required
     * @return this
     */
    public ServerOptions setTlsParameters(KeyStore keyStore, String certificatePassword,
            TrustManager clientAuthTrustManager) {
        this.defaultKeyStore = new KeyStoreData("*", keyStore, certificatePassword, clientAuthTrustManager);
        return this;
    }

    int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Timeout on client connection.
     * 
     * @param timeoutMs - timeout in milliseconds, default 60,000ms = 1min
     * @return this
     */
    public ServerOptions setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    int getTimeoutBetweenRequestsMs() {
        return timeoutBetweenRequestsMs;
    }

    /**
     * Timeout on client connection between requests from client (if client is
     * pooling connections, this will define for how long the client can keep the
     * connection in the pool without any activity on it, i.e. while the connection
     * is idle).
     * 
     * @param ttimeoutBetweenRequestsMs - timeout in milliseconds, default 60,000ms
     *                                  = 1min
     * @return this
     */
    public ServerOptions setTimeoutBetweenRequestsMs(int timeoutBetweenRequestsMs) {
        this.timeoutBetweenRequestsMs = timeoutBetweenRequestsMs;
        return this;
    }

    int getTimeoutDuringTlsHandshakeMs() {
        return timeoutDuringTlsHandshakeMs;
    }

    /**
     * Timeout on client connection while executing TLS handshake.
     * 
     * @param timeoutDuringTlsHandshakeMs - timeout in milliseconds, default
     *                                    10,000ms = 10sec
     * @return this
     */
    public ServerOptions setTimeoutDuringTlsHandshakeMs(int timeoutDuringTlsHandshakeMs) {
        this.timeoutDuringTlsHandshakeMs = timeoutDuringTlsHandshakeMs;
        return this;
    }

    boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Switch TCP_NODELAY
     * 
     * When it is TCP_NODELAY is on, Nagle's algorithm is switched off
     * 
     * @param tcpNoDelay - when true then Nagle's algorithm is off (default -
     *                   Nagle's algorithm is used)
     * @return this
     */
    public ServerOptions setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    boolean isNeedClientAuthentication() {
        return needClientAuthentication;
    }

    /**
     * This requests a client to provide authentication certificate (TLS only)
     * 
     * @param needClientAuthentication - true if authentication required, false if
     *                                 not, default "false"
     * @return this
     */
    public ServerOptions setNeedClientAuthentication(boolean needClientAuthentication) {
        this.needClientAuthentication = needClientAuthentication;
        return this;
    }

    int getWaitForProcessesToFinishOnShutdownMs() {
        return waitForProcessesToFinishOnShutdownMs;
    }

    /**
     * Specifies for how long we should wait until the running processes finish when
     * shutting down the server, default 10,000ms = 10 seconds
     * 
     * @param waitForProcessesToFinishOnShutdownMs - time to wait in milliseconds
     * @return this
     */
    public ServerOptions setWaitForProcessesToFinishOnShutdownMs(int waitForProcessesToFinishOnShutdownMs) {
        this.waitForProcessesToFinishOnShutdownMs = waitForProcessesToFinishOnShutdownMs;
        return this;
    }
}
