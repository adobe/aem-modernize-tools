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
package com.adobe.aem.modernize.design.datasources;

import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.adobe.aem.modernize.design.impl.PoliciesImportUtils;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.day.cq.commons.Externalizer;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static javax.jcr.query.Query.JCR_SQL2;

/**
 * Returns a list of component styles found on the given path
 */
@SlingServlet(
        resourceTypes = "cq/modernize/design/componentstyles/datasource",
        methods = { "GET" })
public final class ComponentStylesDataSource extends SlingSafeMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentStylesDataSource.class);

    private static final String CRX_LITE_PATH = "/crx/de/index";

    @Reference
    private ExpressionResolver expressionResolver;

    @Reference
    private Externalizer externalizer;

    @Reference
    private PoliciesImportRuleService policiesImportRuleService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        // Get design path
        Resource resource = request.getResource();
        ValueMap properties = resource.getValueMap();
        String designPath = properties.get("designPath", String.class);
        designPath = PoliciesImportUtils.getDesignPath(expressionResolver.resolve(designPath, request.getLocale(), String.class, request));
        if (StringUtils.isEmpty(designPath)) {
            LOGGER.warn("Path unavailable");
            return;
        }

        List<Resource> result = new ArrayList<>();
        ResourceResolver resolver = request.getResourceResolver();

        // Get corresponding design
        Designer designer = resolver.adaptTo(Designer.class);
        Design design = designer.getDesign(designPath);
        if (design == null) {
            LOGGER.warn("Design does not exist: {}", designPath);
            return;
        }
        if (!design.hasContent()) {
            LOGGER.warn("Design at {} is empty", designPath);
            return;
        }


        try {

            if (CollectionUtils.isEmpty(policiesImportRuleService.getSlingResourceTypes(resolver))) {
                // No rules.
                return;
            }

            String designContentResourcePath = design.getContentResource().getPath();
            Iterator<Resource> it = resolver.findResources(generateQuery(designContentResourcePath, resolver), JCR_SQL2);
            int index = 0;
            while (it.hasNext()) {
                result.add(buildResource(it.next(), request, designContentResourcePath, properties.get("itemResourceType", String.class), index++));
            }

            DataSource ds = new SimpleDataSource(result.iterator());

            request.setAttribute(DataSource.class.getName(), ds);
        } catch (RepositoryException ex) {
            LOGGER.warn("Unable to list Component designs: {}", ex.getMessage());
        }
    }

    private String generateQuery(String rootPath, ResourceResolver resourceResolver) throws RepositoryException {

        String path = RewriteUtils.encodePath(rootPath);
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM [").append(JcrConstants.NT_UNSTRUCTURED).append("] AS design ");
        sb.append("WHERE ISDESCENDANTNODE(design, '").append(path).append("') ");
        sb.append("AND design.[sling:resourceType] IN ( ");
        Iterator<String> it = policiesImportRuleService.getSlingResourceTypes(resourceResolver).iterator();
        while (it.hasNext()) {
            sb.append("'").append(it.next()).append("'");
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    private Resource buildResource(Resource resource, SlingHttpServletRequest request, String rootPath, String itemResourceType, int index) {
        ValueMap props = resource.getValueMap();
        Map<String, Object> map = new HashMap<>();

        String resourceType = props.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class);
        map.put("resourceType", resourceType);

        String cellPath = resource.getPath().replaceFirst(rootPath + "/", "");
        map.put("cellPath", cellPath);

        String crxHref = externalizer.relativeLink(request, CRX_LITE_PATH) + ".jsp#" + RewriteUtils.encodePath(resource.getPath());
        map.put("crxHref", crxHref);

        String policyPath = props.get(PoliciesImportUtils.PN_IMPORTED, String.class);
        map.put("imported", policyPath != null);
        if (policyPath != null) {
            map.put("policyHref", externalizer.relativeLink(request, CRX_LITE_PATH) + ".jsp#" + RewriteUtils.encodePath(policyPath));
        }

        return new ValueMapResource(request.getResourceResolver(), request.getResource() + "/component_" + index, itemResourceType, new ValueMapDecorator(map));
    }

}
