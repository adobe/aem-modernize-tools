package com.adobe.aem.modernize.structure.impl.rules;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import static org.junit.Assert.*;

public class ColumnControlRewriteRuleTest {
    private final String ROOTS_PATH = "/libs/cq/modernize/templatestructure/content/roots";

    private static final String LAYOUT_VALUE = "2;cq-colctrl-lt0";
    private static final String[] COLUMN_WIDTHS = {"6", "6"};

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerSlingNodeTypes(adminSession);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/templatestructure/test-content.json", ROOTS_PATH);

        // get bundle context
        bundleContext = MockOsgi.newBundleContext();

        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();

        // inject dependencies
        MockOsgi.injectServices(rule, bundleContext);

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", COLUMN_WIDTHS);
        // activate service
        MockOsgi.activate(rule, bundleContext, props);
        bundleContext.registerService(StructureRewriteRule.class, rule, props);

    }

    @Test(expected = RuntimeException.class)
    public void testActivateNoLayout() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test(expected = RuntimeException.class)
    public void testActivateNoWidths() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test(expected = RuntimeException.class)
    public void testActivateWidthsWrongType() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", "String?");
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivate() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", COLUMN_WIDTHS);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivateNoResourceType() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", COLUMN_WIDTHS);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivateBlankResourceType() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        ColumnControlRewriteRule rule = new ColumnControlRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", COLUMN_WIDTHS);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testMatches() throws Exception {

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content/par/colctrl").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);

        assertTrue(rule.matches(rootNode));
    }

    @Test
    public void testNoMatchResourceType() throws Exception {

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);

        assertFalse(rule.matches(rootNode));
    }

    @Test
    public void testNoMatchLayout() throws Exception {

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content/par/col_break").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);

        assertFalse(rule.matches(rootNode));
    }

    @Test
    public void testNoMatchWrongColumnCount() throws Exception {

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/doesNotMatch/jcr:content/par/colctrl").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);

        assertFalse(rule.matches(rootNode));
    }


    @Test
    public void testApplyTo() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content/par/colctrl").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);

        Node rewrittenNode = rule.applyTo(rootNode, new HashSet<>());
        assertTrue(rewrittenNode.getName().contains("responsivegrid"));
        assertEquals(RewriteUtils.RESPONSIVE_GRID_RES_TYPE,
                rewrittenNode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());

        // Verify child order of first column.
        NodeIterator children = rewrittenNode.getNodes();
        assertTrue(children.hasNext());
        assertEquals("cq:responsive", children.nextNode().getName());
        Node responsive = rewrittenNode.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        Node width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("6", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("0", width.getProperty("offset").getString());
        assertTrue(children.hasNext());
        assertEquals("image", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("title_1", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("text_1", children.nextNode().getName());

        // Need to find the next node in the list of siblings, it should be the next column.
        Node parent = rewrittenNode.getParent();
        NodeIterator siblings = parent.getNodes();
        while (siblings.hasNext()) {
            if (siblings.nextNode().getName().equals(rewrittenNode.getName())) {
                break;
            }
        }

        // Found the next new responsive grid
        // Verify it's structure
        assertTrue(siblings.hasNext());
        Node sib = siblings.nextNode();
        assertTrue(sib.getName().contains("responsivegrid"));
        assertEquals(RewriteUtils.RESPONSIVE_GRID_RES_TYPE,
                sib.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
        children = sib.getNodes();
        assertTrue(children.hasNext());
        assertEquals("cq:responsive", children.nextNode().getName());
        responsive = sib.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("6", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("6", width.getProperty("offset").getString());

        assertTrue(children.hasNext());
        assertEquals("image_0", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("title_2", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("text_0", children.nextNode().getName());

        // Verify column end is no longer in the list of components
        siblings = parent.getNodes();
        while (siblings.hasNext()) {
            String nodeName = siblings.nextNode().getName();
            assertFalse(StringUtils.equals("colctrl", nodeName));
            assertFalse(nodeName.contains("col_break"));
            assertFalse(StringUtils.equals("col_end", nodeName));
        }
    }

    @Test
    public void testApplyToFourColumns() throws Exception {

        StructureRewriteRule rule = new ColumnControlRewriteRule();
        BundleContext bundleContext = MockOsgi.newBundleContext();

        // inject dependencies
        MockOsgi.injectServices(rule, bundleContext);

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", new String[]{"4", "2", "4", "2"});

        // activate service
        MockOsgi.activate(rule, bundleContext, props);
        bundleContext.registerService(StructureRewriteRule.class, rule, props);

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/fourColumns/jcr:content/par/colctrl").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        rule = bundleContext.<StructureRewriteRule>getService(ref);

        Node rewrittenNode = rule.applyTo(rootNode, new HashSet<>());
        assertTrue(rewrittenNode.getName().contains("responsivegrid"));
        assertEquals(RewriteUtils.RESPONSIVE_GRID_RES_TYPE,
                rewrittenNode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());

        // Verify child order of first column.
        NodeIterator children = rewrittenNode.getNodes();
        assertTrue(children.hasNext());
        assertEquals("cq:responsive", children.nextNode().getName());
        Node responsive = rewrittenNode.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        Node width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("4", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("0", width.getProperty("offset").getString());
        assertTrue(children.hasNext());
        assertEquals("image", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("title_1", children.nextNode().getName());

        // Need to find the next node in the list of siblings, it should be the next column.
        Node parent = rewrittenNode.getParent();
        NodeIterator siblings = parent.getNodes();
        while (siblings.hasNext()) {
            if (siblings.nextNode().getName().equals(rewrittenNode.getName())) {
                break;
            }
        }

        // Found the next new responsive grid
        // Verify it's structure
        assertTrue(siblings.hasNext());
        Node sib = siblings.nextNode();
        assertTrue(sib.getName().contains("responsivegrid"));
        children = sib.getNodes();
        assertTrue(children.hasNext());
        assertEquals("cq:responsive", children.nextNode().getName());
        responsive = sib.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("2", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("4", width.getProperty("offset").getString());
        assertTrue(children.hasNext());
        assertEquals("text_1", children.nextNode().getName());

        assertTrue(siblings.hasNext());
        sib = siblings.nextNode();
        assertTrue(sib.getName().contains("responsivegrid"));
        children = sib.getNodes();
        assertTrue(children.hasNext());
        assertEquals("cq:responsive", children.nextNode().getName());
        responsive = sib.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("4", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("6", width.getProperty("offset").getString());
        assertTrue(children.hasNext());
        assertEquals("image_0", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("title_2", children.nextNode().getName());

        assertTrue(siblings.hasNext());
        sib = siblings.nextNode();
        assertTrue(sib.getName().contains("responsivegrid"));
        children = sib.getNodes();
        assertTrue(children.hasNext());
        assertEquals("cq:responsive", children.nextNode().getName());
        responsive = sib.getNode("cq:responsive");
        assertTrue(responsive.hasNode("default"));
        width = responsive.getNode("default");
        assertTrue(width.hasProperty("width"));
        assertEquals("2", width.getProperty("width").getString());
        assertTrue(width.hasProperty("offset"));
        assertEquals("10", width.getProperty("offset").getString());
        assertTrue(children.hasNext());
        assertEquals("text_0", children.nextNode().getName());

        // Verify column end is no longer in the list of components
        siblings = parent.getNodes();
        while (siblings.hasNext()) {
            assertFalse(StringUtils.equals("col_end", siblings.nextNode().getName()));
        }
    }

    @Test
    public void testRanking() throws Exception {

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);
        assertEquals(3, rule.getRanking());
    }

}
