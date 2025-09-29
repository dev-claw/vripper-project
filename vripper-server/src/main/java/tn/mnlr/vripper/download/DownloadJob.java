package tn.mnlr.vripper.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.function.CheckedRunnable;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.AbstractExecutionAwareRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.util.EntityUtils;
import tn.mnlr.vripper.SpringContext;
import tn.mnlr.vripper.exception.DownloadException;
import tn.mnlr.vripper.exception.HostException;
import tn.mnlr.vripper.jpa.domain.Image;
import tn.mnlr.vripper.jpa.domain.Post;
import tn.mnlr.vripper.jpa.domain.enums.Status;
import tn.mnlr.vripper.services.*;
import tn.mnlr.vripper.services.domain.Settings;

@Slf4j
public class DownloadJob implements CheckedRunnable {

  private static final Object LOCK = new Object();
  private static final int READ_BUFFER_SIZE = 8192;
  private final DataService dataService;
  private final PathService pathService;
  private final ConnectionService cm;
  private final VGAuthService authService;
  private final DownloadSpeedService downloadSpeedService;
  private final Settings settings;
  private final HttpClientContext context;
  @Getter private final Image image;
  @Getter private final Post post;
  private volatile boolean stopped = false;
  @Getter private volatile boolean finished = false;

  DownloadJob(Post post, Image image, Settings settings) {
    this.image = image;
    this.post = post;
    this.settings = settings;
    dataService = SpringContext.getBean(DataService.class);
    pathService = SpringContext.getBean(PathService.class);
    cm = SpringContext.getBean(ConnectionService.class);
    authService = SpringContext.getBean(VGAuthService.class);
    downloadSpeedService = SpringContext.getBean(DownloadSpeedService.class);
    context = HttpClientContext.create();
    context.setCookieStore(new BasicCookieStore());
    context.setAttribute(
        ContextAttributes.OPEN_CONNECTION.toString(),
        Collections.synchronizedList(new ArrayList<AbstractExecutionAwareRequest>()));
  }

  public void download(final Post post, final Image image) throws DownloadException {

    try {

      image.setStatus(Status.DOWNLOADING);
      image.setCurrent(0);
      dataService.updateImageStatus(image.getStatus(), image.getId());
      dataService.updateImageCurrent(image.getCurrent(), image.getId());

      synchronized (LOCK) {
        if (!post.getStatus().equals(Status.DOWNLOADING)
            && !post.getStatus().equals(Status.PARTIAL)) {
          post.setStatus(Status.DOWNLOADING);
          dataService.updatePostStatus(post.getStatus(), post.getId());
        }

        // The post may be updated and the download directory might be set by another thread
        Post updatedPost = dataService.findById(post.getId()).orElseThrow();
        if (updatedPost.getDownloadDirectory() == null) {
          pathService.createDefaultPostFolder(updatedPost, settings);
        }
        if (settings.getLeaveThanksOnStart() != null && settings.getLeaveThanksOnStart()) {
          authService.leaveThanks(updatedPost);
        }
      }

      if (stopped) {
        return;
      }
      /*
       * HOST SPECIFIC
       */
      log.debug(
          String.format(
              "Getting image url and name from %s using %s", image.getUrl(), image.getHost()));
      HostService.NameUrl nameAndUrl = image.getHost().getNameAndUrl(image, context);
      log.debug(String.format("Resolved name for %s: %s", image.getUrl(), nameAndUrl.getName()));
      log.debug(
          String.format("Resolved image url for %s: %s", image.getUrl(), nameAndUrl.getUrl()));
      /*
       * END HOST SPECIFIC
       */

      if (stopped) {
        return;
      }
      String formatImageFileName = pathService.formatImageFileName(nameAndUrl.getName());
      log.debug(
          String.format(
              "Sanitizing image name from %s to %s", nameAndUrl.getName(), formatImageFileName));
      nameAndUrl = new HostService.NameUrl(formatImageFileName, nameAndUrl.getUrl());

      HttpClient client = cm.getClient().build();

      log.debug(String.format("Downloading %s", nameAndUrl.getUrl()));
      HttpGet httpGet = cm.buildHttpGet(nameAndUrl.getUrl(), context);
      httpGet.addHeader("Referer", image.getUrl());
      try (CloseableHttpResponse response =
          (CloseableHttpResponse) client.execute(httpGet, context)) {

        if (response.getStatusLine().getStatusCode() / 100 != 2) {
          EntityUtils.consumeQuietly(response.getEntity());
          throw new DownloadException(
              String.format("Server returned code %d", response.getStatusLine().getStatusCode()));
        }
        if (stopped) {
          return;
        }
        File outputFile = Files.createTempFile("vripper", "tmp").toFile();
        try (InputStream downloadStream = response.getEntity().getContent();
            FileOutputStream fos = new FileOutputStream(outputFile)) {

          if (stopped) {
            return;
          }

          image.setTotal(response.getEntity().getContentLength());
          dataService.updateImageTotal(image.getTotal(), image.getId());

          log.debug(String.format("%s length is %d", nameAndUrl.getUrl(), image.getTotal()));
          log.debug(String.format("Starting data transfer for %s", nameAndUrl.getUrl()));

          byte[] buffer = new byte[READ_BUFFER_SIZE];
          int read;
          while ((read = downloadStream.read(buffer, 0, READ_BUFFER_SIZE)) != -1 && !stopped) {
            fos.write(buffer, 0, read);
            image.increase(read);
            downloadSpeedService.increase(read);
            dataService.updateImageCurrent(image.getCurrent(), image.getId());
          }
          fos.flush();
          EntityUtils.consumeQuietly(response.getEntity());
          if (stopped) {
            return;
          }
        }
        checkImageTypeAndRename(
            dataService.findById(post.getId()).orElseThrow(),
            outputFile,
            nameAndUrl.getName(),
            image.getIndex());
      }
    } catch (Exception e) {
      if (stopped) {
        return;
      }
      throw new DownloadException(e);
    } finally {
      if (image.getCurrent() == image.getTotal() && image.getTotal() > 0) {
        image.setStatus(Status.COMPLETE);
      } else if (stopped) {
        image.setStatus(Status.STOPPED);
      } else {
        image.setStatus(Status.ERROR);
      }
      dataService.updateImageStatus(image.getStatus(), image.getId());
      finished = true;
    }
  }

