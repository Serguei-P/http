package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

public class MultiPartRequestTest {

    private static final String BOUNDARY = "------------------------943603c96ae951d6";
    private static final String TEXT = "TheText";
    private static final String FILE1 = "This is line 1 from file 1\r\nThis is line 2 from file 1\r\n";
    private static final String FILE2 = "This is line 1 from file 2\r\nThis is line 2 from file 2\r\n";
 // @formatter:off
    private String[] REQUEST_DATA = {
        "POST /test HTTP/1.1",
        "Host: www.google.co.uk",
        "Content-Length: 542",
        "Content-Type: multipart/form-data; boundary=" + BOUNDARY,
        "",
        "--" + BOUNDARY,
        "Content-Disposition: form-data; name=\"text\"",
        "",
        TEXT,
        "--" + BOUNDARY,
        "Content-Disposition: form-data; name=\"file1\"; filename=\"t1.txt\"",
        "Content-Type: text/plain",
        "",
        FILE1,
        "--" + BOUNDARY,
        "Content-Disposition: form-data; name=\"file2\"; filename=\"t2.txt\"",
        "Content-Type: application/test",
        "",
        FILE2,
        "--" + BOUNDARY + "--"};
 // @formatter:on  

    @Test
    public void serverShouldProcessMultipartRequest() throws Exception {
        InputStream inputStream = getRequestAsStream();
        HttpRequest request = new HttpRequest(inputStream);

        assertTrue(request.hasMultipartBody());

        RequestValues requestValues = request.readBodyAsValues();

        assertEquals(TEXT, requestValues.getValue("text"));
        assertArrayEquals(TEXT.getBytes("UTF-8"), requestValues.getBytesValue("text"));
        assertNull("text/plain", requestValues.getContentType("text"));
        assertNull(requestValues.getFileName("text"));

        assertEquals(FILE1, requestValues.getValue("file1"));
        assertArrayEquals(FILE1.getBytes("UTF-8"), requestValues.getBytesValue("file1"));
        assertEquals("text/plain", requestValues.getContentType("file1"));
        assertEquals("t1.txt", requestValues.getFileName("file1"));

        assertEquals(FILE2, requestValues.getValue("file2"));
        assertArrayEquals(FILE2.getBytes("UTF-8"), requestValues.getBytesValue("file2"));
        assertEquals("t2.txt", requestValues.getFileName("file2"));
        assertEquals("application/test", requestValues.getContentType("file2"));

        assertNull(requestValues.getValue("wrong"));
        assertEquals(0, inputStream.available());
    }

    private InputStream getRequestAsStream() throws UnsupportedEncodingException {
        return new ByteArrayInputStream((Utils.concatWithDelimiter(REQUEST_DATA, "\r\n") + "\r\n").getBytes("UTF-8"));
    }

}
