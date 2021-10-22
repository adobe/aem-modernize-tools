package com.adobe.aem.modernize.rule;

import java.util.Set;

import org.apache.sling.api.resource.Resource;

import org.jetbrains.annotations.NotNull;

public interface ServiceBasedRewriteRule extends RewriteRule {

  /**
   * Lists all resource paths in tree rooted at the specified resource, that match any rules of which this service is aware.
   * <p>
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resource Resource for the root of the search
   * @return list of paths that match rules or an empty set if none match
   */
  @SuppressWarnings("unused")
  @NotNull
  Set<String> findMatches(@NotNull Resource resource);

  /**
   * Indicates if this service uses any of the specified {@code sling:resourceType} in any of its matching logic.
   *
   * @param slingResourceTypes the sling resource type to check
   * @return true type matches
   */
  boolean hasPattern(@NotNull String... slingResourceTypes);

}
