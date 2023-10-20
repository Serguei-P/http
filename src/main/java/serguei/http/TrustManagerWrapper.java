package serguei.http;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

class TrustManagerWrapper implements X509TrustManager {

    private final X509TrustManager trustManager;
    private X509Certificate[] tlsCertificates;

    TrustManagerWrapper(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager != null ? trustManager.getAcceptedIssuers() : null;
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (trustManager != null) {
            trustManager.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        tlsCertificates = chain;
        if (trustManager != null) {
            trustManager.checkClientTrusted(chain, authType);
        }
    }
}
