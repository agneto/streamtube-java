package com.streamtube.application.port.out;

/** Output port producing a short, URL-safe, unique-ish video slug. */
public interface SlugGenerator {

  String generate();
}
