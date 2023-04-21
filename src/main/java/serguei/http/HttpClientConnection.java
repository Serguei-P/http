package serguei.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import serguei.http.utils.Utils;

/**
 * HTTP client connection - a simple HTTP client
 * 
 * @author Serguei Poliakov
 *
 */
public class HttpClientConnection implements Closeable {

    private static final int BUFFER_SIZE = 8192;

    private static SSLContext noHostValidatingContext;
    private static SSLContext hostValidatingContext;

    private final InetSocketAddress serverAddress;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private InputStreamWrapperFactory inputStreamWrapperFactory;
    private OutputStreamWrapperFactory outputStreamWrapperFactory;
    private X509Certificate[] tlsCertificates;
    private TlsVersion[] enabledTlsProtocols;
    private String[] enabledCipherSuites;
    private TlsVersion negotiatedTlsProtocol;
    private String negotiatedCipher;
    private byte[] tlsSessionId;
    private int timeoutMs = 0;
    private int connectTimeoutMs = 0;
    private boolean tcpNoDelay;

    /**
     * Create an instance of HttpClientConnection. We don't connect to the server yet at this point.
     * 
     * @param host
     *            - host name (could be a string contains an IP address), if we are connecting via a proxy, this should
     *            be proxy hostname/IP
     * @param port
     *            - port (e.g. 80 for HTTP or 443 for HTTPS)
     */
    public HttpClientConnection(String host, int port) {
        this(new InetSocketAddress(host, port));
    }

    /**
     * Create an instance of HttpClientConnection. We don't connect to the server yet at this point.
     * 
     * @param address
     *            - IP socket address to the server we will be connecting. If we connecting via a proxy, this should be
     *            an address of the proxy.
     */
    public HttpClientConnection(InetSocketAddress address) {
        this.serverAddress = address;
    }

    /**
     * This sends a request without body and waits for response. It will create a connection if necessary.
     * 
     * @param requestLine
     *            - request line of the request (e.g. "GET /path HTTP/1.1")
     * @param headers
     *            - headers (e.g. "Accept-Encoding: gzip")
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse sendRequest(String requestLine, String... headers) throws IOException {
        connectIfNecessary();
        outputStream.write(requestLine.getBytes());
        outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        for (String line : headers) {
            outputStream.write(line.getBytes());
            outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        }
        outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        outputStream.flush();
        return new HttpResponse(inputStream);
    }

    /**
     * This sends a request without body and waits for a response. It will create a connection if necessary.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders) throws IOException {
        return send(requestHeaders, (byte[])null, BodyCompression.NONE);
    }

    /**
     * This sends a request with a body and waits for a response. It will create a connection if necessary.
     * 
     * This adds a "Content-Length" header based on the length of the body (different from body.length()) to
     * requestHeaders before sending them.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param body
     *            - a body of the request
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders, String body) throws IOException {
        return send(requestHeaders, body, BodyCompression.NONE);
    }

    /**
     * This sends a request with a body and waits for a response. It will create a connection if necessary.
     * 
     * This adds "Content-Length" header based on the length of the body (different from body.length()) and
     * "Content-Encoding" if compression is specified to requestHeaders before sending request.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param body
     *            - a body of the request
     * @param compression
     *            - specifies if body needs to be compressed
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders, String body, BodyCompression compression) throws IOException {
        byte[] bodyAsBytes;
        if (body != null) {
            bodyAsBytes = body.getBytes(HttpBody.BODY_CODEPAGE);
        } else {
            bodyAsBytes = null;
        }
        return send(requestHeaders, bodyAsBytes, compression);
    }

    /**
     * This sends a request with a body and waits for a response. It will create a connection if necessary.
     * 
     * This adds a "Content-Length" header based on the length of the body to requestHeaders before sending headers.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param body
     *            - a body of the request
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders, byte[] body) throws IOException {
        return send(requestHeaders, body, BodyCompression.NONE);
    }

    /**
     * This sends a request with a body and waits for a response. It will create a connection if necessary.
     * 
     * This adds "Content-Length" header based on the length of the body (it might different from body.length if body is
     * compressed) and "Content-Encoding" if compression is specified to requestHeaders before sending request.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param body
     *            - a body of the request
     * @param compression
     *            - specifies if body needs to be compressed
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders, byte[] body, BodyCompression compression) throws IOException {
        connectIfNecessary();
        if (body != null) {
            if (compression == BodyCompression.GZIP) {
                body = gzip(body);
                requestHeaders.setHeader("Content-Encoding", "gzip");
            } else if (compression == BodyCompression.DEFLATE) {
                body = deflate(body);
                requestHeaders.setHeader("Content-Encoding", "deflate");
            }
            requestHeaders.setHeader("Content-Length", Integer.toString(body.length));
        }
        requestHeaders.write(outputStream);
        if (body != null) {
            outputStream.write(body);
        }
        outputStream.flush();
        return new HttpResponse(inputStream);
    }

    /**
     * This sends a request with a body and waits for a response. It will create a connection if necessary.
     * 
     * This will send body using chunked transfer encoding.
     * 
     * This adds "Transfer-Encoding: chunked" header to requestHeaders before sending the request.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param body
     *            - an input stream from where to read data for the request body (the data is read until end of stream
     *            encountered)
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders, InputStream body) throws IOException {
        return send(requestHeaders, body, BodyCompression.NONE);
    }

    /**
     * This sends a request with a body and waits for a response. It will create a connection if necessary.
     * 
     * This will send body using chunked transfer encoding.
     * 
     * This adds "Transfer-Encoding: chunked" header and, if compression is specified, "Content-Encoding" header to
     * requestHeaders before sending the request.
     * 
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param body
     *            - an input stream from where to read data for the request body (the data is read until end of stream
     *            encountered)
     * @param compression
     *            - specifies if body needs to be compressed
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(HttpRequestHeaders requestHeaders, InputStream body, BodyCompression compression)
            throws IOException {
        ActiveRequestWithWritableBody activeRequest = startRequest(requestHeaders, compression);
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = body.read(buffer)) != -1) {
            activeRequest.write(buffer, 0, read);
        }
        return activeRequest.readResponse();
    }


    /**
     * This starts a request with a request body to be written and waits for a response.
     * It will create a connection if necessary.
     *
     * The body will be written using chunked transfer encoding as it is written into the returned instance of
     * ActiveRequestWithWritableBody.
     *
     * This adds "Transfer-Encoding: chunked" header.
     *
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @return an instance of ActiveRequestWithWritableBody that can be used to finish the request processing
     * @throws IOException
     */
    public ActiveRequestWithWritableBody startRequest(HttpRequestHeaders requestHeaders) throws IOException {
        return startRequest(requestHeaders, BodyCompression.NONE);
    }

