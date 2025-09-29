package me.vripper.services.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.vripper.SpringContext;
import me.vripper.host.Host;
import me.vripper.jpa.domain.Image;
import me.vripper.jpa.domain.Post;
import me.vripper.services.SettingsService;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

@Slf4j
class PostScanHandler extends DefaultHandler {

  private final Collection<Host> supportedHosts;

  private final String threadId;
  private final String postId;
  private final String postUrl;
  private final Set<Image> images = new HashSet<>();
  private Set<String> previews = new HashSet<>();
  private String threadTitle;
  private String forum;
  private String userHash;
  private int index = 0;

  private Post parsedPost;

  PostScanHandler(String threadId, String postId) {
    this.threadId = threadId;
    this.postId = postId;
    supportedHosts = SpringContext.getBeansOfType(Host.class).values();
    SettingsService settingsService = SpringContext.getBean(SettingsService.class);
    postUrl =
        String.format(
            "%s/threads/%s/?p=%s&viewfull=1#post%s",
            settingsService.getSettings().getVProxy(), this.threadId, this.postId, this.postId);
  }

  public PostScanResult getParsedPost() {
    return new PostScanResult(parsedPost, images);
  }

  @Override
  public void startDocument() {}

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {

    switch (qName.toLowerCase()) {
      case "forum":
        forum = attributes.getValue("title").trim();
        break;
      case "user":
        userHash = attributes.getValue("hash").trim();
        break;
      case "thread":
        threadTitle = attributes.getValue("title").trim();
        break;
      case "post":
        String postTitle =
            Optional.ofNullable(attributes.getValue("title"))
                .map(e -> e.trim().isEmpty() ? null : e.trim())
                .orElse(threadTitle);
        parsedPost = new Post(postTitle, postUrl, postId, threadId, threadTitle, forum, userHash);
        break;
      case "image":
        index++;
        String thumbUrl = attributes.getValue("thumb_url").trim();
        if (previews.size() < 4) {
          previews.add(thumbUrl);
        }

        String mainUrl =
            Optional.ofNullable(attributes.getValue("main_url")).map(String::trim).orElse(null);
        if (mainUrl != null) {
          Host foundHost =
              supportedHosts.stream()
                  .filter(host -> host.isSupported(mainUrl))
                  .findFirst()
                  .orElse(null);
          if (foundHost != null) {
            log.debug(
                String.format(
                    "Found supported host %s for %s",
                    foundHost.getClass().getSimpleName(), mainUrl));
            images.add(new Image(postId, mainUrl, thumbUrl, foundHost, index));
          } else {
            log.warn(String.format("unsupported host for %s, skipping", mainUrl));
          }
        }
        break;
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if ("post".equalsIgnoreCase(qName)) {
      parsedPost.setTotal(images.size());
      if (!previews.isEmpty()) {
        parsedPost.setPreviews(previews);
      }
      parsedPost.setHosts(
          images.stream().map(Image::getHost).map(Host::getHost).collect(Collectors.toSet()));
      index = 0;
      previews = new HashSet<>();
    }
  }
}
