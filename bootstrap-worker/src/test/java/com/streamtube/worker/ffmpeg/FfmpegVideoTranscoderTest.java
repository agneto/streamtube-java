package com.streamtube.worker.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ladder selection (never upscale, even heights) and master playlist assembly — no ffmpeg. */
class FfmpegVideoTranscoderTest {

  @Test
  void ladderNeverUpscales() {
    assertThat(FfmpegVideoTranscoder.ladderFor(1080)).containsExactly(720, 480, 360);
    assertThat(FfmpegVideoTranscoder.ladderFor(720)).containsExactly(720, 480, 360);
    assertThat(FfmpegVideoTranscoder.ladderFor(480)).containsExactly(480, 360);
    assertThat(FfmpegVideoTranscoder.ladderFor(360)).containsExactly(360);
    assertThat(FfmpegVideoTranscoder.ladderFor(240)).containsExactly(240);
    assertThat(FfmpegVideoTranscoder.ladderFor(241)).containsExactly(240); // forced even
  }

  @Test
  void writesOneRenditionPerLadderStepAndAMasterPlaylist(@TempDir Path workDir) throws Exception {
    List<List<String>> commands = new ArrayList<>();
    FfmpegVideoTranscoder transcoder =
        new FfmpegVideoTranscoder() {
          @Override
          protected void run(List<String> command, Path dir) {
            commands.add(command); // ffmpeg stubbed out
          }
        };

    List<String> renditions = transcoder.transcodeToHls("http://minio/in", workDir, 480);

    assertThat(renditions).containsExactly("480p", "360p");
    assertThat(commands).hasSize(2);
    assertThat(commands.get(0)).contains("scale=-2:480");
    assertThat(commands.get(1)).contains("scale=-2:360");
    String master = Files.readString(workDir.resolve("master.m3u8"));
    assertThat(master)
        .startsWith("#EXTM3U")
        .contains("#EXT-X-STREAM-INF:BANDWIDTH=1528000")
        .contains("480p/playlist.m3u8")
        .contains("360p/playlist.m3u8");
  }
}
