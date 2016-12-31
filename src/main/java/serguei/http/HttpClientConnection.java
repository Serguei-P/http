package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class HttpClientConnection {

    private final InetSocketAddress serverAddress;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private X509Certificate[] sslCertificates;
    private TlsVersion[] enabledSslProtocols;
    private String[] enabledCipherSuites;
    private TlsVersion negotiatedSslProtocol;
    private String negotiatedCipher;

    public HttpClientConnection(String host, int port) {
        this.serverAddress = new InetSocketAddress(host, port);
    }

    public HttpClientConnection(InetSocketAddress address) {
        this.serverAddress = address;
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public HttpResponse sendRequest(String requestLine, String... headers) throws IOException {
        connectIfNecessary();
        for (String line : headers) {
            outputStream.write(line.getBytes());
            outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        }
        outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        outputStream.flush();
        return new HttpResponse(inputStream);
    }

    public HttpResponse send(HttpRequestHeaders request) throws IOException {
        return send(request, (byte[])null, BodyCompression.NONE);
    }

    public HttpResponse send(HttpRequestHeaders request, String body) throws IOException {
        return send(request, body, BodyCompression.NONE);
    }

    public HttpResponse send(HttpRequestHeaders request, String body, BodyCompression compression) throws IOException {
        byte[] bodyAsBytes;
        if (body != null) {
            bodyAsBytes = body.getBytes(HttpBody.BODY_CODEPAGE);
        } else {
            bodyAsBytes = null;
        }
        return send(request, bodyAsBytes, compression);
    }

    public HttpResponse send(HttpRequestHeaders request, byte[] body) throws IOException {
        return send(request, body, BodyCompression.NONE);
    }

    public HttpResponse send(HttpRequestHeaders request, byte[] body, BodyCompression compression) throws IOException {
        connectIfNecessary();
        if (body != null) {
            if (compression == BodyCompression.GZIP) {
                body = gzip(body);
                request.setHeader("Content-Encoding", "gzip");
            }
            request.setHeader("Content-Length", Integer.toString(body.length));
        }
        request.write(outputStream);
        if (body != null) {
            outputStream.write(body);
        }
        outputStream.flush();
        return new HttpResponse(inputStream);
    }

    public HttpResponse send(HttpRequestHeaders request, InputStream body) throws IOException {
        return send(request, body, BodyCompression.NONE);
    }

    public HttpResponse send(HttpRequestHeaders request, InputStream body, BodyCompression compression) throws IOException {
        connectIfNecessary();
        OutputStream bodyStream = new ChunkedOutputStream(outputStream, true);
        if (compression == BodyCompression.GZIP) {
            request.setHeader("Content-Encoding", "gzip");
            bodyStream = new GZIPOutputStream(bodyStream);
        }
        request.setHeader("Transfer-Encoding", "chunked");
        request.write(outputStream);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = body.read(buffer)) != -1) {
            bodyStream.write(buffer, 0, read);
        }
        bodyStream.close();
        return new HttpResponse(inputStream);
    }

    public static HttpRequestHeaders connectRequest(String host) {
        try {
            return new HttpRequestHeaders("CONNECT " + host + " HTTP/1.1", "Host: " + host);
        } catch (HttpException e) {
            // should never happen
            return null;
        }
    }

    public static HttpRequestHeaders getRequest(String url) throws IOException {
        String host = (new URL(url)).getHost();
        return new HttpRequestHeaders("GET " + url + " HTTP/1.1", "Host: " + host);
    }

    public void sendConnectRequest(String host) throws IOException {
        HttpResponse response = send(connectRequest(host));
        if (response == null) {
            throw new IOException("No response returned from Proxy after sending CONNECT");
        } else if (response.getStatusCode() != 200) {
            throw new IOException(
                    "Response to CONNECT " + response.getStatusCode() + " " + response.getReason() + " is not 200");
        }
    }

    public void setupTlsConnectionViaProxy(String host) throws IOException {
        sendConnectRequest(host);
        startHandshake(host);
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void startHandshake() throws IOException {
        startHandshake(serverAddress.getHostString());
    }

    public void startHandshake(String hostName) throws IOException {
        startHandshake(hostName, false);
    }

    public void startHandshakeAndValidate(String hostName) throws IOException {
        startHandshake(hostName, true);
    }

    private void startHandshake(String hostName, boolean validateCertificates) throws IOException {
        connectIfNecessary();
        SSLContext sslContext = createSslContext(validateCertificates);
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket)socketFactory.createSocket(socket, hostName, serverAddress.getPort(), true);
        if (enabledSslProtocols != null) {
            sslSocket.setEnabledProtocols(TlsVersion.toJdkStrings(enabledSslProtocols));
        }
        if (enabledCipherSuites != null) {
            sslSocket.setEnabledCipherSuites(enabledCipherSuites);
        }
        sslSocket.startHandshake();
        socket = sslSocket;
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        negotiatedSslProtocol = TlsVersion.fromJdkString(sslSocket.getSession().getProtocol());
        negotiatedCipher = sslSocket.getSession().getCipherSuite();
    }

    public void close() {
        closeQuietly(inputStream);
        closeQuietly(outputStream);
        closeQuietly(socket);
        socket = null;
        inputStream = null;
        outputStream = null;
        negotiatedSslProtocol = null;
        negotiatedCipher = null;
    }

    public X509Certificate[] getSslCertificates() {
        return sslCertificates;
    }

    public void setSslProtocol(TlsVersion... enabledSslProtocols) {
        this.enabledSslProtocols = enabledSslProtocols;
    }

    public void useJdkDefaultSslProtocols() {
        enabledSslProtocols = null;
    }

    public void setCipherSuites(String... enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public void useJdkDefaultCipherSuites() {
        this.enabledCipherSuites = null;
    }

    public int getSocketSourcePort() {
        return socket != null ? socket.getLocalPort() : -1;
    }

    public void connect() throws IOException {
        close();
        socket = connectSocket();
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    public TlsVersion getNegotiatedSslProtocol() {
        return negotiatedSslProtocol;
    }

    public String getNegotiatedCipher() {
        return negotiatedCipher;
    }

    private void closeQuietly(Closeable closable) {
        try {
            closable.close();
        } catch (IOException e) {
            // quietly
        }
    }

    private SSLContext createSslContext(boolean validateCertificates) {
        SSLContext sslContext;
        try {
            X509TrustManager trustManager = new TrustManagerWrapper(validateCertificates ? getDefaultTrustManager() : null);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
        return sslContext;
    }

    private void connectIfNecessary() throws IOException {
        if (socket == null) {
            socket = connectSocket();
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        }
    }

    private Socket connectSocket() throws IOException {
        Socket newSocket = new Socket();
        newSocket.setReuseAddress(true);
        newSocket.setSoLinger(false, 1);
        newSocket.connect(serverAddress);
        return newSocket;
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
            sslCertificates = chain;
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

}
