package serguei.http.examples;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import serguei.http.HttpClientConnection;
import serguei.http.HttpRequestHeaders;
import serguei.http.HttpResponse;
import serguei.http.utils.WaitingByteArrayInputStream;

public class ServerRespondingWithChunkedBodyTest {

    private final WaitingByteArrayInputStream inputStream = new WaitingByteArrayInputStream();
    private final ServerRespondingWithChunkedBody server = new ServerRespondingWithChunkedBody(inputStream);
    private final HttpClientConnection connection = new HttpClientConnection("localhost", server.getPort());

    @Before
    public void setup() {
        Thread thread = new Thread(server);
        thread.start();
        waitUntilRunning(true);
        assertTrue(server.isRunning());
    }

    @After
    public void clearUp() throws Exception {
        connection.close();
        if (server.isRunning()) {
            inputStream.push("quit" + System.lineSeparator());
            waitUntilRunning(false);
        }
    }

    @Test
    public void shouldStopServer() throws Exception {

        inputStream.push("quit" + System.lineSeparator());

        waitUntilRunning(false);
        assertFalse(server.isRunning());
    }

    @Test
    public void shouldReceiveResponseFromServer() throws Exception {
        try (HttpClientConnection connection = new HttpClientConnection("localhost", server.getPort())) {
        
            HttpResponse response = connection
                    .send(HttpRequestHeaders.getRequest("http://localhost:" + server.getPort() + "/"));

            assertEquals(200, response.getStatusCode());
            assertTrue(response.isContentChunked());
            assertEquals("gzip", response.getHeader("Content-Encoding"));
            assertTrue(response.readBodyAsString().contains("--- END ---"));
        }
    }

    @Test
    public void shouldLeaveConnectionOpenAfterFirstRequest() throws Exception {
        try (HttpClientConnection connection = new HttpClientConnection("localhost", server.getPort())) {

            HttpRequestHeaders headers = HttpRequestHeaders.getRequest("http://localhost:" + server.getPort() + "/");
            headers.setHeader("Accept-Encoding", "gzip");

            HttpResponse response = connection.send(headers);
            assertEquals(200, response.getStatusCode());
            assertTrue(response.readBodyAsString().contains("--- END ---"));

            response = connection.send(headers);
            assertEquals(200, response.getStatusCode());
            assertTrue(response.readBodyAsString().contains("--- END ---"));
        }
    }

    private void waitUntilRunning(boolean running) {
        long startTime = System.currentTimeMillis();
        while (server.isRunning() != running || System.currentTimeMillis() - startTime > 2000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

}
