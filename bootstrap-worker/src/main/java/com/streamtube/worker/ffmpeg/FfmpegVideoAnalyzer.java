package com.streamtube.worker.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.VideoAnalyzer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** FFprobe/FFmpeg implementation via {@link ProcessBuilder}. */
@Component
public class FfmpegVideoAnalyzer implements VideoAnalyzer {

  private static final long TIMEOUT_SECONDS = 120;

  private final ObjectMapper objectMapper;

  public FfmpegVideoAnalyzer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public ProbeResult probe(String inputUrl) {
    byte[] out =
        run(
            List.of(
                "ffprobe",
                "-v",
                "quiet",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                inputUrl),
            null);
    try {
      String json = new String(out);
      JsonNode root = objectMapper.readTree(json);
      JsonNode duration = root.path("format").path("duration");
      Double seconds = duration.isMissingNode() ? null : duration.asDouble();
      return new ProbeResult(seconds, json);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse ffprobe output", e);
    }
  }

  @Override
  public byte[] extractThumbnail(String inputUrl) {
    try {
      Path tmp = Files.createTempFile("thumb-", ".jpg");
      try {
        run(
            List.of(
                "ffmpeg",
                "-i",
                inputUrl,
                "-vf",
                "thumbnail,scale=1280:-1",
                "-frames:v",
                "1",
                "-y",
                tmp.toString()),
            null);
        return Files.readAllBytes(tmp);
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to extract thumbnail", e);
    }
  }

  private byte[] run(List<String> command, Path workingDir) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (workingDir != null) {
        pb.directory(workingDir.toFile());
      }
      pb.redirectErrorStream(false);
      Process process = pb.start();

      byte[] stdout;
      try (InputStream is = process.getInputStream()) {
        stdout = readAll(is);
      }
      boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Process timed out: " + command.get(0));
      }
      if (process.exitValue() != 0) {
        throw new IllegalStateException(
            command.get(0) + " exited with code " + process.exitValue());
      }
      return stdout;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted running " + command.get(0), e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed running " + command.get(0), e);
    }
  }

  private byte[] readAll(InputStream is) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = is.read(chunk)) != -1) {
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }
}
