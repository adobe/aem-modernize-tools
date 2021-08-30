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

package com.adobe.aem.modernize;

import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ResourceResolver;

public interface RewriteRuleService<T extends RewriteRule> {

    /**
     * Lists all of the registered ComponentRewriteRules for processing.
     * @param resolver the resource resolver for reading the repository
     * @return a list of all component rewrite rules.
     * @throws RepositoryException if the rules cannot be read
     */
    List<T> getRules(ResourceResolver resolver) throws RepositoryException;

}
