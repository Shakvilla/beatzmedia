package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.BrowseCategoryView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ListBrowseCategories;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;

/**
 * Application service for LLFR-CATALOG-01.3 (browse categories). Catalog ADD §4.1, WU-CAT-2.
 */
@ApplicationScoped
@Transactional
public class ListBrowseCategoriesService implements ListBrowseCategories {

  private final CatalogRepository catalogRepository;

  @Inject
  public ListBrowseCategoriesService(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  @Override
  public List<BrowseCategoryView> list() {
    return catalogRepository.browseCategories().stream()
        .map(c -> new BrowseCategoryView(c.id(), c.title(), c.colorClass()))
        .toList();
  }
}
