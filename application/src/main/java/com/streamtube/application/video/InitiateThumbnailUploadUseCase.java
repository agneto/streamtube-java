package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.ForbiddenVideoAccessException;
import com.streamtube.domain.shared.VideoExceptions.InvalidUploadSizeException;
import com.streamtube.domain.shared.VideoExceptions.UnsupportedThumbnailTypeException;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a presigned URL for the owner to upload a custom thumbnail directly to storage. As with
 * video uploads, the declared size and content type are baked into the signature so storage rejects
 * a mismatching PUT. The key is deterministic ({@code thumbnails/{slug}-custom}), so re-uploads
 * overwrite instead of accumulating objects; it only becomes the video's thumbnail on complete.
 */
@Service
public class InitiateThumbnailUploadUseCase {

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;
  private final long maxThumbnailSizeBytes;

  public InitiateThumbnailUploadUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      StoragePort storage,
      @Value("${upload.thumbnail-max-size-bytes}") long maxThumbnailSizeBytes) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
    this.maxThumbnailSizeBytes = maxThumbnailSizeBytes;
  }

  @Transactional(readOnly = true)
  public String execute(UUID videoId, UUID userId, long sizeBytes, String contentType) {
    Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

    Channel channel =
        channelRepository.findByUserId(userId).orElseThrow(ForbiddenVideoAccessException::new);
    if (!video.channelId().equals(channel.id())) {
      throw new ForbiddenVideoAccessException();
    }

    if (sizeBytes <= 0 || sizeBytes > maxThumbnailSizeBytes) {
      throw new InvalidUploadSizeException();
    }
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
      throw new UnsupportedThumbnailTypeException();
    }

    return storage.presignUpload(customThumbnailKey(video), sizeBytes, contentType);
  }

  static String customThumbnailKey(Video video) {
    return "thumbnails/" + video.slug() + "-custom";
  }
}
