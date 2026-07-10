package org.shakvilla.beatzmedia.identity.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for {@link TokenIssuer#issueImpersonation} (the {@code
 * org.shakvilla.beatzmedia.identity.adapter.out.integration.JwtTokenIssuer} implementation).
 * Security-review fix: an impersonation token must NOT be byte-for-byte indistinguishable from the
 * target's own normal login token — it must carry an {@code act} claim naming the real admin actor
 * plus a distinct {@code jti} (security-authz.md §3). Decodes the JWT payload directly (no
 * signature verification needed), same pattern as {@link AuthResourceIT}'s JWT-claim assertions.
 */
@QuarkusTest
@Tag("integration")
class ImpersonationTokenClaimsIT {

  @Inject TokenIssuer tokenIssuer;

  @Test
  void issueImpersonation_carries_act_claim_and_distinct_jti() throws Exception {
    AccountId target = new AccountId("acc-target-it");
    AccountId actor = new AccountId("acc-admin-it");
    Set<String> roles = Set.of("fan", "artist");

    String token = tokenIssuer.issueImpersonation(target, roles, actor, Duration.ofMinutes(15));

    JsonNode payload = decodePayload(token);

    assertEquals(target.value(), payload.get("sub").asText(), "sub must be the target account");

    JsonNode act = payload.get("act");
    assertNotNull(act, "impersonation token must carry an `act` claim (security-authz.md §3)");
    assertEquals(actor.value(), act.get("sub").asText(), "act.sub must name the real admin actor");
    assertEquals("impersonation", act.get("scope").asText());

    JsonNode jti = payload.get("jti");
    assertNotNull(jti, "impersonation token must carry a distinct jti");
    assertFalse(jti.asText().isBlank(), "jti must be non-empty");
    assertTrue(jti.asText().startsWith("imp_"), "jti should be recognisable as an impersonation id");
  }

  @Test
  void issue_normal_login_token_never_carries_an_act_claim() throws Exception {
    AccountId subject = new AccountId("acc-normal-it");

    String token = tokenIssuer.issue(subject, Set.of("fan"));

    JsonNode payload = decodePayload(token);

    assertEquals(subject.value(), payload.get("sub").asText());
    assertFalse(payload.has("act"), "a normal login token must never carry an `act` claim");
  }

  private static JsonNode decodePayload(String token) throws Exception {
    String[] parts = token.split("\\.");
    assertNotNull(parts[1], "JWT must have a payload");
    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
    return new ObjectMapper().readTree(payloadJson);
  }
}
