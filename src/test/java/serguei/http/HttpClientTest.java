package serguei.http;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;

import org.junit.Test;


public class HttpClientTest {

    @Test
    public void testHttp() throws Exception {
        String hostName = "www.httpvshttps.com";
        InetSocketAddress address = new InetSocketAddress("www.httpvshttps.com", 80);
        HttpClient client = new HttpClient(address);

        HttpResponse response = client.request(HttpClient.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());
        
        String body = client.readResponseBodyAsString();
        System.out.println(body);
        assertTrue(client.getInputStream() instanceof ChunkedInputStream);
        assertTrue(body.length() > 0);
    }

    @Test
    public void testHttps() throws Exception {
        String hostName = "www.httpvshttps.com";
        InetSocketAddress address = new InetSocketAddress("www.httpvshttps.com", 443);
        HttpClient client = new HttpClient(address);
        
        client.startHandshake(hostName);
        HttpResponse response = client.request(HttpClient.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());
        
        String body = client.readResponseBodyAsString();
        assertTrue(client.getInputStream() instanceof ChunkedInputStream);
        assertTrue(body.length() > 0);
    }

}
