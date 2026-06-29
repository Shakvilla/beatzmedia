package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/** Input port: list all browse categories. LLFR-CATALOG-01.3, WU-CAT-2. */
public interface ListBrowseCategories {
  List<BrowseCategoryView> list();
}
