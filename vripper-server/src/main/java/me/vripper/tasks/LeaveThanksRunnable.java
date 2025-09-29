package me.vripper.tasks;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.Utils;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.repositories.ILogEventRepository;
import me.vripper.services.ConnectionService;
import me.vripper.services.DataService;
import me.vripper.services.SettingsService;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

@Slf4j
public class LeaveThanksRunnable implements Runnable {

  private final ConnectionService cm;
  private final DataService dataService;
  private final HttpClientContext context;
  private final SettingsService settingsService;
  private final ILogEventRepository eventRepository;
  private final boolean authenticated;
  private final Post post;
  private final LogEvent logEvent;

  public LeaveThanksRunnable(Post post, boolean authenticated, HttpClientContext context) {
    this.post = post;
    this.authenticated = authenticated;
    this.context = context;
    cm = SpringContext.getBean(ConnectionService.class);
    dataService = SpringContext.getBean(DataService.class);
    eventRepository = SpringContext.getBean(ILogEventRepository.class);
    settingsService = SpringContext.getBean(SettingsService.class);
    logEvent =
        new LogEvent(
            LogEvent.Type.THANKS,
            LogEvent.Status.PENDING,
            LocalDateTime.now(),
            String.format("Leaving thanks for %s", post.getUrl()));
    eventRepository.save(logEvent);
  }

  @Override
  public void run() {
    try {
      logEvent.setStatus(LogEvent.Status.PROCESSING);
      eventRepository.update(logEvent);
      if (!settingsService.getSettings().getVLogin()) {
        logEvent.setMessage(
            String.format(
                "Will not send a like for %s\nAuthentication with ViperGirls option is disabled",
                post.getUrl()));
        logEvent.setStatus(LogEvent.Status.DONE);
        eventRepository.update(logEvent);
        return;
      }
      if (!settingsService.getSettings().getVThanks()) {
        logEvent.setMessage(
            String.format(
                "Will not send a like for %s\nLeave thanks option is disabled", post.getUrl()));
        logEvent.setStatus(LogEvent.Status.DONE);
        eventRepository.update(logEvent);
        return;
      }
      if (!authenticated) {
        logEvent.setMessage(
            String.format("Will not send a like for %s\nYou are not authenticated", post.getUrl()));
        logEvent.setStatus(LogEvent.Status.ERROR);
        eventRepository.update(logEvent);
        return;
      }
      if (post.isThanked()) {
        logEvent.setMessage(
            String.format("Will not send a like for %s\nAlready left a thanks", post.getUrl()));
        logEvent.setStatus(LogEvent.Status.DONE);
        eventRepository.update(logEvent);
        return;
      }

      HttpPost postThanks =
          cm.buildHttpPost(settingsService.getSettings().getVProxy() + "/post_thanks.php", null);
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("do", "post_thanks_add"));
      params.add(new BasicNameValuePair("using_ajax", "1"));
      params.add(new BasicNameValuePair("p", post.getPostId()));
      params.add(new BasicNameValuePair("securitytoken", post.getSecurityToken()));
      try {
        postThanks.setEntity(new UrlEncodedFormEntity(params));
      } catch (UnsupportedEncodingException e) {
        String error = String.format("Request error for %s", post.getUrl());
        log.error(error, e);
        logEvent.setMessage(error + "\n" + Utils.throwableToString(e));
        logEvent.setStatus(LogEvent.Status.ERROR);
        eventRepository.update(logEvent);
        return;
      }

      postThanks.addHeader("Referer", settingsService.getSettings().getVProxy());
      postThanks.addHeader(
          "Host",
          settingsService.getSettings().getVProxy().replace("https://", "").replace("http://", ""));

      CloseableHttpClient client = cm.getClient().build();

      try (CloseableHttpResponse response = client.execute(postThanks, context)) {
        if (response.getStatusLine().getStatusCode() / 100 == 2) {
          post.setThanked(true);
          dataService.updatePostThanks(post.isThanked(), post.getId());
        }
        EntityUtils.consumeQuietly(response.getEntity());
      }
      logEvent.setStatus(LogEvent.Status.DONE);
      eventRepository.update(logEvent);
    } catch (Exception e) {
      String error = String.format("Failed to leave a thanks for %s", post.getUrl());
      log.error(error, e);
      logEvent.setMessage(error + "\n" + Utils.throwableToString(e));
      logEvent.setStatus(LogEvent.Status.ERROR);
      eventRepository.update(logEvent);
    }
  }
}
