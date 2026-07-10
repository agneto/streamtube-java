package com.streamtube.worker.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.application.port.out.VideoAnalyzer.ProbeResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the ffprobe/ffmpeg output handling with the process execution faked out. */
class FfmpegVideoAnalyzerTest {

  private static final byte[] THUMBNAIL = {9, 9, 9};

  /** Fakes the external processes: canned stdout for ffprobe, file output for ffmpeg. */
  private static final class FakeProcessAnalyzer extends FfmpegVideoAnalyzer {
    private final String probeJson;

    FakeProcessAnalyzer(String probeJson) {
      this.probeJson = probeJson;
    }

    @Override
    protected byte[] run(List<String> command, Path workingDir) {
      if ("ffprobe".equals(command.get(0))) {
        return probeJson.getBytes(StandardCharsets.UTF_8);
      }
      try {
        Files.write(Path.of(command.get(command.size() - 1)), THUMBNAIL);
        return new byte[0];
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Test
  void probeParsesDurationHeightAndKeepsRawJson() {
    String json =
        "{\"format\":{\"duration\":\"12.5\"},\"streams\":["
            + "{\"codec_type\":\"audio\"},"
            + "{\"codec_type\":\"video\",\"height\":720}]}";

    ProbeResult result = new FakeProcessAnalyzer(json).probe("http://input");

    assertThat(result.durationSeconds()).isEqualTo(12.5);
    assertThat(result.height()).isEqualTo(720); // first VIDEO stream, audio skipped
    assertThat(result.rawJson()).isEqualTo(json);
  }

  @Test
  void probeReturnsNullDurationAndHeightWhenFfprobeOmitsThem() {
    ProbeResult result = new FakeProcessAnalyzer("{\"format\":{}}").probe("http://input");

    assertThat(result.durationSeconds()).isNull();
    assertThat(result.height()).isNull();
  }

  @Test
  void probeFailsOnUnparseableOutput() {
    assertThatThrownBy(() -> new FakeProcessAnalyzer("not-json{").probe("http://input"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void extractThumbnailReturnsBytesWrittenByFfmpeg() {
    byte[] thumbnail = new FakeProcessAnalyzer("{}").extractThumbnail("http://input");

    assertThat(thumbnail).isEqualTo(THUMBNAIL);
  }
}
