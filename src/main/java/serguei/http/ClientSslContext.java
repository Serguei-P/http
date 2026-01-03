package serguei.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class ClientSslContext {
    private final SSLContext sslContext;
    private final TrustManagerWrapper trustManagerWrapper;

    public ClientSslContext(SSLContext sslContext, TrustManagerWrapper trustManagerWrapper) {
        this.sslContext = sslContext;
        this.trustManagerWrapper = trustManagerWrapper;
    }

    SSLSocketFactory getSocketFactory() {
        return sslContext.getSocketFactory();
    }
}