  private void checkImageTypeAndRename(Post post, File outputFile, String imageName, int index)
      throws HostException {
    try (ImageInputStream iis = ImageIO.createImageInputStream(outputFile)) {
      Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
      if (!it.hasNext()) {
        throw new HostException("Image file is not recognized!");
      }
      ImageReader reader = it.next();
      if (reader.getFormatName().equalsIgnoreCase("JPEG")) {
        String imageNameLC = imageName.toLowerCase();
        if (!imageNameLC.endsWith("_jpg") && !imageNameLC.endsWith("_jpeg")) {
          imageName += ".jpg";
        } else {
          String toReplace = null;
          if (imageNameLC.endsWith("_jpg")) {
            toReplace = "_jpg";
          } else if (imageNameLC.endsWith("_jpeg")) {
            toReplace = "_jpeg";
          }
          if (toReplace != null) {
            imageName = imageName.substring(0, imageName.length() - toReplace.length()) + ".jpg";
          }
        }
      } else if (reader.getFormatName().equalsIgnoreCase("PNG")) {
        String imageNameLC = imageName.toLowerCase();
        if (!imageNameLC.endsWith("_png")) {
          imageName += ".png";
        } else {
          String toReplace = null;
          if (imageNameLC.endsWith("_png")) {
            toReplace = "_png";
          }
          if (toReplace != null) {
            imageName = imageName.substring(0, imageName.length() - toReplace.length()) + ".png";
          }
        }
      }
    } catch (Exception e) {
      throw new HostException("Failed to guess image format", e);
    }
    try {
      pathService.getDirectoryAccess().lock();
      File downloadDestinationFolder = pathService.calcDownloadDirectory(post, settings);
      File outImage =
          new File(
              downloadDestinationFolder,
              (settings.getForceOrder() ? String.format("%03d_", index) : "") + imageName);
      Files.copy(outputFile.toPath(), outImage.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new HostException("Failed to rename the image", e);
    } finally {
      pathService.getDirectoryAccess().unlock();
      try {
        Files.delete(outputFile.toPath());
      } catch (IOException e) {
        log.warn(String.format("Failed to delete temporary file %s", outputFile.getAbsolutePath()));
      }
    }
  }

  @Override
  public void run() throws Exception {
    if (stopped) {
      finished = true;
      return;
    }
    log.debug(String.format("Starting downloading %s", image.getUrl()));
    download(post, image);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DownloadJob that = (DownloadJob) o;
    return Objects.equals(image, that.image) && Objects.equals(post, that.post);
  }

  @Override
  public int hashCode() {
    return Objects.hash(image, post);
  }

  public void stop() {
    this.stopped = true;
    List<AbstractExecutionAwareRequest> requests =
        (List<AbstractExecutionAwareRequest>)
            this.context.getAttribute(ContextAttributes.OPEN_CONNECTION.toString());
    if (requests != null) {
      for (AbstractExecutionAwareRequest request : requests) {
        request.abort();
      }
    }
  }

  public enum ContextAttributes {
    OPEN_CONNECTION("OPEN_CONNECTION");

    private final String value;

    ContextAttributes(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
