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
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.impl.PolicyConstants;
import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.day.cq.wcm.api.designer.Design;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites the jcr:content node of a Page.
 * Updates the <code>cq:template</code> reference to the mapped value
 * Removes the <code>cq:designPath</code> property, as it is unnecessary on Editable Templates
 * Creates a root responsive layout component and moves all children nodes (e.g. components) to this container.
 */
// TODO: Rewrite this to understand changes based on referenced components
public class ResponsiveGridPolicyImportRule implements PolicyImportRule {

    private static final Logger logger = LoggerFactory.getLogger(ResponsiveGridPolicyImportRule.class);

    private static final String PARSYS_RESOURCE_TYPE = "foundation/components/parsys";

    private static Set<String> patternSlingResourceTypes = new HashSet<>();


    static {
        patternSlingResourceTypes.add(PARSYS_RESOURCE_TYPE);
        patternSlingResourceTypes = Collections.unmodifiableSet(patternSlingResourceTypes);
    }

    private int ranking = Integer.MAX_VALUE;
    private String id = this.getClass().getName();
//
//    @Override
//    public Set<String> getPatternSlingResourceTypes() throws RepositoryException {
//        return new HashSet<>(patternSlingResourceTypes);
//    }
//
//    @Override
//    public String getReplacementSlingResourceType() throws RepositoryException {
//        return RewriteUtils.RESPONSIVE_GRID_RES_TYPE;
//    }

    @Override
    public void setTargetDesign(Design destination) {

    }

    @Override
    public @NotNull Set<String> findMatches(@NotNull Resource resource) {
        return null;
    }

    @Override
    public @NotNull boolean hasPattern(@NotNull String... slingResourceTypes) {
        return false;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public boolean matches(Node root) throws RepositoryException {
        if (!StringUtils.equals(root.getPrimaryNodeType().getName(), JcrConstants.NT_UNSTRUCTURED)) {
            return false;
        }

        if (!root.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
            return false;
        }

        String srt = root.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();

        return StringUtils.equals(srt, PARSYS_RESOURCE_TYPE);
    }

    @Override
    public Node applyTo(Node root, Set<String> finalPaths) throws RewriteException, RepositoryException {
        root.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, PolicyConstants.POLICY_RESOURCE_TYPE);
        return root;
    }

    @Activate
    protected void activate(ComponentContext context) throws RepositoryException {
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> props = context.getProperties();
        this.id = PropertiesUtil.toString(props.get("service.pid"), this.id);
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
}
