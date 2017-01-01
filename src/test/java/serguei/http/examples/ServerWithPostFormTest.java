package serguei.http.examples;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import serguei.http.HttpClientConnection;
import serguei.http.HttpRequestHeaders;
import serguei.http.HttpResponse;
import serguei.http.MultipartFormDataRequestBody;

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
        assertTrue(response.readBodyAsString().contains('"' + text + '"'));

        response = client.send(HttpRequestHeaders.getRequest("http://localhost:" + server.getPort() + "/image.jpg"));

        assertArrayEquals(image, response.readBodyAsBytes());
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

    private class WaitingByteArrayInputStream extends ByteArrayInputStream {

        public WaitingByteArrayInputStream() {
            super(new byte[0]);
        }

        @Override
        public synchronized int read() {
            while (available() == 0) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
            return super.read();
        }

        @Override
        public synchronized int read(byte[] buffer, int off, int len) {
            while (available() == 0) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
            return super.read(buffer, off, len);
        }

        private synchronized void push(String line) throws IOException {
            byte[] data = line.getBytes("UTF-8");
            byte[] newBuffer = new byte[buf.length + data.length];
            System.arraycopy(buf, 0, newBuffer, 0, buf.length);
            System.arraycopy(data, 0, newBuffer, buf.length, data.length);
            buf = newBuffer;
            count += data.length;
            this.notifyAll();
        }

    }

}
