package serguei.http;

import static org.junit.Assert.*;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.X509TrustManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientAndServerSslTest {

    private static final String HOST = "localhost";
    private static final String BODY_CHARSET = "UTF-8";
    private static final String PATH = "/test/file.txt";
    private static final String REQUEST_LINE = "POST " + PATH + " HTTP/1.1";

    private String requestBody = makeBody("client");
    private String responseBody = makeBody("server");

    private TestServer server;
    private HttpClientConnection clientConnection;

    @Before
    public void setup() throws Exception {
        server = new TestServer();
        clientConnection = new HttpClientConnection(HOST, server.getSslPort());
    }

    @After
    public void clear() {
        clientConnection.close();
        server.stop();
        HttpClientConnection.clearSslContexts();
    }

    @Test
    public void shouldSendAndReceiveFromServer() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshake();
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals("http://localhost" + PATH, server.getLatestRequestHeaders().getUrl().toString());
        assertTrue(server.getLatestConnectionContext().isSsl());
        assertNotNull(server.getLatestConnectionContext().getNegotiatedTlsProtocol());
        assertTrue(server.getLatestConnectionContext().getNegotiatedCipher().length() > 0);
        assertEquals(requestBody, server.getLatestRequestBodyAsString());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getHeader("Content-Encoding"));
        assertEquals(responseBody.getBytes(BODY_CHARSET).length, response.getContentLength());
        assertFalse(response.isContentChunked());
        assertEquals(responseBody, response.readBodyAsString());
    }

    @Test
    public void shouldSendAndReceiveFromServerWhichOnlySupportsTls10() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        server.setTlsProtocol(TlsVersion.TLSv10);
        clientConnection.setTlsProtocol(TlsVersion.TLSv10, TlsVersion.TLSv11, TlsVersion.TLSv12);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshake();
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertEquals(TlsVersion.TLSv10, clientConnection.getNegotiatedTlsProtocol());
    }

    @Test
    public void shouldSendSniToServer() throws Exception {
        String sni = "www.fitltd.com";
        server.setResponse(HttpResponseHeaders.ok(), new byte[0]);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshake(sni);
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertEquals(sni, server.getLatestConnectionContext().getSni());
    }

    @Test
    public void shouldSendNoSniToServerWhenNotSpecified() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), new byte[0]);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshake();
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertEquals("", server.getLatestConnectionContext().getSni());
    }

    @Test(expected = SSLProtocolException.class)
    public void shouldFailWhenServerIsSetToBreakOnSni() throws Exception {
        // Java, unlike OpenSSL, fails when the unrecognized_name warning received
        String sni = "www.fitltd.com";
        server.shouldWarnWhenSniNotMatching(true);

        clientConnection.startHandshake(sni);
    }

    @Test
    public void shouldNotFailWhenServerIsSetToBreakOnSniAndNoSni() throws Exception {
        server.shouldWarnWhenSniNotMatching(true);

        clientConnection.startHandshake("");
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldFailWhenServerRequiresSniAndAbsent() throws Exception {
        server.shouldFailWhenNoSni(true);

        clientConnection.startHandshake("");
    }

    @Test
    public void shouldNotFailWhenServerRequiresSniAndPresent() throws Exception {
        String sni = "www.fitltd.com";
        server.shouldFailWhenNoSni(true);

        clientConnection.startHandshake(sni);
    }

    @Test
    public void shouldRequestClientAuthentication() throws Exception {
        String sni = "www.fitltd.com";
        server.setNeedClientAuthentication(true);
        server.setResponse(HttpResponseHeaders.ok(), new byte[0]);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshakeWithClientAuth(sni, keyStorePath(), "password", "test01");
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertEquals(sni, server.getLatestConnectionContext().getSni());
        assertEquals(1, server.getLatestConnectionContext().getTlsCertificates().length);
    }

    @Test
    public void shouldFailWhenClientDidNotProvideAuthenticationWhenRequested() throws Exception {
        String sni = "www.fitltd.com";
        server.setNeedClientAuthentication(true);
        server.setResponse(HttpResponseHeaders.ok(), new byte[0]);

        try {
            clientConnection.startHandshake(sni);
            fail("Exception expected");
        } catch (SSLHandshakeException e) {
            assertTrue(e.getMessage().contains("bad_certificate"));
        }
    }

    @Test
    public void shouldRequestClientAuthenticationWithUserDefinedTrustManager() throws Exception {
        String sni = "www.fitltd.com";
        TestTrustManager trustManager = new TestTrustManager();
        server.stop();
        server = new TestServer(trustManager);
        server.setNeedClientAuthentication(true);
        server.setResponse(HttpResponseHeaders.ok(), new byte[0]);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshakeWithClientAuth(sni, keyStorePath(), "password", "test01");
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertEquals(sni, server.getLatestConnectionContext().getSni());
        assertEquals(1, server.getLatestConnectionContext().getTlsCertificates().length);
        assertNotNull(trustManager.clientCertificates);
        assertNull(trustManager.serverCertificates);
    }

    @Test
    public void shouldReturnSessionId() throws Exception {
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.startHandshake();
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertTrue(clientConnection.getTlsSessionId().length > 0);
        assertTrue(server.getLatestConnectionContext().isSsl());
        assertNotNull(server.getLatestConnectionContext().getNegotiatedTlsProtocol());
        assertTrue(server.getLatestConnectionContext().getNegotiatedCipher().length() > 0);
        assertTrue(server.getLatestConnectionContext().getTlsSessionId().length > 0);
    }

    @Test
    public void shouldReturnSessionIdFromClientHello() throws Exception {
        String sniName = "www.mimecast.com";
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.close();
        clientConnection = new HttpClientConnection("127.0.0.1", server.getSslPort());

        clientConnection.startHandshake(sniName);
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertTrue(clientConnection.getTlsSessionId().length > 0);
        assertTrue(server.getLatestConnectionContext().isSsl());
        assertNotNull(server.getLatestConnectionContext().getNegotiatedTlsProtocol());
        assertTrue(server.getLatestConnectionContext().getNegotiatedCipher().length() > 0);
        assertTrue(server.getLatestConnectionContext().getTlsSessionId().length > 0);
        assertFalse(server.getLatestConnectionContext().getRequestedTlsSessionId().length > 0);
        byte[] tlsSessionId = server.getLatestConnectionContext().getTlsSessionId();

        clientConnection.close();
        clientConnection = new HttpClientConnection("127.0.0.1", server.getSslPort());

        clientConnection.startHandshake(sniName);
        response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertTrue(clientConnection.getTlsSessionId().length > 0);
        assertTrue(server.getLatestConnectionContext().isSsl());
        assertNotNull(server.getLatestConnectionContext().getNegotiatedTlsProtocol());
        assertTrue(server.getLatestConnectionContext().getNegotiatedCipher().length() > 0);
        assertTrue(server.getLatestConnectionContext().getTlsSessionId().length > 0);
        assertTrue(server.getLatestConnectionContext().getRequestedTlsSessionId().length > 0);
        assertArrayEquals(tlsSessionId, server.getLatestConnectionContext().getRequestedTlsSessionId());
        assertArrayEquals(tlsSessionId, server.getLatestConnectionContext().getTlsSessionId());
    }

    @Test
    public void shouldClearSslContext() throws Exception {
        String sniName = "www.mimecast.com";
        server.setResponse(HttpResponseHeaders.ok(), responseBody.getBytes(BODY_CHARSET), BodyCompression.NONE);
        HttpRequestHeaders headers = new HttpRequestHeaders(REQUEST_LINE, "Host: localhost");

        clientConnection.close();
        clientConnection = new HttpClientConnection("127.0.0.1", server.getSslPort());

        clientConnection.startHandshake(sniName);
        HttpResponse response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertTrue(server.getLatestConnectionContext().getTlsSessionId().length > 0);
        assertFalse(server.getLatestConnectionContext().getRequestedTlsSessionId().length > 0);

        clientConnection.close();
        HttpClientConnection.clearSslContexts();
        clientConnection = new HttpClientConnection("127.0.0.1", server.getSslPort());

        clientConnection.startHandshake(sniName);
        response = clientConnection.send(headers, requestBody);

        assertEquals(200, response.getStatusCode());
        assertTrue(server.getLatestConnectionContext().getTlsSessionId().length > 0);
        // no sessionId passed with ClientHello as the context was cleaned
        assertFalse(server.getLatestConnectionContext().getRequestedTlsSessionId().length > 0);
    }

    private static String makeBody(String msg) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            builder.append("This is line " + i + " for " + msg);
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String keyStorePath() {
        return TestServer.class.getResource("/test.jks").getFile();
    }

    private class TestTrustManager implements X509TrustManager {

        private X509Certificate[] clientCertificates;
        private X509Certificate[] serverCertificates;

        @Override
        public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
            this.clientCertificates = certificates;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
            this.serverCertificates = certificates;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }
}
