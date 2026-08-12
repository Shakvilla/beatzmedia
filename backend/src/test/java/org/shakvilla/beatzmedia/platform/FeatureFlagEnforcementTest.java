package org.shakvilla.beatzmedia.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.events.adapter.in.rest.EventsResource;
import org.shakvilla.beatzmedia.payments.adapter.in.rest.TipResource;
import org.shakvilla.beatzmedia.platform.adapter.in.rest.RequiresFeature;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.podcasts.adapter.in.rest.PodcastResource;
import org.shakvilla.beatzmedia.studio.adapter.in.rest.StudioPodcastResource;

/**
 * Asserts that the feature flags an operator can switch off are actually attached to the endpoints
 * they govern.
 *
 * <p><strong>Why this exists.</strong> {@code @RequiresFeature} and the filter that enforces it were
 * both built — and the annotation was never applied to a single resource. So the Settings page
 * offered platform-wide toggles for Podcasts, Events and Tipping, writing them to
 * {@code feature_flag}, while every one of those endpoints kept serving. Verified at the time:
 * with {@code podcasts=false} and {@code events=false} committed, {@code GET /v1/podcasts} and
 * {@code GET /v1/events} both still returned 200 with full payloads. There was no kill switch.
 *
 * <p>A unit test of the filter proves the mechanism works; it cannot notice that nothing uses it.
 * These tests assert the wiring itself, which is what was missing.
 */
@Tag("unit")
class FeatureFlagEnforcementTest {

  private static List<FeatureKey> required(Class<?> resource) {
    RequiresFeature a = resource.getAnnotation(RequiresFeature.class);
    assertNotNull(a, resource.getSimpleName() + " must carry @RequiresFeature");
    return Arrays.asList(a.value());
  }

  private static Method method(Class<?> resource, String name) {
    for (Method m : resource.getDeclaredMethods()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    throw new AssertionError("no method " + name + " on " + resource.getSimpleName());
  }

  @Test
  void podcastEndpointsRequireThePodcastsFlag() {
    assertEquals(List.of(FeatureKey.PODCASTS), required(PodcastResource.class));
  }

  @Test
  void studioPodcastEndpointsRequireThePodcastsFlag() {
    // Switching Podcasts off has to stop creators publishing new ones too, not just stop fans
    // reading them — otherwise the feature is only half off.
    assertEquals(List.of(FeatureKey.PODCASTS), required(StudioPodcastResource.class));
  }

  @Test
  void eventEndpointsRequireTheEventsFlag() {
    assertEquals(List.of(FeatureKey.EVENTS), required(EventsResource.class));
  }

  @Test
  void tipEndpointsRequireTheTippingFlag() {
    assertEquals(List.of(FeatureKey.TIPPING), required(TipResource.class));
  }

  /**
   * A method annotation replaces the class annotation rather than adding to it, so tipping a
   * podcast must name both keys. Listing only TIPPING would let podcast tips keep flowing with the
   * whole podcasts feature switched off — the exact kind of hole this change is closing.
   */
  @Test
  void tippingAPodcastRequiresBothPodcastsAndTipping() {
    RequiresFeature a = method(PodcastResource.class, "tip").getAnnotation(RequiresFeature.class);
    assertNotNull(a, "the podcast tip endpoint must carry @RequiresFeature");
    assertEquals(
        Set.of(FeatureKey.PODCASTS, FeatureKey.TIPPING),
        Set.copyOf(Arrays.asList(a.value())));
  }

  /**
   * Every flag an operator can toggle must be enforced somewhere.
   *
   * <p>{@code FAN_MESSAGING} is the deliberate exception: the Settings page offers the toggle, but
   * no artist-to-fan messaging endpoint exists anywhere in the API, so there is nothing to gate.
   * It is listed here rather than quietly skipped, so that adding the feature without wiring the
   * flag fails this test — and so the exception stays visible while it lasts.
   */
  @Test
  void everyOperatorFacingFlagIsEnforcedSomewhere() {
    Set<FeatureKey> annotationEnforced =
        Set.of(FeatureKey.PODCASTS, FeatureKey.EVENTS, FeatureKey.TIPPING);
    // Enforced in application services rather than at the boundary.
    Set<FeatureKey> serviceEnforced = Set.of(FeatureKey.ARTIST_SIGNUPS, FeatureKey.PSP_REDDE);
    /*
      Payment rails (GAP-13). Enforced by PaymentProviderPolicy in InitiateChargeService, not by
      @RequiresFeature — and deliberately so. The annotation gates a whole resource; these must gate
      one *field* of a request (the chosen rail) while the endpoint stays open for every other rail,
      and they are read fail-closed, which the shared filter does not do.

      Kept as its own category rather than folded into serviceEnforced because the enforcement
      differs in a way that matters: a missing row here refuses the charge and stops the boot, where
      every other flag on this list fails open.
    */
    Set<FeatureKey> chargePolicyEnforced =
        Set.of(
            FeatureKey.PROVIDER_MTN,
            FeatureKey.PROVIDER_TELECEL,
            FeatureKey.PROVIDER_AIRTELTIGO,
            FeatureKey.PROVIDER_CARD,
            FeatureKey.PROVIDER_BANK);
    Set<FeatureKey> noSurfaceYet = Set.of(FeatureKey.FAN_MESSAGING);

    List<FeatureKey> unaccounted = new ArrayList<>();
    for (FeatureKey key : FeatureKey.values()) {
      if (!annotationEnforced.contains(key)
          && !serviceEnforced.contains(key)
          && !chargePolicyEnforced.contains(key)
          && !noSurfaceYet.contains(key)) {
        unaccounted.add(key);
      }
    }

    assertTrue(
        unaccounted.isEmpty(),
        () -> "these feature flags are toggleable but enforced nowhere: " + unaccounted);
  }
}
