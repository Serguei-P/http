package serguei.http;

import static org.junit.Assert.*;

import java.io.IOException;
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
    private static final int SSL_PORT = 8443;
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

    @Test(timeout = 60000)
    public void shouldStartAndStopServerWithSsl() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch);
        HttpServer server = new HttpServer(requestHandler, PORT, SSL_PORT, keyStorePath(), "password", "test01");
        @SuppressWarnings("unchecked")
        Future<HttpResponse>[] responses = new Future[requestNumber];

        server.start();

        for (int i = 0; i < requestNumber; i++) {
            responses[i] = threadPool.submit(new SslRequestProcess());
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
        public void process(ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream) {
            latch.countDown();
            try {
                latch.await();
                started.incrementAndGet();
                String body = request.readBodyAsString();
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
            try (HttpClientConnection connection = new HttpClientConnection("localhost", PORT)) {
                return connection.send(HttpRequestHeaders.postRequest("http://localhost:" + PORT + "/"), REQUEST_BODY);
            } catch (IOException e) {
                // we don't want to miss stack trace when things go wrong
                e.printStackTrace();
                return null;
            }
        }
    }

    private class SslRequestProcess implements Callable<HttpResponse> {

        @Override
        public HttpResponse call() {
            try (HttpClientConnection connection = new HttpClientConnection("localhost", SSL_PORT)) {
                connection.startHandshake();
                return connection.send(HttpRequestHeaders.postRequest("http://localhost:" + SSL_PORT + "/"), REQUEST_BODY);
            } catch (IOException e) {
                // we don't want to miss stack trace when things go wrong
                e.printStackTrace();
                return null;
            }
        }
    }

    private String keyStorePath() {
        return getClass().getResource("/test.jks").getFile();
    }

}
