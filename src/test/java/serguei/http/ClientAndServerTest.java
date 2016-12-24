package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientAndServerTest {

    private static final String HOST = "localhost";
    private static final String BODY_CHARSET = "UTF-8";
    private static final String PATH = "/test/file.txt";
    private static final String REQUEST_LINE = "POST " + PATH + " HTTP/1.1";

    private String requestBody = makeBody("client");
    private String responseBody = makeBody("server");

    private TestServer server;
    private HttpClientConnection clientConnection;

    @Before
    public void setup() throws Exception {
        server = new TestServer();
        clientConnection = new HttpClientConnection(HOST, server.getPort());
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
        assertFalse(response.isResponseChunked());
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
        assertFalse(response.isResponseChunked());
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
        assertTrue(response.isResponseChunked());
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
        assertTrue(response.isResponseChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendTwoChunkedRequestsOnSameConnection() throws Exception {
        server.setChunkedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");
        InputStream inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        HttpResponse response = clientConnection.send(headers, inputStream);

        assertNull(response.getHeader("Content-Encoding"));
        assertTrue(response.isResponseChunked());
        assertEquals(responseBody, response.readBodyAsString());

        server.setChunkedResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost", "Accept-Encoding: gzip");
        inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        response = clientConnection.send(headers, inputStream, BodyCompression.GZIP);

        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertTrue(response.isResponseChunked());
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

}
