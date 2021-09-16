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

import java.util.List;
import java.util.Set;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.jetbrains.annotations.NotNull;

/**
 * Provides a mechanism for listing all configured rules either via Nodes or custom implementations.
 */
public interface ComponentRewriteRuleService {

    /**
     * Applies the indicated rules to the provided resource. If {@code deep} is set, rules will be applied recursively.
     *
     * Transformations are performed but not saved.
     *
     * The rules can be either a fully qualified path to a rule or a Service PID depending on the implementation.
     *
     * Implementations decide how to handle rule paths which are invalid for their context.
     *
     * @param resource  Parent node for applying rules
     * @param rules the rules to apply
     * @param deep {@code true} to recurse into the tree
     */
    void apply(@NotNull final Resource resource, @NotNull final String[] rules, boolean deep);

    // TODO: Remove these
    /**
     * Lists all of the sling:resourceType properties identified by the patterns.
     * @param resolver ResourceResolver for repository operations
     * @return list of ResourceTypes this service can process
     * @throws RepositoryException when any repository operation errors occur
     */
    Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException;
    List<ComponentRewriteRule> getRules(ResourceResolver resolver) throws RepositoryException;
}
