package me.vripper.host;

import java.util.Optional;
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
public class PostImgHost extends Host {

  private static final String host = "postimg.cc";
  private static final String TITLE_XPATH = "//span[contains(@class,'imagename')]";
  private static final String IMG_XPATH = "//a[@id='download']";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public PostImgHost(HostService hostService, XpathService xpathService) {
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

    Node urlNode, titleNode;
    try {
      log.debug(String.format("Looking for xpath expression %s in %s", TITLE_XPATH, url));
      titleNode = xpathService.getAsNode(doc, TITLE_XPATH);

      log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url));
      urlNode = xpathService.getAsNode(doc, IMG_XPATH);
    } catch (XpathException e) {
      throw new HostException(e);
    }

    try {
      log.debug(String.format("Resolving name and image url for %s", url));
      String imgTitle =
          Optional.ofNullable(titleNode)
              .map(node -> node.getTextContent().trim())
              .orElseGet(() -> hostService.getDefaultImageName(url));

      return new HostService.NameUrl(
          imgTitle, urlNode.getAttributes().getNamedItem("href").getTextContent().trim());
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
