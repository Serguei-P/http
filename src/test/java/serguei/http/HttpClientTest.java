package serguei.http;

import static org.junit.Assert.*;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Test;

public class HttpClientTest {

    @Test
    public void testHttp() throws Exception {
        String hostName = "www.cisco.com";
        HttpClient client = new HttpClient(hostName, 80);

        HttpRequest request = HttpClient.getRequest("http://" + hostName + "/");
        HttpResponse response = client.send(request);

        assertEquals(200, response.getStatusCode());

        String body = client.readResponseBodyAsString();
        assertTrue(body.length() > 0);
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void testHttpWithGzip() throws Exception {
        String hostName = "www.cisco.com";
        HttpClient client = new HttpClient(hostName, 80);

        HttpRequest request = HttpClient.getRequest("http://" + hostName + "/");
        request.addHeader("Accept-Encoding", "gzip");
        HttpResponse response = client.send(request);

        assertEquals(200, response.getStatusCode());
        
        String body = client.readResponseBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldAllowSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        HttpClient client = new HttpClient(hostName, 443);

        client.startHandshake(hostName);
        HttpResponse response = client.send(HttpClient.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = client.readResponseBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldFailOnSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        HttpClient client = new HttpClient(hostName, 443);

        client.startHandshakeAndValidate(hostName);
    }

}
