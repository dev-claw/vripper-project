package me.vripper.services.domain;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import me.vripper.SpringContext;
import me.vripper.host.Host;
import me.vripper.jpa.domain.Queued;
import me.vripper.services.SettingsService;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class MultiPostScanHandler extends DefaultHandler {

  private final Queued queued;
  private final Collection<Host> supportedHosts;
  private final SettingsService settingsService;
  private final Map<Host, AtomicInteger> hostMap = new HashMap<>();
  private final List<MultiPostItem> posts = new ArrayList<>();
  private String error;
  private List<String> previews = new ArrayList<>();
  private String threadTitle;
  private String postId;
  private String postTitle;
  private int imageCount;
  private int postCounter;
  private int previewCounter = 0;

  public MultiPostScanHandler(Queued queued) {
    this.queued = queued;
    supportedHosts = SpringContext.getBeansOfType(Host.class).values();
    settingsService = SpringContext.getBean(SettingsService.class);
  }

  public MultiPostScanResult getScanResult() {
    return new MultiPostScanResult(posts, error);
  }

  @Override
  public void startDocument() {}

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {

    switch (qName.toLowerCase()) {
      case "error":
        error = attributes.getValue("details");
        break;
      case "thread":
        threadTitle = attributes.getValue("title").trim();
        break;
      case "post":
        imageCount = Integer.parseInt(attributes.getValue("imagecount").trim());
        postId = attributes.getValue("id").trim();
        postCounter = Integer.parseInt(attributes.getValue("number").trim());
        postTitle =
            Optional.ofNullable(attributes.getValue("title"))
                .map(e -> e.trim().isEmpty() ? null : e.trim())
                .orElse(threadTitle);
        break;
      case "image":
        Optional.ofNullable(attributes.getValue("main_url"))
            .map(String::trim)
            .flatMap(
                mainUrl ->
                    supportedHosts.stream().filter(host -> host.isSupported(mainUrl)).findFirst())
            .ifPresent(
                host ->
                    Optional.ofNullable(hostMap.get(host))
                        .ifPresentOrElse(
                            AtomicInteger::incrementAndGet,
                            () -> hostMap.put(host, new AtomicInteger(1))));
        if (previewCounter++ < 4) {
          Optional.ofNullable(attributes.getValue("thumb_url"))
              .map(String::trim)
              .ifPresent(previews::add);
        }
        break;
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if ("post".equalsIgnoreCase(qName)) {
      if (imageCount != 0) {
        posts.add(
            new MultiPostItem(
                queued.getThreadId(),
                postId,
                postCounter,
                postTitle,
                imageCount,
                String.format(
                    "%s/threads/?p=%s&viewfull=1#post%s",
                    settingsService.getSettings().getVProxy(), postId, postId),
                previews,
                hostMap.entrySet().stream()
                    .filter(v -> v.getValue().get() > 0)
                    .map(e -> e.getKey().getHost() + " (" + e.getValue().get() + ")")
                    .collect(Collectors.joining(", "))));
        queued.increment();
      }
      previewCounter = 0;
      previews = new ArrayList<>();
      hostMap.clear();
    }
  }

  @Override
  public void endDocument() {
    queued.done();
  }
}
