package serguei.http;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.junit.Test;

import serguei.http.utils.Utils;

public class HostnameCheckerUnitTest {

    @Test
    public void shouldVerifyHost() throws CertificateException, IOException {
        HostnameChecker checker = new HostnameChecker();
        InputStream inputStream = getClass().getResourceAsStream("/www-bbc-co-uk.pem");
        X509Certificate certificate = Utils.readPemFile(inputStream);
        
        assertTrue(checker.check("www.bbc.co.uk", certificate));
        assertTrue(checker.check("m.live.bbc.co.uk", certificate));
        assertTrue(checker.check("www.BBC.co.uk", certificate));
        assertTrue(checker.check("m.live.bbc.CO.UK", certificate));
    }

    @Test
    public void shouldNotVerifyHost() throws CertificateException, IOException {
        HostnameChecker checker = new HostnameChecker();
        InputStream inputStream = getClass().getResourceAsStream("/www-bbc-co-uk.pem");
        X509Certificate certificate = Utils.readPemFile(inputStream);

        assertFalse(checker.check("www.google.com", certificate));
        assertFalse(checker.check("test.bbc.co.uk", certificate));
    }

    @Test
    public void shouldVerifyHostWithWidecard() throws CertificateException, IOException {
        HostnameChecker checker = new HostnameChecker();
        InputStream inputStream = getClass().getResourceAsStream("/google-com.pem");
        X509Certificate certificate = Utils.readPemFile(inputStream);

        assertTrue(checker.check("youtubeeducation.com", certificate));
        assertTrue(checker.check("test.youtubeeducation.com", certificate));
    }

    @Test
    public void shouldFailVerifyHostWithWidecard() throws CertificateException, IOException {
        HostnameChecker checker = new HostnameChecker();
        InputStream inputStream = getClass().getResourceAsStream("/google-com.pem");
        X509Certificate certificate = Utils.readPemFile(inputStream);

        assertFalse(checker.check("test.ssl.youtubeeducation.com", certificate));
        assertFalse(checker.check("random.com", certificate));
    }

    @Test
    public void shouldVerifyHostWithIps() throws CertificateException, IOException {
        HostnameChecker checker = new HostnameChecker();
        InputStream inputStream = getClass().getResourceAsStream("/1-1-1-1.pem");
        X509Certificate certificate = Utils.readPemFile(inputStream);

        assertTrue(checker.check("1.1.1.1", certificate));
        assertTrue(checker.check("2606:4700:4700::6400", certificate));
        assertTrue(checker.check("[2606:4700:4700::6400]", certificate));
    }

    @Test
    public void shouldNotVerifyHostWithIps() throws CertificateException, IOException {
        HostnameChecker checker = new HostnameChecker();
        InputStream inputStream = getClass().getResourceAsStream("/1-1-1-1.pem");
        X509Certificate certificate = Utils.readPemFile(inputStream);

        assertFalse(checker.check("1.10.10.10", certificate));
        assertFalse(checker.check("1111:2222:4700::6400", certificate));
        assertFalse(checker.check("[1111:2222:4700::6400]", certificate));
    }

}
