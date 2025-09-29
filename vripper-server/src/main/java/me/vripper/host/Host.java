package me.vripper.host;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import me.vripper.exception.HostException;
import me.vripper.jpa.domain.Image;
import me.vripper.services.HostService;
import org.apache.http.client.protocol.HttpClientContext;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public abstract class Host {

  public abstract String getHost();

  public abstract String getLookup();

  public boolean isSupported(String url) {
    return url.contains(getLookup());
  }

  public abstract HostService.NameUrl getNameAndUrl(
      final Image image, final HttpClientContext context) throws HostException;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Host host = (Host) o;
    return Objects.equals(getHost(), host.getHost());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHost());
  }

  @Override
  public String toString() {
    return getHost();
  }
}
