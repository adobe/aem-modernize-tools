package com.adobe.aem.modernize.structure.impl.rules;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
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

public class ParsysRewriteRuleTest {
    private final String ROOTS_PATH = "/libs/cq/modernize/structure/content/roots";

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);


    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerSlingNodeTypes(adminSession);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/structure/test-content.json", ROOTS_PATH);
    }

    @Test
    public void testMatches() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content/par").adaptTo(Node.class);
        ParsysRewriteRule rule = new ParsysRewriteRule();
        assertTrue(rule.matches(rootNode));
    }

    @Test
    public void testDoesNotMatch() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content").adaptTo(Node.class);
        ParsysRewriteRule rule = new ParsysRewriteRule();
        assertFalse(rule.matches(rootNode));
    }

    @Test
    public void testApplyTo() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content/par").adaptTo(Node.class);

        ParsysRewriteRule rule = new ParsysRewriteRule();
        Node rewrittenNode = rule.applyTo(rootNode, new LinkedHashSet<>());

        assertEquals(RewriteUtils.RESPONSIVE_GRID_RES_TYPE,
                rewrittenNode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
        assertEquals(JcrConstants.NT_UNSTRUCTURED, rewrittenNode.getPrimaryNodeType().getName());

        // Verify Children
        NodeIterator children = rewrittenNode.getNodes();
        assertTrue(children.hasNext());
        assertEquals("colctrl", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("image", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("title_1", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("text_1", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("col_break", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("image_0", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("title_2", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("text_0", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("col_end", children.nextNode().getName());

        // Verify Parent order preserved
        NodeIterator siblings = rewrittenNode.getParent().getNodes();
        assertTrue(siblings.hasNext());
        assertEquals("par", siblings.nextNode().getName());
        assertTrue(siblings.hasNext());
        assertEquals("toBeRemoved", siblings.nextNode().getName());
        assertTrue(siblings.hasNext());
        assertEquals("title", siblings.nextNode().getName());
        assertTrue(siblings.hasNext());
        assertEquals("header", siblings.nextNode().getName());
        assertFalse(siblings.hasNext());
    }

    @Test
    public void testRanking() throws Exception {
    // get bundle context
        BundleContext bundleContext = MockOsgi.newBundleContext();

        StructureRewriteRule rule = new ParsysRewriteRule();

        // inject dependencies
        MockOsgi.injectServices(rule, bundleContext);

        Dictionary<String, Object> props = new Hashtable<>();
        // activate service
        MockOsgi.activate(rule, bundleContext, props);
        bundleContext.registerService(StructureRewriteRule.class, rule, props);
        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        rule = bundleContext.<StructureRewriteRule>getService(ref);

        assertEquals(2, rule.getRanking());
    }

}
