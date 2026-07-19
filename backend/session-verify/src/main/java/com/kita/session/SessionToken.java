package com.kita.session;

import java.time.Instant;
import java.util.List;

/** The claims carried by a verified session token. */
public record SessionToken(String subject, String client, List<String> roles, Instant expiresAt) {}
