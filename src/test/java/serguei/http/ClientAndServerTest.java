package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

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
        long connectionsBefore = server.getConnectionsCreated();
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
        assertEquals(1, server.getConnectionsCreated() - connectionsBefore);
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
    public void shouldSendAndReceiveDeflatedDataFromServer() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.DEFLATE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody, BodyCompression.DEFLATE);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals("deflate", response.getHeader("Content-Encoding"));
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
        assertTrue(response.isBodyCompressed());
        assertTrue(server.isLatestRequestBodyCompressed());
    }

    @Test
    public void shouldReceiveGZippedDataFromServerChunkedUsingTransferEncodingHeader() throws Exception {
        server.setChunkedGzippeddResponseUsingTransferEncordingOnly(HttpResponseHeaders.ok(),
                responseBody.getBytes(BODY_CHARSET));
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost", "Accept-Encoding: gzip");
        InputStream inputStream = new ByteArrayInputStream(requestBody.getBytes(BODY_CHARSET));

        HttpResponse response = clientConnection.send(headers, inputStream);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals("gzip, chunked", response.getHeader("Transfer-Encoding"));
        assertEquals(-1, response.getContentLength());
        assertTrue(response.isContentChunked());
        assertTrue(response.isBodyCompressed());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void serverShouldUnderstandGZippedDataFromClientChunkedUsingTransferEncodingHeader() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET));
        byte[] headers = (REQUEST_LINE + EOL + "Host: localhost" + EOL + "Transfer-Encoding: gzip, chunked" + EOL + EOL)
                .getBytes();
        byte[] body = chunkBody(gzipBody(requestBody.getBytes(BODY_CHARSET)));

        HttpResponse response = clientConnection.send(Utils.concat(headers, body));

        HttpRequestHeaders receivedRequest = server.getLatestRequestHeaders();
        assertEquals("http://localhost" + PATH, receivedRequest.getUrl().toString());
        assertEquals("gzip, chunked", receivedRequest.getHeader("Transfer-Encoding"));
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceivePlainTextPretendingToBeGzipped() throws Exception {
        HttpResponseHeaders responseHeaders = HttpResponseHeaders.ok();
        responseHeaders.setHeader("Content-Encoding", "gzip");
        server.setResponse(responseHeaders, responseBody.getBytes(BODY_CHARSET));
        HttpRequestHeaders requestHeaders = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost", "Content-Encoding: gzip");

        HttpResponse response = clientConnection.send(requestHeaders, requestBody);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals("gzip", server.getLatestRequestHeaders().getHeader("Content-Encoding"));
        assertEquals(200, response.getStatusCode());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertTrue(response.getContentLength() > 0);
        assertEquals(responseBody, response.readBodyAsString());
        assertFalse(response.isBodyCompressed());
        assertFalse(server.isLatestRequestBodyCompressed());
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

    @Test
    public void shouldReadResponseBodyAsStream() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        String body;
        try (InputStream inputStream = response.getBodyAsStream()) {
            body = readFromStreamByBuffer(response.getBodyAsStream());
        }

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals(responseBody, body);
    }

    @Test
    public void shouldReadTwoResponsesAsInputStreams() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        try (InputStream inputStream = response.getBodyAsStream()) {
            String body = readFromStreamByBuffer(response.getBodyAsStream());
            assertEquals(requestBody, server.getLatestRequestBodyAsString());
            assertEquals(200, response.getStatusCode());
            assertEquals(responseBody, body);
        }

        response = clientConnection.send(headers, requestBody);
        try (InputStream inputStream = response.getBodyAsStream()) {
            String body = readFromStreamByBuffer(response.getBodyAsStream());
            assertEquals(requestBody, server.getLatestRequestBodyAsString());
            assertEquals(200, response.getStatusCode());
            assertEquals(responseBody, body);
        }
    }

    @Test
    public void shouldReadTwoResponsesAsInputStreamsWithoutClosing() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        assertEquals(200, response.getStatusCode());
        String body = readFromStreamByBuffer(response.getBodyAsStream());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, body);

        response = clientConnection.send(headers, requestBody);
        assertEquals(200, response.getStatusCode());
        body = readFromStreamByBuffer(response.getBodyAsStream());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, body);
    }

    @Test
    public void shouldReadTwoResponsesAsInputStreamsWithCompression() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        try (InputStream inputStream = response.getBodyAsStream()) {
            String body = readFromStreamByBuffer(response.getBodyAsStream());
            assertEquals(requestBody, server.getLatestRequestBodyAsString());
            assertEquals(200, response.getStatusCode());
            assertEquals(responseBody, body);
        }

        response = clientConnection.send(headers, requestBody);
        try (InputStream inputStream = response.getBodyAsStream()) {
            String body = readFromStreamByBuffer(response.getBodyAsStream());
            assertEquals(requestBody, server.getLatestRequestBodyAsString());
            assertEquals(200, response.getStatusCode());
            assertEquals(responseBody, body);
        }
    }

    @Test
    public void shouldReadTwoResponsesAsInputStreamsWithoutClosingWithCompression() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        assertEquals(200, response.getStatusCode());
        String body = readFromStreamByBuffer(response.getBodyAsStream());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, body);

        response = clientConnection.send(headers, requestBody);
        assertEquals(200, response.getStatusCode());
        body = readFromStreamByBuffer(response.getBodyAsStream());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, body);
    }

    @Test
    public void shouldReadResponseAfterResponseNotFinishedResponse() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        try (InputStream inputStream = response.getBodyAsStream()) {
            byte[] buffer = new byte[100];
            inputStream.read(buffer);
            String partOfBody = new String(buffer);
            assertEquals(partOfBody, responseBody.substring(0, partOfBody.length()));
        }
        response = clientConnection.send(headers, requestBody);
        String body;
        try (InputStream inputStream = response.getBodyAsStream()) {
            body = readFromStreamByBuffer(response.getBodyAsStream());
        }

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals(responseBody, body);
    }

    @Test
    public void shouldReadTwoResponsesAsInputStreamsWithoutClosingWithCompressionByByte() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.GZIP);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        assertEquals(200, response.getStatusCode());
        String body = readFromStreamByByte(response.getBodyAsStream());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, body);

        response = clientConnection.send(headers, requestBody);
        assertEquals(200, response.getStatusCode());
        body = readFromStreamByByte(response.getBodyAsStream());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(responseBody, body);
    }

    @Test
    public void shouldReadResponseAfterResponseNotFinishedResponseByByte() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        HttpResponse response = clientConnection.send(headers, requestBody);
        try (InputStream inputStream = response.getBodyAsStream()) {
            byte[] buffer = new byte[100];
            inputStream.read(buffer);
            String partOfBody = new String(buffer);
            assertEquals(partOfBody, responseBody.substring(0, partOfBody.length()));
        }
        response = clientConnection.send(headers, requestBody);
        String body;
        try (InputStream inputStream = response.getBodyAsStream()) {
            body = readFromStreamByByte(response.getBodyAsStream());
        }

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertEquals(responseBody, body);
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

    private byte[] gzipBody(byte[] body) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            GZIPOutputStream gzipStream = new GZIPOutputStream(output);
            gzipStream.write(body);
            gzipStream.close();
        } catch (IOException e) {
            // this is used for tests only
            throw new RuntimeException("Error compressing body", e);
        }
        return output.toByteArray();
    }

    private byte[] chunkBody(byte[] body) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            OutputStream stream = new ChunkedOutputStream(output, false);
            stream.write(body);
            stream.close();
        } catch (IOException e) {
            // this is used for tests only
            throw new RuntimeException("Error compressing body", e);
        }
        return output.toByteArray();
    }

    private String readFromStreamByBuffer(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString();
    }

    private String readFromStreamByByte(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int read;
        while ((read = inputStream.read()) != -1) {
            output.write(read);
        }
        return output.toString();
    }
}
