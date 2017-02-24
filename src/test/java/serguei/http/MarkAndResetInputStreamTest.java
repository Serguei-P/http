package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class MarkAndResetInputStreamTest {

    private byte[] dataToRead = {0, 1, -2, 3, -4, 5, -6, 7, -8, 9, -10};
    private byte[] firstPart = {0, 1, -2, 3};
    private byte[] lastPart = {-8, 9, -10};

    @Test
    public void shouldMarkAndResetData() throws IOException {
        ByteArrayInputStream originalStream = new ByteArrayInputStream(dataToRead);
        try (MarkAndResetInputStream stream = new MarkAndResetInputStream(originalStream)) {
            stream.mark(0);

            byte[] buffer1 = new byte[firstPart.length];
            int len = stream.read(buffer1);

            assertEquals(buffer1.length, len);
            assertArrayEquals(firstPart, buffer1);

            stream.reset();
            byte[] buffer2 = new byte[dataToRead.length];
            len = stream.read(buffer2);

            assertEquals(buffer2.length, len);
            assertArrayEquals(dataToRead, buffer2);
        }
    }

    @Test
    public void shouldMarkAndResetReadByByte() throws IOException {
        ByteArrayInputStream originalStream = new ByteArrayInputStream(dataToRead);
        try (MarkAndResetInputStream stream = new MarkAndResetInputStream(originalStream)) {
            stream.mark(0);
            byte[] buffer1 = new byte[dataToRead.length];

            for (int i = 0; i < dataToRead.length; i++) {
                byte value = (byte)stream.read();
                buffer1[i] = value;
            }

            assertArrayEquals(dataToRead, buffer1);

            stream.reset();
            byte[] buffer2 = new byte[dataToRead.length];
            int len = stream.read(buffer2);

            assertEquals(buffer2.length, len);
            assertArrayEquals(dataToRead, buffer2);
        }
    }

    @Test
    public void shouldReplayInt() throws IOException {
        ByteArrayInputStream originalStream = new ByteArrayInputStream(dataToRead);
        try (MarkAndResetInputStream stream = new MarkAndResetInputStream(originalStream)) {
            stream.mark(0);

            byte[] buffer = new byte[4];
            stream.read(buffer);
            stream.reset();

            assertEquals(0, stream.read());
            assertEquals(1, stream.read());
            assertEquals(254, stream.read());
            assertEquals(3, stream.read());
            assertEquals(252, stream.read());
        }
    }

    @Test
    public void shouldSkipOverResetPos() throws IOException {
        ByteArrayInputStream originalStream = new ByteArrayInputStream(dataToRead);
        try (MarkAndResetInputStream stream = new MarkAndResetInputStream(originalStream)) {
            stream.mark(0);

            byte[] buffer1 = new byte[firstPart.length];
            stream.read(buffer1);

            stream.reset();
            stream.skip(dataToRead.length - lastPart.length);

            byte[] buffer2 = new byte[lastPart.length];
            stream.read(buffer2);

            assertArrayEquals(lastPart, buffer2);
        }
    }

    @Test
    public void shouldSupportMark() throws IOException {
        ByteArrayInputStream originalStream = new ByteArrayInputStream(dataToRead);
        try (MarkAndResetInputStream stream = new MarkAndResetInputStream(originalStream)) {
            assertTrue(stream.markSupported());
        }
    }
}