    /**
     * This starts a request with a request body to be written and waits for a response.
     * It will create a connection if necessary.
     *
     * The body will be written using chunked transfer encoding as it is written into the returned instance of
     * ActiveRequestWithWritableBody.
     *
     * This adds "Transfer-Encoding: chunked" header and, if compression is specified, "Content-Encoding" header to
     * requestHeaders before sending the request.
     *
     * @param requestHeaders
     *            - request headers that will be sent to the server
     * @param compression
     *            - specifies if body needs to be compressed
     * @return an instance of ActiveRequestWithWritableBody that can be used to finish the request processing
     * @throws IOException
     */
    public ActiveRequestWithWritableBody startRequest(HttpRequestHeaders requestHeaders, BodyCompression compression)
            throws IOException {
        connectIfNecessary();
        OutputStream bodyStream = new ChunkedOutputStream(outputStream, true);
        if (compression == BodyCompression.GZIP) {
            requestHeaders.setHeader("Content-Encoding", "gzip");
            bodyStream = new GZIPOutputStream(bodyStream);
        } else if (compression == BodyCompression.DEFLATE) {
            requestHeaders.setHeader("Content-Encoding", "deflate");
            bodyStream = new DeflaterOutputStream(bodyStream);
        }
        requestHeaders.setHeader("Transfer-Encoding", "chunked");
        requestHeaders.write(outputStream);
        return new ActiveRequestWithWritableBody(bodyStream, inputStream);
    }

    /**
     * This sends data to the server and waits for a response. It will create a connection if necessary.
     * 
     * @param data
     *            - data to be sent to the server as-is
     * 
     * @return a response the server sends after receiving the request
     * @throws IOException
     */
    public HttpResponse send(byte[] data) throws IOException {
        connectIfNecessary();
        outputStream.write(data);
        outputStream.flush();
        return new HttpResponse(inputStream);
    }

