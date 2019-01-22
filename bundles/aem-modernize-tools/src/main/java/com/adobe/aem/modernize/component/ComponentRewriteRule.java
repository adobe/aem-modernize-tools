/*
 *  (c) 2014 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.component;

import javax.jcr.RepositoryException;
import java.util.Set;

import com.adobe.aem.modernize.RewriteRule;

/**
 * Interface for services that implement a component rewrite rule. A rewrite rule matches certain subtrees of the
 * component tree (usually corresponding to one component component) and rewrites (i.e. modifies or replaces) them.
 */
public interface ComponentRewriteRule extends RewriteRule {



    /**
     * Returns a set of all <code>sling:resourceType</code> values specified in the <i>pattern</i> properties.
     * @return
     */
    Set<String> getSlingResourceTypes() throws RepositoryException;
}
