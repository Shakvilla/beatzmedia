package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Exposes the {@link ReddeRestClient} MP REST Client as a plain {@link ReddeClient} bean (WU-PAY-6),
 * so {@code ReddePaymentGateway} depends only on the annotation-free port. Since {@link
 * ReddeRestClient} extends {@link ReddeClient}, this producer just returns the client proxy typed as
 * the port — no delegating adapter needed.
 */
@ApplicationScoped
public class ReddeClientProvider {

  @Produces
  @ApplicationScoped
  public ReddeClient reddeClient(@RestClient ReddeRestClient restClient) {
    return restClient;
  }
}
