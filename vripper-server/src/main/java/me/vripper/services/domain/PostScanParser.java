package me.vripper.services.domain;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.SAXParserFactory;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.exception.DownloadException;
import me.vripper.exception.PostParseException;
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
public class PostScanParser {

  private static final SAXParserFactory factory = SAXParserFactory.newInstance();

  private final String threadId;
  private final String postId;
  private final ConnectionService cm;
  private final VGAuthService VGAuthService;
  private final SettingsService settingsService;

  public PostScanParser(String threadId, String postId) {
    this.threadId = threadId;
    this.postId = postId;
    cm = SpringContext.getBean(ConnectionService.class);
    VGAuthService = SpringContext.getBean(VGAuthService.class);
    settingsService = SpringContext.getBean(SettingsService.class);
  }

  public PostScanResult parse() throws PostParseException {

    log.debug(String.format("Parsing post %s", postId));
    HttpGet httpGet;
    try {
      URIBuilder uriBuilder = new URIBuilder(settingsService.getSettings().getVProxy() + "/vr.php");
      uriBuilder.setParameter("p", postId);
      httpGet = cm.buildHttpGet(uriBuilder.build(), null);
    } catch (URISyntaxException e) {
      throw new PostParseException(e);
    }

    AtomicReference<Throwable> thr = new AtomicReference<>();
    PostScanHandler postScanHandler = new PostScanHandler(threadId, postId);
    log.debug(String.format("Requesting %s", httpGet));
    PostScanResult post = getPost(httpGet, postScanHandler, thr);
    if (thr.get() != null) {
      log.error(
          String.format("parsing failed for thread %s, post %s", threadId, postId), thr.get());
      throw new PostParseException(thr.get());
    }
    return post;
  }

  private PostScanResult getPost(
      HttpGet httpGet, PostScanHandler postScanHandler, AtomicReference<Throwable> thr) {
    return Failsafe.with(cm.getRetryPolicy())
        .onFailure(e -> thr.set(e.getFailure()))
        .get(
            () -> {
              HttpClient connection = cm.getClient().build();
              try (CloseableHttpResponse response =
                  (CloseableHttpResponse) connection.execute(httpGet, VGAuthService.getContext())) {
                if (response.getStatusLine().getStatusCode() / 100 != 2) {
                  throw new DownloadException(
                      String.format(
                          "Unexpected response code '%d' for %s",
                          response.getStatusLine().getStatusCode(), httpGet));
                }

                factory.newSAXParser().parse(response.getEntity().getContent(), postScanHandler);
                EntityUtils.consumeQuietly(response.getEntity());
                return postScanHandler.getParsedPost();
              }
            });
  }
}
