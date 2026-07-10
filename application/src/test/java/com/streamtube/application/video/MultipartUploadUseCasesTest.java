package com.streamtube.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.UploadedPart;
import com.streamtube.application.port.out.VideoProcessingPublisher;
import com.streamtube.application.video.result.InitiateMultipartResult;
import com.streamtube.application.video.result.PartUrlView;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.InvalidPartNumbersException;
import com.streamtube.domain.shared.VideoExceptions.NoActiveUploadException;
import com.streamtube.domain.shared.VideoExceptions.UploadNotCompletedException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Session rules, part-length math (last part = remainder) and the complete-time guards. */
class MultipartUploadUseCasesTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private static final long PART = 8L * 1024 * 1024; // 8 MiB
  private static final long SIZE = 20_000_000L; // 3 parts: 8 MiB, 8 MiB, remainder

  private VideoRepository videos;
  private ChannelRepository channels;
  private StoragePort storage;
  private VideoProcessingPublisher publisher;
  private VideoOwnership ownership;
  private UUID userId;
  private UUID channelId;
  private UUID videoId;

  @BeforeEach
  void setUp() {
    videos = Mockito.mock(VideoRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    storage = Mockito.mock(StoragePort.class);
    publisher = Mockito.mock(VideoProcessingPublisher.class);
    ownership = new VideoOwnership(videos, channels);
    userId = UUID.randomUUID();
    channelId = UUID.randomUUID();
    videoId = UUID.randomUUID();
    when(channels.findByUserId(userId))
        .thenReturn(Optional.of(Channel.createForUser(channelId, userId, "name", "nick", NOW)));
    when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Video videoWithSession() {
    Video video = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    video.beginMultipartUpload("up-1", SIZE, PART, NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));
    return video;
  }

  @Test
  void initiateOpensSessionAndComputesParts() {
    var slugGenerator = Mockito.mock(com.streamtube.application.port.out.SlugGenerator.class);
    when(slugGenerator.generate()).thenReturn("slug123");
    when(storage.createMultipartUpload("videos/slug123", "video/mp4")).thenReturn("up-1");
    var useCase =
        new InitiateMultipartUploadUseCase(
            videos,
            channels,
            storage,
            new UniqueSlugs(slugGenerator, videos),
            Clock.fixed(NOW, ZoneOffset.UTC),
            2L * 1024 * 1024 * 1024,
            PART);

    InitiateMultipartResult result = useCase.execute(userId, "Grande", SIZE, "video/mp4");

    assertThat(result.partSizeBytes()).isEqualTo(PART);
    assertThat(result.totalParts()).isEqualTo(3);
    verify(videos).save(any());
  }

  @Test
  void partUrlsSignExactLengthsAndValidateRange() {
    videoWithSession();
    when(storage.presignUploadPart(anyString(), anyString(), anyInt(), anyLong()))
        .thenAnswer(inv -> "url-" + inv.getArgument(2));
    var useCase = new IssuePartUrlsUseCase(ownership, storage);

    List<PartUrlView> urls = useCase.execute(videoId, userId, List.of(1, 3));

    assertThat(urls.get(0).contentLengthBytes()).isEqualTo(PART);
    assertThat(urls.get(1).contentLengthBytes()).isEqualTo(SIZE - 2 * PART); // remainder
    assertThatThrownBy(() -> useCase.execute(videoId, userId, List.of(0)))
        .isInstanceOf(InvalidPartNumbersException.class);
    assertThatThrownBy(() -> useCase.execute(videoId, userId, List.of(4)))
        .isInstanceOf(InvalidPartNumbersException.class);
  }

  @Test
  void partUrlsWithoutSessionAre409() {
    Video video = Video.initiate(videoId, channelId, "T", "slug123", "videos/slug123", NOW);
    when(videos.findById(videoId)).thenReturn(Optional.of(video));

    assertThatThrownBy(
            () -> new IssuePartUrlsUseCase(ownership, storage).execute(videoId, userId, List.of(1)))
        .isInstanceOf(NoActiveUploadException.class);
  }

  @Test
  void completeRejectsMissingParts() {
    videoWithSession();
    when(storage.listUploadedParts("videos/slug123", "up-1"))
        .thenReturn(List.of(new UploadedPart(1, PART, "e1")));
    var useCase =
        new CompleteMultipartUploadUseCase(
            ownership,
            storage,
            new QueueForProcessing(videos, publisher),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(UploadNotCompletedException.class);
    verify(storage, never()).completeMultipartUpload(anyString(), anyString(), any());
    verify(publisher, never()).publish(any());
  }

  @Test
  void completeRejectsSizeMismatchAndDeletesTheObject() {
    videoWithSession();
    when(storage.listUploadedParts("videos/slug123", "up-1"))
        .thenReturn(
            List.of(
                new UploadedPart(1, PART, "e1"),
                new UploadedPart(2, PART, "e2"),
                new UploadedPart(3, 1L, "e3"))); // short last part
    when(storage.objectSizeBytes("videos/slug123")).thenReturn(2 * PART + 1);
    var useCase =
        new CompleteMultipartUploadUseCase(
            ownership,
            storage,
            new QueueForProcessing(videos, publisher),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(UploadNotCompletedException.class);
    verify(storage).deleteObject("videos/slug123");
    verify(publisher, never()).publish(any());
  }

  @Test
  void completeQueuesAndPublishesWhenEverythingMatches() {
    Video video = videoWithSession();
    List<UploadedPart> parts =
        List.of(
            new UploadedPart(1, PART, "e1"),
            new UploadedPart(2, PART, "e2"),
            new UploadedPart(3, SIZE - 2 * PART, "e3"));
    when(storage.listUploadedParts("videos/slug123", "up-1")).thenReturn(parts);
    when(storage.objectSizeBytes("videos/slug123")).thenReturn(SIZE);
    var useCase =
        new CompleteMultipartUploadUseCase(
            ownership,
            storage,
            new QueueForProcessing(videos, publisher),
            Clock.fixed(NOW, ZoneOffset.UTC));

    useCase.execute(videoId, userId);

    verify(storage).completeMultipartUpload("videos/slug123", "up-1", parts);
    verify(publisher).publish(videoId);
    assertThat(video.hasActiveUpload()).isFalse();
    assertThat(video.status().name()).isEqualTo("QUEUED");
  }

  @Test
  void abortDiscardsSession() {
    Video video = videoWithSession();
    var useCase =
        new AbortMultipartUploadUseCase(
            ownership, videos, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    useCase.execute(videoId, userId);

    verify(storage).abortMultipartUpload("videos/slug123", "up-1");
    assertThat(video.hasActiveUpload()).isFalse();
    assertThatThrownBy(() -> useCase.execute(videoId, userId))
        .isInstanceOf(NoActiveUploadException.class);
  }
}
