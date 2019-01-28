package com.adobe.aem.modernize.structure.datasources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.day.cq.commons.Externalizer;
import com.day.cq.wcm.api.NameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Returns a list of components found on the given path
 */
@SlingServlet(
        resourceTypes = "cq/modernize/templatestructure/datasource",
        methods = { "GET" })
public final class PageDataSource extends SlingSafeMethodsServlet {

    private static final Logger logger = LoggerFactory.getLogger(PageDataSource.class);

    private static final String CRX_LITE_PATH = "/crx/de/index";

    @Reference
    private ExpressionResolver expressionResolver;

    @Reference
    private Externalizer externalizer;

    @Reference
    private StructureRewriteRuleService structureRewriteRuleService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {

        try {
            Resource resource = request.getResource();
            ValueMap properties = resource.getValueMap();
            String itemResourceType = properties.get("itemResourceType", String.class);

            String path = properties.get("path", String.class);

            if (StringUtils.isEmpty(path)) {
                logger.warn("Path unavailable");
                return;
            }

            path = expressionResolver.resolve(path, request.getLocale(), String.class, request);

            setDataSource(path, request, itemResourceType);
        } catch (RepositoryException e) {
            logger.warn("Unable to list components: {}", e.getMessage());
        }
    }

    private void setDataSource(String searchPath, SlingHttpServletRequest request, String itemResourceType) throws RepositoryException {

        List<Resource> resources = buildResources(searchPath, request, itemResourceType);
        DataSource ds = new SimpleDataSource(resources.iterator());
        request.setAttribute(DataSource.class.getName(), ds);
    }

    private List<Resource> buildResources(String searchPath, SlingHttpServletRequest request, String itemResourceType) throws RepositoryException {
        Query query = createQuery(searchPath, request.getResourceResolver());
        NodeIterator it = query.execute().getNodes();


        int index = 0;
        List<Resource> resources = new ArrayList<>();
        while (it.hasNext()) {
            Node pageContent = it.nextNode();
            String pagePath = pageContent.getPath();
            String href = externalizer.relativeLink(request, pageContent.getParent().getPath()) + ".html";
            String crxHref = externalizer.relativeLink(request, CRX_LITE_PATH) + ".jsp#" + pageContent.getPath();

            String title = "";
            if (pageContent.hasProperty(NameConstants.PN_TITLE)) {
                title = pageContent.getProperty(NameConstants.PN_TITLE).getString();
            }
            Map<String, Object> map = new HashMap<>();
            map.put("title", title);
            map.put("pagePath", pagePath);
            map.put("templateType", pageContent.getProperty(NameConstants.NN_TEMPLATE).getString());
            map.put("href", href);
            map.put("crxHref", crxHref);
            resources.add(new ValueMapResource(request.getResourceResolver(), request.getResource() + "/page" + index, itemResourceType, new ValueMapDecorator(map)));
            index++;
        }
        return resources;
    }

    private Query createQuery(String searchPath, ResourceResolver resolver) throws RepositoryException {
        String encodedPath = "/".equals(searchPath) ? "" : ISO9075.encodePath(searchPath);
        if (encodedPath.length() > 1 && encodedPath.endsWith("/")) {
            encodedPath = encodedPath.substring(0, encodedPath.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM [cq:PageContent] AS pc ");
        sb.append("WHERE ISDESCENDANTNODE(pc, '").append(encodedPath).append("') ");
        sb.append("AND pc.[cq:template] IN (");

        Iterator<String> it = structureRewriteRuleService.getTemplates().iterator();
        while (it.hasNext()) {
            sb.append("'").append(it.next()).append("'");
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(")");
        Session session = resolver.adaptTo(Session.class);
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        return queryManager.createQuery(sb.toString(), Query.JCR_SQL2);

    }
}
