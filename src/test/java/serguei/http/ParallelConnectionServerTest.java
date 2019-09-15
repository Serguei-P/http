package serguei.http;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Test;

public class ParallelConnectionServerTest {

    private static final byte[] REQUEST_BODY = "This is a request body".getBytes();
    private static final String RESPONSE_BODY = "This is a response body";
    private static final byte[] RESPONSE_BODY_BYTES = RESPONSE_BODY.getBytes();
    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    private ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;

    @After
    public void clearUp() {
        executor.shutdown();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void parallelRequestTest() throws Exception {
        server = new HttpServer(new RequestHandler(), PORT);
        server.start(3, 500, 100);
        int count = 200;
        List<Future<Boolean>> futures = new ArrayList<>();
        executor = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            Future<Boolean> future = executor.submit(new Requester(latch));
            futures.add(future);
        }

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get().booleanValue()) {
                successCount++;
            }
        }
        assertEquals(count, successCount);
    }

    private class Requester implements Callable<Boolean> {

        private final CountDownLatch latch;

        public Requester(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public Boolean call() throws Exception {
            HttpRequestHeaders headers = new HttpRequestHeaders("POST /test HTTP/1.1", "Host: localhost");
            try (HttpClientConnection client = new HttpClientConnection(HOST, PORT)) {
                latch.countDown();
                latch.await();
                HttpResponse response = client.send(headers, REQUEST_BODY);
                if (response.getStatusCode() == 200) {
                    if (response.readBodyAsString().equals(RESPONSE_BODY)) {
                        return Boolean.TRUE;
                    } else {
                        return Boolean.FALSE;
                    }
                } else {
                    return Boolean.FALSE;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Boolean.FALSE;
            }
        }

    }
    
    private class RequestHandler implements HttpServerRequestHandler {

        @Override
        public void process(ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream)
                throws IOException {
            request.readBodyAsBytes();
            HttpResponseHeaders response = HttpResponseHeaders.ok();
            response.addHeader("Content-Length", Integer.toString(RESPONSE_BODY_BYTES.length));
            response.write(outputStream);
            outputStream.write(RESPONSE_BODY_BYTES);
            outputStream.flush();
        }
        
    }

}