    /**
     * This will send a CONNECT request and wait for a response, expecting to receive 200 OK. A connection will be
     * created if required.
     * 
     * This is usually used to make a TLS connection via HTTP proxy.
     * 
     * @param host
     *            - host name to be used in the request
     * @throws IOException
     *             - when IO failed or the proxy returned a result different from 200.
     */
    public void sendConnectRequest(String host) throws IOException {
        HttpResponse response = send(HttpRequestHeaders.connectRequest(host));
        if (response == null) {
            throw new IOException("No response returned from Proxy after sending CONNECT");
        } else if (response.getStatusCode() != 200) {
            throw new IOException(
                    "Response to CONNECT " + response.getStatusCode() + " " + response.getReason() + " is not 200");
        }
    }

    /**
     * Start TLS connection without validating certificates received from the server. A connection will be created if
     * required.
     * 
     * If we specified unresolved host name when instantiating this instance, it will send it as SNI in ClientHello.
     * 
     * @throws IOException
     */
    public void startHandshake() throws IOException {
        startHandshake(serverAddress.getHostString(), getNoHostValidatingContext(), false);
    }

    /**
     * Start TLS connection without validating certificates received from the server sending specified host name as part
     * of ClientHello. A connection will be created if required.
     * 
     * @param hostName
     *            - host name to be sent in SNI in ClientHello. This is needed for the server to choose a correct
     *            certificate, if null - do not set SNI
     * @throws IOException
     */
    public void startHandshake(String hostName) throws IOException {
        startHandshake(hostName, getNoHostValidatingContext(), false);
    }

    /**
     * Start TLS connection without validating certificates received from the server sending specified host name as part
     * of ClientHello and a certificate for client validation. A connection will be created if required.
     * 
     * @param hostName
     *            - host name to be sent in SNI in ClientHello. This is needed for the server to choose a correct
     *            certificate, if null - do not set SNI
     * @param keyStorePath
     *            - path to Java keystore file (JKS file)
     * @param keyStorePassword
     *            - password for Java keystore file
     * @param certificatePassword
     *            - password for certificates in Java keystore
     * @throws IOException
     */
    public void startHandshakeWithClientAuth(String hostName, String keyStorePath, String keyStorePassword,
            String certificatePassword) throws IOException {
        SSLContext sslContext = createSslContext(false, keyStorePath, keyStorePassword, certificatePassword);
        startHandshake(hostName, sslContext, false);
    }

    /**
     * Start TLS connection validating certificates received from the server using default trust manager. A connection
     * will be created if required.
     * 
     * If we specified unresolved host name when instantiating this instance, it will send it as SNI in ClientHello.
     * 
     * @throws IOException
     */
    public void startHandshakeAndValidate() throws IOException {
        startHandshake(serverAddress.getHostString(), getHostValidatingContext(), true);
    }

    /**
     * Start TLS connection validating certificates received from the server using default trust manager. A connection
     * will be created if required.
     * 
     * @param hostName
     *            - host name to be sent in SNI in ClientHello. This is needed for the server to choose a correct
     *            certificate.
     * @throws IOException
     */
    public void startHandshakeAndValidate(String hostName) throws IOException {
        startHandshake(hostName, getHostValidatingContext(), true);
    }

    /**
     * Start TLS connection validating certificates received from the server sending specified host name as part of
     * ClientHello and a certificate for client validation. A connection will be created if required.
     * 
     * @param hostName
     *            - host name to be sent in SNI in ClientHello. This is needed for the server to choose a correct
     *            certificate, if null - do not set SNI
     * @param keyStorePath
     *            - path to Java keystore file (JKS file)
     * @param keyStorePassword
     *            - password for Java keystore file
     * @param certificatePassword
     *            - password for certificates in Java keystore
     * @throws IOException
     */
    public void startHandshakeWithClientAuthAndValidate(String hostName, String keyStorePath, String keyStorePassword,
            String certificatePassword) throws IOException {
        SSLContext sslContext = createSslContext(true, keyStorePath, keyStorePassword, certificatePassword);
        startHandshake(hostName, sslContext, true);
    }

    public void setInputStreamWrapperFactory(InputStreamWrapperFactory inputStreamWrapperFactory) {
        this.inputStreamWrapperFactory = inputStreamWrapperFactory;
    }

    public void setOutputStreamWrapperFactory(OutputStreamWrapperFactory outputStreamWrapperFactory) {
        this.outputStreamWrapperFactory = outputStreamWrapperFactory;
    }

