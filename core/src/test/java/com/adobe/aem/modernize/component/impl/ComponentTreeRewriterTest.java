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

package com.adobe.aem.modernize.component.impl;

import java.util.List;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
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

public class ComponentTreeRewriterTest {

    private final String RULES_PATH = "/libs/cq/modernize/component/rules";
    private final String ROOTS_PATH = "/libs/cq/modernize/component/content/roots";
    private final String IMAGE_PATH = ROOTS_PATH + "/binary/image/data";

    private ComponentRewriteRuleService componentRewriteRuleService;

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerSlingNodeTypes(adminSession);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/component/test-rules.json", RULES_PATH);
        context.load().json("/component/test-content.json", ROOTS_PATH);
        context.load().binaryFile("/component/image.jpg", IMAGE_PATH);

        componentRewriteRuleService = context.registerService(ComponentRewriteRuleService.class, new ComponentRewriteRuleServiceImpl());
    }


    @Test
    public void testRewriteMaintainsOrder() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/level1/mapProperties").adaptTo(Node.class);

        List<ComponentRewriteRule> rules = componentRewriteRuleService.getRules(context.resourceResolver());


        ComponentTreeRewriter rewriter = new ComponentTreeRewriter(rules);
        Node rewrittenNode = rewriter.rewrite(rootNode);

        assertEquals("map-property-3", rewrittenNode.getProperty("map-property-nested").getValue().getString());

        Node level1 =  context.resourceResolver().getResource(ROOTS_PATH + "/level1").adaptTo(Node.class);
        NodeIterator nit = level1.getNodes();
        assertEquals("simple", nit.nextNode().getName());
        assertEquals("mapProperties", nit.nextNode().getName());
        assertEquals("rewriteProperties", nit.nextNode().getName());
        assertEquals("rewriteMapChildren", nit.nextNode().getName());
    }
}
