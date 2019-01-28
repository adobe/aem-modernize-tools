package com.adobe.aem.modernize.structure.impl;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.impl.rules.ColumnControlRewriteRule;
import com.adobe.aem.modernize.structure.impl.rules.PageRewriteRule;
import com.adobe.aem.modernize.structure.impl.rules.ParsysRewriteRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import static org.junit.Assert.*;

public class StructureTreeRewriterTest {

    private final String ROOTS_PATH = "/libs/cq/modernize/component/content/roots";

    private static final String STATIC_TEMPLATE = "/apps/geometrixx/templates/homepage";
    private static final String EDITABLE_TEMPLATE = "/conf/geodemo/settings/wcm/templates/geometrixx-demo-home-page/structure";
    private static final String[] ORDER = { "header", "title" };
    private static final String[] REMOVE = { "toBeRemoved" };

    private static final String LAYOUT_VALUE = "2;cq-colctrl-lt0";
    private static final String[] COLUMN_WIDTHS = {"6", "6"};

    List<StructureRewriteRule> rules = new ArrayList<>();

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerSlingNodeTypes(adminSession);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/structure/test-content.json", ROOTS_PATH);

        bundleContext = MockOsgi.newBundleContext();

        StructureRewriteRule rule = new PageRewriteRule();
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("order.components", ORDER);
        props.put("remove.components", REMOVE);
        MockOsgi.activate(rule, bundleContext, props);
        rules.add(rule);

        rule = new ParsysRewriteRule();
        props = new Hashtable<>();
        MockOsgi.activate(rule, bundleContext, props);
        rules.add(rule);

        rule = new ColumnControlRewriteRule();
        props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", COLUMN_WIDTHS);
        MockOsgi.activate(rule, bundleContext, props);
        rules.add(rule);

    }


    @Test
    public void testRewrite() throws Exception {

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content").adaptTo(Node.class);

        StructureTreeRewriter rewriter = new StructureTreeRewriter(rules);
        Node rewrittenNode = rewriter.rewrite(rootNode);

        assertFalse(rewrittenNode.hasProperty("cq:designPath"));
        assertEquals(EDITABLE_TEMPLATE, rewrittenNode.getProperty("cq:template").getString());
        Node rootContainer = rewrittenNode.getNode("root");
        assertNotNull(rootContainer);

        NodeIterator rootContent = rootContainer.getNodes();
        assertTrue(rootContent.hasNext());
        assertEquals("header", rootContent.nextNode().getName());
        assertTrue(rootContent.hasNext());
        assertEquals("title", rootContent.nextNode().getName());

        assertTrue(rootContent.hasNext());
        Node replacedParsys = rootContent.nextNode();
        assertEquals("par", replacedParsys.getName());
        assertEquals(RewriteUtils.RESPONSIVE_GRID_RES_TYPE,
                replacedParsys.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());

        NodeIterator oldParsysContent = replacedParsys.getNodes();
        Node convertedColumn = oldParsysContent.nextNode();
        assertTrue(convertedColumn.getName().contains("responsivegrid"));
        assertEquals(RewriteUtils.RESPONSIVE_GRID_RES_TYPE,
                convertedColumn.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
        NodeIterator columnContent = convertedColumn.getNodes();
        assertEquals("cq:responsive", columnContent.nextNode().getName());
        Node responsive = convertedColumn.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        Node width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("6", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("0", width.getProperty("offset").getString());
        assertTrue(columnContent.hasNext());
        assertEquals("image", columnContent.nextNode().getName());
        assertTrue(columnContent.hasNext());
        assertEquals("title_1", columnContent.nextNode().getName());
        assertTrue(columnContent.hasNext());
        assertEquals("text_1", columnContent.nextNode().getName());

        assertFalse(columnContent.hasNext());

        // Found the next new responsive grid
        // Verify it's structure
        assertTrue(oldParsysContent.hasNext());
        convertedColumn = oldParsysContent.nextNode();
        assertTrue(convertedColumn.getName().contains("responsivegrid"));
        columnContent = convertedColumn.getNodes();
        assertTrue(columnContent.hasNext());
        assertEquals("cq:responsive", columnContent.nextNode().getName());
        responsive = convertedColumn.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("6", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("6", width.getProperty("offset").getString());

        assertTrue(columnContent.hasNext());
        assertEquals("image_0", columnContent.nextNode().getName());
        assertTrue(columnContent.hasNext());
        assertEquals("title_2", columnContent.nextNode().getName());
        assertTrue(columnContent.hasNext());
        assertEquals("text_0", columnContent.nextNode().getName());

        assertFalse(columnContent.hasNext());
        assertFalse(rootContent.hasNext());

    }
}
