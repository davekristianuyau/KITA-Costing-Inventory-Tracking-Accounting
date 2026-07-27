package com.kita.operations.security;

import com.kita.operations.security.ServiceCallRefusal.Reason;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies the identity of the <em>service</em> calling this one and refuses callers it cannot verify,
 * persisting every refusal (018 FR-008, SC-005).
 *
 * <p>TLS is configured with {@code client-auth=want} rather than {@code need} on purpose: {@code need}
 * drops an unverifiable caller during the handshake, before any application code runs, so the refusal
 * could never be <em>recorded</em>. With {@code want} the request reaches this filter, which decides —
 * and writes a {@link ServiceCallRefusal} row — before returning 401.
 *
 * <p>Disabled by default ({@code kita.mtls.enabled=false}) so local/unit runs are unaffected; the
 * composed and Floci stacks switch it on. Actuator endpoints are always exempt, otherwise container
 * health checks (which do not present a client certificate) would fail.
 */
@Component
@ConditionalOnProperty(name = "kita.mtls.enabled", havingValue = "true")
public class ServiceIdentityFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ServiceIdentityFilter.class);
  private static final String X509_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

  private final List<String> allowedCns;
  private final ServiceCallRefusalRecorder recorder;

  public ServiceIdentityFilter(
      @Value("${kita.mtls.allowed-cns:}") List<String> allowedCns,
      ServiceCallRefusalRecorder recorder) {
    this.allowedCns = allowedCns.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    this.recorder = recorder;
  }

  /** Health/metrics must stay reachable without a client certificate (container health checks). */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    X509Certificate[] chainCerts = (X509Certificate[]) request.getAttribute(X509_ATTRIBUTE);
    if (chainCerts == null || chainCerts.length == 0) {
      refuse(request, response, null, Reason.NO_CERT);
      return;
    }

    X509Certificate peer = chainCerts[0];
    String cn = commonName(peer);
    try {
      peer.checkValidity();
    } catch (CertificateException expired) {
      refuse(request, response, cn, Reason.EXPIRED);
      return;
    }
    if (!allowedCns.isEmpty() && (cn == null || !allowedCns.contains(cn))) {
      refuse(request, response, cn, Reason.NOT_ALLOWLISTED);
      return;
    }
    chain.doFilter(request, response);
  }

  private void refuse(
      HttpServletRequest request, HttpServletResponse response, String cn, Reason reason)
      throws IOException {
    recorder.record(
        new ServiceCallRefusal(
            Instant.now(),
            request.getRemoteAddr(),
            cn,
            reason,
            request.getMethod(),
            request.getRequestURI()));
    log.warn(
        "refused unverified internal caller: reason={} cn={} peer={} {} {}",
        reason,
        cn,
        request.getRemoteAddr(),
        request.getMethod(),
        request.getRequestURI());
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    // An operational failure, deliberately distinct from a business rejection (422) or a role
    // refusal (403), so the caller reports "temporarily unavailable / not trusted", not "your input".
    response.getWriter().write("{\"detail\":\"caller service identity could not be verified\"}");
  }

  /** CN from the subject DN, e.g. {@code CN=workflow-service,O=KITA-dev} → {@code workflow-service}. */
  static String commonName(X509Certificate certificate) {
    try {
      LdapName dn = new LdapName(certificate.getSubjectX500Principal().getName());
      for (Rdn rdn : dn.getRdns()) {
        if ("CN".equalsIgnoreCase(rdn.getType())) {
          return String.valueOf(rdn.getValue());
        }
      }
    } catch (Exception e) {
      log.debug("cannot parse subject DN", e);
    }
    return null;
  }
}
