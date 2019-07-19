package serguei.http;

import static org.junit.Assert.assertEquals;

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
    }
}
