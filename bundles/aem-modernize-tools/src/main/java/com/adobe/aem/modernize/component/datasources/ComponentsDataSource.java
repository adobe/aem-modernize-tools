/*
 *  (c) 2019 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.component.datasources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.day.cq.commons.Externalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Returns a list of components found on the given path
 */
@SlingServlet(
        resourceTypes = "cq/modernize/component/datasource",
        methods = { "GET" })
public final class ComponentsDataSource extends SlingSafeMethodsServlet {

    private final static Logger log = LoggerFactory.getLogger(ComponentsDataSource.class);

    private static final String CRX_LITE_PATH = "/crx/de/index";

    @Reference
    private ExpressionResolver expressionResolver;

    @Reference
    private Externalizer externalizer;

    @Reference
    private ComponentRewriteRuleService componentRewriteRuleService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        try {
            Resource resource = request.getResource();
            ValueMap properties = resource.getValueMap();
            String itemResourceType = properties.get("itemResourceType", String.class);

            String path = properties.get("path", String.class);

            if (StringUtils.isEmpty(path)) {
                log.warn("Path unavailable");
                return;
            }

            path = expressionResolver.resolve(path, request.getLocale(), String.class, request);

            setDataSource(path, request, itemResourceType);
        } catch (RepositoryException e) {
            log.warn("Unable to list components: {}", e.getMessage());
        }
    }

    private void setDataSource(String path, SlingHttpServletRequest request, String itemResourceType) throws RepositoryException {
        List<Resource> resources = new ArrayList<>();

        if (StringUtils.isNotBlank(path)) {
            ResourceResolver resourceResolver = request.getResourceResolver();
            TreeMap<String, Node> nodeMap = new TreeMap<>();

            // sanitize path
            path = path.trim();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            buildNodeMap(path, nodeMap, resourceResolver);

            buildResources(resources, nodeMap, request, itemResourceType);
        }

        DataSource ds = new SimpleDataSource(resources.iterator());

        request.setAttribute(DataSource.class.getName(), ds);
    }

    private void buildNodeMap(String searchPath, Map<String, Node> nodeMap, ResourceResolver resolver) throws RepositoryException {

        Resource rootResource = resolver.getResource(searchPath);

        String slingResourceType = rootResource.getValueMap().get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class);
        if (componentRewriteRuleService.getSlingResourceTypes(resolver).contains(slingResourceType)) {
            nodeMap.put(searchPath, rootResource.adaptTo(Node.class));
        }

        // If the path does not point to a component node: we query for component nodes
        if (nodeMap.isEmpty()) {
            Query query = createQuery(searchPath, resolver);

            NodeIterator iterator = query.execute().getNodes();
            while (iterator.hasNext()) {
                Node node = iterator.nextNode();
                nodeMap.put(node.getPath(), node);
            }
        }
    }

    private void buildResources(List<Resource> resources, Map<String, Node> nodes, SlingHttpServletRequest request, String itemResourceType) throws RepositoryException {
        int index = 0;
        Iterator iterator = nodes.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry)iterator.next();
            Node component = (Node)entry.getValue();

            if (component == null) {
                continue;
            }

            Node parent = component.getParent();

            if (parent == null) {
                continue;
            }

            String resourceType = component.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();
            String componentPath = component.getPath();
            String href = externalizer.relativeLink(request, componentPath) + ".html";
            String crxHref = externalizer.relativeLink(request, CRX_LITE_PATH) + ".jsp#" + componentPath;

            Map<String, Object> map = new HashMap<>();
            map.put("componentPath", componentPath);
            map.put("resourceType", resourceType);
            map.put("href", href);
            map.put("crxHref", crxHref);

            resources.add(new ValueMapResource(request.getResourceResolver(), request.getResource() + "/component_" + index, itemResourceType, new ValueMapDecorator(map)));
            index++;
        }
    }

    private Query createQuery(String path, ResourceResolver resourceResolver) throws RepositoryException {
        String encodedPath = "/".equals(path) ? "" : ISO9075.encodePath(path);
        if (encodedPath.length() > 1 && encodedPath.endsWith("/")) {
            encodedPath = encodedPath.substring(0, encodedPath.length() - 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM [").append(JcrConstants.NT_UNSTRUCTURED).append("] AS res ");
        sb.append("WHERE ISDESCENDANTNODE(res, '").append(encodedPath).append("') ");
        sb.append("AND res.[sling:resourceType] IN (");

        Iterator<String> it = componentRewriteRuleService.getSlingResourceTypes(resourceResolver).iterator();
        while (it.hasNext()) {
            sb.append("'").append(it.next()).append("'");
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(")");

        Session session = resourceResolver.adaptTo(Session.class);
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        return queryManager.createQuery(sb.toString(), Query.JCR_SQL2);

    }
}
