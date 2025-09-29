package tn.mnlr.vripper.host;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.protocol.HttpClientContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import tn.mnlr.vripper.exception.HostException;
import tn.mnlr.vripper.exception.XpathException;
import tn.mnlr.vripper.jpa.domain.Image;
import tn.mnlr.vripper.services.HostService;
import tn.mnlr.vripper.services.XpathService;

@Service
@Slf4j
public class ImageZillaHost extends Host {

  private static final String host = "imagezilla.net";
  private static final String lookup = "imagezilla.net/show";
  private static final String IMG_XPATH = "//img[@id='photo']";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public ImageZillaHost(HostService hostService, XpathService xpathService) {
    this.hostService = hostService;
    this.xpathService = xpathService;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public String getLookup() {
    return lookup;
  }

  @Override
  public HostService.NameUrl getNameAndUrl(final Image image, final HttpClientContext context)
      throws HostException {

    Document doc = hostService.getResponse(image.getUrl(), context).getDocument();

    String title;
    try {
      log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, image.getUrl()));
      Node titleNode = xpathService.getAsNode(doc, IMG_XPATH).getAttributes().getNamedItem("title");
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
      return new HostService.NameUrl(title, image.getUrl().replace("show", "images"));
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
