package com.streamtube.infrastructure.messaging;

/** Names for the video-processing exchange/queue/routing (shared by publisher and listener). */
public final class VideoQueue {

  public static final String EXCHANGE = "video.exchange";
  public static final String ROUTING_KEY = "video.process";
  public static final String QUEUE = "video.processing";
  public static final String DLX = "video.dlx";
  public static final String DLQ = "video.processing.dlq";

  private VideoQueue() {}
}
