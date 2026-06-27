package com.streamtube.domain.video;

/** Lifecycle of a video from upload initiation to playable (or failed). */
public enum VideoStatus {
  PENDING_UPLOAD,
  QUEUED,
  PROCESSING,
  READY,
  ERROR
}
