package org.shakvilla.beatzmedia.studio.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.studio.application.port.in.BillingView;
import org.shakvilla.beatzmedia.studio.application.port.in.NotificationsView;
import org.shakvilla.beatzmedia.studio.application.port.in.PayoutSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.PrivacySettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioSettingsCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioDefaultsView;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioSettingsView;
import org.shakvilla.beatzmedia.studio.application.port.in.TeamMemberView;
import org.shakvilla.beatzmedia.studio.application.port.in.VerificationView;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.NotificationPrefs;
import org.shakvilla.beatzmedia.studio.domain.PayoutSettings;
import org.shakvilla.beatzmedia.studio.domain.PrivacySettings;
import org.shakvilla.beatzmedia.studio.domain.StudioDefaults;
import org.shakvilla.beatzmedia.studio.domain.StudioSettings;
import org.shakvilla.beatzmedia.studio.domain.TeamMember;
import org.shakvilla.beatzmedia.studio.domain.TeamRole;

/**
 * Maps between the {@link StudioSettings} domain aggregate (Category A only) and its wire
 * read-model / command shapes. Also composes the honest static/derived Category B fields (email,
 * sessions, connectedApps, verification, billing, twoFactor, phone, language, timezone, country)
 * that have no backing subsystem — see studio.md §16. Studio ADD §6.
 */
final class StudioSettingsMapper {

  /** Fixed platform defaults for fields with no backing subsystem (studio.md §16). */
  private static final String DEFAULT_LANGUAGE = "English";
  private static final String DEFAULT_TIMEZONE = "GMT (Accra)";

  private StudioSettingsMapper() {}

  static StudioSettingsView toView(StudioSettings settings, String email) {
    return new StudioSettingsView(
        email == null ? "" : email,
        "", // phone — no such field anywhere reachable; not part of Category A either.
        "", // country — no real value; honest empty string.
        DEFAULT_LANGUAGE,
        DEFAULT_TIMEZONE,
        false, // twoFactor — no 2FA infrastructure in identity.
        List.of(), // sessions — no session-tracking infrastructure (JWTs are stateless).
        List.of(), // connectedApps — no OAuth/third-party integration infrastructure.
        // artist=true is real: this endpoint is @RolesAllowed("artist")-gated, so it always holds
        // for a caller who reaches this code. identity/payout/rights have no backing subsystem.
        new VerificationView(true, false, false, false),
        // No subscription/billing engine anywhere in the backend — fixed honest default, never a
        // fabricated paid plan.
        new BillingView("Free", BigDecimal.ZERO, null),
        toView(settings.notifications()),
        toView(settings.defaults()),
        toView(settings.payouts()),
        toView(settings.privacy()),
        settings.team().stream().map(StudioSettingsMapper::toView).toList());
  }

  private static NotificationsView toView(NotificationPrefs prefs) {
    return new NotificationsView(
        prefs.sales(), prefs.tips(), prefs.followers(), prefs.payouts(), prefs.weeklySummary(),
        prefs.comments(), prefs.marketing());
  }

  private static StudioDefaultsView toView(StudioDefaults defaults) {
    return new StudioDefaultsView(
        defaults.trackPrice().toCedis(), defaults.releaseVisibility(), defaults.autoExplicit(),
        defaults.allowOffers());
  }

  private static PayoutSettingsView toView(PayoutSettings payouts) {
    return new PayoutSettingsView(
        payouts.autoWithdraw(), payouts.autoWithdrawThreshold().toCedis(), payouts.taxId());
  }

  private static PrivacySettingsView toView(PrivacySettings privacy) {
    return new PrivacySettingsView(
        privacy.discoverable(), privacy.showRealName(), privacy.acceptBookings(),
        privacy.allowDms());
  }

  private static TeamMemberView toView(TeamMember member) {
    return new TeamMemberView(member.id(), member.name(), member.email(), member.role().wireValue());
  }

  /** Builds the domain aggregate (Category A only) from a validated command. */
  static StudioSettings toDomain(ArtistId artist, SaveStudioSettingsCommand cmd, Instant updatedAt) {
    NotificationPrefs notifications = cmd.notifications() == null
        ? NotificationPrefs.blank()
        : new NotificationPrefs(
            cmd.notifications().sales(), cmd.notifications().tips(),
            cmd.notifications().followers(), cmd.notifications().payouts(),
            cmd.notifications().weeklySummary(), cmd.notifications().comments(),
            cmd.notifications().marketing());

    StudioDefaults defaults = cmd.defaults() == null
        ? StudioDefaults.blank()
        : new StudioDefaults(
            Money.ofCedis(orZero(cmd.defaults().trackPrice())), cmd.defaults().releaseVisibility(),
            cmd.defaults().autoExplicit(), cmd.defaults().allowOffers());

    PayoutSettings payouts = cmd.payouts() == null
        ? PayoutSettings.blank()
        : new PayoutSettings(
            cmd.payouts().autoWithdraw(), Money.ofCedis(orZero(cmd.payouts().autoWithdrawThreshold())),
            cmd.payouts().taxId());

    PrivacySettings privacy = cmd.privacy() == null
        ? PrivacySettings.blank()
        : new PrivacySettings(
            cmd.privacy().discoverable(), cmd.privacy().showRealName(),
            cmd.privacy().acceptBookings(), cmd.privacy().allowDms());

    List<TeamMember> team = cmd.team() == null
        ? List.of()
        : cmd.team().stream()
            .map(t -> new TeamMember(t.id(), t.name(), t.email(), TeamRole.fromWireValue(t.role())))
            .toList();

    return new StudioSettings(artist, notifications, defaults, payouts, privacy, team, updatedAt);
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
