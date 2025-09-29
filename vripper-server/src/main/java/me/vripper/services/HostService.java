package me.vripper.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.vripper.exception.HostException;
import me.vripper.exception.HtmlProcessorException;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

@Service
@Slf4j
public class HostService {

  private final ConnectionService cm;
  private final HtmlProcessorService htmlProcessorService;

  @Autowired
  public HostService(ConnectionService cm, HtmlProcessorService htmlProcessorService) {
    this.cm = cm;
    this.htmlProcessorService = htmlProcessorService;
  }

  public Response getResponse(final String url, final HttpClientContext context)
      throws HostException {
    String basePage;

    HttpClient client = cm.getClient().build();
    HttpGet httpGet = cm.buildHttpGet(url, context);
    Header[] headers;
    log.debug(String.format("Requesting %s", url));
    try (CloseableHttpResponse response =
        (CloseableHttpResponse) client.execute(httpGet, context)) {
      if (response.getStatusLine().getStatusCode() / 100 != 2) {
        throw new HostException(
            String.format(
                "Unexpected response code: %d", response.getStatusLine().getStatusCode()));
      }
      headers = response.getAllHeaders();
      basePage = EntityUtils.toString(response.getEntity());
      log.debug(String.format("%s response: %n%s", url, basePage));
      EntityUtils.consumeQuietly(response.getEntity());
    } catch (IOException e) {
      throw new HostException(e);
    }

    try {
      log.debug(String.format("Cleaning %s response", url));
      return new Response(htmlProcessorService.clean(basePage), headers);
    } catch (HtmlProcessorException e) {
      throw new HostException(e);
    }
  }

  public String appendUri(String uri, String appendQuery) throws URISyntaxException {
    URI oldUri = new URI(uri);

    String newQuery = oldUri.getQuery();
    if (newQuery == null) {
      newQuery = appendQuery;
    } else {
      newQuery += "&" + appendQuery;
    }

    return new URI(
            oldUri.getScheme(),
            oldUri.getAuthority(),
            oldUri.getPath(),
            newQuery,
            oldUri.getFragment())
        .toString();
  }

  public String getDefaultImageName(final String imgUrl) {
    String imageTitle = imgUrl.substring(imgUrl.lastIndexOf('/') + 1);
    log.debug(String.format("Extracting name from url %s: %s", imgUrl, imageTitle));
    return imgUrl;
  }

  @Getter
  public static class Response {

    private final Document document;
    private final Header[] headers;

    protected Response(Document document, Header[] headers) {
      this.document = document;
      this.headers = headers;
    }
  }

  @Getter
  public static class NameUrl {

    private final String name;
    private final String url;

    public NameUrl(String name, String url) {
      this.name = name;
      this.url = url;
    }
  }
}
