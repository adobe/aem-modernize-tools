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

package com.adobe.aem.modernize.design;

import java.util.List;
import java.util.Set;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.day.cq.wcm.api.designer.Design;
import org.jetbrains.annotations.NotNull;

public interface PoliciesImportRuleService {


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
    void apply(@NotNull final Design design, @NotNull final String[] rules, boolean deep, boolean overwrite);

    List<PoliciesImportRule> getRules(ResourceResolver resolver) throws RepositoryException;
    Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException;
}
