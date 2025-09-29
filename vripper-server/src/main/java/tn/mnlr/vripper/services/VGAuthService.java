package tn.mnlr.vripper.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import tn.mnlr.vripper.event.Event;
import tn.mnlr.vripper.event.EventBus;
import tn.mnlr.vripper.exception.VripperException;
import tn.mnlr.vripper.jpa.domain.Post;
import tn.mnlr.vripper.tasks.LeaveThanksRunnable;

@Service
@Slf4j
public class VGAuthService {

  private final ConnectionService cm;
  private final SettingsService settingsService;
  private final ThreadPoolService threadPoolService;

  private final Disposable disposable;

  @Getter private final HttpClientContext context = HttpClientContext.create();
  @Getter private boolean authenticated = false;
  @Getter private String loggedUser = "";

  private final EventBus eventBus;

  @Autowired
  public VGAuthService(
      ConnectionService cm,
      SettingsService settingsService,
      ThreadPoolService threadPoolService,
      EventBus eventBus) {
    this.cm = cm;
    this.settingsService = settingsService;
    this.threadPoolService = threadPoolService;
    this.eventBus = eventBus;
    disposable =
        eventBus
            .flux()
            .filter(p -> p.getKind().equals(Event.Kind.SETTINGS_UPDATE))
            .subscribe(e -> authenticate());
  }

  @PostConstruct
  private void init() {
    context.setCookieStore(new BasicCookieStore());
    authenticate();
  }

  @PreDestroy
  private void destroy() {
    disposable.dispose();
  }

  public void authenticate() {

    log.info("Authenticating using ViperGirls credentials");
    authenticated = false;

    if (!settingsService.getSettings().getVLogin()) {
      log.debug("Authentication option is disabled");
      context.getCookieStore().clear();
      loggedUser = "";
      eventBus.publishEvent(Event.wrap(Event.Kind.VG_USER, loggedUser));
      return;
    }

    String username = settingsService.getSettings().getVUsername();
    String password = settingsService.getSettings().getVPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      log.error("Cannot authenticate with ViperGirls credentials, username or password is empty");
      context.getCookieStore().clear();
      loggedUser = "";
      eventBus.publishEvent(Event.wrap(Event.Kind.VG_USER, loggedUser));
      return;
    }

    HttpPost postAuth =
        cm.buildHttpPost(settingsService.getSettings().getVProxy() + "/login.php?do=login", null);
    List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("vb_login_username", username));

    params.add(new BasicNameValuePair("cookieuser", "1"));
    params.add(new BasicNameValuePair("do", "login"));
    params.add(new BasicNameValuePair("vb_login_md5password", password));
    try {
      postAuth.setEntity(new UrlEncodedFormEntity(params));
    } catch (Exception e) {
      context.getCookieStore().clear();
      loggedUser = "";
      eventBus.publishEvent(Event.wrap(Event.Kind.VG_USER, loggedUser));
      log.error("Failed to authenticate user with " + settingsService.getSettings().getVProxy(), e);
      return;
    }

    postAuth.addHeader("Referer", settingsService.getSettings().getVProxy());
    postAuth.addHeader(
        "Host",
        settingsService.getSettings().getVProxy().replace("https://", "").replace("http://", ""));

    CloseableHttpClient client = cm.getClient().build();

    try (CloseableHttpResponse response = client.execute(postAuth, context)) {

      if (response.getStatusLine().getStatusCode() / 100 != 2) {
        throw new VripperException(
            String.format(
                "Unexpected response code returned %s", response.getStatusLine().getStatusCode()));
      }
      String responseBody = EntityUtils.toString(response.getEntity());
      log.debug(String.format("Authentication with ViperGirls response body:%n%s", responseBody));
      EntityUtils.consumeQuietly(response.getEntity());
      if (context.getCookieStore().getCookies().stream()
          .map(Cookie::getName)
          .noneMatch(e -> e.equals("vg_userid"))) {
        log.error(
            String.format(
                "Failed to authenticate user with %s, missing vg_userid cookie",
                settingsService.getSettings().getVProxy()));
        return;
      }
    } catch (Exception e) {
      context.getCookieStore().clear();
      loggedUser = "";
      eventBus.publishEvent(Event.wrap(Event.Kind.VG_USER, loggedUser));
      log.error("Failed to authenticate user with " + settingsService.getSettings().getVProxy(), e);
      return;
    }
    authenticated = true;
    loggedUser = username;
    log.info(String.format("Authenticated: %s", username));
    eventBus.publishEvent(Event.wrap(Event.Kind.VG_USER, loggedUser));
  }

  public void leaveThanks(Post post) {
    threadPoolService
        .getGeneralExecutor()
        .submit(new LeaveThanksRunnable(post, authenticated, context));
  }
}
