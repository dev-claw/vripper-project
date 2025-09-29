package me.vripper.services.domain;

import java.io.BufferedInputStream;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.SAXParserFactory;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.exception.DownloadException;
import me.vripper.exception.PostParseException;
import me.vripper.jpa.domain.Queued;
import me.vripper.services.ConnectionService;
import me.vripper.services.SettingsService;
import me.vripper.services.VGAuthService;
import net.jodah.failsafe.Failsafe;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;

@Slf4j
public class MultiPostScanParser {

  private static final SAXParserFactory factory = SAXParserFactory.newInstance();
  private final Queued queued;
  private final ConnectionService cm;
  private final VGAuthService VGAuthService;
  private final SettingsService settingsService;

  public MultiPostScanParser(Queued queued) {
    this.queued = queued;
    cm = SpringContext.getBean(ConnectionService.class);
    VGAuthService = SpringContext.getBean(VGAuthService.class);
    settingsService = SpringContext.getBean(SettingsService.class);
  }

  public MultiPostScanResult parse() throws PostParseException {

    log.debug(String.format("Parsing thread %s", queued));
    HttpGet httpGet;
    try {
      URIBuilder uriBuilder = new URIBuilder(settingsService.getSettings().getVProxy() + "/vr.php");
      uriBuilder.setParameter("t", queued.getThreadId());
      httpGet = cm.buildHttpGet(uriBuilder.build(), null);
    } catch (URISyntaxException e) {
      throw new PostParseException(e);
    }

    MultiPostScanHandler multiPostScanHandler = new MultiPostScanHandler(queued);
    AtomicReference<Throwable> thr = new AtomicReference<>();
    log.debug(String.format("Requesting %s", httpGet));
    MultiPostScanResult multiPostScanResult =
        Failsafe.with(cm.getRetryPolicy())
            .onFailure(e -> thr.set(e.getFailure()))
            .get(
                () -> {
                  HttpClient connection = cm.getClient().build();
                  try (CloseableHttpResponse response =
                      (CloseableHttpResponse)
                          connection.execute(httpGet, VGAuthService.getContext())) {
                    if (response.getStatusLine().getStatusCode() / 100 != 2) {
                      throw new DownloadException(
                          String.format(
                              "Unexpected response code '%d' for %s",
                              response.getStatusLine().getStatusCode(), httpGet));
                    }

                    try {
                      factory
                          .newSAXParser()
                          .parse(
                              new BufferedInputStream(response.getEntity().getContent()),
                              multiPostScanHandler);
                      return multiPostScanHandler.getScanResult();
                    } catch (Exception e) {
                      throw new PostParseException(
                          String.format("Failed to parse thread %s", queued), e);
                    } finally {
                      EntityUtils.consumeQuietly(response.getEntity());
                    }
                  }
                });
    if (thr.get() != null) {
      log.error(String.format("parsing failed for thread %s", queued), thr.get());
      throw new PostParseException(thr.get());
    }
    return multiPostScanResult;
  }
}
