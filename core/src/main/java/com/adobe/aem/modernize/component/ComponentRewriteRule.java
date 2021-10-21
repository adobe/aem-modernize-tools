/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.adobe.aem.modernize.component;

import java.util.Set;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;

import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Interface for services that implement a component rewrite rule. A rewrite rule matches certain subtrees of the
 * component tree (usually corresponding to one component) and rewrites (i.e. modifies or replaces) them.
 */
@ConsumerType
public interface ComponentRewriteRule extends RewriteRule {

  /**
   * Lists all resource paths that match any rules of which this service is aware.
   *
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
  @NotNull
  boolean hasPattern(@NotNull String... slingResourceTypes);
}
