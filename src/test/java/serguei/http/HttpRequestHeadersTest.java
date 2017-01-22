package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class HttpRequestHeadersTest {

    private static final String LINE_BREAK = "\r\n";

    @Test
    public void shouldCreateRequest() throws Exception {
        HttpRequestHeaders request = new HttpRequestHeaders("GET http://www.fitltd.com/test.jsp HTTP/1.1", "HOST: www.fitltd.com",
                "content-length: 100");

        assertEquals("GET", request.getMethod());
        assertEquals(new URL("http://www.fitltd.com/test.jsp"), request.getUrl());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("www.fitltd.com", request.getHeader("Host"));
        assertEquals("100", request.getHeader("Content-Length"));
        assertEquals("www.fitltd.com", request.getHost());
    }

    @Test
    public void shouldCreateRequestWithRelativePath() throws Exception {
        HttpRequestHeaders request = new HttpRequestHeaders("GET /test.jsp HTTP/1.1", "Host: www.fitltd.com",
                "Content-Length: 100");

        assertEquals("GET", request.getMethod());
        assertEquals(new URL("http://www.fitltd.com/test.jsp"), request.getUrl());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("www.fitltd.com", request.getHeader("Host"));
        assertEquals("100", request.getHeader("Content-Length"));
        assertEquals("www.fitltd.com", request.getHost());
    }

    @Test
    public void shouldCreateRequestWithoutHostHeader() throws Exception {
        HttpRequestHeaders request = new HttpRequestHeaders("GET http://www.fitltd.com/test.jsp HTTP/1.0", "content-length: 100");

        assertEquals("GET", request.getMethod());
        assertEquals(new URL("http://www.fitltd.com/test.jsp"), request.getUrl());
        assertEquals("HTTP/1.0", request.getVersion());
        assertNull(request.getHeader("Host"));
        assertEquals("100", request.getHeader("Content-Length"));
        assertEquals("www.fitltd.com", request.getHost());
    }

    @Test
    public void shouldCreateRequestWithDiddrenttHostInUrlAndHeader() throws Exception {
        String url = "http://www.fitltd.com/test.jsp";
        HttpRequestHeaders request = new HttpRequestHeaders("GET " + url + " HTTP/1.1", "host: www.microsoft.com",
                "content-length: 100");

        assertEquals("GET", request.getMethod());
        assertEquals(new URL(url), request.getUrl());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("www.microsoft.com", request.getHeader("Host"));
        assertEquals("100", request.getHeader("Content-Length"));
        assertEquals("www.microsoft.com", request.getHost());
    }

    @Test
    public void shouldAllowOnlyCommandLine() throws Exception {
        HttpRequestHeaders request = new HttpRequestHeaders("GET http://www.fitltd.com/test.jsp HTTP/1.1");

        assertEquals("GET", request.getMethod());
        assertEquals(new URL("http://www.fitltd.com/test.jsp"), request.getUrl());
        assertEquals("HTTP/1.1", request.getVersion());
        assertNull(request.getHeader("Host"));
        assertEquals("www.fitltd.com", request.getHost());
    }

    @Test
    public void shouldAllowDuplicateHeaders() throws Exception {
        HttpRequestHeaders request = new HttpRequestHeaders("GET http://www.fitltd.com/test.jsp HTTP/1.1", "host: www.fitltd.com",
                "header: test1", "header: test2");

        assertEquals("GET", request.getMethod());
        assertEquals(new URL("http://www.fitltd.com/test.jsp"), request.getUrl());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("www.fitltd.com", request.getHeader("Host"));
        assertEquals("www.fitltd.com", request.getHost());
        assertEquals("test1", request.getHeader("header"));
        assertEquals(Arrays.asList("test1", "test2"), request.getHeaders("header"));
    }

    @Test
    public void shouldReadFromStream() throws Exception {
        String content = "GET http://www.fitltd.com/test.jsp HTTP/1.1" + LINE_BREAK + "HOST: www.fitltd.com"
                + LINE_BREAK + "content-length: 12" + LINE_BREAK + LINE_BREAK + "This is body";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes("UTF-8"));

        HttpRequestHeaders request = new HttpRequestHeaders(inputStream);

        assertEquals("GET", request.getMethod());
        assertEquals(new URL("http://www.fitltd.com/test.jsp"), request.getUrl());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("www.fitltd.com", request.getHeader("Host"));
        assertEquals("12", request.getHeader("Content-Length"));
        assertEquals("www.fitltd.com", request.getHost());
    }

    @Test
    public void shouldReturnToString() throws Exception {
        String[] lines = {"GET /test.js HTTP/1.1", "Host: localhost"};
        String expectedValue = lines[0] + System.lineSeparator() + lines[1];

        HttpRequestHeaders request = new HttpRequestHeaders(lines[0], lines[1]);

        assertEquals(expectedValue, request.toString());
    }

    @Test
    public void shouldWriteToStream() throws Exception {
        String[] lines = {"GET /test.js HTTP/1.1", "Host: localhost"};
        byte[] expectedValue = (lines[0] + "\r\n" + lines[1] + "\r\n\r\n").getBytes("ASCII");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HttpRequestHeaders request = new HttpRequestHeaders(lines[0], lines[1]);

        request.write(output);

        assertArrayEquals(expectedValue, output.toByteArray());
    }

    @Test
    public void shouldParseHeaderValue() throws Exception {
        List<String> expected = Arrays.asList("multipart/form-data", "boundary=------------------------943603c96ae956d6");
        HttpRequestHeaders headers = new HttpRequestHeaders("GET / HTTP/1.1", "Host: localhost",
                "Content-Type: multipart/form-data; boundary=------------------------943603c96ae956d6");

        List<String> result = headers.headerValues("Content-Type");

        assertEquals(expected, result);
    }

    @Test
    public void shouldParseHeaderValueWithQuotes() throws Exception {
        List<String> expected = Arrays.asList("multipart/form-data", "boundary=------------------------943603c96ae956d;6");
        HttpRequestHeaders headers = new HttpRequestHeaders("GET / HTTP/1.1", "Host: localhost",
                "Content-Type: multipart/form-data; boundary=\"------------------------943603c96ae956d;6\"");

        List<String> result = headers.headerValues("Content-Type");

        assertEquals(expected, result);
    }

    @Test
    public void shouldPreserveHeadersOrder() throws Exception {
        String data = "GET / HTTP/1.1" + LINE_BREAK + "Host: www.myhost.com" + "Header1: header1" + LINE_BREAK
                + "Header2: header2" + LINE_BREAK + LINE_BREAK;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes("UTF-8"));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        HttpRequestHeaders headers = new HttpRequestHeaders(inputStream);
        headers.write(outputStream);
        String result = new String(outputStream.toString("UTF-8"));

        assertEquals(data, result);
    }

}
