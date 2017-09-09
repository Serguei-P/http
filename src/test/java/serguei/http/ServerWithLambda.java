package serguei.http;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Test;

public class ServerWithLambda {

    private static final String BODY = "This is a response body";
    private static final int PORT = 8888;

    HttpServer server = null;

    @After
    public void cleanUp() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void serverShouldRespond() throws IOException {
        server = new HttpServer((ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream) -> {
            byte[] body = BODY.getBytes("UTF-8");
            HttpResponseHeaders headers = new HttpResponseHeaders("HTTP/1.1 200 OK", "Content-Length: " + body.length);
            headers.write(outputStream);
            outputStream.write(body);
        }, PORT);
        server.start();
        try (HttpClientConnection connection = new HttpClientConnection("localhost", PORT)) {
            HttpResponse response = connection.sendRequest("GET / HTTP/1.1", "Host: localhost");
            assertEquals(200, response.getStatusCode());
            assertEquals(BODY, response.readBodyAsString());
        }
    }

}
