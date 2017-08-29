package serguei.http;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.Test;

public class HttpClientConnectionTest {

    private HttpClientConnection connection;

    @After
    public void clearUp() {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void testHttp() throws Exception {
        String hostName = "www.fitltd.com";
        connection = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpRequestHeaders.getRequest("http://" + hostName + "/");
        HttpResponse response = connection.send(request);

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.length() > 0);
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void testHttpWithGzip() throws Exception {
        String hostName = "www.fitltd.com";
        connection = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpRequestHeaders.getRequest("http://" + hostName + "/");
        request.setHeader("Accept-Encoding", "gzip");
        HttpResponse response = connection.send(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        
        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldAllowSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);
        HttpResponse response = connection.send(HttpRequestHeaders.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldFailOnSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshakeAndValidate(hostName);
    }

    @Test
    public void shouldCloseUnusedConnection() {
        connection = new HttpClientConnection("www.cisco.com", 80);
        connection.close();
    }

    @Test
    public void shouldConnectToOpera() throws IOException {
        String hostName = "opera.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);

        HttpRequestHeaders requestHeaders = new HttpRequestHeaders("GET / HTTP/1.1", "Host: " + hostName,
                "User-Agent: curl/7.51.0", "Accept: */*");

        HttpResponse response = connection.send(requestHeaders);

        assertEquals(301, response.getStatusCode());
    }

    @Test
    public void shouldTimeout() throws Exception {
        String hostName = "127.0.0.2"; // nothing is there
        connection = new HttpClientConnection(hostName, 80);
        connection.setTimeoutMillis(1000);

        long start = System.currentTimeMillis();
        try {
            connection.connect();
            fail("Should not connect");
        } catch (IOException e) {

        }
        long end = System.currentTimeMillis();

        assertTrue("Time taken is " + (end - start) + " which is too long", end - start < 1200);
    }

}
