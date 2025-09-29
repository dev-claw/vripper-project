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
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@Service
@Slf4j
public class ImageBamHost extends Host {

  private static final String host = "imagebam.com";
  private static final String IMG_XPATH = "//img[contains(@class,'main-image')]";
  private static final String CONTINUE_XPATH = "//*[contains(text(), 'Continue')]";

  private final HostService hostService;
  private final XpathService xpathService;

  @Autowired
  public ImageBamHost(HostService hostService, XpathService xpathService) {
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
    context.setCookieStore(new BasicCookieStore());
    BasicClientCookie cookie = new BasicClientCookie("nsfw_inter", "1");
    cookie.setDomain("www.imagebam.com");
    context.getCookieStore().addCookie(cookie);

    HostService.Response response = hostService.getResponse(image.getUrl(), context);
    Document doc = response.getDocument();

    try {
      log.debug(
          String.format("Looking for xpath expression %s in %s", CONTINUE_XPATH, image.getUrl()));
      if (xpathService.getAsNode(doc, CONTINUE_XPATH) != null) {
        // Button detected. No need to actually click it, just make the call again.
        response = hostService.getResponse(image.getUrl(), context);
        doc = response.getDocument();
      }
    } catch (XpathException e) {
      throw new HostException(e);
    }

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
