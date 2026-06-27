package com.streamtube.application.port.out;

/** Output port producing a candidate channel nickname from an email (uniqueness ensured by caller). */
public interface NicknameGenerator {

  String generate(String email);
}
