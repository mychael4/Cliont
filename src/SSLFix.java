import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SSLFix {

    public static void apply() {
        try {

            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(
                                X509Certificate[] certs,
                                String authType
                        ) {
                            // Trust all clients
                        }

                        @Override
                        public void checkServerTrusted(
                                X509Certificate[] certs,
                                String authType
                        ) {
                            // Trust all servers (DISABLES SSL VALIDATION)
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");

            sc.init(
                    null,
                    trustAllCerts,
                    new SecureRandom()
            );

            HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory()
            );

            HttpsURLConnection.setDefaultHostnameVerifier(
                    (hostname, session) -> true
            );

            System.out.println("[SSLFix] SSL validation disabled (DEV MODE)");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}