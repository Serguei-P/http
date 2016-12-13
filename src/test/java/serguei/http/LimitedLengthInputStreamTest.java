package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

public class LimitedLengthInputStreamTest {

    @Test
    public void shouldLimitLengthWhenLessThenBuffer() throws IOException {
        byte[] data = makeData(1024);
        ByteArrayInputStream originalStream = new ByteArrayInputStream(data);
        InputStream inputStream = new LimitedLengthInputStream(originalStream, 1000);
        
        byte[] result = readToData(inputStream, 2048);
        
        assertArrayEquals(Arrays.copyOf(data, 1000), result);
    }

    @Test
    public void shouldLimitLengthWhenMoreThenBuffer() throws IOException {
        byte[] data = makeData(1024);
        ByteArrayInputStream originalStream = new ByteArrayInputStream(makeData(1024));
        InputStream inputStream = new LimitedLengthInputStream(originalStream, 1000);
        
        byte[] result = readToData(inputStream, 99);

        assertArrayEquals(Arrays.copyOf(data, 1000), result);
    }

    @Test
    public void shouldLimitLengthWhenReadingByByte() throws IOException {
        byte[] data = makeData(1024);
        ByteArrayInputStream originalStream = new ByteArrayInputStream(makeData(1024));
        InputStream inputStream = new LimitedLengthInputStream(originalStream, 1000);

        byte[] result = readToDataByByte(inputStream);

        assertArrayEquals(Arrays.copyOf(data, 1000), result);
    }

    private static byte[] makeData(int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte)('a' + i % 26);
        }
        return result;
    }

    private byte[] readToData(InputStream inputStream, int bufferSize) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[bufferSize];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toByteArray();
    }

    private byte[] readToDataByByte(InputStream inputStream) throws IOException {
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
        return result.toByteArray();
    }

}
