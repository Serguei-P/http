package serguei.http;

import java.net.InetSocketAddress;

import org.junit.Test;


public class HttpClientTest {

    @Test
    public void testHttp() throws Exception {
        String hostName = "www.httpvshttps.com";
        InetSocketAddress address = new InetSocketAddress("www.httpvshttps.com", 80);
        HttpClient client = new HttpClient(address);

        HttpResponse response = client.request(HttpClient.getRequest("http://" + hostName + "/"));

        System.out.println(response);
    }

    @Test
    public void testHttps() throws Exception {
        String hostName = "www.httpvshttps.com";
        InetSocketAddress address = new InetSocketAddress("www.httpvshttps.com", 443);
        HttpClient client = new HttpClient(address);
        
        client.startHandshake(hostName);
        HttpResponse response = client.request(HttpClient.getRequest("http://" + hostName + "/"));

        System.out.println(response);
    }

}