    private void startHandshake(String hostname, SSLContext sslContext, boolean checkHostname) throws IOException {
        connectIfNecessary();
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        if (inputStreamWrapperFactory != null || outputStreamWrapperFactory != null) {
            socket = new SocketWrapper(socket, inputStream, outputStream);
        }
        SSLSocket sslSocket = (SSLSocket)socketFactory.createSocket(socket, hostname, serverAddress.getPort(), true);
        if (enabledTlsProtocols != null) {
            sslSocket.setEnabledProtocols(TlsVersion.toJdkStrings(enabledTlsProtocols));
        }
        if (enabledCipherSuites != null) {
            sslSocket.setEnabledCipherSuites(enabledCipherSuites);
        }
        sslSocket.startHandshake();
        this.socket = sslSocket;
        this.inputStream = sslSocket.getInputStream();
        this.outputStream = sslSocket.getOutputStream();
        SSLSession session = sslSocket.getSession();
        Certificate[] certificates = session.getPeerCertificates();
        tlsCertificates = new X509Certificate[certificates.length];
        for (int i = 0; i < certificates.length; i++) {
            tlsCertificates[i] = (X509Certificate)certificates[i];
        }
        negotiatedTlsProtocol = TlsVersion.fromJdkString(session.getProtocol());
        negotiatedCipher = session.getCipherSuite();
        tlsSessionId = session.getId();
        if (checkHostname) {
            HostnameChecker hostnameChecker = new HostnameChecker();
            if (!hostnameChecker.check(hostname, tlsCertificates[0])) {
                throw new SSLException("Hostname " + hostname + " does not match certificate");
            }
        }
    }

    /**
     * Close the connection to the server
     */
    public void close() {
        Utils.closeQuietly(inputStream);
        Utils.closeQuietly(outputStream);
        Utils.closeQuietly(socket);
        socket = null;
        inputStream = null;
        outputStream = null;
        tlsCertificates = null;
        negotiatedTlsProtocol = null;
        negotiatedCipher = null;
        tlsSessionId = null;
    }

    /**
     * Reset the connection to the server
     */
    public void reset() {
        if (socket != null) {
            try {
                socket.setSoLinger(true, 0);
            } catch (SocketException e) {
                // nothing can be done
            }
        }
        close();
    }

    /**
     * @return Certificates received from the server during TLS handshake or null if no TLS connection was established.
     */
    public X509Certificate[] getTlsCertificates() {
        return tlsCertificates;
    }

    /**
     * @return A TLS protocol negotiated during TLS handshake or null if TLS handshake did not happen
     */
    public TlsVersion getNegotiatedTlsProtocol() {
        return negotiatedTlsProtocol;
    }

    /**
     * @return A TLS cipher negotiated during TLS handshake or null if TLS handshake did not happen
     */
    public String getNegotiatedCipher() {
        return negotiatedCipher;
    }

    /**
     * @return A TLS session id if the server supports session resumption (null if not TLS connection)
     */
    public byte[] getTlsSessionId() {
        return tlsSessionId;
    }

    /**
     * @param enabledTlsProtocols
     *            - list of allowed TLS protocols
     */
    public void setTlsProtocol(TlsVersion... enabledTlsProtocols) {
        this.enabledTlsProtocols = enabledTlsProtocols;
    }

    public void useJdkDefaultTlsProtocols() {
        enabledTlsProtocols = null;
    }

