package tn.mnlr.vripper.host;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.protocol.HttpClientContext;
import org.springframework.stereotype.Service;
import tn.mnlr.vripper.exception.HostException;
import tn.mnlr.vripper.jpa.domain.Image;
import tn.mnlr.vripper.services.HostService;

@Service
@Slf4j
public class ImxHost extends Host {

  private static final String host = "imx.to";

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

    log.debug(String.format("Resolving name and image url for %s", image.getUrl()));
    String imgTitle = String.format("IMG_%04d", image.getIndex() + 1);
    String imgUrl =
        image
            .getThumbUrl()
            .replace("http:", "https:")
            .replace("upload/small/", "u/i/")
            .replace("u/t/", "u/i/");
    return new HostService.NameUrl(imgTitle, imgUrl);
  }
}
