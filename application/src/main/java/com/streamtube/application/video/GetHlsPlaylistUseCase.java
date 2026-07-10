package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.shared.VideoExceptions.VideoNotFoundException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves one rendition's playlist with every segment line rewritten to a presigned URL. Segment
 * URLs use their own (long) TTL: a VOD player fetches this playlist once and must be able to play
 * to the end — the default 1 h read TTL would 403 a long video mid-playback.
 */
@Service
public class GetHlsPlaylistUseCase {

  private static final Pattern RENDITION = Pattern.compile("[0-9]{3,4}p");

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final VideoViewAccess access;
  private final long segmentUrlTtlSeconds;

  public GetHlsPlaylistUseCase(
      VideoRepository videoRepository,
      StoragePort storage,
      VideoViewAccess access,
      @Value("${hls.segment-url-ttl-seconds:21600}") long segmentUrlTtlSeconds) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.access = access;
    this.segmentUrlTtlSeconds = segmentUrlTtlSeconds;
  }

  @Transactional(readOnly = true)
  public String execute(String slug, String rendition, UUID viewerUserId) {
    Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
    access.ensureViewable(video, viewerUserId);
    if (video.hlsMasterKey() == null || !RENDITION.matcher(rendition).matches()) {
      throw new VideoNotFoundException();
    }
    String prefix = "hls/" + slug + "/" + rendition + "/";
    String key = prefix + "playlist.m3u8";
    if (!storage.objectExists(key)) {
      throw new VideoNotFoundException(); // rendition not part of this video's ladder
    }
    return storage
        .getObjectText(key)
        .lines()
        .map(
            line ->
                GetHlsMasterUseCase.isUriLine(line)
                    ? storage.presignStream(prefix + line, segmentUrlTtlSeconds)
                    : line)
        .collect(Collectors.joining("\n", "", "\n"));
  }
}
