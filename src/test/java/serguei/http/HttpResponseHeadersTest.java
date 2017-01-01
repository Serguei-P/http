package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.junit.Test;

public class HttpResponseHeadersTest {

    private static final String LINE_BREAK = "\r\n";

    @Test
    public void shouldCreateResponse() throws Exception {
        HttpResponseHeaders response = new HttpResponseHeaders("HTTP/1.1 200 OK", "Content-Length: 100");

        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReason());
        assertEquals("100", response.getHeader("Content-Length"));
        assertNull(response.getHeader("random"));
    }

    @Test(expected = HttpException.class)
    public void shouldThrowExceptionWhenWrongStatus() throws Exception {
        new HttpResponseHeaders("HTTP/1.1 WRONG OK", "Content-Length: 100");
    }

    @Test
    public void shouldReadResponse() throws Exception {
        String data = "HTTP/1.1 200 OK" + LINE_BREAK + "Content-Length: 100" + LINE_BREAK + LINE_BREAK;
        InputStream inputStream = new ByteArrayInputStream(data.getBytes("UTF-8"));

        HttpResponseHeaders response = new HttpResponseHeaders(inputStream);

        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReason());
        assertEquals("100", response.getHeader("Content-Length"));
        assertNull(response.getHeader("random"));
    }

    @Test
    public void shouldReturnOkResponse() {
        HttpResponseHeaders response = HttpResponseHeaders.ok();

        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReason());
    }

    @Test
    public void shouldReturnRedirectResponse() {
        String url = "http://www.google.com/";
        HttpResponseHeaders response = HttpResponseHeaders.redirect(url);

        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(302, response.getStatusCode());
        assertEquals("Found", response.getReason());
        assertEquals(url, response.getHeader("location"));
    }

    @Test
    public void shouldReturnServerError() {
        HttpResponseHeaders response = HttpResponseHeaders.serverError();

        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(500, response.getStatusCode());
        assertEquals("Server Error", response.getReason());
    }

    @Test
    public void shouldReturnChunkedBody() throws Exception {
        HttpResponseHeaders response = new HttpResponseHeaders("HTTP/1.1 200 OK", "Transfer-Encoding: chunked");

        assertTrue(response.hasChunkedBody());
        assertEquals(-1, response.getContentLength());
    }

    @Test
    public void shouldReturnContentLength() throws Exception {
        HttpResponseHeaders response = new HttpResponseHeaders("HTTP/1.1 200 OK", "Content-Length: 100");

        assertFalse(response.hasChunkedBody());
        assertEquals(100, response.getContentLength());
    }

    @Test
    public void shouldReturnToString() throws Exception {
        String[] lines = {"HTTP/1.1 200 OK", "Host: localhost"};
        String expectedValue = lines[0] + System.lineSeparator() + lines[1];

        HttpResponseHeaders response = new HttpResponseHeaders(lines[0], lines[1]);

        assertEquals(expectedValue, response.toString());
    }

    @Test
    public void shouldWriteToStream() throws Exception {
        String[] lines = {"HTTP/1.1 200 OK", "Host: localhost"};
        byte[] expectedValue = (lines[0] + "\r\n" + lines[1] + "\r\n\r\n").getBytes("ASCII");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpResponseHeaders response = new HttpResponseHeaders(lines[0], lines[1]);

        response.write(output);

        assertArrayEquals(expectedValue, output.toByteArray());
    }

}