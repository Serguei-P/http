package serguei.http;

import static org.junit.Assert.*;

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

    private ExecutorService threadPool = Executors.newCachedThreadPool();

    private AtomicInteger started = new AtomicInteger(0);
    private AtomicInteger stopped = new AtomicInteger(0);

    @Test(timeout = 60000)
    public void shouldStartAndStopServer() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch);
        HttpServer server = new HttpServer(requestHandler, PORT);
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
        public void process(HttpRequest request, InputStream inputStream, OutputStream outputStream) {
            latch.countDown();
            try {
                latch.await();
                started.incrementAndGet();
                HttpResponse response = new HttpResponse("HTTP/1.1 200 OK");
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
            return client.send(HttpClient.getRequest("http://localhost:" + PORT + "/"));
        }
    }

}
