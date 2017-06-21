package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import serguei.http.utils.Utils;

public class ClientAndServerTest {

    private static final String HOST = "localhost";
    private static final String BODY_CHARSET = "UTF-8";
    private static final String PATH = "/test/file.txt";
    private static final String REQUEST_LINE = "POST " + PATH + " HTTP/1.1";
    private static final String EOL = "\r\n";

    private String requestBody = makeBody("client");
    private String responseBody = makeBody("server");

    private TestServer server;
    private HttpClientConnection clientConnection;

    @Before
    public void setup() throws Exception {
        server = new TestServer();
        clientConnection = new HttpClientConnection(HOST, server.getPort());
        server.setOnRequestHeadersHandler(null);
    }

    @After
    public void clear() {
        clientConnection.close();
        server.stop();
    }

    @Test
    public void shouldSendAndReceiveFromServer() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Encoding"));
        assertEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertFalse(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceiveFromServerUsingSendRequestMethod() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);

        HttpResponse response = clientConnection.sendRequest("GET " + PATH + " HTTP/1.1", "Host: localhost");

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Encoding"));
        assertEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertFalse(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceiveGZippedDataFromServer() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody, BodyCompression.GZIP);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertTrue(response.getContentLength() > 0);
        assertNotEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertFalse(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceiveFromServerChunked() throws Exception {
        server.setChunkedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");
        InputStream inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        HttpResponse response = clientConnection.send(headers, inputStream);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Encoding"));
        assertEquals(-1, response.getContentLength());
        assertTrue(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceiveGZippedDataFromServerChunked() throws Exception {
        server.setChunkedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost",
                "Accept-Encoding: gzip");
        InputStream inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        HttpResponse response = clientConnection.send(headers, inputStream, BodyCompression.GZIP);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals("gzip", server.getLatestRequestHeaders().getHeader("Accept-Encoding"));
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertEquals(-1, response.getContentLength());
        assertTrue(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendTwoChunkedRequestsOnSameConnection() throws Exception {
        server.setChunkedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");
        InputStream inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        HttpResponse response = clientConnection.send(headers, inputStream);

        assertNull(response.getHeader("Content-Encoding"));
        assertTrue(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());

        server.setChunkedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost", "Accept-Encoding: gzip");
        inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        response = clientConnection.send(headers, inputStream, BodyCompression.GZIP);

        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertTrue(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendBytesAndReceiveFromServer() throws Exception {
        byte[] body = requestBody.getBytes(BODY_CHARSET);
        byte[] headers = (REQUEST_LINE + EOL + "Host: localhost" + EOL + "Content-Length: " + body.length + EOL + EOL)
                .getBytes("ASCII");
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        byte[] data = Utils.concat(headers, body);

        HttpResponse response = clientConnection.send(data);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertFalse(server.getLatestConnectionContext().isSsl());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Encoding"));
        assertEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertFalse(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test(timeout = 10000)
    public void shouldReceiveUnknownLengthResponse() throws Exception {
        server.setUnmodifiedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET));
        server.closeAfterResponse();
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Length"));
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test(timeout = 10000)
    public void shouldRespond100Continue() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes("UTF-8"));
        server.setOnRequestHeadersHandler(new On_100_Continue());
        byte[] requestBodyAsBytes = requestBody.getBytes("UTF-8");
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost", "Expect: 100-continue",
                "Content-Length: " + requestBodyAsBytes.length);

        HttpResponse response = clientConnection.send(headers);

        assertEquals(100, response.getStatusCode());
        
        response = clientConnection.send(requestBodyAsBytes);

        assertEquals(200, response.getStatusCode());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, response.readBodyAsString());
    }

    private static String makeBody(String msg) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            builder.append("This is line " + i + " for " + msg);
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private class On_100_Continue implements HttpServerOnRequestHeadersProcess {

        @Override
        public boolean process(ConnectionContext connectionContext, HttpRequestHeaders requestHeaders,
                OutputStream outputStream) throws IOException {
            String expectHeader = requestHeaders.getHeader("Expect");
            if (expectHeader != null && expectHeader.toLowerCase().equals("100-continue")) {
                HttpResponseHeaders response = new HttpResponseHeaders("HTTP/1.1 100 Continue");
                response.write(outputStream);
                outputStream.flush();
            }
            return true;
        }

    }

}
