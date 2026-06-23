package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

/** Panache repository for the single-row {@code platform_settings} table. */
@ApplicationScoped
public class PlatformSettingsRepository implements PanacheRepositoryBase<PlatformSettingsEntity, Short> {}
