package serguei.http;

import static org.junit.Assert.*;

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
        String hostName = "www.cisco.com";
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
        String hostName = "www.cisco.com";
        connection = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpRequestHeaders.getRequest("http://" + hostName + "/");
        request.setHeader("Accept-Encoding", "gzip");
        HttpResponse response = connection.send(request);

        assertEquals(200, response.getStatusCode());
        
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

}
