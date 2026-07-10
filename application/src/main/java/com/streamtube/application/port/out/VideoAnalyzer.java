package com.streamtube.application.port.out;

/** Output port for video probing and thumbnail extraction (FFprobe/FFmpeg in infrastructure). */
public interface VideoAnalyzer {

  ProbeResult probe(String inputUrl);

  byte[] extractThumbnail(String inputUrl);

  /** {@code height} is the source's video-stream height (drives the HLS ladder); may be null. */
  record ProbeResult(Double durationSeconds, Integer height, String rawJson) {}
}
