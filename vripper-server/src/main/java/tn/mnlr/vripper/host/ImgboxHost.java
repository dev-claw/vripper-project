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
public class ImgboxHost extends Host {

  private static final String host = "imgbox.com";
  private static final String IMG_XPATH = "//img[@id='img']";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public ImgboxHost(HostService hostService, XpathService xpathService) {
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

    Node imgNode;
    try {
      log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, image.getUrl()));
      imgNode = xpathService.getAsNode(doc, IMG_XPATH);
    } catch (XpathException e) {
      throw new HostException(e);
    }

    try {
      log.debug(String.format("Resolving name and image url for %s", image.getUrl()));
      String imgTitle = imgNode.getAttributes().getNamedItem("title").getTextContent().trim();
      String imgUrl = imgNode.getAttributes().getNamedItem("src").getTextContent().trim();

      return new HostService.NameUrl(imgTitle, imgUrl);
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
