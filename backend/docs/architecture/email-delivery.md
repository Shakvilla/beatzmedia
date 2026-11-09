# Email delivery inventory and template map

This document records the email-producing paths currently present in the codebase and the template coverage for each. All app mail goes through the provider-neutral `notifications.application.port.out.Mailer`; the active provider selection chooses SMTP/Mailpit or Resend. Feature code should not call a provider SDK directly.

## Active email flows

| Flow | Trigger and path | Recipients and content | Templates | Notes |
| --- | --- | --- | --- | --- |
| Password reset | `RequestPasswordResetService` calls identity's `Mailer`; `SmtpPasswordResetMailer` renders and forwards through the notifications `Mailer` | Account email; one-time reset link and expiry | [`password-reset.html`](../../src/main/resources/templates/email/password-reset.html), [`password-reset.txt`](../../src/main/resources/templates/email/password-reset.txt) | Existing branded template. Provider selection is shared with other transactional mail. |
| Release split invitation | Catalog emits `SplitInviteIssued`; `SplitInviteEmailObserver` sends to invited address | Collaborator address, artist/release names, proposed track shares, acceptance link | [`split-invite.html`](../../src/main/resources/templates/email/split-invite.html), [`split-invite.txt`](../../src/main/resources/templates/email/split-invite.txt) | May go to an address without a BeatzClik account. Idempotency key is the invite acceptance URL. |
| Account notification | `NotifyService` fires `NotificationCreated`; `DispatchSubscriber` applies contact preferences; `DeliveryAttemptService` uses the renderer and selected mail provider | Account email; notification title/body and optional in-app route CTA | [`notification.html`](../../src/main/resources/templates/email/notification.html), [`notification.txt`](../../src/main/resources/templates/email/notification.txt) | The template is general by design. The currently wired producer is settled creator tips (`NotificationEventObservers.onTipReceived`, “You got a tip”). Route is propagated into both initial sends and retries. |
| Welcome | `RegisterFanService` and first-time `SocialLoginService` emit `AccountRegistered`; `WelcomeEmailObserver` sends after successful registration | New account email and name; link to explore | [`welcome.html`](../../src/main/resources/templates/email/welcome.html), [`welcome.txt`](../../src/main/resources/templates/email/welcome.txt) | A stable account-scoped provider idempotency key is used. Existing social logins do not emit the event, so they do not get another welcome. |

## Related paths that do not currently send email

- Admin invitations (`InviteAdminService`) create a stub account and admin membership without login credentials, but do not send an email. A future invitation message should include a secure account-setup step rather than reuse the generic welcome message.
- Artist registration and account verification currently have no email-sending path. Artist role upgrades start from an existing account and do not emit a new-account welcome.
- Password-reset requests for unknown accounts intentionally do not send an email; the public response remains non-enumerating.
- Notification types for sales, followers, payouts, releases, episodes, and system messages are future producers in the notification architecture. When wired, they should reuse the notification templates rather than add provider-specific mail code.

## Template and delivery conventions

- Keep a plain-text and HTML version for every transactional template. The text version is the fallback and improves accessibility and deliverability.
- Qute `.html` templates escape dynamic content. Do not inject prebuilt HTML into the body; render plain text fields and keep links as separately validated URLs.
- Keep credentials, provider selection, retries, idempotency, and delivery status in the existing provider/delivery-attempt layer. Templates format content only.
- Notification action routes are app-relative. The renderer only makes routes beginning with one `/` clickable and prefixes the configured `beatz.app.base-url`; arbitrary external URLs are not accepted from notification data.
- SMS remains a separate channel and continues to send the concise notification body without email markup.

## Adding a future email flow

1. Identify the domain event or application use case that owns the send and the exact recipient consent/eligibility rules.
2. Define subject, plain-text content, HTML content, action link, retry/idempotency behavior, and privacy constraints.
3. Add matching Qute `.html` and `.txt` templates under `src/main/resources/templates/email/`.
4. Send an `EmailMessage` via the `Mailer` output port or the notification delivery pipeline. Do not call Resend/SMTP adapters from feature code.
5. Add the flow and its trigger to this inventory when implementation lands.
