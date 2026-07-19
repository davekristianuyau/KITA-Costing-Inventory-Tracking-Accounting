package com.kita.identity.api;

import com.kita.identity.auth.AuthService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (contracts/identity-api.md). On success sets an httpOnly (optionally Secure)
 * cookie holding the encrypted session token, and returns the resolved client + expiry. Never returns or
 * logs a password/token body.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;
  private final String cookieName;
  private final boolean cookieSecure;

  public AuthController(
      AuthService authService,
      @Value("${identity.cookie.name:kita_session}") String cookieName,
      @Value("${identity.cookie.secure:false}") boolean cookieSecure) {
    this.authService = authService;
    this.cookieName = cookieName;
    this.cookieSecure = cookieSecure;
  }

  public record LoginRequest(
      @NotBlank String company, @NotBlank String username, @NotBlank String password) {}

  public record LoginResponse(String client, long expiresIn) {}

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body) {
    AuthService.LoginResult result =
        authService.login(body.company(), body.username(), body.password());
    ResponseCookie cookie =
        ResponseCookie.from(cookieName, result.token().token())
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(result.token().expiresInSeconds())
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new LoginResponse(result.companyId(), result.token().expiresInSeconds()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie cleared =
        ResponseCookie.from(cookieName, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(0)
            .build();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
  }

  @ExceptionHandler(AuthService.InvalidCredentialsException.class)
  public ResponseEntity<ErrorBody> invalid() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody("invalid credentials"));
  }

  @ExceptionHandler(AuthService.AccountLockedException.class)
  public ResponseEntity<ErrorBody> locked() {
    return ResponseEntity.status(HttpStatus.LOCKED).body(new ErrorBody("account temporarily locked"));
  }

  public record ErrorBody(String message) {}
}
