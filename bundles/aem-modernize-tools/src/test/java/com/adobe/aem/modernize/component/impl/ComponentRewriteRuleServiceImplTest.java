package com.adobe.aem.modernize.component.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComponentRewriteRuleServiceImplTest {

    private final String RULES_PATH = "/libs/cq/modernize/component/rules";

    private ComponentRewriteRuleService componentRewriteRuleService;

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerNodeType(adminSession,
                getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/component/test-rules.json", RULES_PATH);

        // register conversion service
        componentRewriteRuleService = context.registerService(ComponentRewriteRuleService.class,
                new ComponentRewriteRuleServiceImpl());
    }

    @Test
    public void testGetRules() throws Exception {
        List<String> expectedRulePaths = new ArrayList<>();

        // expected ordering based on applied ranking is also represented here
        expectedRulePaths.addAll(Arrays.asList(
                RULES_PATH + "/rewriteRanking",
                RULES_PATH + "/simple",
                RULES_PATH + "/copyChildren",
                RULES_PATH + "/copyChildrenOrder",
                RULES_PATH + "/mapProperties",
                RULES_PATH + "/rewriteOptional",
                RULES_PATH + "/rewriteMapChildren",
                RULES_PATH + "/rewriteFinal",
                RULES_PATH + "/rewriteFinalOnReplacement",
                RULES_PATH + "/rewriteProperties",
                RULES_PATH + "/nested1/rule1",
                RULES_PATH + "/nested1/rule2",
                RULES_PATH + "/nested2/rule1"));

        List<ComponentRewriteRule> rules = componentRewriteRuleService.getRules(context.resourceResolver());

        assertEquals(expectedRulePaths.size(), rules.size());

        // asserts:
        // - rules considered at root and first level folders
        // - rules ordered based on ranking
        int index = 0;
        for (ComponentRewriteRule rule : rules) {
            String path = expectedRulePaths.get(index);
            assertTrue(rule.toString().contains("path=" + path + ","));
            index++;
        }
    }

    @Test
    public void testGetSlingResourceTypes() throws Exception {
        Set<String> resourceTypes = componentRewriteRuleService.getSlingResourceTypes(context.resourceResolver());

        assertTrue(resourceTypes.contains("geometrixx/components/simple"));
        resourceTypes.remove("geometrixx/components/simple");
        assertTrue(resourceTypes.contains("geometrixx/components/mapProperties"));
        resourceTypes.remove("geometrixx/components/mapProperties");
        assertTrue(resourceTypes.contains("geometrixx/components/rewriteOptional"));
        resourceTypes.remove("geometrixx/components/rewriteOptional");
        assertTrue(resourceTypes.contains("geometrixx/components/rewriteRanking"));
        resourceTypes.remove("geometrixx/components/rewriteRanking");
        assertTrue(resourceTypes.contains("geometrixx/components/rewriteMapChildren"));
        resourceTypes.remove("geometrixx/components/rewriteMapChildren");
        assertTrue(resourceTypes.contains("geometrixx/components/rewriteFinal"));
        resourceTypes.remove("geometrixx/components/rewriteFinal");
        assertTrue(resourceTypes.contains("geometrixx/components/rewriteFinalOnReplacement"));
        resourceTypes.remove("geometrixx/components/rewriteFinalOnReplacement");
        assertTrue(resourceTypes.contains("geometrixx/components/rewriteProperties"));
        resourceTypes.remove("geometrixx/components/rewriteProperties");
        assertTrue(resourceTypes.contains("granite/ui/components/foundation/container"));
        resourceTypes.remove("granite/ui/components/foundation/container");



        assertTrue(resourceTypes.isEmpty());

    }
}
