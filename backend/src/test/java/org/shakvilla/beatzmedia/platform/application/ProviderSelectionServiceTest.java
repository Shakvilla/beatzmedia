package org.shakvilla.beatzmedia.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.ProviderReadinessProbe;
import org.shakvilla.beatzmedia.platform.application.port.out.ProviderSelectionRepository;
import org.shakvilla.beatzmedia.platform.application.service.ProviderSelectionService;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderSelection;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

@Tag("unit")
class ProviderSelectionServiceTest {
  private InMemorySelections repository;
  private ProviderSelectionService service;

  @BeforeEach
  void setUp() {
    repository = new InMemorySelections();
    ProviderReadinessProbe ready = (capability, provider) -> ProviderReadinessProbe.Readiness.available();
    service = new ProviderSelectionService(repository, ready, FakeClock.fixed());
  }

  @Test
  void changesACompatibleReadyProviderWithCompareAndSetVersion() {
    var changed = service.change("admin-1", "email", "resend", 1);

    assertEquals("resend", changed.provider());
    assertEquals(2, changed.version());
    assertEquals("admin-1", changed.updatedBy());
    assertTrue(changed.ready());
  }

  @Test
  void rejectsProviderFromAnotherCapabilityWithoutWriting() {
    assertThrows(ValidationException.class, () -> service.change("admin-1", "sms", "resend", 1));
    assertEquals("capture", repository.forCapability(ProviderCapability.SMS).provider().wireValue());
  }

  @Test
  void rejectsUnreadyProviderWithoutWriting() {
    service =
        new ProviderSelectionService(
            repository,
            (capability, provider) ->
                provider == ProviderCode.RESEND
                    ? ProviderReadinessProbe.Readiness.unavailable("missing_configuration")
                    : ProviderReadinessProbe.Readiness.available(),
            FakeClock.fixed());

    ValidationException error =
        assertThrows(
            ValidationException.class,
            () -> service.change("admin-1", "email", "resend", 1));
    assertEquals("provider", error.getField());
    assertEquals("smtp", repository.forCapability(ProviderCapability.EMAIL).provider().wireValue());
  }

  @Test
  void rejectsStaleVersion() {
    service.change("admin-1", "sms", "capture", 1);
    assertThrows(
        ConflictException.class, () -> service.change("admin-2", "sms", "capture", 1));
  }

  @Test
  void listReportsReadinessWithoutExposingConfiguration() {
    service =
        new ProviderSelectionService(
            repository,
            (capability, provider) ->
                capability == ProviderCapability.PAYMENT
                    ? ProviderReadinessProbe.Readiness.unavailable("disabled")
                    : ProviderReadinessProbe.Readiness.available(),
            FakeClock.fixed());

    var payment = service.list().stream().filter(v -> v.capability().equals("payment")).findFirst().orElseThrow();
    assertFalse(payment.ready());
    assertEquals("disabled", payment.readinessReason());
    assertEquals(4, service.list().size());
  }

  private static final class InMemorySelections implements ProviderSelectionRepository {
    private final List<ProviderSelection> selections = new ArrayList<>();

    private InMemorySelections() {
      Instant now = Instant.parse("2026-01-01T00:00:00Z");
      selections.add(new ProviderSelection(ProviderCapability.STORAGE, ProviderCode.MINIO, 1, now, "system"));
      selections.add(new ProviderSelection(ProviderCapability.EMAIL, ProviderCode.SMTP, 1, now, "system"));
      selections.add(new ProviderSelection(ProviderCapability.SMS, ProviderCode.CAPTURE, 1, now, "system"));
      selections.add(
          new ProviderSelection(
              ProviderCapability.PAYMENT, ProviderCode.PAYMENT_DISABLED, 1, now, "system"));
    }

    @Override
    public List<ProviderSelection> listAll() {
      return List.copyOf(selections);
    }

    @Override
    public Optional<ProviderSelection> updateIfVersion(
        ProviderCapability capability,
        ProviderCode provider,
        long expectedVersion,
        Instant updatedAt,
        String updatedBy) {
      for (int i = 0; i < selections.size(); i++) {
        ProviderSelection current = selections.get(i);
        if (current.capability() == capability && current.version() == expectedVersion) {
          ProviderSelection changed =
              new ProviderSelection(
                  capability, provider, current.version() + 1, updatedAt, updatedBy);
          selections.set(i, changed);
          return Optional.of(changed);
        }
      }
      return Optional.empty();
    }

    ProviderSelection forCapability(ProviderCapability capability) {
      return selections.stream().filter(s -> s.capability() == capability).findFirst().orElseThrow();
    }
  }
}
