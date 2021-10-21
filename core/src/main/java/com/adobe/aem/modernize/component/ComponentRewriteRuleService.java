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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides a mechanism for listing all configured rules either via Nodes or custom implementations.
 */
@ProviderType
public interface ComponentRewriteRuleService {

  /**
   * Applies the indicated rules to the provided resource. If {@code deep} is set, rules will be applied recursively.
   * <p>
   * Transformations are performed but not saved.
   * <p>
   * The rules can be either a fully qualified path to a rule or a Service PID depending on the implementation.
   * <p>
   * Implementations decide how to handle rule paths which are invalid for their context.
   *
   * @param resource Parent node for applying rules
   * @param rules    the rules to apply
   * @param deep     {@code true} to recurse into the tree
   * @throws RewriteException if any errors occur when applying the rules
   */
  void apply(@NotNull final Resource resource, @NotNull final Set<String> rules, boolean deep) throws RewriteException;

  /**
   * Lists all resource paths that match any rules of which this service is aware.
   * <p>
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resource Resource for the root of the search
   * @return list of paths that match rules or empty set if none found or an error occurs
   */
  @NotNull
  Set<String> findResources(Resource resource);

  /**
   * Lists all rules that may apply to the specified {@code sling:resourceType}.
   * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
   *
   * @param resourceResolver ResourceResolver for searching
   * @param slingResourceType the {@code sling:resourceType}(s) to check
   * @return list of rules by path or PID
   */
  @NotNull
  Set<RewriteRule> listRules(ResourceResolver resourceResolver, String... slingResourceType);
}
