package serguei.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.Test;

public class HttpClientConnectionTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private HttpClientConnection connection;

    @After
    public void clearUp() {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void testHttp() throws Exception {
        String hostName = "http.badssl.com";
        connection = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpRequestHeaders.getRequest("http://" + hostName + "/");
        HttpResponse response = connection.send(request);

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.length() > 0);
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void testHttpWithGzip() throws Exception {
        String hostName = "http.badssl.com";
        connection = new HttpClientConnection(hostName, 80);

        HttpRequestHeaders request = HttpRequestHeaders.getRequest("http://" + hostName + "/");
        request.setHeader("Accept-Encoding", "gzip");
        HttpResponse response = connection.send(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("gzip", response.getHeader("Content-Encoding"));

        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldAllowSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);
        HttpResponse response = connection.send(HttpRequestHeaders.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldConnectToGoogle() throws Exception {
        String hostName = "google.co.uk";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshakeAndValidate(hostName);
        HttpRequestHeaders request = HttpRequestHeaders.getRequest("https://" + hostName + "/");
        HttpResponse response = connection.send(request);

        assertEquals(301, response.getStatusCode());

        X509Certificate[] certificates = connection.getTlsCertificates();
        for (X509Certificate cert : certificates) {
            System.out.println(cert.getSubjectX500Principal());
        }
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldFailOnSelfSignedCertificate() throws Exception {
        String hostName = "self-signed.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshakeAndValidate(hostName);
    }

    @Test
    public void shouldAllowWrongHostnameCertificate() throws Exception {
        String hostName = "wrong.host.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);
        HttpResponse response = connection.send(HttpRequestHeaders.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldFailOnWrongHostnameCertificate() throws Exception {
        String hostName = "wrong.host.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        try {
            connection.startHandshakeAndValidate(hostName);
            fail("Exception expected");
        } catch (SSLException e) {
            // It seems that Java now validates the name on the certificate and the error message is different
//            assertEquals("Hostname wrong.host.badssl.com does not match certificate", e.getMessage());
        }
    }

    @Test
    public void shouldAllowWrongUntrustedRootCertificate() throws Exception {
        String hostName = "untrusted-root.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);
        HttpResponse response = connection.send(HttpRequestHeaders.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();

        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldFailOnUntrustedRootCertificate() throws Exception {
        String hostName = "untrusted-root.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshakeAndValidate(hostName);
    }

    @Test
    public void shouldAllowExpiredCertificate() throws Exception {
        String hostName = "expired.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);
        HttpResponse response = connection.send(HttpRequestHeaders.getRequest("http://" + hostName + "/"));

        assertEquals(200, response.getStatusCode());

        String body = response.readBodyAsString();
        assertTrue(body.toUpperCase().contains("</HTML>"));
    }

    @Test
    public void shouldFailOnExpiredCertificate() throws Exception {
        String hostName = "expired.badssl.com";
        connection = new HttpClientConnection(hostName, 443);

        try {
            connection.startHandshakeAndValidate(hostName);
            fail("Exception expected");
        } catch (SSLHandshakeException e) {
            // expected
        }
    }

    @Test
    public void shouldCloseUnusedConnection() {
        connection = new HttpClientConnection("www.cisco.com", 80);
        connection.close();
    }

    @Test
    public void shouldConnectToOpera() throws IOException {
        String hostName = "opera.com";
        connection = new HttpClientConnection(hostName, 443);

        connection.startHandshake(hostName);

        HttpRequestHeaders requestHeaders = new HttpRequestHeaders("GET / HTTP/1.1", "Host: " + hostName,
                "User-Agent: curl/7.51.0", "Accept: */*");

        HttpResponse response = connection.send(requestHeaders);

        assertEquals(301, response.getStatusCode());
    }

    @Test
    public void shouldTimeoutOnConnection() throws Exception {
        String hostName = "127.0.0.2"; // nothing is there
        connection = new HttpClientConnection(hostName, 80);
        connection.setConnectTimeoutMillis(1000);

        long start = System.currentTimeMillis();
        try {
            connection.connect();
            fail("Should not connect");
        } catch (IOException e) {
            // expected
        }
        long end = System.currentTimeMillis();

        assertTrue("Time taken is " + (end - start) + " which is too long", end - start < 1200);
    }

    @Test
    public void shouldTimeoutOnConnectionWhenSocketTimeoutSet() throws Exception {
        String hostName = "127.0.0.2"; // nothing is there
        connection = new HttpClientConnection(hostName, 80);
        connection.setTimeoutMillis(1000);

        long start = System.currentTimeMillis();
        try {
            connection.connect();
            fail("Should not connect");
        } catch (IOException e) {
            // expected
        }
        long end = System.currentTimeMillis();

        assertTrue("Time taken is " + (end - start) + " which is too long", end - start < 1200);
    }

    @Test
    public void multiThreadingTls() throws InterruptedException, ExecutionException, TimeoutException {
        String[] tcgmsList = { "CN=secure.tcgms.net", "CN=R11, O=Let's Encrypt, C=US" };
        String[] googleList = { "CN=*.google.com", "CN=WR2, O=Google Trust Services, C=US",
                "CN=GTS Root R1, O=Google Trust Services LLC, C=US" };
        List<Future<Boolean>> futureList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<Boolean> future = executor.submit(() -> makeConnection("tcgms.net", Arrays.asList(tcgmsList)));
            futureList.add(future);
            future = executor.submit(() -> makeConnection("google.com", Arrays.asList(googleList)));
            futureList.add(future);
        }
        for (Future<Boolean> future : futureList) {
            boolean result = future.get(5000, TimeUnit.MILLISECONDS);
            assertTrue(result);
        }
    }

    private boolean makeConnection(String hostName, List<String> expected) {
        try (HttpClientConnection connection = new HttpClientConnection(hostName, 443)) {
            connection.startHandshake(hostName);
            HttpRequestHeaders request = HttpRequestHeaders.getRequest("https://" + hostName + "/");
            HttpResponse response = connection.send(request);
            assertEquals(301, response.getStatusCode());
            List<String> actual = new ArrayList<>();
            X509Certificate[] certificates = connection.getTlsCertificates();
            for (X509Certificate cert : certificates) {
                actual.add(cert.getSubjectX500Principal().getName());
            }
            assertEquals(removeWhitespace(expected), removeWhitespace(actual));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<String> removeWhitespace(List<String> data) {
        return data.stream().map(value -> removeWhitespace(value)).collect(Collectors.toList());
    }

    private String removeWhitespace(String line) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (!Character.isWhitespace(ch)) {
                result.append(ch);
            }
        }
        return result.toString();
    }
}
