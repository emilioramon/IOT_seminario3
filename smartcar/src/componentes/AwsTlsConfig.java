package componentes;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class AwsTlsConfig {

    public static SSLSocketFactory buildSocketFactory(
            String keystorePath,
            String keystorePassword,
            String caCertPath) throws Exception {

        KeyStore clientKs = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystorePath)) {
            clientKs.load(in, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKs, keystorePassword.toCharArray());

        KeyStore trustKs = KeyStore.getInstance(KeyStore.getDefaultType());
        trustKs.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream in = new FileInputStream(caCertPath)) {
            X509Certificate root = (X509Certificate) cf.generateCertificate(in);
            trustKs.setCertificateEntry("aws-root", root);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustKs);

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx.getSocketFactory();
    }
}
