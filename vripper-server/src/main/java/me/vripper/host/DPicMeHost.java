package me.vripper.host;

import java.util.Optional;
import java.util.UUID;
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
public class DPicMeHost extends Host {

  private static final String IMG_XPATH = "//img[@id='pic']";
  private static final String host = "dpic.me";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public DPicMeHost(HostService hostService, XpathService xpathService) {
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

    HostService.Response response =
        hostService.getResponse(image.getUrl().replace("http://", "https://"), context);
    Document doc = response.getDocument();

    Node imgNode;
    try {
      log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, image.getUrl()));
      imgNode = xpathService.getAsNode(doc, IMG_XPATH);
    } catch (XpathException e) {
      throw new HostException(e);
    }

    if (imgNode == null) {
      throw new HostException(
          String.format("Xpath '%s' cannot be found in '%s'", IMG_XPATH, image.getUrl()));
    }

    try {
      log.debug(String.format("Resolving name and image url for %s", image.getUrl()));
      String imgTitle =
          Optional.ofNullable(imgNode.getAttributes().getNamedItem("alt"))
              .map(e -> e.getTextContent().trim())
              .orElse("");
      String imgUrl =
          Optional.ofNullable(imgNode.getAttributes().getNamedItem("src"))
              .map(e -> e.getTextContent().trim())
              .orElse("");
      String defaultName = UUID.randomUUID().toString();

      int index = imgUrl.lastIndexOf('/');
      if (index != -1 && index < imgUrl.length()) {
        defaultName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1);
      }

      return new HostService.NameUrl(imgTitle.isEmpty() ? defaultName : imgTitle, imgUrl);
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
