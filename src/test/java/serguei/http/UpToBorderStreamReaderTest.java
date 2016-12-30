package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

public class UpToBorderStreamReaderTest {

    private static final String CHARSET = "ASCII";

    @Test
    public void readDataSimple() throws Exception {
        String expected = "This is line 1\r\nThis is line 2\r\n";
        String border = "----border----";
        String data = expected + border;
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(CHARSET));

        UpToBorderStreamReader reader = new UpToBorderStreamReader(inputStream, border.getBytes(CHARSET));
        String result = new String(reader.read(), CHARSET);

        assertEquals(expected, result);
    }

    @Test
    public void readDataWithSinglePartialMatch() throws Exception {
        String expected = "This is line 1\r\nThis is line 2\r\n";
        String border = "\r\n----border----";
        String data = expected + border;
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(CHARSET));
        
        UpToBorderStreamReader reader = new UpToBorderStreamReader(inputStream, border.getBytes(CHARSET));
        String result = new String(reader.read(), CHARSET);

        assertEquals(expected, result);
    }

    @Test
    public void readDataWithMultiplePartialMatch() throws Exception {
        String expected = "This is line 1\r\n\r\n\r\n!\r\nThis is line 2\r\n";
        String border = "\r\n----border----";
        String data = expected + border;
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(CHARSET));

        UpToBorderStreamReader reader = new UpToBorderStreamReader(inputStream, border.getBytes(CHARSET));
        String result = new String(reader.read(), CHARSET);

        assertEquals(expected, result);
    }

    @Test
    public void readDataWithNoMatch() throws Exception {
        String expected = "This is line 1\r\n\r\n\r\n!\r\nThis is line 2";
        String border = "\r\n----border----";
        String data = expected;
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(CHARSET));

        UpToBorderStreamReader reader = new UpToBorderStreamReader(inputStream, border.getBytes(CHARSET));
        String result = new String(reader.read(), CHARSET);

        assertEquals(expected, result);
    }

    @Test
    public void readDataWithNoMatchAndPartialMatchAtTheEnd() throws Exception {
        String expected = "This is line 1\r\n\r\n\r\n!\r\nThis is line 2\r\n";
        String border = "\r\n----border----";
        String data = expected;
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(CHARSET));

        UpToBorderStreamReader reader = new UpToBorderStreamReader(inputStream, border.getBytes(CHARSET));
        String result = new String(reader.read(), CHARSET);

        assertEquals(expected, result);
    }

}
