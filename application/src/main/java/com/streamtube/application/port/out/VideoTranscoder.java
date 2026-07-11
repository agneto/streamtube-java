package com.streamtube.application.port.out;

import java.nio.file.Path;
import java.util.List;

/**
 * Worker-only output port: renders the HLS ladder for a source video. Like {@link VideoAnalyzer},
 * its implementation lives in the worker (FFmpeg) and is never wired in the API context.
 */
public interface VideoTranscoder {

  /**
   * Transcodes {@code inputUrl} into an HLS ladder under {@code workDir}: one directory per
   * rendition ({@code {height}p/playlist.m3u8} + TS segments) plus {@code master.m3u8} at the
   * root. The ladder is derived from {@code sourceHeight} and never upscales.
   *
   * @return the rendition names actually produced (e.g. {@code ["720p", "480p", "360p"]})
   */
  List<String> transcodeToHls(String inputUrl, Path workDir, int sourceHeight);
}
