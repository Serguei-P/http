package serguei.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.TrustManager;

public class TestServer {

    private static final int PORT = 8080;
    private static final int SSL_PORT = 8443;
    private static final String BODY_CODEPAGE = "UTF-8";

    private final TestRequestHandler requestHandler = new TestRequestHandler();

    private HttpServer httpServer;

    public TestServer() {
    }

    public void start() throws IOException {
        start(null, defaultStorePath());
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public void start(TrustManager clientAuthTrustManager, String keyStorePath) throws IOException {
        httpServer = new HttpServer(requestHandler, PORT, SSL_PORT, keyStorePath, "password", "test01", clientAuthTrustManager);
        httpServer.start();
        long startTime = System.currentTimeMillis();
        while (!httpServer.isRunning() && System.currentTimeMillis() - startTime < 1000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // nothing to do
            }
        }
        if (!httpServer.isRunning()) {
            throw new IOException("Server did not start on " + PORT + " and " + SSL_PORT);
        }
    }

    public int getPort() {
        return PORT;
    }

    public int getSslPort() {
        return SSL_PORT;
    }

    public void setResponse(HttpResponseHeaders headers, byte[] body) {
        setResponse(headers, body, BodyCompression.NONE);
    }

    public void setResponse(HttpResponseHeaders headers, byte[] body, BodyCompression compression) {
        byte[] responseBody;
        if (compression == BodyCompression.GZIP) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                GZIPOutputStream gzipStream = new GZIPOutputStream(output);
                gzipStream.write(body);
                gzipStream.close();
            } catch (IOException e) {
                // this is used for tests only
                throw new RuntimeException("Error compressing body", e);
            }
            responseBody = output.toByteArray();
            addContentLengthIfAbsent(headers, responseBody.length);
            headers.setHeader("Content-Encoding", "gzip");
        } else if (compression == BodyCompression.DEFLATE) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                DeflaterOutputStream deflaterStream = new DeflaterOutputStream(output);
                deflaterStream.write(body);
                deflaterStream.close();
            } catch (IOException e) {
                // this is used for tests only
                throw new RuntimeException("Error compressing body", e);
            }
            responseBody = output.toByteArray();
            addContentLengthIfAbsent(headers, responseBody.length);
            headers.setHeader("Content-Encoding", "deflate");
        } else {
            responseBody = body;
            addContentLengthIfAbsent(headers, body.length);
        }
        requestHandler.setResponse(new Response(headers, responseBody));
    }

    public void setChunkedResponse(HttpResponseHeaders headers, byte[] body) {
        setChunkedResponse(headers, body, BodyCompression.NONE);
    }

    public void setChunkedResponse(HttpResponseHeaders headers, byte[] body, BodyCompression compression) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStream stream = new ChunkedOutputStream(output, true);
        try {
            if (compression == BodyCompression.GZIP) {
                stream = new GZIPOutputStream(stream);
                headers.setHeader("content-encoding", "gzip");
            }
            stream.write(body);
            stream.close();
        } catch (IOException e) {
            // this is used for tests only
            throw new RuntimeException("Error compressing body", e);
        }
        headers.addHeader("transfer-encoding", "chunked");
        requestHandler.setResponse(new Response(headers, output.toByteArray()));
    }

    public void setChunkedGzippeddResponseUsingTransferEncordingOnly(HttpResponseHeaders headers, byte[] body) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStream stream = new ChunkedOutputStream(output, true);
        try {
            stream = new GZIPOutputStream(stream);
            stream.write(body);
            stream.close();
        } catch (IOException e) {
            // this is used for tests only
            throw new RuntimeException("Error compressing body", e);
        }
        headers.addHeader("Transfer-Encoding", "gzip, chunked");
        requestHandler.setResponse(new Response(headers, output.toByteArray()));
    }

    public void setUnmodifiedResponse(HttpResponseHeaders headers, byte[] body) {
        requestHandler.setResponse(new Response(headers, body));
    }

    public void closeAfterResponse() {
        requestHandler.closeAfterResponse();
    }

    public HttpRequestHeaders getLatestRequestHeaders() {
        return requestHandler.getLatestRequestHeaders();
    }

    public byte[] getLatestRequestBody() {
        return requestHandler.getLatestRequestBody();
    }

    public ConnectionContext getLatestConnectionContext() {
        return requestHandler.getLatestConnectionContext();
    }

    public boolean isLatestRequestBodyCompressed() {
        return requestHandler.isLatestRequestBodyCompressed();
    }

    public String getLatestRequestBodyAsString() {
        try {
            return new String(getLatestRequestBody(), BODY_CODEPAGE);
        } catch (UnsupportedEncodingException e) {
            // should not happen
            e.printStackTrace();
            return null;
        }
    }

    public void setOnRequestHeadersHandler(HttpServerOnRequestHeadersProcess onRequestHeadersHandler) {
        httpServer.setOnRequestHeadersHandler(onRequestHeadersHandler);
    }

    public long getConnectionsCreated() {
        return httpServer.getConnectionsCreated();
    }

    public void setTlsProtocol(TlsVersion... enabledTlsProtocols) {
        httpServer.setTlsProtocol(enabledTlsProtocols);
    }

    public void shouldWarnWhenSniNotMatching(boolean warnWhenSniNotMatching) {
        httpServer.shouldWarnWhenSniNotMatching(warnWhenSniNotMatching);
    }

    public void shouldFailWhenNoSni(boolean shouldFail) {
        httpServer.shouldFailWhenNoSni(shouldFail);
    }

    public void setNeedClientAuthentication(boolean needClientAuthentication) {
        httpServer.setNeedClientAuthentication(needClientAuthentication);
    }

    private void addContentLengthIfAbsent(HttpResponseHeaders headers, int value) {
        if (headers.getHeader("Content-Length") == null) {
            headers.setHeader("Content-Length", Integer.toString(value));
        }
    }

    private static class TestRequestHandler implements HttpServerRequestHandler {

        private Response response;
        private boolean closeAfterResponse = false;
        private volatile HttpRequestHeaders latestRequestHeaders;
        private volatile byte[] latestRequestBody;
        private volatile ConnectionContext latestConnectionContext;
        private volatile boolean latestRequestBodyCompressed;

        @Override
        public void process(ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream) {
            latestRequestHeaders = request.headers();
            latestConnectionContext = connectionContext;
            latestRequestBodyCompressed = request.isBodyCompressed();
            try {
                latestRequestBody = request.readBodyAsBytes();
                // updating in case it changed when on attempt to decompress the body was not compressed
                latestRequestBodyCompressed = request.isBodyCompressed();
                response.getHeaders().write(outputStream);
                outputStream.write(response.getBody());
                if (closeAfterResponse) {
                    connectionContext.closeConnection();
                }
            } catch (IOException e) {
                throw new RuntimeException("Error", e);
            }
        }

        public HttpRequestHeaders getLatestRequestHeaders() {
            return latestRequestHeaders;
        }

        public byte[] getLatestRequestBody() {
            return latestRequestBody;
        }

        public ConnectionContext getLatestConnectionContext() {
            return latestConnectionContext;
        }

        public boolean isLatestRequestBodyCompressed() {
            return latestRequestBodyCompressed;
        }

        public void setResponse(Response response) {
            this.response = response;
        }

        public void closeAfterResponse() {
            closeAfterResponse = true;
        }
    }

    private static class Response {

        private final HttpResponseHeaders headers;
        private final byte[] body;

        public Response(HttpResponseHeaders headers, byte[] body) {
            this.headers = headers;
            this.body = body;
        }

        public HttpResponseHeaders getHeaders() {
            return headers;
        }

        public byte[] getBody() {
            return body;
        }

    }

    private static String defaultStorePath() {
        return TestServer.class.getResource("/server-keystore.jks").getFile();
    }
}
