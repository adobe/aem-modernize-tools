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

package com.adobe.aem.modernize.structure.impl.rules;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.structure.StructureRewriteRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import static org.junit.Assert.*;

public class PageRewriteRuleTest {
    private final String ROOTS_PATH = "/libs/cq/modernize/templatestructure/content/roots";

    private static final String STATIC_TEMPLATE = "/apps/geometrixx/templates/homepage";
    private static final String EDITABLE_TEMPLATE = "/conf/geodemo/settings/wcm/templates/geometrixx-demo-home-page";
    private static final String SLING_RESOURCE_TYPE = "geodemo/components/structure/page";



    private static final String[] ORDER = {
            "responsivegrid",
            "responsivegrid_12345",
            "responsivegrid:header",
            "responsivegrid:title"
    };
    private static final String[] REMOVE = { "toBeRemoved" };
    private static final String[] RENAME = { "par:responsivegrid/responsivegrid", "rightpar:responsivegrid_12345" };

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

        PageRewriteRule rule = new PageRewriteRule();

        // inject dependencies
        MockOsgi.injectServices(rule, bundleContext);

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        props.put("order.components", ORDER);
        props.put("remove.components", REMOVE);
        props.put("rename.components", RENAME);
        // activate service
        MockOsgi.activate(rule, bundleContext, props);
        bundleContext.registerService(StructureRewriteRule.class, rule, props);

    }

    @Test(expected = RuntimeException.class)
    public void testActivateNoStatic() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test(expected = RuntimeException.class)
    public void testActivateNoEditable() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        MockOsgi.activate(rule, bundleContext, props);
    }


    @Test(expected = RuntimeException.class)
    public void testActivateNoSlingResourceType() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivateNoOrder() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        props.put("remove.components", REMOVE);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivateNoRemove() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        props.put("order.components", ORDER);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivateNoRename() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        props.put("order.components", ORDER);
        props.put("remove.components", REMOVE);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testActivate() {
        BundleContext bundleContext = MockOsgi.newBundleContext();
        PageRewriteRule rule = new PageRewriteRule();
        MockOsgi.injectServices(rule, bundleContext);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        props.put("order.components", ORDER);
        props.put("remove.components", REMOVE);
        props.put("rename.components", RENAME);
        MockOsgi.activate(rule, bundleContext, props);
    }

    @Test
    public void testMatches() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule = bundleContext.<StructureRewriteRule>getService(ref);

        assertTrue(rule.matches(rootNode));
    }

    @Test
    public void testDoesNotMatch() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/doesNotMatch/jcr:content").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule = bundleContext.<StructureRewriteRule>getService(ref);

        assertFalse(rule.matches(rootNode));
    }

    @Test
    public void testApplyTo() throws Exception {

        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/matches/jcr:content").adaptTo(Node.class);

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule = bundleContext.<StructureRewriteRule>getService(ref);

        Node rewrittenNode = rule.applyTo(rootNode, new HashSet<>());

        assertFalse(rewrittenNode.hasProperty("cq:designPath"));
        assertEquals(EDITABLE_TEMPLATE, rewrittenNode.getProperty("cq:template").getString());
        assertEquals(SLING_RESOURCE_TYPE, rewrittenNode.getProperty("sling:resourceType").getString());
        Node rootContainer = rewrittenNode.getNode("root");
        assertNotNull(rootContainer);
        assertEquals("nt:unstructured", rootContainer.getPrimaryNodeType().getName());
        assertEquals("wcm/foundation/components/responsivegrid", rootContainer.getProperty("sling:resourceType").getString());
        NodeIterator children = rootContainer.getNodes();
        assertTrue(children.hasNext());
        Node rg = children.nextNode();
        assertEquals("responsivegrid", rg.getName());
        assertTrue(children.hasNext());
        assertEquals("responsivegrid_12345", children.nextNode().getName());
        assertFalse(children.hasNext());

        NodeIterator rgChildren = rg.getNodes();
        assertEquals("header", rgChildren.nextNode().getName());
        assertTrue(rgChildren.hasNext());
        assertEquals("title", rgChildren.nextNode().getName());
        assertTrue(rgChildren.hasNext());
        assertEquals("responsivegrid", rgChildren.nextNode().getName());
        assertFalse(rgChildren.hasNext());
    }

    @Test
    public void testRanking() throws Exception {

        ServiceReference ref = bundleContext.getServiceReference(StructureRewriteRule.class.getName());
        StructureRewriteRule rule  = bundleContext.<StructureRewriteRule>getService(ref);
        assertEquals(1, rule.getRanking());
    }

}
