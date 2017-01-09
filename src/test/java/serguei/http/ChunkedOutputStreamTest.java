package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.junit.Test;

public class ChunkedOutputStreamTest {

    private byte[] OUTPUT_DATA = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10};
    private byte[] EXPECTED = {'a', '\r', '\n', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, '\r', '\n', 'a', '\r', '\n', -1, -2,
            -3, -4, -5, -6, -7, -8, -9, -10, '\r', '\n', '0', '\r', '\n', '\r', '\n'};
    private byte[] EXPECTED_ONE_CHUNK = {'1', '4', '\r', '\n', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -1, -2,
            -3, -4, -5, -6, -7, -8, -9, -10, '\r', '\n', '0', '\r', '\n', '\r', '\n'};

    @Test
    public void shouldWriteChunk() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChunkedOutputStream outputStream = new ChunkedOutputStream(output, 10, false);

        outputStream.write(OUTPUT_DATA);
        outputStream.close();

        assertArrayEquals(EXPECTED, output.toByteArray());
    }

    @Test
    public void shouldWriteChunkByByte() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChunkedOutputStream outputStream = new ChunkedOutputStream(output, 10, false);

        for (byte ch : OUTPUT_DATA) {
            outputStream.write((int)ch);
        }
        outputStream.close();

        assertArrayEquals(EXPECTED, output.toByteArray());
    }

    @Test
    public void shouldWriteChunkDefaultBuffer() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChunkedOutputStream outputStream = new ChunkedOutputStream(output, false);

        outputStream.write(OUTPUT_DATA);
        outputStream.close();

        assertArrayEquals(EXPECTED_ONE_CHUNK, output.toByteArray());
    }

    @Test
    public void shouldBeReadByChunkedInputStream() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChunkedOutputStream outputStream = new ChunkedOutputStream(output, 12, false);

        outputStream.write(OUTPUT_DATA);
        outputStream.close();

        InputStream inputStream = new ByteArrayInputStream(output.toByteArray());
        InputStream chunkedInputStream = new ChunkedInputStream(inputStream);

        byte[] buffer = new byte[OUTPUT_DATA.length];
        int read = chunkedInputStream.read(buffer);
        assertEquals(OUTPUT_DATA.length, read);
        assertEquals(-1, chunkedInputStream.read());
        assertArrayEquals(OUTPUT_DATA, buffer);
        chunkedInputStream.close();
    }
}
