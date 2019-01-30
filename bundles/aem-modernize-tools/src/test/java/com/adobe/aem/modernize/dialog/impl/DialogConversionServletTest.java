/*
 *  (c) 2017 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.dialog.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.dialog.DialogRewriteRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class DialogConversionServletTest {

    private final String RULES_PATH = "/libs/cq/modernize/dialog/rules";

    private DialogConversionServlet dialogConversionServlet;

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Before
    public void setUp() throws Exception {
        ResourceResolver resolver = context.resourceResolver();
        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/dialog/test-rules.json", RULES_PATH);

        // register conversion servlet
        dialogConversionServlet = context.registerService(DialogConversionServlet.class, new DialogConversionServlet());
    }

    @Test
    public void testGetRules() throws Exception {
        List<String> expectedRulePaths = new ArrayList<String>();

        // expected ordering based on applied ranking is also represented here
        expectedRulePaths.addAll(Arrays.asList(
            RULES_PATH + "/rewriteRanking",
            RULES_PATH + "/simple",
            RULES_PATH + "/rewriteOptional",
            RULES_PATH + "/rewriteFinal",
            RULES_PATH + "/rewriteFinalOnReplacement",
            RULES_PATH + "/rewriteMapChildren",
            RULES_PATH + "/rewriteCommonAttrs",
            RULES_PATH + "/rewriteCommonAttrsData",
            RULES_PATH + "/rewriteRenderCondition",
            RULES_PATH + "/mapProperties",
            RULES_PATH + "/rewriteProperties",
            RULES_PATH + "/nested1/rule1",
            RULES_PATH + "/nested1/rule2",
            RULES_PATH + "/nested2/rule1"));

        Class[] cArgs = new Class[1];
        cArgs[0] = ResourceResolver.class;
        Method method = dialogConversionServlet.getClass().getDeclaredMethod("getRules", cArgs);
        method.setAccessible(true);

        Object[] args = new Object[1];
        args[0] = context.resourceResolver();
        List<DialogRewriteRule> rules = (List<DialogRewriteRule>) method.invoke(dialogConversionServlet, args);

        assertEquals(expectedRulePaths.size(), rules.size());

        // asserts:
        // - rules considered at root and first level folders
        // - rules ordered based on ranking
        int index = 0;
        for (DialogRewriteRule rule : rules) {
            String path = expectedRulePaths.get(index);
            assertTrue(rule.toString().contains("path=" + path + ","));
            index++;
        }
    }
}