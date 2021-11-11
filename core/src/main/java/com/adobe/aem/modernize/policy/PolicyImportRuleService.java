package com.adobe.aem.modernize.policy;

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

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface PolicyImportRuleService extends RewriteRuleService {

    String PN_IMPORTED = "cq:imported";
    String POLICY_REL_PATH = "settings/wcm/policies";

    /**
     * Applies the indicated rules to the provided Style. If {@code deep} is set, rules will be applied recursively.
     * That is: if the Style reference is a page's root Style resource, all children styles will also be imported if {@code deep} is set.
     *
     * If {@code overwrite} is true, then any referenced existing Policy created from a Design will be overwritten with the rules applied. Otherwise, if a Policy has already been created from a Design, it will be ignored.
     *
     * Transformations are performed but not saved.
     *
     * The rules can be either a fully qualified path to a rule or a Service PID depending on the implementation.
     *
     * Implementations decide how to handle rule paths which are invalid for their context.
     *
     * @param source The Resource Style from which to read design configurations
     * @param confRoot The {@code /conf} root path into which the new policies will be created
     * @param rules the rules to apply
     * @param deep {@code true} to recurse into the tree
     * @param overwrite {@code true} to overwrite existing modernization
     * @throws RewriteException if any errors occur when applying the rules
     */
    void apply(@NotNull Resource source, @NotNull String confRoot, @NotNull Set<String> rules, boolean deep, boolean overwrite) throws RewriteException;

}
