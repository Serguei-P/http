package serguei.http.examples;

import java.io.IOException;

import serguei.http.HttpClientConnection;
import serguei.http.HttpRequestHeaders;
import serguei.http.HttpResponse;

public class TempTest {

    public void run() throws IOException {
        HttpClientConnection connection = new HttpClientConnection("www.nature.com", 80);
        HttpRequestHeaders headers = HttpRequestHeaders.getRequest("http://www.nature.com/nplants/");

        HttpResponse response = connection.send(headers);
        System.out.println(response);
        response.readBodyAsString();
        System.out.println("  ");

        headers = new HttpRequestHeaders("PURGE / HTTP/1.1", "Host: www.nature.com");

        response = connection.send(headers);
        System.out.println(response);
        response.readBodyAsString();
    }

    public static void main(String[] args) {
        TempTest test = new TempTest();
        try {
            test.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
