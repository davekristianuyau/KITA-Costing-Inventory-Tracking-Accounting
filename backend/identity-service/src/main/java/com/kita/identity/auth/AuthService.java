package com.kita.identity.auth;

import com.kita.identity.domain.AppUser;
import com.kita.identity.domain.AppUserRepository;
import com.kita.identity.token.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authenticates company + username + password (BCrypt), applies brute-force throttling, and issues a session
 * token on success. Failures are non-revealing (FR-008); repeated failures lock the account (FR-009).
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final int maxFailed;
  private final Duration lockFor;

  public AuthService(
      AppUserRepository users,
      PasswordEncoder passwordEncoder,
      TokenService tokenService,
      @Value("${identity.security.max-failed-attempts:5}") int maxFailed,
      @Value("${identity.security.lock-minutes:15}") long lockMinutes) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.maxFailed = maxFailed;
    this.lockFor = Duration.ofMinutes(lockMinutes);
  }

  public record LoginResult(String companyId, TokenService.IssuedToken token) {}

  public LoginResult login(String companyId, String username, String password) {
    Instant now = Instant.now();
    AppUser user =
        users
            .findByClientCompanyIdAndUsername(companyId, username)
            .filter(u -> u.isActive() && u.getClient().isActive())
            .orElseGet(
                () -> {
                  // Non-revealing outcome; log the attempt (never the password) for observability.
                  log.info("login denied company={} username={} reason=unknown-user", companyId, username);
                  throw new InvalidCredentialsException();
                });

    if (user.isLocked(now)) {
      log.info("login denied company={} username={} reason=locked", companyId, username);
      throw new AccountLockedException();
    }
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      user.recordFailure(maxFailed, lockFor, now);
      users.save(user); // persist the failure/lock (must survive the thrown rejection)
      log.info("login denied company={} username={} reason=bad-password", companyId, username);
      throw new InvalidCredentialsException();
    }
    user.recordSuccess();
    users.save(user);
    TokenService.IssuedToken token =
        tokenService.issue(user.getUsername(), user.getClient().getCompanyId(), List.of());
    log.info("login ok company={} username={}", user.getClient().getCompanyId(), user.getUsername());
    return new LoginResult(user.getClient().getCompanyId(), token);
  }

  /** Invalid credentials or unknown user — always the same, non-revealing outcome. */
  public static class InvalidCredentialsException extends RuntimeException {}

  /** Too many failed attempts; the account is temporarily locked. */
  public static class AccountLockedException extends RuntimeException {}
}
