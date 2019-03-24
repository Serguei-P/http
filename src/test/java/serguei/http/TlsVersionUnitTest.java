package serguei.http;

import static org.junit.Assert.*;

import org.junit.Test;

public class TlsVersionUnitTest {

    @Test
    public void shouldConvertVersionToString() {
        assertEquals("SSLv2Hello", TlsVersion.SSLv2Hello.toJdkString());
        assertEquals("SSLv3", TlsVersion.SSLv3.toJdkString());
        assertEquals("TLSv1", TlsVersion.TLSv10.toJdkString());
        assertEquals("TLSv1.1", TlsVersion.TLSv11.toJdkString());
        assertEquals("TLSv1.2", TlsVersion.TLSv12.toJdkString());
        assertEquals("TLSv1.3", TlsVersion.TLSv13.toJdkString());
    }

    @Test
    public void shouldConvertToTlsVersionAndBack() {
        assertSame(TlsVersion.SSLv2Hello, TlsVersion.fromJdkString(TlsVersion.SSLv2Hello.toJdkString()));
        assertSame(TlsVersion.SSLv3, TlsVersion.fromJdkString(TlsVersion.SSLv3.toJdkString()));
        assertSame(TlsVersion.TLSv10, TlsVersion.fromJdkString(TlsVersion.TLSv10.toJdkString()));
        assertSame(TlsVersion.TLSv11, TlsVersion.fromJdkString(TlsVersion.TLSv11.toJdkString()));
        assertSame(TlsVersion.TLSv12, TlsVersion.fromJdkString(TlsVersion.TLSv12.toJdkString()));
        assertSame(TlsVersion.TLSv13, TlsVersion.fromJdkString(TlsVersion.TLSv13.toJdkString()));
    }

}
