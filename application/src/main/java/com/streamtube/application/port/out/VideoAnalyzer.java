package com.streamtube.application.port.out;

/** Output port for video probing and thumbnail extraction (FFprobe/FFmpeg in infrastructure). */
public interface VideoAnalyzer {

  ProbeResult probe(String inputUrl);

  byte[] extractThumbnail(String inputUrl);

  record ProbeResult(Double durationSeconds, String rawJson) {}
}
