package security;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

/**
 * Do not use this in real production code!
 */
public class TrustAllCerts {
    private static TrustAllCerts ourInstance = new TrustAllCerts();

    public static TrustAllCerts getInstance() {
        return ourInstance;
    }

    public HostnameVerifier getHostNameVerifier() {
        return (hostname, session) -> true;
    }

    private TrustAllCerts() {
    }

    public SSLConnectionSocketFactory getSslConnectionSocketFactory(SSLContext sslContext) {
        return new SSLConnectionSocketFactory(
            sslContext,
            new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"},
            null,
            ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    public SSLContext getSslContext(TrustManager tm) {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{tm}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return sslContext;
    }

    public TrustManager getTrustManager() {
        return new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };
    }

}
