package com.streamtube.worker.ffmpeg;

import com.streamtube.application.port.out.VideoTranscoder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * FFmpeg HLS transcoder: one invocation per rendition (H.264 + AAC, 6 s TS segments), then a
 * hand-written master playlist. The ladder never upscales; heights are forced even so libx264
 * accepts any source aspect ({@code scale=-2:h} keeps the width even too).
 */
@Component
public class FfmpegVideoTranscoder implements VideoTranscoder {

  // Transcoding a long video takes real time — nothing like the 2-minute probe/thumbnail budget.
  private static final long TIMEOUT_SECONDS = 1800;

  private static final Map<Integer, String> VIDEO_BITRATE =
      Map.of(720, "2800k", 480, "1400k", 360, "800k");
  private static final Map<Integer, Long> BANDWIDTH_BITS =
      Map.of(720, 2_928_000L, 480, 1_528_000L, 360, 928_000L);

  @Override
  public List<String> transcodeToHls(String inputUrl, Path workDir, int sourceHeight) {
    try {
      List<Integer> heights = ladderFor(sourceHeight);
      List<String> renditions = new ArrayList<>();
      StringBuilder master = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
      for (int height : heights) {
        String rendition = height + "p";
        Path dir = Files.createDirectories(workDir.resolve(rendition));
        run(
            List.of(
                "ffmpeg",
                "-i", inputUrl,
                "-vf", "scale=-2:" + height,
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-b:v", VIDEO_BITRATE.getOrDefault(height, "800k"),
                "-c:a", "aac",
                "-b:a", "128k",
                "-hls_time", "6",
                "-hls_list_size", "0",
                "-hls_segment_filename", dir.resolve("seg-%03d.ts").toString(),
                "-y", dir.resolve("playlist.m3u8").toString()),
            workDir);
        master
            .append("#EXT-X-STREAM-INF:BANDWIDTH=")
            .append(BANDWIDTH_BITS.getOrDefault(height, 928_000L))
            .append("\n")
            .append(rendition)
            .append("/playlist.m3u8\n");
        renditions.add(rendition);
      }
      Files.writeString(workDir.resolve("master.m3u8"), master.toString(), StandardCharsets.UTF_8);
      return renditions;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to transcode HLS ladder", e);
    }
  }

  /** Ladder capped at the source height (never upscale); heights forced even for libx264. */
  static List<Integer> ladderFor(int sourceHeight) {
    if (sourceHeight >= 720) {
      return List.of(720, 480, 360);
    }
    if (sourceHeight >= 480) {
      return List.of(480, 360);
    }
    if (sourceHeight >= 360) {
      return List.of(360);
    }
    return List.of(Math.max(2, sourceHeight & ~1));
  }

  /** Runs the external process; overridable in tests to avoid requiring ffmpeg binaries. */
  protected void run(List<String> command, Path workingDir) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(workingDir.toFile());
      pb.redirectErrorStream(true);
      Process process = pb.start();
      // Drain stdout so ffmpeg never blocks on a full pipe.
      try (InputStream is = process.getInputStream()) {
        while (is.read(new byte[8192]) != -1) {
          // discard
        }
      }
      boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("ffmpeg transcode timed out");
      }
      if (process.exitValue() != 0) {
        throw new IllegalStateException("ffmpeg exited with code " + process.exitValue());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted running ffmpeg", e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed running ffmpeg", e);
    }
  }
}
