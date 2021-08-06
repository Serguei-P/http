package serguei.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import serguei.http.utils.Utils;

public class HttpServerTest {

    private static final int PORT = 8080;
    private static final int SSL_PORT = 8443;
    private static final String REQUEST_BODY = "This is a body of the request";

    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final AtomicInteger started = new AtomicInteger(0);
    private final AtomicInteger stopped = new AtomicInteger(0);
    private HttpServer server;

    @After
    public void clearUp() {
        if (server != null) {
            server.stopNow();
        }
    }

    @Test(timeout = 60000)
    public void shouldStartAndStopServer() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch, 1000);
        server = new HttpServer(requestHandler, PORT);
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
    public void shouldStartAndAbruptlyStopServer() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch, 1000);
        server = new HttpServer(requestHandler, PORT);
        @SuppressWarnings("unchecked")
        Future<HttpResponse>[] responses = new Future[requestNumber];

        server.start();

        for (int i = 0; i < requestNumber; i++) {
            responses[i] = threadPool.submit(new RequestProcess());
        }
        latch.await();

        server.stopNow();

        assertEquals(requestNumber, started.get());
        assertEquals(0, stopped.get()); // there was IOExeption while waiting in Thread.sleep()
        for (int i = 0; i < requestNumber; i++) {
            assertEquals(200, responses[i].get().getStatusCode());
        }
    }

    @Test(timeout = 60000)
    public void shouldStartAndStopServerWithSsl() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch, 1000);
        server = new HttpServer(requestHandler, PORT, SSL_PORT, keyStorePath(), "password", "test01");
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

    @Test(timeout = 60000)
    public void shouldStartAndStopServerWithSslAndKeystorePassed() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        RequestHandler requestHandler = new RequestHandler(latch, 1000);
        KeyStore keyStore = KeyStore.getInstance("JKS");
        InputStream inputStream = this.getClass().getResourceAsStream("/test.jks");
        keyStore.load(inputStream, "password".toCharArray());
        server = new HttpServer(requestHandler, PORT, SSL_PORT, keyStore, "test01");
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

    @Test(timeout = 60000)
    public void shouldCloseConnection() throws Exception {
        HttpServerRequestHandler requestHandler = new ClosingConnectionRequestHandler();
        server = new HttpServer(requestHandler, PORT);
        server.start();
        try (HttpClientConnection connection = new HttpClientConnection("localhost", PORT)) {
            HttpResponse response = connection.send(HttpRequestHeaders.getRequest("http://localhost:" + PORT + "/"));
            assertEquals(200, response.getStatusCode());

            try {
                response = connection.send(HttpRequestHeaders.getRequest("http://localhost:" + PORT + "/"));
                fail("Connection was not closed");
            } catch (IOException e) {
                // should close connection coursing the exception
            }
        }
    }

    @Test(timeout = 60000)
    public void shouldCloseAllConnections() throws Exception {
        int requestNumber = 2;
        CountDownLatch latch = new CountDownLatch(requestNumber);
        server = new HttpServer(new RequestHandler(latch, 0), PORT);
        server.start();

        for (int i = 0; i < requestNumber; i++) {
            threadPool.execute(new SendDataAndWaitProcess());
        }
        latch.await();

        assertEquals(requestNumber, server.getConnectionNo());
        server.closeAllConnection();

        long start = System.currentTimeMillis();
        while (server.getConnectionNo() > 0 && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(100);
        }
        assertEquals(0, server.getConnectionNo());
    }

    @Test(timeout = 60000)
    public void shouldThrottleRead() throws Exception {
        SimpleRequestHandler requestHandler = new SimpleRequestHandler();
        server = new HttpServer(requestHandler, PORT);
        server.setThrottlingDelay(300);
        HttpClientConnection client = new HttpClientConnection("localhost", PORT);
        try {
            server.start();
            byte[] body = Utils.buildDataArray(10000);
            byte[] headers = ("POST / HTTP/1.1\r\nHost: www.fitltd.com\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes("ASCII");

            long start = System.currentTimeMillis();
            HttpResponse response = client.send(Utils.concat(headers, body));
            long end = System.currentTimeMillis();

            assertEquals(200, response.getStatusCode());
            assertArrayEquals(body, requestHandler.getLatestRequestBody());
            assertTrue("Time was " + (end - start) + " which is shorter then expected 5 secs", end - start > 3000);
        } finally {
            client.close();
        }
    }

    @Test(timeout = 60000)
    public void shouldStartServerWhenSocketIsSlowToFree() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HttpServer server1 = new HttpServer(new SimpleRequestHandler(), PORT);
        HttpServer server2 = new HttpServer(new SimpleRequestHandler(), PORT);
        ServerStartRunnable runnable = new ServerStartRunnable(server2, latch);
        try {
            server1.start();
            assertCanMakeSimpleRequest();
            threadPool.execute(runnable);
            latch.await();
            Thread.sleep(10);
            server1.stop();
            while (!runnable.finished) {
                Thread.sleep(100);
            }
            assertCanMakeSimpleRequest();
        } finally {
            server1.stop();
            server2.stop();
        }
        assertTrue(runnable.success);
    }

    @Test(timeout = 60000)
    public void shouldCallOnConnect() throws Exception {
        TestOnConnectProcess onConnectProcess = new TestOnConnectProcess(true);
        SimpleRequestHandler requestHandler = new SimpleRequestHandler();
        server = new HttpServer(requestHandler, PORT);
        server.setOnConnectHandler(onConnectProcess);
        HttpClientConnection client = new HttpClientConnection("localhost", PORT);
        try {
            server.start();
            byte[] body = Utils.buildDataArray(10000);
            byte[] headers = ("POST / HTTP/1.1\r\nHost: www.fitltd.com\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes("ASCII");

            HttpResponse response = client.send(Utils.concat(headers, body));

            assertEquals(200, response.getStatusCode());
            assertArrayEquals(body, requestHandler.getLatestRequestBody());
            assertEquals(1, onConnectProcess.callCount.get());
        } finally {
            client.close();
        }
    }

    @Test(timeout = 60000)
    public void shouldCallOnConnectThatDisconnects() throws Exception {
        TestOnConnectProcess onConnectProcess = new TestOnConnectProcess(false);
        SimpleRequestHandler requestHandler = new SimpleRequestHandler();
        server = new HttpServer(requestHandler, PORT);
        server.setOnConnectHandler(onConnectProcess);
        try (HttpClientConnection client = new HttpClientConnection("localhost", PORT)) {
            server.start();
            byte[] body = Utils.buildDataArray(10000);
            byte[] headers = ("POST / HTTP/1.1\r\nHost: www.fitltd.com\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes("ASCII");

            client.send(Utils.concat(headers, body));

            fail("Expected exception");
        } catch (IOException e) {
            assertEquals(1, onConnectProcess.callCount.get());
        }
    }

    @Test(timeout = 60000)
    public void shouldCallOnConnectWithSsl() throws Exception {
        String sni = "www.test.com";
        TestOnConnectProcess onConnectProcess = new TestOnConnectProcess(true);
        SimpleRequestHandler requestHandler = new SimpleRequestHandler();
        server = new HttpServer(requestHandler, PORT, SSL_PORT, keyStorePath(), "password", "test01");
        server.setOnConnectHandler(onConnectProcess);
        server.start();
        try (HttpClientConnection client = new HttpClientConnection("localhost", SSL_PORT)) {
            byte[] body = Utils.buildDataArray(10000);
            byte[] headers = ("POST / HTTP/1.1\r\nHost: www.fitltd.com\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes("ASCII");

            client.startHandshake(sni);
            HttpResponse response = client.send(Utils.concat(headers, body));

            assertEquals(200, response.getStatusCode());
            assertArrayEquals(body, requestHandler.getLatestRequestBody());
            assertEquals(1, onConnectProcess.callCount.get());
            assertEquals(sni, onConnectProcess.latestClientHello.getSniHostName());
        }
    }

    @Test(timeout = 60000)
    public void shouldSetShortTimeout() throws Exception {
        HttpServerRequestHandler requestHandler = new SimpleRequestHandler();
        ServerOptions options = new ServerOptions();
        options.setTimeoutMs(200);
        options.setTimeoutBetweenRequestsMs(2000);
        options.setPort(PORT);
        server = new HttpServer(requestHandler, options);
        server.start();

        try (HttpClientConnection client = new HttpClientConnection("localhost", PORT)) {
            byte[] body = Utils.buildDataArray(10000);
            byte[] headers = ("POST / HTTP/1.1\r\nHost: www.fitltd.com\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes("ASCII");
            try {
                client.connect();
                Thread.sleep(300);
                client.send(Utils.concat(headers, body));
                fail("Exception expected");
            } catch (IOException e) {
                // expected due to connection timeout on server side
            }
        }
    }

    private class RequestHandler implements HttpServerRequestHandler {

        private final CountDownLatch latch;
        private final int timeout;

        public RequestHandler(CountDownLatch latch, int timeout) {
            this.latch = latch;
            this.timeout = timeout;
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
                Thread.sleep(timeout); // we want to test if the server waits for all processes to finish
                stopped.incrementAndGet();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private class ClosingConnectionRequestHandler implements HttpServerRequestHandler {

        @Override
        public void process(ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream)
                throws IOException {
            HttpResponseHeaders.ok().write(outputStream);
            connectionContext.closeConnection();
        }
    }

    private class SimpleRequestHandler implements HttpServerRequestHandler {

        private byte[] requestBody;

        @Override
        public void process(ConnectionContext connectionContext, HttpRequest request, OutputStream outputStream)
                throws IOException {
            requestBody = request.readBodyAsBytes();
            HttpResponseHeaders.ok().write(outputStream);
        }

        public byte[] getLatestRequestBody() {
            return requestBody;
        }
    }

    private class SendDataAndWaitProcess implements Runnable {

        @Override
        public void run() {
            try (Socket socket = new Socket()) {
                socket.setReuseAddress(true);
                socket.setSoLinger(false, 1);
                socket.setSoTimeout(0);
                socket.connect(new InetSocketAddress("localhost", PORT));
                byte[] data = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1000\r\n\r\n".getBytes("ASCII");
                socket.getOutputStream().write(data);
                socket.getOutputStream().flush();
                socket.getInputStream().read(); // this will read indefinitely as the server awaits the body
            } catch (IOException e) {
                System.out.println(e.getMessage());
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
                return connection.send(HttpRequestHeaders.postRequest("http://localhost:" + SSL_PORT + "/"),
                        REQUEST_BODY);
            } catch (IOException e) {
                // we don't want to miss stack trace when things go wrong
                e.printStackTrace();
                return null;
            }
        }
    }

    private class ServerStartRunnable implements Runnable {

        private final HttpServer server;
        private final CountDownLatch latch;
        private volatile boolean success;
        private volatile boolean finished = false;

        public ServerStartRunnable(HttpServer server, CountDownLatch latch) {
            this.server = server;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                latch.countDown();
                server.start(3, 100);
                success = true;
                System.out.println("done");
            } catch (IOException e) {
                e.printStackTrace();
                success = false;
            }
            finished = true;
        }
    }

    private String keyStorePath() {
        return getClass().getResource("/test.jks").getFile();
    }

    private void assertCanMakeSimpleRequest() throws IOException {
        try (HttpClientConnection connection = new HttpClientConnection("localhost", PORT)) {
            HttpResponse response = connection.sendRequest("GET / HTTP/1.1", "Host: localhost:" + PORT);
            assertEquals(200, response.getStatusCode());
        }
    }

    private static class TestOnConnectProcess implements HttpServerOnConnectProcess {

        private final boolean resultToReturn;
        private AtomicInteger callCount = new AtomicInteger(0);
        private ClientHello latestClientHello;

        public TestOnConnectProcess(boolean resultToReturn) {
            this.resultToReturn = resultToReturn;
        }

        @Override
        public boolean process(Socket socket, ClientHello clientHello) {
            this.latestClientHello = clientHello;
            callCount.incrementAndGet();
            return resultToReturn;
        }
    }
}
