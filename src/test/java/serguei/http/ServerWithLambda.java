package serguei.http;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.junit.After;
import org.junit.Test;

import serguei.http.utils.Utils;

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

    @Test
    public void serverShouldReadRequestBodyAsStream() throws IOException {
        server = new HttpServer((ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream) -> {
            byte[] body;
            try (InputStream inputStream = request.getBodyAsStream()) {
                body = Utils.readFully(inputStream);
            }
            HttpResponseHeaders headers = new HttpResponseHeaders("HTTP/1.1 200 OK", "Content-Length: " + body.length);
            headers.write(outputStream);
            outputStream.write(body);
        }, PORT);
        server.start();
        try (HttpClientConnection connection = new HttpClientConnection("localhost", PORT)) {
            HttpResponse response = connection.send(new HttpRequestHeaders("POST / HTTP/1.1", "Host: localhost"), BODY);
            assertEquals(200, response.getStatusCode());
            assertEquals(BODY, response.readBodyAsString());
            response = connection.send(new HttpRequestHeaders("POST / HTTP/1.1", "Host: localhost"), BODY);
            assertEquals(200, response.getStatusCode());
            assertEquals(BODY, response.readBodyAsString());
        }
    }

    @Test
    public void serverShouldReadRequestBodyAsStreamWhenRequestClosesPrematurely() throws IOException {
        server = new HttpServer((ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream) -> {
            byte[] body;
            try (InputStream inputStream = request.getBodyAsStream()) {
                body = Utils.readFully(inputStream);
            }
            HttpResponseHeaders headers = new HttpResponseHeaders("HTTP/1.1 200 OK", "Content-Length: " + body.length);
            headers.write(outputStream);
            outputStream.write(body);
        }, PORT);
        server.start();
        try (Socket socket = new Socket("localhost", PORT)) {
            // request with socket closed before the body is sent
            socket.getOutputStream().write("POST / HTTP.1.1\r\nHost: localhost\r\nContent-Length: 2000\r\n\r\n".getBytes());
        }
        try (HttpClientConnection connection = new HttpClientConnection("localhost", PORT)) {
            HttpResponse response = connection.send(new HttpRequestHeaders("POST / HTTP/1.1", "Host: localhost"), BODY);
            assertEquals(200, response.getStatusCode());
            assertEquals(BODY, response.readBodyAsString());
        }
    }

}
