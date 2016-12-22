package serguei.http;

import static org.junit.Assert.*;

import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.Test;

public class HttpClientTest {

    private HttpClientConnection client;

    @After
    public void clearUp() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testHttp() throws Exception {
        String hostName = "www.cisco.com";
        client = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpClientConnection.getRequest("http://" + hostName + "/");
        HttpResponse response = client.send(request);

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.length() > 0);
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void testHttpWithGzip() throws Exception {
        String hostName = "www.cisco.com";
        client = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpClientConnection.getRequest("http://" + hostName + "/");
        request.addHeader("Accept-Encoding", "gzip");
        HttpResponse response = client.send(request);

        assertEquals(200, response.getStatusCode());
        
        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldAllowSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        client = new HttpClientConnection(hostName, 443);

        client.startHandshake(hostName);
        HttpResponse response = client.send(HttpClientConnection.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldFailOnSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        client = new HttpClientConnection(hostName, 443);

        client.startHandshakeAndValidate(hostName);
    }

}
