package com.streamtube.application.video;

/** Normalizes client-supplied pagination: negative pages clamp to 0, size clamps to [1, 100]. */
final class PageRequests {

  static final int MAX_PAGE_SIZE = 100;

  private PageRequests() {}

  static int page(int page) {
    return Math.max(page, 0);
  }

  static int size(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
