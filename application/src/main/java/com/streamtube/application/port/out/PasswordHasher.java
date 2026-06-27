package com.streamtube.application.port.out;

/** Output port for password hashing/verification (implemented with Argon2 in infrastructure). */
public interface PasswordHasher {

  String hash(String rawPassword);

  boolean matches(String rawPassword, String passwordHash);
}
