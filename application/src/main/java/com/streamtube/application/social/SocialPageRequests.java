package com.streamtube.application.social;

/** Same pagination clamps as the video listings: page ≥ 0, size in [1, 100]. */
final class SocialPageRequests {

  static final int MAX_PAGE_SIZE = 100;

  private SocialPageRequests() {}

  static int page(int page) {
    return Math.max(page, 0);
  }

  static int size(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
