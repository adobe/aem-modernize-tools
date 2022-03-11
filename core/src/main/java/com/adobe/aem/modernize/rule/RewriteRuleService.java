package com.adobe.aem.modernize.rule;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface RewriteRuleService {

  /**
   * Lists all resource paths that match any rules of which this service is aware.
   * <p>
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resource Resource for the root of the search
   * @return list of mappings that match rules or empty set if none found or an error occurs
   */
  @Deprecated(since = "2.1.0")
  @NotNull
  Set<String> find(@NotNull Resource resource);

  /**
   * Lists all rules that may apply to the specified {@code sling:resourceType}.
   * <p>
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resourceResolver ResourceResolver for searching
   * @param slingResourceType the {@code sling:resourceType}(s) to check
   * @return set of rules by path or PID
   */
  @Deprecated(since = "2.1.0")
  @NotNull
  Set<RewriteRule> listRules(@NotNull ResourceResolver resourceResolver, String... slingResourceType);

  /**
   * Finds set of rule ids of which this service is aware, that may match the provided resource's {@code sling:resourceType}.
   * <p>
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead. 
   *
   * @param resource Resource to find matching rules
   * @return set of rules by path or PID
   */
  @NotNull
  Set<String> listRules(@NotNull Resource resource);

  /**
   * Returns the rule based on its unique identifier.
   * 
   * @param resourceResolver the Resource Resolver to use to for rule lookup
   * @param id the id for the rule
   * @return the rule associated with the id or null if none exists 
   */
  @Nullable
  RewriteRule getRule(@NotNull ResourceResolver resourceResolver, @NotNull String id);

}
