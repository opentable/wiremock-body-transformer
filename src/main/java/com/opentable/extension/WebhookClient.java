package com.opentable.extension;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import security.TrustAllCerts;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static java.util.concurrent.TimeUnit.SECONDS;

public class WebhookClient {
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;

    WebhookClient() {
        this.scheduler = Executors.newScheduledThreadPool(10);

        final TrustManager trustManager = TrustAllCerts.getInstance().getTrustManager();
        final SSLContext sslContext = TrustAllCerts.getInstance().getSslContext(trustManager);

        this.httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(TrustAllCerts.getInstance().getHostNameVerifier())
            .setSSLSocketFactory(TrustAllCerts.getInstance().getSslConnectionSocketFactory(sslContext))
            .build();
    }

    public CompletableFuture post(String url, String body) {
        return postInternal(url, body);
    }

    public CompletableFuture post(String url, String body, String headerKey, String headerValue) {
        return postInternal(url, body, headerKey, headerValue);
    }

    CompletableFuture postInternal(String url, String body, String... headers) {

        CompletableFuture future = new CompletableFuture();
        scheduler.schedule(
            () -> {
                try {
                    HttpUriRequest request = buildPost(url, body, headers);
                    HttpResponse response = httpClient.execute(request);
                    future.complete(response);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    throwUnchecked(e);
                }
            },
            2L,
            SECONDS
        );
        return future;
    }

    private static HttpUriRequest buildPost(String url, String body, String... args) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        if (args != null && args.length != 0) {
            for (int i = 0; i < args.length - 1; i += 2) {
                httpPost.setHeader(args[i], args[i + 1]);
            }
        }
        httpPost.setEntity(new StringEntity(body));
        return httpPost;
    }

}
