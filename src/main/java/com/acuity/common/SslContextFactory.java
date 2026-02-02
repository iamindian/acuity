package com.acuity.common;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

/**
 * Factory for creating SSL contexts for client and server
 */
public class SslContextFactory {
    private static SslContext serverContext;
    private static SslContext clientContext;

    /**
     * Get SSL context for server
     */
    public static SslContext getServerContext() throws CertificateException, SSLException {
        if (serverContext == null) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            serverContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        }
        return serverContext;
    }

    /**
     * Get SSL context for client (using insecure trust manager for self-signed certs)
     */
    public static SslContext getClientContext() throws SSLException {
        if (clientContext == null) {
            clientContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        }
        return clientContext;
    }
}
