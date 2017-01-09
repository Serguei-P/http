package serguei.http.examples;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import serguei.http.HttpClientConnection;
import serguei.http.HttpRequestHeaders;
import serguei.http.HttpResponse;
import serguei.http.MultipartFormDataRequestBody;
import serguei.http.utils.WaitingByteArrayInputStream;

public class ServerWithPostFormTest {

    private final WaitingByteArrayInputStream inputStream = new WaitingByteArrayInputStream();
    private final ServerWithPostForm server = new ServerWithPostForm(inputStream);

    @Before
    public void setup() {
        Thread thread = new Thread(server);
        thread.start();
        waitUntilRunning(true);
        assertTrue(server.isRunning());
    }

    @After
    public void clearUp() throws Exception {
        if (server.isRunning()) {
            inputStream.push("quit" + System.lineSeparator());
            waitUntilRunning(false);
        }
    }

    @Test
    public void shouldStopServer() throws Exception {

        inputStream.push("quit" + System.lineSeparator());

        waitUntilRunning(false);
        assertFalse(server.isRunning());
    }

    @Test
    public void shouldInputValues() throws Exception {
        String field1 = "This is field1 value";
        String field2 = "This is field2 value";
        String field3 = "This is field3 value";
        String body = "field1=" + field1 + "&field2=" + field2 + "&field3=" + field3;
        HttpClientConnection client = new HttpClientConnection("localhost", server.getPort());
        HttpRequestHeaders headers = HttpRequestHeaders.postRequest("http://localhost:" + server.getPort() + "/input");

        HttpResponse response = client.send(headers, body);

        assertEquals(200, response.getStatusCode());
        String responseBody = response.readBodyAsString();
        assertTrue(responseBody.contains('"' + field1 + '"'));
        assertTrue(responseBody.contains('"' + field2 + '"'));
        assertTrue(responseBody.contains('"' + field3 + '"'));
    }

    @Test
    public void shouldUploadImage() throws Exception {
        String text = "This is the text";
        String boundary = "--------------------------943603c96ae956d6";
        byte[] image = {10, 11, 12, 13, 14, 15};

        MultipartFormDataRequestBody body = new MultipartFormDataRequestBody(boundary);
        body.add(text.getBytes("UTF-8"), "text");
        body.add(image, "image", "image.jpg", "image/jpg");
        HttpClientConnection client = new HttpClientConnection("localhost", server.getPort());
        HttpRequestHeaders headers = HttpRequestHeaders.postRequest("http://localhost:" + server.getPort() + "/upload");
        headers.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

        HttpResponse response = client.send(headers, body.getBody());

        assertEquals(200, response.getStatusCode());
        String responseBody = response.readBodyAsString();
        assertTrue(responseBody.contains('"' + text + '"'));

        response = client.send(HttpRequestHeaders.getRequest("http://localhost:" + server.getPort() + "/image.jpg"));

        assertArrayEquals(image, response.readBodyAsBytes());
    }

    @Test
    public void shouldInputValuesWithSpecialCharacters() throws Exception {
        String field1 = "This is field+1 value";
        String field2 = "This is field++2 value";
        String field3 = "%22This is field3 value%22";
        String body = "field1=" + field1 + "&field2=" + field2 + "&field3=" + field3;
        HttpClientConnection client = new HttpClientConnection("localhost", server.getPort());
        HttpRequestHeaders headers = HttpRequestHeaders.postRequest("http://localhost:" + server.getPort() + "/input");

        HttpResponse response = client.send(headers, body);

        assertEquals(200, response.getStatusCode());
        String responseBody = response.readBodyAsString();
        assertTrue(responseBody.contains("\"This is field 1 value\""));
        assertTrue(responseBody.contains("\"This is field  2 value\""));
        assertTrue(responseBody.contains("\"&quot;This is field3 value&quot;\""));
    }

    private void waitUntilRunning(boolean running) {
        long startTime = System.currentTimeMillis();
        while (server.isRunning() != running || System.currentTimeMillis() - startTime > 2000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

}
