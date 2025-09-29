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
public class PixRouteHost extends Host {

  private static final String host = "pixroute.com";
  private static final String IMG_XPATH = "//img[@id='imgpreview']";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public PixRouteHost(HostService hostService, XpathService xpathService) {
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

    String url = image.getUrl().replace("http://", "https://");
    Document doc = hostService.getResponse(url, context).getDocument();

    Node imgNode;
    try {
      log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url));
      imgNode = xpathService.getAsNode(doc, IMG_XPATH);
    } catch (XpathException e) {
      throw new HostException(e);
    }

    if (imgNode == null) {
      throw new HostException("Failed to locate image");
    }

    try {
      log.debug(String.format("Resolving name and image url for %s", url));

      return new HostService.NameUrl(
          imgNode.getAttributes().getNamedItem("alt").getTextContent().trim(),
          imgNode.getAttributes().getNamedItem("src").getTextContent().trim());
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
