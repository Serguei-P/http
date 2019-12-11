package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class HttpRequestTest {

    @Test
    public void shouldParseRequest() throws IOException {
        String requestBody = "This is a request";
        int bodyLen = requestBody.getBytes().length;
        String requestData = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + bodyLen + "\r\n\r\n" + requestBody
                + "extra data";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());

        HttpRequest request = new HttpRequest(inputStream);

        assertEquals("localhost", request.getHeader("Host"));
        assertEquals(requestBody, request.readBodyAsString());
        assertTrue(request.hasBody());
    }

    @Test
    public void shouldParseRequestWithoutABody() throws IOException {
        String requestData = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestData.getBytes());

        HttpRequest request = new HttpRequest(inputStream);

        assertEquals("localhost", request.getHeader("Host"));
        assertEquals("", request.readBodyAsString());
        assertFalse(request.hasBody());
    }
}
