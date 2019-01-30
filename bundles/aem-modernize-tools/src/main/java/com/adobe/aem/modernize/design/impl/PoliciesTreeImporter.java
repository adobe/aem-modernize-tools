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
package com.adobe.aem.modernize.design.impl;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.design.PoliciesImportRule;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.designer.Style;
import com.day.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.adobe.aem.modernize.design.impl.PoliciesImportUtils.PN_IMPORTED;

class PoliciesTreeImporter {

    private static Logger LOGGER = LoggerFactory.getLogger(PoliciesTreeImporter.class);

    private static final String NT_INTERMEDIATE = "nt:unstructured";
    private static final String PN_RESOURCE_TYPE = "sling:resourceType";
    private static final String PN_POLICY_RESOURCE_TYPE = "policyResourceType";

    // TODO: add support for rewrite rules
    private List<PoliciesImportRule> rules;

    PoliciesTreeImporter(List<PoliciesImportRule> rules) {
        this.rules = rules;
    }

    String importStyleAsPolicy(ResourceResolver resolver, Style style, String targetPath) throws RewriteException {
        LOGGER.debug("Importing style {} as a policy under {}", style.getPath(), targetPath);

        long tick = System.currentTimeMillis();

        // Get resource type
        String resourceType = style.get(PN_RESOURCE_TYPE, String.class);
        if (StringUtils.isEmpty(resourceType)) {
            throw new RewriteException("Unable to get resource type from style: " + style.getPath());
        }
        // TODO: do it right iterating over search path
        if (resourceType.startsWith("/libs") || resourceType.startsWith("/apps")) {
            resourceType = resourceType.substring(5);
        }

        // Determine parent path for policy based on the resource type of the component style
        String parentPath = Text.makeCanonicalPath(targetPath + "/" + resourceType);

        try {
            // Create intermediate structure if required
            Resource parent = ResourceUtil.getOrCreateResource(resolver, parentPath, NT_INTERMEDIATE, NT_INTERMEDIATE, false);

            // Create policy
            Map<String, Object> properties = new HashMap<>(style);
            properties.put(PN_RESOURCE_TYPE, "wcm/core/components/policy/policy");
            properties.put(PN_POLICY_RESOURCE_TYPE, resourceType);
            properties.put(NameConstants.PN_TITLE, "Imported (" + style.getCell().getPath() + ")");
            properties.put(NameConstants.PN_DESCRIPTION, "Imported from " + style.getPath());
            Resource policy = resolver.create(parent, ResourceUtil.createUniqueChildName(parent, "policy"), properties);

            // Mark previous style as "imported"
            Resource old = resolver.getResource(style.getPath());
            if (old != null) {
                old.adaptTo(ModifiableValueMap.class).put(PN_IMPORTED, policy.getPath());
            }

            // Save
            resolver.commit();

            long tack = System.currentTimeMillis();
            LOGGER.debug("Imported style {} as {} in {} ms", style.getPath(), policy.getPath(), tack - tick);
            return policy.getPath();

        } catch (PersistenceException e) {
            resolver.revert();
            throw new RewriteException("Unable to import policy " + style.getPath() + " into " + targetPath, e);
        }
    }

}
