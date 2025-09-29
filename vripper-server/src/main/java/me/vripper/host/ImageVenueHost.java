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
public class ImageVenueHost extends Host {

  private static final String host = "imagevenue.com";
  private static final String CONTINUE_BUTTON_XPATH = "//a[@title='Continue to ImageVenue']";
  private static final String IMG_XPATH = "//a[@data-toggle='full']/img";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public ImageVenueHost(HostService hostService, XpathService xpathService) {
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

    HostService.Response resp = hostService.getResponse(url, context);
    Document doc = resp.getDocument();

    try {
      log.debug(String.format("Looking for xpath expression %s in %s", CONTINUE_BUTTON_XPATH, url));
      if (xpathService.getAsNode(doc, CONTINUE_BUTTON_XPATH) != null) {
        // Button detected. No need to actually click it, just make the call again.
        resp = hostService.getResponse(url, context);
        doc = resp.getDocument();
      }
    } catch (XpathException e) {
      throw new HostException(e);
    }

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
      String imgTitle = imgNode.getAttributes().getNamedItem("alt").getTextContent().trim();
      String imgUrl = imgNode.getAttributes().getNamedItem("src").getTextContent().trim();

      return new HostService.NameUrl(
          imgTitle.isEmpty() ? imgUrl.substring(imgUrl.lastIndexOf('/') + 1) : imgTitle, imgUrl);
    } catch (Exception e) {
      throw new HostException("Unexpected error occurred", e);
    }
  }
}
