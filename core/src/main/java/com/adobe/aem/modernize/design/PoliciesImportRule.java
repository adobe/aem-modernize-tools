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

/*************************************************************************
 * ADOBE CONFIDENTIAL
 * ___________________
 *
 * Copyright 2019 Adobe
 * All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Adobe and its suppliers, if any. The intellectual
 * and technical concepts contained herein are proprietary to Adobe
 * and its suppliers and are protected by all applicable intellectual
 * property laws, including trade secret and copyright laws.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe.
 **************************************************************************/
package com.adobe.aem.modernize.design;

import com.adobe.aem.modernize.RewriteRule;

import javax.jcr.RepositoryException;
import java.util.Set;

/**
 * Interface for services that implement a Design rewrite rule.
 */
public interface PoliciesImportRule extends RewriteRule {

    String POLICY_RESOURCE_TYPE = "wcm/core/components/policy/policy";

    /**
     * Returns a set of all <code>sling:resourceType</code> values specified in the <i>pattern</i> properties.
     * @return set of all resource types
     * @throws RepositoryException when any repository operation error occurs
     */
    Set<String> getPatternSlingResourceTypes() throws RepositoryException;

    /**
     * The replacement <code>sling:resourceType</code> specified on the replacement node.
     * @return replacement slingResourceType
     * @throws RepositoryException when any repository operation error occurs
     */
    String getReplacementSlingResourceType() throws RepositoryException;

}
