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
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpClient {

    private static final String BODY_CODEPAGE = "UTF-8";
    private static final int BUFFER_SIZE = 1024 * 4;

    private final InetSocketAddress proxyAddress;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private X509Certificate[] sslCertificates;
    private long contentLength;
    private boolean chunked;
    private TlsVersion[] enabledSslProtocols;
    private String[] enabledCipherSuites;
    private TlsVersion negotiatedSslProtocol;
    private String negotiatedCipher;

    public HttpClient(InetSocketAddress proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    public InetSocketAddress getProxyAddress() {
        return proxyAddress;
    }

    public HttpResponse request(String... lines) throws IOException {
        connectIfNecessary();
        for (String line : lines) {
            outputStream.write(line.getBytes());
            outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        }
        outputStream.write(HttpHeaders.LINE_SEPARATOR_BYTES);
        outputStream.flush();
        return readResult();
    }

    public HttpResponse request(HttpRequest request) throws IOException {
        return request(request, null);
    }

    public HttpResponse request(HttpRequest request, String body) throws IOException {
        connectIfNecessary();
        request.write(outputStream);
        if (body != null) {
            outputStream.write(body.getBytes(BODY_CODEPAGE));
        }
        outputStream.flush();
        return readResult();
    }

    private HttpResponse readResult() throws IOException {
        HttpResponse result = new HttpResponse(inputStream);
        contentLength = getContentLength(result);
        chunked = contentLength < 0 && hasChunkedBody(result);
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
        HttpResponse response = request(connectRequest(host));
        if (response == null) {
            throw new IOException("No response returned from Proxy after sending CONNECT");
        } else if (response.getStatusCode() != 200) {
            throw new IOException("Response to CONNECT " + response.getStatusCode() + " " + response.getReason()
                    + " is not 200");
        }
    }

    public void setupSslConnectionToProxy(String host) throws IOException {
        sendConnectRequest(host);
        startHandshake(host);
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void startHandshake() throws IOException {
        startHandshake(proxyAddress.getHostString());
    }

    public void startHandshake(String hostName) throws IOException {
        connectIfNecessary();
        SSLContext sslContext = trustAllSslContext();
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket)socketFactory.createSocket(socket, hostName, proxyAddress.getPort(), true);
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

    public String readResponseBodyAsString(int len) throws IOException {
        byte[] buffer = readResponseBodyAsBytes(len);
        return new String(buffer, BODY_CODEPAGE);
    }

    public byte[] readResponseBodyAsBytes() throws IOException {
        if (contentLength >= 0) {
            return readResponseBodyAsBytes((int)contentLength);
        } else if (chunked) {
            return readStream(inputStream);
        } else {
            return new byte[0];
        }
    }

    public byte[] readResponseBodyAsBytes(int len) throws IOException {
        return readStream(inputStream, len);
    }

    public String readResponseBodyAndUnzip() throws IOException {
        byte[] buffer = readResponseBodyAsBytes();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);
        GZIPInputStream stream = new GZIPInputStream(inputStream);
        byte[] result = readStream(stream);
        return new String(result, BODY_CODEPAGE);
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

    private Socket connectSocket() throws IOException {
        Socket newSocket = new Socket();
        newSocket.setReuseAddress(true);
        newSocket.setSoLinger(false, 1);
        newSocket.connect(proxyAddress);
        return newSocket;
    }

    private void closeQuietly(Closeable closable) {
        try {
            closable.close();
        } catch (IOException e) {
            // quietly
        }
    }

    private SSLContext trustAllSslContext() {
        SSLContext trustAllSslContext;
        try {
            trustAllSslContext = SSLContext.getInstance("TLS");
            trustAllSslContext.init(null, new TrustManager[]{new TrustAllTrustManager()}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
        return trustAllSslContext;
    }

    private void connectIfNecessary() throws IOException {
        if (socket == null) {
            socket = connectSocket();
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        }
    }

    private class TrustAllTrustManager implements X509TrustManager {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            // Do Nothing...
        }

        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            sslCertificates = arg0;
        }
    }

    private byte[] readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = stream.read(buffer);
        while (read > 0) {
            outputStream.write(buffer, 0, read);
            if (read < buffer.length) {
                break;
            }
            read = stream.read(buffer);
        }
        return outputStream.toByteArray();
    }

    private byte[] readStream(InputStream stream, int len) throws IOException {
        if (len <= 0) {
            return new byte[0];
        }
        int bytesLeft = len;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(len);
        byte[] buffer = new byte[len >= BUFFER_SIZE ? BUFFER_SIZE : len];
        int read = stream.read(buffer);
        while (read > 0) {
            outputStream.write(buffer, 0, read);
            bytesLeft -= read;
            if (bytesLeft <= 0) {
                break;
            }
            read = stream.read(buffer, 0, bytesLeft >= BUFFER_SIZE ? BUFFER_SIZE : bytesLeft);
        }
        return outputStream.toByteArray();
    }

    public long getContentLength(HttpResponse response) {
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

    public boolean hasChunkedBody(HttpResponse response) {
        if (contentLength >= 0) {
            return false;
        }
        List<String> transferEncodings = response.getHeaders("Transfer-Encoding");
        int numberOfEncodings = transferEncodings.size();
        if (numberOfEncodings > 0) {
            String lastEncoding = transferEncodings.get(numberOfEncodings - 1);
            return lastEncoding.equals("chunked");
        }
        return false;
    }

}
