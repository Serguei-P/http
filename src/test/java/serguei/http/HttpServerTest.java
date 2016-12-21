package serguei.http;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class HttpServerTest {

    private static final int PORT = 8080;
    private static final String REQUEST_BODY = "This is a body of the request";

    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final AtomicInteger started = new AtomicInteger(0);
    private final AtomicInteger stopped = new AtomicInteger(0);

    @Test(timeout = 60000)
    public void shouldStartAndStopServer() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch);
        HttpServer server = new HttpServer(requestHandler, PORT);
        @SuppressWarnings("unchecked")
        Future<HttpResponse>[] responses = new Future[requestNumber];

        server.start();

        for (int i = 0; i < requestNumber; i++) {
            responses[i] = threadPool.submit(new RequestProcess());
        }
        latch.await();

        server.stop();

        assertEquals(requestNumber, started.get());
        assertEquals(requestNumber, stopped.get());
        for (int i = 0; i < requestNumber; i++) {
            assertEquals(200, responses[i].get().getStatusCode());
        }
    }

    private class RequestHandler implements HttpServerRequestHandler {

        private final CountDownLatch latch;

        public RequestHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void process(HttpRequestHeaders request, InputStream inputStream, OutputStream outputStream) {
            latch.countDown();
            try {
                latch.await();
                started.incrementAndGet();
                int bodyLen = Integer.parseInt(request.getHeader("Content-Length"));
                String body = readToString(inputStream, bodyLen);
                HttpResponseHeaders response;
                if (body.equals(REQUEST_BODY)) {
                    response = HttpResponseHeaders.ok();
                } else {
                    response = HttpResponseHeaders.serverError();
                }
                response.write(outputStream);
                outputStream.flush();
                Thread.sleep(1000); // we want to test if the server waits for all processes to finish
                stopped.incrementAndGet();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class RequestProcess implements Callable<HttpResponse> {

        @Override
        public HttpResponse call() throws Exception {
            HttpClient client = new HttpClient("localhost", PORT);
            return client.send(HttpClient.getRequest("http://localhost:" + PORT + "/"), REQUEST_BODY);
        }
    }

    private String readToString(InputStream inputStream, int len) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while (result.size() < len && (length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

}
