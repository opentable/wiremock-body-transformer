package com.opentable.extension;

import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.http.HttpClientFactory;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static java.util.concurrent.TimeUnit.SECONDS;

public class WebhookClient {

    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;

    public WebhookClient() {
            this.scheduler = Executors.newScheduledThreadPool(10);
            this.httpClient = HttpClients.custom()
                //TODO enable mTLS
                .build();
    }

    public CompletableFuture post(String url, String body) {

        CompletableFuture future = new CompletableFuture();
        scheduler.schedule(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        HttpUriRequest request = buildPost(url, body);
                        HttpResponse response = httpClient.execute(request);
                        System.out.println(
                            String.format("post - request to %s returned status %s\n\n%s",
                                url,
                                response.getStatusLine(),
                                EntityUtils.toString(response.getEntity())
                            )
                        );
                        future.complete(response);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                        throwUnchecked(e);
                    }
                }
            },
            0L,
            SECONDS
        );
        return future;
    }

    private static HttpUriRequest buildPost(String url, String body) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new StringEntity(body));
        return httpPost;
    }

}
