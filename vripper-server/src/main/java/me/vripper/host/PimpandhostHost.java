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
public class PimpandhostHost extends Host {

  private static final String host = "pimpandhost.com";
  private static final String IMG_XPATH = "//img[contains(@class, 'original')]";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public PimpandhostHost(HostService hostService, XpathService xpathService) {
    super();
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

    String url;
    try {
      url = hostService.appendUri(image.getUrl().replace("http://", "https://"), "size=original");
    } catch (Exception e) {
      throw new HostException(e);
    }

    HostService.Response resp = hostService.getResponse(url, context);
    Document doc = resp.getDocument();

    Node imgNode;
    try {
      log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url));
      imgNode = xpathService.getAsNode(doc, IMG_XPATH);
    } catch (XpathException e) {
      throw new HostException(e);
    }

    try {
      log.debug(String.format("Resolving name and image url for %s", url));
      String imgTitle = imgNode.getAttributes().getNamedItem("alt").getTextContent().trim();
      String imgUrl =
          "https:" + imgNode.getAttributes().getNamedItem("src").getTextContent().trim();

      return new HostService.NameUrl(
          imgTitle.isEmpty() ? imgUrl.substring(imgUrl.lastIndexOf('/') + 1) : imgTitle, imgUrl);
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
