package serguei.http;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class TestServer extends HttpServer {

    private static final int PORT = 8080;
    private static final String BODY_CODEPAGE = "UTF-8";

    private final TestRequestHandler requestHandler;

    public TestServer() throws IOException {
        super(new TestRequestHandler(), PORT);
        requestHandler = (TestRequestHandler)getRequestHandler();
        start();
        long startTime = System.currentTimeMillis();
        while (!isRunning() && System.currentTimeMillis() - startTime < 1000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // nothing to do
            }
        }
        if (!isRunning()) {
            throw new IOException("Server did not start on " + PORT);
        }
    }

    public int getPort() {
        return PORT;
    }

    public void setRespose(HttpResponseHeaders headers, byte[] body) {
        requestHandler.responseHeaders = headers;
        requestHandler.responseBody = body;
    }

    public HttpRequestHeaders getLatestRequestHeaders() {
        return requestHandler.getLatestRequestHeaders();
    }

    public byte[] getLatestRequestBody() {
        return requestHandler.getLatestRequestBody();
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

    private static class TestRequestHandler implements HttpServerRequestHandler {

        private volatile HttpResponseHeaders responseHeaders;
        private volatile byte[] responseBody;
        private volatile HttpRequestHeaders latestRequestHeaders;
        private volatile byte[] latestRequestBody;

        @Override
        public void process(HttpRequest request, OutputStream outputStream) {
            latestRequestHeaders = request.getHeaders();
            try {
                latestRequestBody = request.readBodyAsBytes();
                responseHeaders.write(outputStream);
                outputStream.write(responseBody);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public HttpRequestHeaders getLatestRequestHeaders() {
            return latestRequestHeaders;
        }

        public byte[] getLatestRequestBody() {
            return latestRequestBody;
        }
    }
}
