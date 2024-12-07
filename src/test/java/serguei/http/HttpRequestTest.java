package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import serguei.http.utils.Utils;

public class HttpRequestTest {

    @Test
    public void shouldParseRequest() throws IOException {
        String requestBody = "This is a request";
        int bodyLen = requestBody.getBytes().length;
        String requestData = "POST /test?param=1 HTTP/1.1\r\nHost: localhost:8080\r\nContent-Length: " + bodyLen + "\r\n\r\n" +
                requestBody + "extra data";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());

        HttpRequest request = new HttpRequest(inputStream);

        assertEquals("localhost:8080", request.getHeader("Host"));
        assertEquals("localhost:8080", request.getHost());
        assertEquals("/test?param=1", request.getPath());
        assertEquals(request.getUrl().getHost(), "localhost");
        assertEquals("/test", request.getUrl().getPath());
        assertEquals("param=1", request.getUrl().getQuery());
        assertEquals(8080, request.getUrl().getPort());
        assertEquals("http", request.getUrl().getProtocol());
        assertEquals(bodyLen, request.getContentLength());
        assertEquals(requestBody, request.readBodyAsString());
        assertFalse(request.isContentChunked());
        assertTrue(request.hasBody());
    }

    @Test
    public void shouldParseRequestWithoutABody() throws IOException {
        String requestData = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());

        HttpRequest request = new HttpRequest(inputStream);

        assertEquals("localhost", request.getHeader("Host"));
        assertEquals("localhost", request.getHost());
        assertEquals("/", request.getPath());
        assertEquals(request.getUrl().getHost(), "localhost");
        assertEquals("/", request.getUrl().getPath());
        assertEquals(-1, request.getUrl().getPort());
        assertEquals("http", request.getUrl().getProtocol());
        assertEquals("", request.readBodyAsString());
        assertFalse(request.hasBody());
    }

    @Test
    public void shouldReturnHeadersWhenPresent() throws IOException {
        String requestData = "GET / HTTP/1.1\r\nHost: localhost\r\nHeader1: test1\r\nHeader1: test2\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());
        HttpRequest request = new HttpRequest(inputStream);

        List<String> headers = request.getHeaders("Header1");

        assertEquals(Arrays.asList("test1", "test2"), headers);
    }

    @Test
    public void shouldReturnEmptyHeaderListWhenMissing() throws IOException {
        String requestData = "GET / HTTP/1.1\r\nHost: localhost\r\nHeader1: test1\r\nHeader1: test2\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());
        HttpRequest request = new HttpRequest(inputStream);

        List<String> headers = request.getHeaders("Not-Present");

        assertEquals(Collections.emptyList(), headers);
    }

    @Test
    public void shouldReturnEmptyBodyInputStreamWhenGetRequest() throws IOException {
        String requestData = "GET / HTTP/1.1\r\nHost: localhost\r\nHeader1: test1\r\nHeader1: test2\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());
        HttpRequest request = new HttpRequest(inputStream);

        byte[] data = Utils.readFully(request.getBodyAsStream());

        assertEquals(0, data.length);
    }

    @Test
    public void shouldReturnEmptyBodyOriginalInputStreamWhenGetRequest() throws IOException {
        String requestData = "GET / HTTP/1.1\r\nHost: localhost\r\nHeader1: test1\r\nHeader1: test2\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());
        HttpRequest request = new HttpRequest(inputStream);

        byte[] data = Utils.readFully(request.getBodyAsOriginalStream());

        assertEquals(0, data.length);
    }
}
