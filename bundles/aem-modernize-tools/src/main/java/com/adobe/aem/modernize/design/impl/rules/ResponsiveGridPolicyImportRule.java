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

package com.adobe.aem.modernize.design.impl.rules;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.design.PoliciesImportRule;
import com.adobe.aem.modernize.impl.RewriteUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites the jcr:content node of a Page.
 * Updates the <code>cq:template</code> reference to the mapped value
 * Removes the <code>cq:designPath</code> property, as it is unnecessary on Editable Templates
 * Creates a root responsive layout component and moves all children nodes (e.g. components) to this container.
 */
@Service(value = { PoliciesImportRule.class })
@Component(
        metatype = true,
        label="Responsive Grid Policy Import Rule", description="Imports parsys design configurations as responsive grid policies.")
@Properties({
        @org.apache.felix.scr.annotations.Property(name="service.ranking", intValue = 1)
})
public class ResponsiveGridPolicyImportRule implements PoliciesImportRule {

    private static final Logger logger = LoggerFactory.getLogger(ResponsiveGridPolicyImportRule.class);

    private static final String PARSYS_RESOURCE_TYPE = "foundation/components/parsys";

    private static Set<String> patternSlingResourceTypes = new HashSet<>();


    static {
        patternSlingResourceTypes.add(PARSYS_RESOURCE_TYPE);
        patternSlingResourceTypes = Collections.unmodifiableSet(patternSlingResourceTypes);
    }

    private int ranking = Integer.MAX_VALUE;

    @Override
    public Set<String> getPatternSlingResourceTypes() throws RepositoryException {
        return new HashSet<>(patternSlingResourceTypes);
    }

    @Override
    public String getReplacementSlingResourceType() throws RepositoryException {
        return RewriteUtils.RESPONSIVE_GRID_RES_TYPE;
    }

    @Override
    public boolean matches(Node root) throws RepositoryException {

        if (!RewriteUtils.hasPrimaryType(root, JcrConstants.NT_UNSTRUCTURED)) {
            return false;
        }

        if (!root.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
            return false;
        }

        String srt = root.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();

        return StringUtils.equals(srt, PARSYS_RESOURCE_TYPE);
    }

    @Override
    public Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException {
        root.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, POLICY_RESOURCE_TYPE);
        return root;
    }

    @Activate
    protected void activate(ComponentContext context) throws RepositoryException {
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> props = context.getProperties();
        // read service ranking property
        Object ranking = props.get("service.ranking");
        if (ranking != null) {
            try {
                this.ranking = (Integer) ranking;
            } catch (ClassCastException e) {
                // ignore
                logger.warn("Found invalid service.ranking value {}", ranking);
            }
        }
    }

    @Override
    public int getRanking() {
        return this.ranking;
    }
}
