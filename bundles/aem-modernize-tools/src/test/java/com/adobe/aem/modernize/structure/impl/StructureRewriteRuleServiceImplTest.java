package com.adobe.aem.modernize.structure.impl;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.structure.PageStructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.adobe.aem.modernize.structure.impl.rules.ColumnControlRewriteRule;
import com.adobe.aem.modernize.structure.impl.rules.PageRewriteRule;
import com.adobe.aem.modernize.structure.impl.rules.ParsysRewriteRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.*;

public class StructureRewriteRuleServiceImplTest {

    private static final String STATIC_HOME_TEMPLATE = "/apps/geometrixx/templates/homepage";
    private static final String STATIC_PRODUCT_TEMPLATE = "/apps/geometrixx/templates/productpage";
    private static final String EDITABLE_TEMPLATE = "/conf/geodemo/settings/wcm/templates/geometrixx-demo-home-page";
    private static final String SLING_RESOURCE_TYPE = "geodemo/components/structure/page";

    private static final String LAYOUT_VALUE = "2;cq-colctrl-lt0";
    private static final String[] COLUMN_WIDTHS = {"6", "6"};

    private StructureRewriteRuleService structureRewriteRuleService;

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private BundleContext bundleContext;

    List<StructureRewriteRule> rules = new ArrayList<>();


    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerNodeType(adminSession,
                getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        // register conversion service
        structureRewriteRuleService = context.registerService(StructureRewriteRuleService.class,
                new StructureRewriteRuleServiceImpl());

        bundleContext = MockOsgi.newBundleContext();

        StructureRewriteRule rule = new PageRewriteRule();
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_HOME_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        MockOsgi.activate(rule, bundleContext, props);
        context.registerService(PageStructureRewriteRule.class, (PageStructureRewriteRule) rule);
        rules.add(rule);

        rule = new PageRewriteRule();
        props = new Hashtable<>();
        props.put("static.template", STATIC_PRODUCT_TEMPLATE);
        props.put("sling.resourceType", SLING_RESOURCE_TYPE);
        props.put("editable.template", "/conf/geodemo/settings/wcm/templates/geometrixx-demo-product-page");
        MockOsgi.activate(rule, bundleContext, props);
        context.registerService(PageStructureRewriteRule.class, (PageStructureRewriteRule) rule);
        rules.add(rule);

        rule = new ParsysRewriteRule();
        props = new Hashtable<>();
        MockOsgi.activate(rule, bundleContext, props);
        context.registerService(StructureRewriteRule.class, rule);
        rules.add(rule);

        rule = new ColumnControlRewriteRule();
        props = new Hashtable<>();
        props.put("layout.value", LAYOUT_VALUE);
        props.put("column.widths", COLUMN_WIDTHS);
        MockOsgi.activate(rule, bundleContext, props);
        context.registerService(StructureRewriteRule.class, rule);
        rules.add(rule);

        // inject dependencies
        MockOsgi.injectServices(structureRewriteRuleService, bundleContext);

    }

    @Test
    public void testGetTemplate() {

        Set<String> templates = structureRewriteRuleService.getTemplates();
        assertEquals(2, templates.size());
        assertTrue(templates.contains(STATIC_HOME_TEMPLATE));
        assertTrue(templates.contains(STATIC_PRODUCT_TEMPLATE));
    }

    @Test
    public void testGetRules() throws Exception {
        List<StructureRewriteRule> rules = structureRewriteRuleService.getRules(null);
        assertNotNull(rules);
        assertFalse(rules.isEmpty());
        assertEquals(4, rules.size());
        Iterator<StructureRewriteRule> it = rules.iterator();
        StructureRewriteRule rule = it.next();
        assertTrue(rule instanceof PageStructureRewriteRule);
        rule = it.next();
        assertTrue(rule instanceof PageStructureRewriteRule);
        rule = it.next();
        assertTrue(rule instanceof ParsysRewriteRule);
        rule = it.next();
        assertTrue(rule instanceof ColumnControlRewriteRule);

    }
}
