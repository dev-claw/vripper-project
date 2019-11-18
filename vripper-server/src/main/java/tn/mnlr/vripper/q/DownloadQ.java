package tn.mnlr.vripper.q;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.mnlr.vripper.entities.Image;
import tn.mnlr.vripper.entities.Post;
import tn.mnlr.vripper.services.AppStateService;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Service
public class DownloadQ {

    private static final List<Post.Status> FINISHED = Arrays.asList(Post.Status.ERROR, Post.Status.COMPLETE, Post.Status.STOPPED);

    private static final Logger logger = LoggerFactory.getLogger(DownloadQ.class);

    @Autowired
    private AppStateService appStateService;

    @Autowired
    private ExecutionService executionService;

    private BlockingQueue<DownloadJob> downloadQ = new LinkedBlockingQueue<>();

    @Getter
    private boolean notPauseQ = true;

    public void put(Image image) throws InterruptedException {
        synchronized (appStateService) {
            logger.debug(String.format("Enqueuing a job for %s", image.getUrl()));
            image.init();
            DownloadJob downloadJob = new DownloadJob(image);
            downloadQ.put(downloadJob);
            appStateService.newDownloadJob(downloadJob);
        }
    }

    DownloadJob take() throws InterruptedException {
        DownloadJob downloadJob = downloadQ.take();
        logger.debug(String.format("Retrieving a job for %s", downloadJob.getImage().getUrl()));
        return downloadJob;
    }

    public synchronized void enqueue(Post post) throws InterruptedException {
        for (Image image : post.getImages()) {
            put(image);
        }
    }

    public void restart(String postId) throws InterruptedException {
        synchronized (appStateService) {
            if (appStateService.getRunningPosts().get(postId) != null && appStateService.getRunningPosts().get(postId).get() > 0) {
                logger.warn(String.format("Cannot restart, jobs are currently running for post id %s", postId));
                return;
            }
            List<Image> images = appStateService.getPost(postId)
                    .getImages()
                    .stream()
                    .filter(e -> !e.getStatus().equals(Image.Status.COMPLETE))
                    .collect(Collectors.toList());
            if (images.isEmpty()) {
                return;
            }
            appStateService.getPost(postId).setStatus(Post.Status.PENDING);
            logger.debug(String.format("Restarting %d jobs for post id %s", images.size(), postId));
            for (Image image : images) {
                put(image);
            }
        }
    }

    private void removeScheduled(Image image) {
        synchronized (appStateService) {
            image.setStatus(Image.Status.STOPPED);
            logger.debug(String.format("Removing scheduled job for %s", image.getUrl()));

            Iterator<DownloadJob> iterator = downloadQ.iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                DownloadJob next = iterator.next();
                if (next.getImage().getPostId().equals(image.getPostId())) {
                    iterator.remove();
                    appStateService.doneDownloadJob(image);
                    logger.debug(String.format("Scheduled job for %s is removed", image.getUrl()));
                    removed = true;
                    break;
                }
            }

            if (!removed) {
                logger.debug(String.format("Job for %s does not exist", image.getUrl()));
            }

            image.cleanup();
        }
    }

    private void removeRunning(String postId) {
        logger.debug(String.format("Interrupting running jobs for post id %s", postId));
        executionService.stop(postId);
    }


    public void stop(String postId) {
        try {
            synchronized (appStateService) {
                if (FINISHED.contains(appStateService.getPost(postId).getStatus())) {
                    return;
                }
                notPauseQ = false;
                appStateService.getPost(postId).setStatus(Post.Status.STOPPED);
                List<Image> images = appStateService.getPost(postId)
                        .getImages()
                        .stream()
                        .filter(e -> !e.getStatus().equals(Image.Status.COMPLETE))
                        .collect(Collectors.toList());
                if (images.isEmpty()) {
                    return;
                }
                logger.debug(String.format("Stopping %d jobs for post id %s", images.size(), postId));
                images.forEach(this::removeScheduled);
                removeRunning(postId);
            }
        } finally {
            notPauseQ = true;
        }
    }

    public int size() {
        return downloadQ.size();
    }

    public void stopAll() {
        synchronized (appStateService) {
            appStateService.getCurrentPosts().values().stream().map(Post::getPostId).forEach(this::stop);
        }
    }

    public void restartAll() throws InterruptedException {
        synchronized (appStateService) {
            for (Post post : appStateService.getCurrentPosts().values()) {
                String postId = post.getPostId();
                restart(postId);
            }
        }
    }
}
