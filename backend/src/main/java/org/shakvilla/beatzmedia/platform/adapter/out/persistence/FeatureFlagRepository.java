package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

/** Panache repository for the {@code feature_flag} table keyed by flag name. */
@ApplicationScoped
public class FeatureFlagRepository implements PanacheRepositoryBase<FeatureFlagEntity, String> {}
