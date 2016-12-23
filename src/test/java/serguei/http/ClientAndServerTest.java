package serguei.http;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientAndServerTest {

    private static final String HOST = "localhost";
    private static final String BODY_CHARSET = "UTF-8";

    private TestServer server;
    private HttpClientConnection client;

    @Before
    public void setup() throws Exception {
        server = new TestServer();
        client = new HttpClientConnection(HOST, server.getPort());
    }

    @After
    public void clear() {
        client.close();
        server.stop();
    }

    @Test
    public void shouldSendAndReceiveFromServer() throws Exception {
        String path = "/test/file.txt";
        String requestBody = makeBody("client");
        String responseBody = makeBody("server");
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders("GET " + path + " HTTP/1.1", "Host: localhost");

        HttpResponse response = client.send(headers, requestBody);

        assertEquals("http://localhost" + path, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Encoding"));
        assertEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceiveGZippedDataFromServer() throws Exception {
        String path = "/test/file.txt";
        String requestBody = makeBody("client");
        String responseBody = makeBody("server");
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        HttpRequestHeaders headers = new HttpRequestHeaders("GET " + path + " HTTP/1.1", "Host: localhost");

        HttpResponse response = client.send(headers, requestBody, BodyCompression.GZIP);

        assertEquals("http://localhost" + path, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertNotNull(responseBody.getBytes(BODY_CHARSET));
        assertNotEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertEquals(responseBody, response.readBodyAsString());
    }

    private String makeBody(String msg) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            builder.append("This is line " + i + " for " + msg);
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

}
