package com.adobe.aem.modernize.rule;

import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.jetbrains.annotations.NotNull;

public interface RewriteRuleService {

  /**
   * Lists all resource paths that match any rules of which this service is aware.
   * <p>
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resource Resource for the root of the search
   * @return list of mappings that match rules or empty set if none found or an error occurs
   */
  @NotNull
  Set<String> find(@NotNull Resource resource);

  /**
   * Lists all rules that may apply to the specified {@code sling:resourceType}.
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resourceResolver ResourceResolver for searching
   * @param slingResourceType the {@code sling:resourceType}(s) to check
   * @return list of rules by path or PID
   */
  @NotNull
  Set<RewriteRule> listRules(@NotNull ResourceResolver resourceResolver, String... slingResourceType);
}
