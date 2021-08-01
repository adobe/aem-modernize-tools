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

import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.RewriteRuleService;

/**
 * Provides a mechanism for listing all of the configured rules either via Nodes or custom implementations.
 */
public interface ComponentRewriteRuleService extends RewriteRuleService<ComponentRewriteRule> {

    /**
     * Lists all of the sling:resourceType properties identified by the patterns.
     * @return
     */
    Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException;
}
