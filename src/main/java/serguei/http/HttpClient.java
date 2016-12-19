package serguei.http;

import java.io.ByteArrayInputStream;
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
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class HttpClient {

    private static final String BODY_CODEPAGE = "UTF-8";
    private static final int BUFFER_SIZE = 1024 * 4;

    private final InetSocketAddress serverAddress;
    private Socket socket;
    private InputStream inputStream;
    private InputStream responseBodyInputStream;
    private OutputStream outputStream;
    private X509Certificate[] sslCertificates;
    private long contentLength;
    private boolean chunked;
    private TlsVersion[] enabledSslProtocols;
    private String[] enabledCipherSuites;
    private TlsVersion negotiatedSslProtocol;
    private String negotiatedCipher;

    public HttpClient(String host, int port) {
        this.serverAddress = new InetSocketAddress(host, port);
    }

    public HttpClient(InetSocketAddress address) {
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
        return readResult();
    }

    public HttpResponse send(HttpRequest request) throws IOException {
        return send(request, (byte[])null);
    }

    public HttpResponse send(HttpRequest request, String body) throws IOException {
        byte[] bodyAsBytes;
        if (body != null) {
            bodyAsBytes = body.getBytes(BODY_CODEPAGE);
        } else {
            bodyAsBytes = null;
        }
        return send(request, bodyAsBytes);
    }

    public HttpResponse send(HttpRequest request, byte[] body) throws IOException {
        connectIfNecessary();
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
        }
        request.write(outputStream);
        if (body != null) {
            outputStream.write(body);
        }
        outputStream.flush();
        return readResult();
    }

    private HttpResponse readResult() throws IOException {
        HttpResponse result = new HttpResponse(inputStream);
        contentLength = getContentLength(result);
        chunked = contentLength < 0 && hasChunkedBody(result);
        String encoding = result.getHeader("content-encoding");
        responseBodyInputStream = inputStream;
        if (chunked) {
            responseBodyInputStream = new ChunkedInputStream(responseBodyInputStream);
        } else if (contentLength > 0) {
            responseBodyInputStream = new LimitedLengthInputStream(responseBodyInputStream, contentLength);
        }
        if (encoding != null && encoding.equals("gzip")) {
            responseBodyInputStream = new GZIPInputStream(responseBodyInputStream);
        }
        return result;
    }

    public static HttpRequest connectRequest(String host) {
        try {
            return new HttpRequest("CONNECT " + host + " HTTP/1.1", "Host: " + host);
        } catch (HttpException e) {
            // should never happen
            return null;
        }
    }

    public static HttpRequest getRequest(String url) throws IOException {
        String host = (new URL(url)).getHost();
        return new HttpRequest("GET " + url + " HTTP/1.1", "Host: " + host);
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

    public InputStream getResponseInputStream() {
        return responseBodyInputStream;
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

    public String readResponseBodyAsString() throws IOException {
        byte[] buffer = readResponseBodyAsBytes();
        return new String(buffer, BODY_CODEPAGE);
    }

    public byte[] readResponseBodyAsBytes() throws IOException {
        if (contentLength > 0 || chunked) {
            return readStream(responseBodyInputStream);
        } else {
            return new byte[0];
        }
    }

    public String readResponseBodyAndUnzip() throws IOException {
        byte[] buffer = readResponseBodyAsBytes();
        ByteArrayInputStream zippedInputStream = new ByteArrayInputStream(buffer);
        GZIPInputStream stream = new GZIPInputStream(zippedInputStream);
        byte[] result = readStream(stream);
        return new String(result, BODY_CODEPAGE);
    }

    public long getResponseContentLength() {
        return contentLength;
    }

    public boolean isResponseChunked() {
        return chunked;
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

    private long getContentLength(HttpResponse response) {
        String contentLengthString = response.getHeader("Content-Length");
        if (contentLengthString != null) {
            try {
                return Long.parseLong(contentLengthString.trim());
            } catch (NumberFormatException e) {
                // nothing
            }
        }
        return -1;
    }

    private boolean hasChunkedBody(HttpResponse response) {
        if (contentLength >= 0) {
            return false;
        }
        List<String> transferEncoding = response.getHeaders("Transfer-Encoding");
        if (transferEncoding != null) {
            int numberOfEncodings = transferEncoding.size();
            if (numberOfEncodings > 0) {
                String lastEncoding = transferEncoding.get(numberOfEncodings - 1);
                return lastEncoding.equals("chunked");
            }
        }
        return false;
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

    private byte[] readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = stream.read(buffer);
        while (read > 0) {
            outputStream.write(buffer, 0, read);
            read = stream.read(buffer);
        }
        return outputStream.toByteArray();
    }

}
