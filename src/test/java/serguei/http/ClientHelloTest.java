package serguei.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class ClientHelloTest {

    private final static byte[] GOOGLE_HELLO = {22, 3, 1, 0, -17, 1, 0, 0, -21, 3, 3, -117, -59, 93, 41, -37, 39, 74, -116,
            -21, -78, 100, 37, 81, 79, -25, -100, -30, -8, 89, 61, 72, 12, -115, 13, -34, 80, 124, 90, 22, 85, 69, 7, 32, 83,
            -48, -67, 44, -2, 12, 20, 109, -83, -126, 40, 100, 21, 112, -28, -34, -86, -50, 14, -15, 123, 83, 76, 85, 103,
            -120, -52, -123, -22, 93, 74, -25, 0, 40, -64, 43, -64, 47, 0, -98, -52, 20, -52, 19, -64, 10, -64, 9, -64, 19,
            -64, 20, -64, 7, -64, 17, 0, 51, 0, 50, 0, 57, 0, -100, 0, 47, 0, 53, 0, 10, 0, 5, 0, 4, 1, 0, 0, 122, 0, 0, 0,
            21, 0, 19, 0, 0, 16, 119, 119, 119, 46, 103, 111, 111, 103, 108, 101, 46, 99, 111, 46, 117, 107, -1, 1, 0, 1, 0,
            0, 10, 0, 8, 0, 6, 0, 23, 0, 24, 0, 25, 0, 11, 0, 2, 1, 0, 0, 35, 0, 0, 51, 116, 0, 0, 0, 16, 0, 27, 0, 25, 6,
            115, 112, 100, 121, 47, 51, 8, 115, 112, 100, 121, 47, 51, 46, 49, 8, 104, 116, 116, 112, 47, 49, 46, 49, 0, 5, 0,
            5, 1, 0, 0, 0, 0, 0, 18, 0, 0, 0, 13, 0, 18, 0, 16, 4, 1, 5, 1, 2, 1, 4, 3, 5, 3, 2, 3, 4, 2, 2, 2};
    private final static byte[] SSLV2_CLIENT_HELLO = {-128, 68, 1, 3, 1, 0, 27, 0, 0, 0, 32, 0, 0, 4, 0, 0, 47, 0, 0, 53, 0,
            0, 10, 0, 0, 5, 0, 0, 9, 0, 0, 51, 0, 0, 57, 0, 0, -1, 69, -35, -123, 28, -20, 90, 115, -15, -72, -121, -27, -43,
            3, -110, 36, -2, 123, -40, 28, 97, -73, 9, -44, 66, -89, 67, 40, -26, 117, 46, 23, 48};
    private final static byte[] SSLV2_CLIENT_HELLO_WRONG_VERSION = {-128, 68, 1, 4, 1, 0, 27, 0, 0, 0, 32, 0, 0, 4, 0, 0, 47,
            0, 0, 53, 0, 0, 10, 0, 0, 5, 0, 0, 9, 0, 0, 51, 0, 0, 57, 0, 0, -1, 69, -35, -123, 28, -20, 90, 115, -15, -72,
            -121, -27, -43, 3, -110, 36, -2, 123, -40, 28, 97, -73, 9, -44, 66, -89, 67, 40, -26, 117, 46, 23, 48};
    private final static byte[] RANDOM = {5, 4, 3, 2, 1, 0};
    private final static byte[] SHORT_DATA = {1, 2};

    private MarkAndResetInputStream googleHelloInputStream = new MarkAndResetInputStream(new ByteArrayInputStream(GOOGLE_HELLO));
    private MarkAndResetInputStream sslv2InputStream = new MarkAndResetInputStream(
            new ByteArrayInputStream(SSLV2_CLIENT_HELLO));
    private MarkAndResetInputStream sslv2InputStreamWrongVersion = new MarkAndResetInputStream(
            new ByteArrayInputStream(SSLV2_CLIENT_HELLO_WRONG_VERSION));
    private MarkAndResetInputStream randomInputStream = new MarkAndResetInputStream(new ByteArrayInputStream(RANDOM));
    private MarkAndResetInputStream shortInputStream = new MarkAndResetInputStream(new ByteArrayInputStream(SHORT_DATA));

    @Test
    public void shouldReadClientHello() throws IOException {
        ClientHello clientHello = ClientHello.read(googleHelloInputStream);

        assertEquals("www.google.co.uk", clientHello.getSniHostName());
        assertEquals(Integer.valueOf(33), clientHello.getProtocolVersion().getCode());
        assertEquals(Integer.valueOf(31), clientHello.getRecordProtocolVersion().getCode());
    }

    @Test
    public void shouldReadSsl2ClientHello() throws IOException {
        ClientHello clientHello = ClientHello.read(sslv2InputStream);

        assertEquals("", clientHello.getSniHostName());
        assertEquals(new TlsVersion(3, 1), clientHello.getProtocolVersion());
        assertEquals(new TlsVersion(2, 0), clientHello.getRecordProtocolVersion());
    }

    @Test
    public void shouldNotReturnWrongVersionInSsl2ClientHello() throws IOException {
        ClientHello clientHello = ClientHello.read(sslv2InputStreamWrongVersion);

        assertEquals("", clientHello.getSniHostName());
        assertNull(clientHello.getProtocolVersion().getCode());
        assertNull(clientHello.getRecordProtocolVersion().getCode());
    }

    @Test
    public void shouldLeaveDataInReplayInputStream() throws IOException {
        assertEquals(GOOGLE_HELLO.length, googleHelloInputStream.getOriginalStream().available());

        ClientHello clientHello = ClientHello.read(googleHelloInputStream);

        assertEquals(GOOGLE_HELLO.length, googleHelloInputStream.available());
        assertEquals(0, googleHelloInputStream.getOriginalStream().available());

        byte[] buffer = new byte[GOOGLE_HELLO.length];
        int len = googleHelloInputStream.read(buffer);

        assertEquals(0, googleHelloInputStream.getOriginalStream().available());
        assertEquals(GOOGLE_HELLO.length, len);
        assertArrayEquals(GOOGLE_HELLO, buffer);

        assertEquals(new TlsVersion(3, 3), clientHello.getProtocolVersion());
        assertEquals(new TlsVersion(3, 1), clientHello.getRecordProtocolVersion());
        assertEquals("www.google.co.uk", clientHello.getSniHostName());
    }

    @Test
    public void shouldNotBreakWhenRandomData() throws IOException {
        ClientHello clientHello = ClientHello.read(randomInputStream);

        assertEquals("", clientHello.getSniHostName());
        assertTrue(clientHello.getProtocolVersion().isUndefined());
        assertTrue(clientHello.getRecordProtocolVersion().isUndefined());
    }

    @Test
    public void shouldNotBreakWhenVeryShortData() throws IOException {
        ClientHello clientHello = ClientHello.read(shortInputStream);

        assertEquals("", clientHello.getSniHostName());
        assertTrue(clientHello.getProtocolVersion().isUndefined());
        assertTrue(clientHello.getRecordProtocolVersion().isUndefined());
    }

}
