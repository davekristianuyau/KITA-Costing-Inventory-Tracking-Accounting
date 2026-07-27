package com.kita.crm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kita.crm.security.ServiceCallRefusal.Reason;
import jakarta.servlet.FilterChain;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 018 FR-008 / SC-005: an unverifiable internal caller is refused <em>and</em> recorded. Each case
 * asserts both halves — refusing without a durable record would defeat the point (knowing afterwards
 * that someone probed the internal network).
 */
class ServiceIdentityFilterTest {

  private static final String X509_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

  private final ServiceCallRefusalRecorder recorder = mock(ServiceCallRefusalRecorder.class);
  private final FilterChain chain = mock(FilterChain.class);

  private ServiceIdentityFilter filter(String... allowedCns) {
    return new ServiceIdentityFilter(List.of(allowedCns), recorder);
  }

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/crm/customers");
    request.setRemoteAddr("10.0.0.9");
    return request;
  }

  private static X509Certificate certificate(String cn, boolean valid) throws Exception {
    X509Certificate certificate = mock(X509Certificate.class);
    when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=" + cn + ",O=KITA-dev"));
    if (!valid) {
      doThrow(new CertificateExpiredException("expired")).when(certificate).checkValidity();
    }
    return certificate;
  }

  private ServiceCallRefusal captureRefusal() {
    ArgumentCaptor<ServiceCallRefusal> captor = ArgumentCaptor.forClass(ServiceCallRefusal.class);
    verify(recorder).record(captor.capture());
    return captor.getValue();
  }

  @Test
  void callerWithNoCertificateIsRefusedAndRecorded() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter("workflow-service").doFilter(request(), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
    ServiceCallRefusal recorded = captureRefusal();
    assertThat(recorded.getReason()).isEqualTo(Reason.NO_CERT.name());
    assertThat(recorded.getAttemptedCn()).isNull();
    assertThat(recorded.getPeerAddress()).isEqualTo("10.0.0.9");
    assertThat(recorded.getRequestPath()).isEqualTo("/api/crm/customers");
  }

  @Test
  void expiredCertificateIsRefusedAndRecorded() throws Exception {
    MockHttpServletRequest request = request();
    request.setAttribute(X509_ATTRIBUTE, new X509Certificate[] {certificate("workflow-service", false)});
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter("workflow-service").doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
    assertThat(captureRefusal().getReason()).isEqualTo(Reason.EXPIRED.name());
  }

  @Test
  void validCertificateWithAnUnexpectedSubjectIsRefusedAndRecorded() throws Exception {
    MockHttpServletRequest request = request();
    request.setAttribute(X509_ATTRIBUTE, new X509Certificate[] {certificate("attacker", true)});
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter("workflow-service", "gateway").doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
    ServiceCallRefusal recorded = captureRefusal();
    assertThat(recorded.getReason()).isEqualTo(Reason.NOT_ALLOWLISTED.name());
    assertThat(recorded.getAttemptedCn()).isEqualTo("attacker"); // who tried, for the audit
  }

  @Test
  void allowlistedPeerPassesThroughUntouched() throws Exception {
    MockHttpServletRequest request = request();
    request.setAttribute(X509_ATTRIBUTE, new X509Certificate[] {certificate("workflow-service", true)});
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter("workflow-service", "gateway").doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(recorder, never()).record(any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void actuatorStaysReachableWithoutACertificateSoHealthChecksKeepWorking() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter("workflow-service").doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(recorder, never()).record(any());
  }
}
