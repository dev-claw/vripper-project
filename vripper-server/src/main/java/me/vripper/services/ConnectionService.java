package me.vripper.services;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.vripper.download.DownloadJob;
import me.vripper.event.Event;
import me.vripper.event.EventBus;
import me.vripper.services.domain.Settings;
import net.jodah.failsafe.RetryPolicy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbstractExecutionAwareRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

@Service
@EnableScheduling
@Slf4j
public class ConnectionService {

  private final Disposable disposable;
  private PoolingHttpClientConnectionManager pcm;
  private RequestConfig rc;

  @Getter private RetryPolicy<Object> retryPolicy;

  private int connectionTimeout;
  private int maxAttempts;

  public ConnectionService(EventBus eventBus, SettingsService settingsService) {
    connectionTimeout = settingsService.getSettings().getConnectionTimeout();
    maxAttempts = settingsService.getSettings().getMaxAttempts();
    disposable =
        eventBus
            .flux()
            .filter(e -> e.getKind().equals(Event.Kind.SETTINGS_UPDATE))
            .map(e -> ((Settings) e.getData()))
            .subscribe(
                settings -> {
                  if (connectionTimeout != settings.getConnectionTimeout()) {
                    connectionTimeout = settings.getConnectionTimeout();
                    buildRequestConfig();
                  }
                  if (maxAttempts != settings.getMaxAttempts()) {
                    maxAttempts = settings.getMaxAttempts();
                    buildRetryPolicy();
                  }
                });

    buildRequestConfig();
    buildRetryPolicy();
    buildConnectionPool();
  }

  @PreDestroy
  private void destroy() {
    disposable.dispose();
  }

  private void buildRetryPolicy() {
    retryPolicy =
        new RetryPolicy<>()
            .withDelay(2, 5, ChronoUnit.SECONDS)
            .withMaxAttempts(maxAttempts)
            .onFailedAttempt(
                e ->
                    log.warn(
                        String.format("#%d tries failed", e.getAttemptCount()),
                        e.getLastFailure()));
  }

  private void buildConnectionPool() {
    pcm = new PoolingHttpClientConnectionManager();
    pcm.setMaxTotal(Integer.MAX_VALUE);
    pcm.setDefaultMaxPerRoute(Integer.MAX_VALUE);
  }

  private void buildRequestConfig() {
    rc =
        RequestConfig.custom()
            .setConnectionRequestTimeout(connectionTimeout * 1000)
            .setConnectTimeout(connectionTimeout * 1000)
            .setSocketTimeout(connectionTimeout * 1000)
            .setCookieSpec(CookieSpecs.STANDARD)
            .build();
  }

  @Scheduled(fixedDelay = 5_000)
  private void idleConnectionMonitoring() {
    pcm.closeExpiredConnections();
    pcm.closeIdleConnections(30, TimeUnit.SECONDS);
  }

  public HttpClientBuilder getClient() {
    return HttpClients.custom()
        .setConnectionManager(pcm)
        .setRedirectStrategy(new LaxRedirectStrategy())
        .disableAutomaticRetries()
        .setDefaultRequestConfig(rc);
  }

  public HttpGet buildHttpGet(String url, final HttpClientContext context) {
    HttpGet httpGet = new HttpGet(url.replace(" ", "+"));
    httpGet.addHeader(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
    addToContext(context, httpGet);
    return httpGet;
  }

  public HttpPost buildHttpPost(String url, final HttpClientContext context) {
    HttpPost httpPost = new HttpPost(url.replace(" ", "+"));
    httpPost.addHeader(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
    addToContext(context, httpPost);
    return httpPost;
  }

  public HttpGet buildHttpGet(URI uri, final HttpClientContext context) {
    HttpGet httpGet = new HttpGet(uri);
    httpGet.addHeader(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
    addToContext(context, httpGet);
    return httpGet;
  }

  public void addToContext(HttpClientContext context, AbstractExecutionAwareRequest request) {
    if (context != null) {
      List<AbstractExecutionAwareRequest> requests =
          (List<AbstractExecutionAwareRequest>)
              context.getAttribute(DownloadJob.ContextAttributes.OPEN_CONNECTION.toString());
      if (requests != null) {
        requests.add(request);
      }
    }
  }
}
