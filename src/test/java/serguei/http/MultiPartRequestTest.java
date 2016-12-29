package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

public class MultiPartRequestTest {

    private static final String CRLF = "\r\n";

 // @formatter:off
    private String[] REQUEST_DATA = {
        "POST /test HTTP/1.1",
        "Host: www.google.co.uk",
        "Content-Length: 536",
        "Content-Type: multipart/form-data; boundary=------------------------943603c96ae956d6",
        "",
        "--------------------------943603c96ae956d6",
        "Content-Disposition: form-data; name=\"text\"",
        "",
        "TheText",
        "--------------------------943603c96ae956d6",
        "Content-Disposition: form-data; name=\"file1\"; filename=\"t1.txt\"",
        "Content-Type: text/plain",
        "",
        "This is line 1 from file 1",
        "This is line 2 from file 1",
        "",
        "--------------------------943603c96ae956d6",
        "Content-Disposition: form-data; name=\"file2\"; filename=\"t2.txt\"",
        "Content-Type: text/plain",
        "",
        "This is line 1 from file 2",
        "This is line 2 from file 2",
        "",
        "--------------------------943603c96ae956d6--"};
 // @formatter:on  

    @Test
    public void serverShouldProcessMultipartRequest() throws Exception {
        HttpRequest request = new HttpRequest(getRequestAsStream());

        assertTrue(request.hasMultipartBody());

        BodyPart part = request.readNextBodyPart();
        assertNotNull(part);
        assertEquals("text", part.getName());
        assertNull(part.getFilename());
        assertEquals("text/plain", part.getContentType());

        part = request.readNextBodyPart();
        assertNotNull(part);
        assertEquals("file1", part.getName());
        assertEquals("t1.txt", part.getFilename());
        assertEquals("text/plain", part.getContentType());

        part = request.readNextBodyPart();
        assertNotNull(part);
        assertEquals("file2", part.getName());
        assertEquals("t2.txt", part.getFilename());
        assertEquals("text/plain", part.getContentType());

        part = request.readNextBodyPart();
        assertNull(part);
    }

    private InputStream getRequestAsStream() throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for (String line : REQUEST_DATA) {
            builder.append(line);
            builder.append(CRLF);
        }
        return new ByteArrayInputStream(builder.toString().getBytes("ASCII"));
    }

}
