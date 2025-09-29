package me.vripper.host;

import lombok.extern.slf4j.Slf4j;
import me.vripper.exception.HostException;
import me.vripper.exception.XpathException;
import me.vripper.jpa.domain.Image;
import me.vripper.services.HostService;
import me.vripper.services.XpathService;
import org.apache.http.client.protocol.HttpClientContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@Service
@Slf4j
public class TurboImageHost extends Host {

  private static final String host = "turboimagehost.com";
  private static final String TITLE_XPATH = "//div[contains(@class,'titleFullS')]/h1";
  private static final String IMG_XPATH = "//img[@id='imageid']";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public TurboImageHost(HostService hostService, XpathService xpathService) {
    this.hostService = hostService;
    this.xpathService = xpathService;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public String getLookup() {
    return host;
  }

  @Override
  public HostService.NameUrl getNameAndUrl(final Image image, final HttpClientContext context)
      throws HostException {

    Document doc = hostService.getResponse(image.getUrl(), context).getDocument();

    String title;
    try {
      log.debug(
          String.format("Looking for xpath expression %s in %s", TITLE_XPATH, image.getUrl()));
      Node titleNode = xpathService.getAsNode(doc, TITLE_XPATH);
      log.debug(String.format("Resolving name for %s", image.getUrl()));
      if (titleNode != null) {
        title = titleNode.getTextContent().trim();
      } else {
        title = null;
      }
    } catch (XpathException e) {
      throw new HostException(e);
    }

    if (title == null || title.isEmpty()) {
      title = hostService.getDefaultImageName(image.getUrl());
    }

    try {
      Node urlNode = xpathService.getAsNode(doc, IMG_XPATH);
      return new HostService.NameUrl(
          title, urlNode.getAttributes().getNamedItem("src").getTextContent().trim());
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
