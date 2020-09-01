package ru.sbrf.sb.service.impl;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.MockServerContainer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.UriBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class SslTest {

    private static final int TOTAL_CONNECTIONS = 100;
    private static final int CONNECTIONS_PER_ROUTE = 10;
    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    @ClassRule
    public static MockServerContainer mockserver = new MockServerContainer()
            .withEnv("MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED", "true")
            .withEnv("MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN", "/opt/localhost.crt")
            .withClasspathResourceMapping("localhost.crt", "/opt/localhost.crt", BindMode.READ_ONLY)
            .withExposedPorts(1080);

    @BeforeAll
    public static void start() {
        mockserver.start();
    }

    @Test
    public void test() {
        String fpaTokenUrl = "https://localhost:" + mockserver.getMappedPort(1080);

        var client = getHttpsClient();
        ResteasyWebTarget target = client.target(UriBuilder.fromPath(fpaTokenUrl));
        TokenClient tokenClient = target
                .proxyBuilder(TokenClient.class)
                .classloader(TokenClient.class.getClassLoader())
                .build();
        tokenClient.getToken();
    }

    public SSLContext getSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");

            char[] keyStorePasswordArray = "password".toCharArray();
            KeyStore ks = getKeyStoreFromFile("./src/main/resources/keystore.jks", "password");

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(ks, keyStorePasswordArray);

            KeyStore ts = getKeyStoreFromFile("./src/main/resources/keystore.jks", "password");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(ts);

            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public KeyStore getKeyStoreFromFile(String keyStorePath, String keyStorePassword)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {

        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] trustStorePasswordArray = keyStorePassword.toCharArray();
        keyStore.load(new FileInputStream(keyStorePath), trustStorePasswordArray);
        return keyStore;
    }

    private ResteasyClient getHttpsClient() {
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register(HTTP, PlainConnectionSocketFactory.getSocketFactory());

        SSLContext sslContext = getSslContext();
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext, new NoopHostnameVerifier());
        registryBuilder.register(HTTPS, sslSocketFactory);

        final Registry<ConnectionSocketFactory> socketFactoryRegistry = registryBuilder.build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        cm.setMaxTotal(TOTAL_CONNECTIONS);
        cm.setDefaultMaxPerRoute(CONNECTIONS_PER_ROUTE);
        CloseableHttpClient httpClient = HttpClients
                .custom()
                .useSystemProperties()
                .setConnectionManager(cm)
                .build();

        return new ResteasyClientBuilder()
                .httpEngine(new ApacheHttpClient43Engine(httpClient))
                .hostnameVerification(ResteasyClientBuilder.HostnameVerificationPolicy.ANY)
                .build();
    }
}