    /**
     * @param enabledCipherSuites
     *            - list of allowed Cipher Suites
     */
    public void setCipherSuites(String... enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public void useJdkDefaultCipherSuites() {
        this.enabledCipherSuites = null;
    }

    /**
     * @return the local port number or -1 if connection was not established
     */
    public int getSocketSourcePort() {
        return socket != null ? socket.getLocalPort() : -1;
    }

    /**
     * Connects to the server. Usually one does not need to call it as it will be called automatically when required.
     *
     * @throws IOException
     */
    public void connect() throws IOException {
        close();
        setSocket(connectSocket(connectTimeoutMs > 0 ? connectTimeoutMs : timeoutMs));
    }

    /**
     * Sets timeout for IO operations on socket
     *
     * @param timeoutMs
     *            - socket timeout in milliseconds
     * @throws SocketException
     *             - when there is an error in TCP protocol
     */
    public void setTimeoutMillis(int timeoutMs) throws SocketException {
        this.timeoutMs = timeoutMs;
        if (socket != null) {
            socket.setSoTimeout(timeoutMs);
        }
    }

    /**
     * Sets timeout for connecting to the socket
     * 
     * @param connectTimeoutMs
     *            - connect timeout in milliseconds
     * @throws SocketException
     *             - when there is an error in TCP protocol
     */
    public void setConnectTimeoutMillis(int connectTimeoutMs) throws SocketException {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * Switch TCP_NODELAY
     * 
     * When it is TCP_NODELAY is on, Nagle's algorithm is switched off
     * 
     * @param tcpNoDelay
     *            - when true then Nagle's algorithm is off (default)
     * @throws SocketException
     */
    public void setTcpNoDelay(boolean tcpNoDelay) throws SocketException {
        this.tcpNoDelay = tcpNoDelay;
        if (socket != null) {
            socket.setTcpNoDelay(tcpNoDelay);
        }
    }

    /**
     * Clear SslContext. This will clear all cached SSL sessions.
     */
    public static void clearSslContexts() {
        synchronized (HttpClientConnection.class) {
            noHostValidatingContext = null;
            hostValidatingContext = null;
        }
    }

    private void connectIfNecessary() throws IOException {
        if (socket == null) {
            setSocket(connectSocket(connectTimeoutMs > 0 ? connectTimeoutMs : timeoutMs));
        }
    }

    private Socket connectSocket(int connectTimeoutMs) throws IOException {
        Socket newSocket = new Socket();
        newSocket.setReuseAddress(true);
        newSocket.setSoLinger(false, 1);
        newSocket.setSoTimeout(timeoutMs);
        newSocket.setTcpNoDelay(tcpNoDelay);
        newSocket.connect(serverAddress, connectTimeoutMs);
        return newSocket;
    }

    private SSLContext createSslContext(boolean validateCertificates, String keyStorePath, String keyStorePassword,
            String certificatePassword) {
        SSLContext sslContext;
        try {
            KeyManager[] keyManagers;
            if (keyStorePath != null) {
                keyManagers = createKeyManagers(keyStorePath, keyStorePassword, certificatePassword);
            } else {
                keyManagers = null;
            }
            X509TrustManager trustManager = new TrustManagerWrapper(validateCertificates ? getDefaultTrustManager() : null);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{trustManager}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | UnrecoverableKeyException
                | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
        return sslContext;
    }

    private X509TrustManager getDefaultTrustManager() throws KeyStoreException, NoSuchAlgorithmException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore)null);
        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager)trustManager;
            }
        }
        return null;
    }

    private KeyManager[] createKeyManagers(String keyStorePath, String keyStorePassword, String certificatePassword)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, certificatePassword.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    private class TrustManagerWrapper implements X509TrustManager {

        private final X509TrustManager trustManager;

        private TrustManagerWrapper(X509TrustManager trustManager) {
            this.trustManager = trustManager;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager != null ? trustManager.getAcceptedIssuers() : null;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (trustManager != null) {
                trustManager.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            tlsCertificates = chain;
            if (trustManager != null) {
                trustManager.checkClientTrusted(chain, authType);
            }
        }
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutput = new GZIPOutputStream(output);
        gzipOutput.write(data);
        gzipOutput.close();
        return output.toByteArray();
    }

    private byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DeflaterOutputStream gzipOutput = new DeflaterOutputStream(output);
        gzipOutput.write(data);
        gzipOutput.close();
        return output.toByteArray();
    }

    private void setSocket(Socket socket) throws IOException {
        this.socket = socket;
        if (inputStreamWrapperFactory != null) {
            this.inputStream = inputStreamWrapperFactory.wrap(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));
        } else {
            this.inputStream = new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE);
        }
        if (outputStreamWrapperFactory != null) {
            this.outputStream = new BufferedOutputStream(outputStreamWrapperFactory.wrap(socket.getOutputStream()),
                    BUFFER_SIZE);
        } else {
            this.outputStream = new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE);
        }
    }

    private SSLContext getNoHostValidatingContext() {
        SSLContext result = noHostValidatingContext;
        if (result == null) {
            synchronized (HttpClientConnection.class) {
                if (noHostValidatingContext == null) {
                    noHostValidatingContext = createSslContext(false, null, null, null);
                }
                return noHostValidatingContext;
            }
        } else {
            return result;
        }
    }

    private SSLContext getHostValidatingContext() {
        SSLContext result = hostValidatingContext;
        if (result == null) {
            synchronized (HttpClientConnection.class) {
                if (hostValidatingContext == null) {
                    hostValidatingContext = createSslContext(true, null, null, null);
                }
                return hostValidatingContext;
            }
        } else {
            return result;
        }
    }

}
