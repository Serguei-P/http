package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

import serguei.http.utils.Utils;

// RFC-2616
public class ChunkedInputStreamTest {

    private static final byte[] CRLF = {13, 10};
    private static final String HTTP_CODEPAGE = "ASCII";
    private static final String DATA_CODEPAGE = "UTF-8";

    @Test
    public void shouldReadData() throws Exception {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] data = Utils.concat(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), makeTrailer("trailerName1: trailerValue1", "trailerName2: trailerValue2"), CRLF);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToString(stream);

        assertEquals(line1 + line2, result);
        assertEquals("trailerValue1", stream.getTrailerValue("trailerName1"));
        assertEquals("trailerValue2", stream.getTrailerValue("trailerName2"));
    }

    @Test
    public void shouldReadDataWithoutTrailer() throws Exception {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] data = Utils.concat(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), CRLF);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToString(stream);

        assertEquals(line1 + line2, result);
    }

    @Test
    public void shouldReadLargeData() throws Exception {
        String line1 = "This is small chunk ";
        String line2 = makeLongString(40000);
        byte[] data = Utils.concat(makeChunk(line1, ""), makeChunk(line2, ""), makeLastChunk(""), makeTrailer(), CRLF);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToString(stream);

        assertEquals(line1 + line2, result);
    }

    @Test
    public void shouldReadDataByByte() throws Exception {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] data = Utils.concat(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), makeTrailer("trailerName1: trailerValue1", "trailerName2: trailerValue2"), CRLF);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToStringByByte(stream);

        assertEquals(line1 + line2, result);
    }

    @Test
    public void shouldReadLargeDataByByte() throws Exception {
        String line1 = "This is small chunk ";
        String line2 = makeLongString(10000);
        byte[] data = Utils.concat(makeChunk(line1, ""), makeChunk(line2, ""), makeLastChunk(""), makeTrailer(), CRLF);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToStringByByte(stream);

        assertEquals(line1 + line2, result);
    }

    @Test
    public void shouldNotReadMoreThenRequired() throws Exception {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] moreData = {10, 11};
        byte[] data = Utils.concat(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), makeTrailer("trailerName1: trailerValue1", "trailerName2: trailerValue2"), CRLF, moreData);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToStringByByte(stream);

        assertEquals(line1 + line2, result);
        assertEquals(moreData.length, input.available());
    }

    @Test
    public void shouldBeAbleToGetMinusOneFromFinishedStringMultipleTimes() throws IOException {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] data = Utils.concat(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), makeTrailer("trailerName1: trailerValue1", "trailerName2: trailerValue2"), CRLF);
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToString(stream);

        assertEquals(line1 + line2, result);
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
    }

    @Test
    public void shouldReadDataFromNonZeroOffset() throws Exception {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] data = Utils.concat(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), makeTrailer("trailerName1: trailerValue1", "trailerName2: trailerValue2"), CRLF);
        byte[] expected = (line1 + line2).getBytes();
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        byte[] buffer = new byte[expected.length];

        try (ChunkedInputStream stream = new ChunkedInputStream(input)) {
            int pos = 0;
            while (pos < buffer.length) {
                int len = buffer.length - pos >= 10 ? 10 : buffer.length - pos;
                int read = stream.read(buffer, pos, len);
                if (read == -1) {
                    break;
                }
                pos += read;
            }
        }

        assertArrayEquals(expected, buffer);
    }

    private byte[] makeChunk(String chunkBody, String extension) throws UnsupportedEncodingException {
        byte[] bodyBuffer = chunkBody.getBytes(DATA_CODEPAGE);
        String header = Integer.toHexString(bodyBuffer.length) + extension;
        byte[] headerBuffer = header.getBytes(HTTP_CODEPAGE);
        return Utils.concat(headerBuffer, CRLF, bodyBuffer, CRLF);
    }

    private byte[] makeLastChunk(String extension) throws UnsupportedEncodingException {
        String header = "0" + extension;
        byte[] headerBuffer = header.getBytes(HTTP_CODEPAGE);
        return Utils.concat(headerBuffer, CRLF);
    }

    private byte[] makeTrailer(String... headers) throws UnsupportedEncodingException {
        int size = 2; // for last CRLF
        byte[][] buffers = new byte[headers.length][];
        for (int i = 0; i < headers.length; i++) {
            buffers[i] = headers[i].getBytes(HTTP_CODEPAGE);
            size += buffers[i].length + 2;
        }
        byte[] result = new byte[size];
        int pos = 0;
        for (byte[] buffer : buffers) {
            System.arraycopy(buffer, 0, result, pos, buffer.length);
            pos += buffer.length;
            System.arraycopy(CRLF, 0, result, pos, CRLF.length);
            pos += 2;
        }
        System.arraycopy(CRLF, 0, result, pos, CRLF.length);
        return result;
    }

    private String readToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(DATA_CODEPAGE);
    }
    
    private String readToStringByByte(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int pos = 0;
        int ch;
        while ((ch = inputStream.read()) != -1) {
            if (pos >= buffer.length) {
                result.write(buffer, 0, pos);
                pos = 0;
            }
            buffer[pos++] = (byte)ch;
        }
        if (pos > 0) {
            result.write(buffer, 0, pos);
        }
        return result.toString(DATA_CODEPAGE);
    }

    private String makeLongString(int len) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < len; i++) {
            result.append((char)(i % 26 + 'a'));
        }
        return result.toString();
    }
}
