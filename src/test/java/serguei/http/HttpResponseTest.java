package serguei.http;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class HttpResponseTest {

    @Test
    public void shouldProcessResponse() throws IOException {
        String responseBody = "This is a response";
        int bodyLen = responseBody.getBytes().length;
        String responseData = "HTTP/1.1 200 OK\r\nContent-Length: " + bodyLen + "\r\n\r\n" + responseBody + "extra data";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(responseData.getBytes());

        HttpResponse response = new HttpResponse(inputStream);

        assertEquals(200, response.getStatusCode());
        assertEquals(responseBody, response.readBodyAsString());
    }
}
