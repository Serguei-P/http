package serguei.http;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class ClientSslContextFactory {

    private static ClientSslContext noHostValidatingContext;
    private static ClientSslContext hostValidatingContext;

    /**
     * This creates Client SSL Context with a custom truststore to validate certificates
     * @param trustStorePath - path to the trust store file
     * @param trustStorePassword - password for the trust store
     */
    public static ClientSslContext createSslContextWithCustomTruststore(String trustStorePath, String trustStorePassword) {
        return createSslContext(true, null, null, null, trustStorePath, trustStorePassword);
    }

    /**
     * This creates Client SSL Context to
     * @param keyStorePath - path to the keystore file containing private key
     * @param keyStorePassword - password for the keystore
     * @param certificatePassword - key password
     */
    public static ClientSslContext createClientAuthenticatingSslContext(String keyStorePath, String keyStorePassword,
            String certificatePassword) {
        return createClientAuthenticatingSslContext(true, keyStorePath, keyStorePassword, certificatePassword);
    }

    static ClientSslContext createClientAuthenticatingSslContext(boolean validateCertificates, String keyStorePath,
            String keyStorePassword, String certificatePassword) {
        return createSslContext(validateCertificates, keyStorePath, keyStorePassword, certificatePassword, null, null);
    }

    static void clearSslContexts() {
        noHostValidatingContext = null;
        hostValidatingContext = null;
    }

    static ClientSslContext getNoHostValidatingContext() {
        ClientSslContext result = noHostValidatingContext;
        if (result == null) {
            synchronized (HttpClientConnection.class) {
                if (noHostValidatingContext == null) {
                    noHostValidatingContext = createSslContext(false, null, null, null, null, null);
                }
                return noHostValidatingContext;
            }
        } else {
            return result;
        }
    }

    static ClientSslContext getHostValidatingContext() {
        ClientSslContext result = hostValidatingContext;
        if (result == null) {
            synchronized (HttpClientConnection.class) {
                if (hostValidatingContext == null) {
                    hostValidatingContext = createSslContext(true, null, null, null, null, null);
                }
                return hostValidatingContext;
            }
        } else {
            return result;
        }
    }

    private static ClientSslContext createSslContext(boolean validateCertificates, String keyStorePath,
            String keyStorePassword, String certificatePassword, String trustStorePath, String trustStorePassword) {
        SSLContext sslContext;
        try {
            KeyManager[] keyManagers;
            if (keyStorePath != null) {
                keyManagers = createKeyManagers(keyStorePath, keyStorePassword, certificatePassword);
            } else {
                keyManagers = null;
            }
            X509TrustManager tm = validateCertificates ? createTrustManager(trustStorePath, trustStorePassword) : null;
            TrustManagerWrapper trustManager = new TrustManagerWrapper(tm);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{trustManager}, null);
            return new ClientSslContext(sslContext, trustManager);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | UnrecoverableKeyException
                 | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509TrustManager createTrustManager(String trustStorePath, String trustStorePassword)
            throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        KeyStore keyStore;
        if (trustStorePath != null) {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(trustStorePath), trustStorePassword.toCharArray());
        } else {
            keyStore = null;
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager)trustManager;
            }
        }
        return null;
    }

    private static KeyManager[] createKeyManagers(String keyStorePath, String keyStorePassword, String certificatePassword)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, certificatePassword.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }
}
