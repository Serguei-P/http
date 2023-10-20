package serguei.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public class ClientSslContext {
    private final SSLContext sslContext;
    private final TrustManagerWrapper trustManagerWrapper;
    private X509Certificate[] tlsCertificates;

    public ClientSslContext(SSLContext sslContext, TrustManagerWrapper trustManagerWrapper) {
        this.sslContext = sslContext;
        this.trustManagerWrapper = trustManagerWrapper;
    }

    SSLSocketFactory getSocketFactory() {
        return sslContext.getSocketFactory();
    }

    X509Certificate[] getTlsCertificates() {
        return tlsCertificates;
    }

    void setTlsCertificates(Certificate[] certificates) {
        X509Certificate[] tlsCertificates = new X509Certificate[certificates.length];
        for (int i = 0; i < certificates.length; i++) {
            tlsCertificates[i] = (X509Certificate) certificates[i];
        }
        this.tlsCertificates = tlsCertificates;
    }
}
