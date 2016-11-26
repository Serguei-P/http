package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

// RFC-2616
public class ChunkedInputStreamTest {

    private static final byte[] CRLF = {13, 10};
    private static final String HTTP_CODEPAGE = "ASCII";
    private static final String DATA_CODEPAGE = "UTF-8";

    @Test
    public void shouldReadData() throws Exception {
        String line1 = "This is the first chunk\r\n";
        String line2 = "And another chunk\r\n";
        byte[] data = mergeBuffers(makeChunk(line1, ";extName1=extValue1;extName2=extValue2"), makeChunk(line2, ""),
                makeLastChunk(""), makeTrailer("trailerName1=trailerValue2", "trailerName1=trailerValue2"));
        System.out.println("making stream");
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        System.out.println("running");
        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToString(stream);

        assertEquals(line1 + line2, result);
    }

    @Test
    public void shouldReadLargeData() throws Exception {
        String line1 = "This is small chunk ";
        String line2 = makeLongString(10000);
        byte[] data = mergeBuffers(makeChunk(line1, ""), makeChunk(line2, ""), makeLastChunk(""), makeTrailer());
        System.out.println("making stream");
        ByteArrayInputStream input = new ByteArrayInputStream(data);

        System.out.println("running");
        ChunkedInputStream stream = new ChunkedInputStream(input);
        String result = readToString(stream);

        System.out.println((line1 + line2).length() + " " + result.length());
        assertEquals(line1 + line2, result);
    }

    private byte[] makeChunk(String chunkBody, String extension) throws UnsupportedEncodingException {
        System.out.println("makeChunk");
        byte[] bodyBuffer = chunkBody.getBytes(DATA_CODEPAGE);
        String header = Integer.toHexString(bodyBuffer.length) + extension;
        byte[] headerBuffer = header.getBytes(HTTP_CODEPAGE);
        return mergeBuffers(headerBuffer, CRLF, bodyBuffer, CRLF);
    }

    private byte[] makeLastChunk(String extension) throws UnsupportedEncodingException {
        String header = "0" + extension;
        byte[] headerBuffer = header.getBytes(HTTP_CODEPAGE);
        return mergeBuffers(headerBuffer, CRLF);
    }

    private byte[] makeTrailer(String... headers) throws UnsupportedEncodingException {
        System.out.println("makeTrailer");
        int size = 2;
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

    private byte[] mergeBuffers(byte[]... buffers) {
        System.out.println("mergeBuffer");
        int size = 0;
        for (byte[] buffer : buffers) {
            size += buffer.length;
        }
        byte[] result = new byte[size];
        int pos = 0;
        for (byte[] buffer : buffers) {
            System.arraycopy(buffer, 0, result, pos, buffer.length);
            pos += buffer.length;
        }
        return result;
    }

    private String readToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            System.out.println("read from stream = " + length);
            result.write(buffer, 0, length);
        }
        return result.toString(DATA_CODEPAGE);
    }
    
    private String makeLongString(int len) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < len; i++) {
            result.append((char)(i % 32 + 'a'));
        }
        return result.toString();
    }
}
