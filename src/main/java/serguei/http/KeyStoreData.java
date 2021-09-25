package serguei.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

class KeyStoreData {

    private final String serverName;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final KeyStore keyStore;
    private final String certificatePassword;
    private final TrustManager clientAuthTrustManager;
    private SSLSocketFactory sslSocketFactory;

    public KeyStoreData(String serverName, String keyStorePath, String keyStorePassword, String certificatePassword,
            TrustManager clientAuthTrustManager) {
        this.serverName = serverName;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.keyStore = null;
        this.certificatePassword = certificatePassword;
        this.clientAuthTrustManager = clientAuthTrustManager;
    }

    public KeyStoreData(String serverName, KeyStore keyStore, String certificatePassword,
            TrustManager clientAuthTrustManager) {
        this.serverName = serverName;
        this.keyStorePath = null;
        this.keyStorePassword = null;
        this.keyStore = keyStore;
        this.certificatePassword = certificatePassword;
        this.clientAuthTrustManager = clientAuthTrustManager;
    }

    public SSLSocketFactory getSslSocketFactory() throws IOException {
        if (sslSocketFactory == null) {
            synchronized (this) {
                if (sslSocketFactory == null) {
                    try {
                        sslSocketFactory = createSSLSocketFactory();
                    } catch (GeneralSecurityException e) {
                        throw new IOException(e.getMessage(), e);
                    }
                }
            }
        }
        return sslSocketFactory;
    }

    public String getServerName() {
        return serverName;
    }

    private SSLSocketFactory createSSLSocketFactory() throws IOException, GeneralSecurityException {
        KeyStore keyStore;
        if (this.keyStore != null) {
            keyStore = this.keyStore;
        } else {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
        }
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, certificatePassword.toCharArray());
        TrustManager[] trustManagers;
        if (clientAuthTrustManager != null) {
            trustManagers = new TrustManager[1];
            trustManagers[0] = clientAuthTrustManager;
        } else {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
            trustManagerFactory.init(keyStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
        return sslContext.getSocketFactory();
    }
}
