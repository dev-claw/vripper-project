package me.vripper.tasks;

import static me.vripper.jpa.domain.LogEvent.Status.ERROR;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.Utils;
import me.vripper.download.DownloadJob;
import me.vripper.exception.DownloadException;
import me.vripper.exception.PostParseException;
import me.vripper.jpa.domain.LogEvent;
import me.vripper.jpa.domain.Metadata;
import me.vripper.jpa.domain.Post;
import me.vripper.jpa.repositories.ILogEventRepository;
import me.vripper.services.*;
import net.jodah.failsafe.Failsafe;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.AbstractExecutionAwareRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@Slf4j
public class MetadataRunnable implements Runnable {

  private static final List<String> dictionary =
      Arrays.asList("download", "link", "rapidgator", "filefactory", "filefox");

  @Getter private final Post post;

  private final DataService dataService;
  private final ILogEventRepository eventRepository;

  private final LogEvent logEvent;
  private final ConnectionService cm;
  private final HtmlProcessorService htmlProcessorService;
  private final XpathService xpathService;

  private final HttpClientContext context = HttpClientContext.create();
  private final MetadataService metadataService;
  private volatile boolean stopped = false;
  @Getter private volatile boolean finished = false;

  public MetadataRunnable(@NonNull Post post) {
    this.post = post;
    dataService = SpringContext.getBean(DataService.class);
    eventRepository = SpringContext.getBean(ILogEventRepository.class);
    cm = SpringContext.getBean(ConnectionService.class);
    VGAuthService vgAuthService = SpringContext.getBean(VGAuthService.class);
    htmlProcessorService = SpringContext.getBean(HtmlProcessorService.class);
    xpathService = SpringContext.getBean(XpathService.class);
    metadataService = SpringContext.getBean(MetadataService.class);

    context.setCookieStore(vgAuthService.getContext().getCookieStore());
    context.setAttribute(
        DownloadJob.ContextAttributes.OPEN_CONNECTION.toString(),
        Collections.synchronizedList(new ArrayList<AbstractExecutionAwareRequest>()));

    logEvent =
        new LogEvent(
            LogEvent.Type.METADATA,
            LogEvent.Status.PENDING,
            LocalDateTime.now(),
            "Fetching metadata for " + post.getUrl());
    eventRepository.save(logEvent);
  }

  @Override
  public void run() {

    try {
      logEvent.setStatus(LogEvent.Status.PROCESSING);
      eventRepository.update(logEvent);
      Metadata metadata = fetchMetadata(post.getPostId(), post.getThreadId(), post.getUrl());
      if (metadata != null && !stopped) {
        dataService.setMetadata(post, metadata);
        logEvent.setStatus(LogEvent.Status.DONE);
      } else {
        logEvent.setStatus(ERROR);
        logEvent.setMessage(String.format("Fetching metadata for %s failed", post.getUrl()));
      }
      eventRepository.update(logEvent);
    } catch (Exception e) {
      String message = String.format("Failed to fetch metadata for %s", post.getUrl());
      log.error(message, e);
      logEvent.setMessage(message + "\n" + Utils.throwableToString(e));
      logEvent.setStatus(ERROR);
      eventRepository.update(logEvent);
    } finally {
      if (stopped) {
        String message = String.format("Fetching metadata for %s interrupted", post.getUrl());
        logEvent.setStatus(LogEvent.Status.DONE);
        logEvent.setMessage(message);
        eventRepository.update(logEvent);
      }
      finished = true;
      metadataService.stopFetchingMetadata(List.of(post.getPostId()));
    }
  }

  private Metadata fetchMetadata(String postId, String threadId, String url) {
    HttpGet httpGet = cm.buildHttpGet(url, context);
    AtomicReference<Metadata> metadataReference = new AtomicReference<>();
    Failsafe.with(cm.getRetryPolicy())
        .onFailure(
            e ->
                log.error(
                    String.format("Error occurred when getting post metadata, postId %s", postId),
                    e.getFailure()))
        .run(
            () -> {
              if (stopped) {
                return;
              }
              HttpClient connection = cm.getClient().build();
              try (CloseableHttpResponse response =
                  (CloseableHttpResponse) connection.execute(httpGet, context)) {
                if (stopped) {
                  return;
                }
                if (response.getStatusLine().getStatusCode() / 100 != 2) {
                  throw new DownloadException(
                      String.format(
                          "Unexpected response code '%d' for %s",
                          response.getStatusLine().getStatusCode(), httpGet));
                }
                try {
                  Document document =
                      htmlProcessorService.clean(EntityUtils.toString(response.getEntity()));

                  if (stopped) {
                    return;
                  }

                  Node postNode =
                      xpathService.getAsNode(
                          document,
                          String.format(
                              "//li[@id='post_%s']/div[contains(@class, 'postdetails')]", postId));

                  if (stopped) {
                    return;
                  }

                  String postedBy =
                      xpathService
                          .getAsNode(
                              postNode,
                              "./div[contains(@class, 'userinfo')]//a[contains(@class, 'username')]//font")
                          .getTextContent()
                          .trim();

                  if (stopped) {
                    return;
                  }

                  Metadata metadata = new Metadata();
                  metadata.setPostedBy(postedBy);

                  if (stopped) {
                    return;
                  }

                  Node node =
                      xpathService.getAsNode(
                          document, String.format("//div[@id='post_message_%s']", postId));
                  metadata.setResolvedNames(findTitleInContent(node));
                  metadata.setPostId(postId);

                  if (stopped) {
                    return;
                  }
                  metadataReference.set(metadata);
                } catch (Exception e) {
                  if (stopped) {
                    log.warn(e.getMessage(), e);
                    return;
                  }
                  throw new PostParseException(
                      String.format("Failed to parse thread %s, post %s", threadId, postId), e);
                } finally {
                  EntityUtils.consumeQuietly(response.getEntity());
                }
              }
            });
    return metadataReference.get();
  }

  private List<String> findTitleInContent(Node node) {
    List<String> altTitle = new ArrayList<>();
    findTitle(node, altTitle, new AtomicBoolean(true));
    return altTitle.stream().distinct().collect(Collectors.toList());
  }

  private void findTitle(Node node, List<String> altTitle, AtomicBoolean keepGoing) {
    if (!keepGoing.get()) {
      return;
    }
    if (node.getNodeName().equals("a") || node.getNodeName().equals("img")) {
      keepGoing.set(false);
      return;
    }
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      for (int i = 0; i < node.getChildNodes().getLength(); i++) {
        Node item = node.getChildNodes().item(i);
        findTitle(item, altTitle, keepGoing);
        if (!keepGoing.get()) {
          return;
        }
      }

    } else if (node.getNodeType() == Node.TEXT_NODE) {
      String text = node.getTextContent().trim();
      if (!text.isBlank()
          && dictionary.stream().noneMatch(e -> text.toLowerCase().contains(e.toLowerCase()))) {
        altTitle.add(text);
      }
    }
  }

  public void stop() {
    this.stopped = true;
    List<AbstractExecutionAwareRequest> requests =
        (List<AbstractExecutionAwareRequest>)
            this.context.getAttribute(DownloadJob.ContextAttributes.OPEN_CONNECTION.toString());
    if (requests != null) {
      for (AbstractExecutionAwareRequest request : requests) {
        request.abort();
      }
    }
  }
}
