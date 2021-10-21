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

package com.adobe.aem.modernize.policy;

import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.wcm.api.designer.Design;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface PolicyImportRuleService {

    /**
     * Applies the indicated rules to the provided resource. If {@code deep} is set, rules will be applied recursively.
     *
     * If {@code overwrite} is true, then any referenced existing Policy created from a Design will be overwritten with the rules applied. Otherwise, if a Policy has already been created from a Design, it will be ignored.
     *
     * Transformations are performed but not saved.
     *
     * The rules can be either a fully qualified path to a rule or a Service PID depending on the implementation.
     *
     * Implementations decide how to handle rule paths which are invalid for their context.
     *
     * @param design The Design from which to import Policies
     * @param rules the rules to apply
     * @param deep {@code true} to recurse into the tree
     * @param overwrite {@code true} to overwrite existing modernization
     */
    void apply(@NotNull final Design design, @NotNull final Set<String> rules, boolean deep, boolean overwrite);

    /**
     * Lists all resource paths that match any rules of which this service is aware.
     * <p>
     * This method may result in fuzzy matches to improve performance and prevent resource utilization overhead.
     *
     * @param resource Resource for the root of the search
     * @return list of paths that match rules or empty set if none found or an error occurs
     */
    @NotNull
    Set<String> findResources(@NotNull Resource resource);

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
