package serguei.http;

import static org.junit.Assert.*;

import org.junit.Test;

public class HttpClientTest {

    @Test
    public void testHttp() throws Exception {
        String hostName = "www.httpvshttps.com";
        HttpClient client = new HttpClient(hostName);

        HttpResponse response = client.request(HttpClient.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());
        
        String body = client.readResponseBodyAsString();
        System.out.println(body);
        assertTrue(client.getResponseInputStream() instanceof ChunkedInputStream);
        assertTrue(body.length() > 0);
    }

    @Test
    public void testHttps() throws Exception {
        String hostName = "www.httpvshttps.com";
        HttpClient client = new HttpClient(hostName, 443);
        
        client.startHandshake(hostName);
        HttpResponse response = client.request(HttpClient.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());
        
        String body = client.readResponseBodyAsString();
        assertTrue(client.getResponseInputStream() instanceof ChunkedInputStream);
        assertTrue(body.length() > 0);
    }

}
