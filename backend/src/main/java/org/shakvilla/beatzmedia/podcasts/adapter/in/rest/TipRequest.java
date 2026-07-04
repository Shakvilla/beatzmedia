package org.shakvilla.beatzmedia.podcasts.adapter.in.rest;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /v1/podcasts/:id/tip} (LLFR-PODCAST-02.1). Podcasts ADD §6 /
 * API-CONTRACT §8 ({@code { amount }} instant MoMo tip).
 *
 * <p>The frontend {@code SupportModal} is MoMo-first: the fan picks an {@code amount} and the client
 * supplies the charging instrument (rail + kind + token). The <strong>recipient is NOT in the
 * body</strong> — it is resolved server-side from the podcast's {@code creator_account_id} by
 * {@code TipShow}, so a client can never redirect a tip to an arbitrary account. The tipping fan is
 * the JWT subject, never a body field. The {@code Idempotency-Key} is a required header, not a body
 * field.
 *
 * @param amount decimal cedis (converted to minor units at the boundary, INV-11)
 * @param currency ISO currency; defaults to {@code GHS} when absent
 * @param provider the payment rail (e.g. {@code mtn}); MoMo-first, so no server default is guessed
 * @param methodKind the instrument kind (e.g. {@code momo})
 * @param paymentToken opaque provider token (e.g. a MoMo MSISDN handle); never logged
 */
public record TipRequest(
    BigDecimal amount,
    String currency,
    String provider,
    String methodKind,
    String paymentToken) {}
