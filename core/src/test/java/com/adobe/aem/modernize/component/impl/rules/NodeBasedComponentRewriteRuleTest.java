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
package com.adobe.aem.modernize.component.impl.rules;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class NodeBasedComponentRewriteRuleTest {

    private final String RULES_PATH = "/libs/cq/modernize/component/rules";
    private final String ROOTS_PATH = "/libs/cq/modernize/component/content/roots";
    private final String IMAGE_PATH = ROOTS_PATH + "/binary/image/data";

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
    }

    @Test
    public void testBasic() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/simple").adaptTo(Node.class);
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/simple").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        assertTrue(rule.matches(rootNode));
        Set<Node> finalNodes = new LinkedHashSet<>();

        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertNotNull(rewrittenNode);
        assertEquals("simple", rewrittenNode.getName());
        assertEquals("core/wcm/components/title/v2/title", rewrittenNode.getProperty("sling:resourceType").getString());
    }

    @Test
    public void testCopyChildren() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/copyChildren").adaptTo(Node.class);
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/copyChildren").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        assertTrue(rule.matches(rootNode));
        Set<Node> finalNodes = new LinkedHashSet<>();

        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertNotNull(rewrittenNode);
        assertEquals("copyChildren", rewrittenNode.getName());
        assertEquals("core/wcm/components/title/v2/title", rewrittenNode.getProperty("sling:resourceType").getString());
        NodeIterator children = rewrittenNode.getNodes();
        assertTrue(children.hasNext());
        assertEquals("items", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("header", children.nextNode().getName());
        assertFalse(children.hasNext());
    }

    @Test
    public void testCopyChildrenOrder() throws Exception {
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/copyChildrenOrder").adaptTo(Node.class);
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/copyChildrenOrder").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        assertTrue(rule.matches(rootNode));
        Set<Node> finalNodes = new LinkedHashSet<>();

        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertNotNull(rewrittenNode);
        assertEquals("copyChildrenOrder", rewrittenNode.getName());
        assertEquals("core/wcm/components/title/v2/title", rewrittenNode.getProperty("sling:resourceType").getString());
        NodeIterator children = rewrittenNode.getNodes();
        assertTrue(children.hasNext());
        assertEquals("parsys", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("items", children.nextNode().getName());
        assertTrue(children.hasNext());
        assertEquals("header", children.nextNode().getName());
        assertFalse(children.hasNext());
    }

    @Test
    public void testMapProperties() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/mapProperties").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/mapProperties").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertEquals("map-property-1", rewrittenNode.getProperty("map-property-simple").getValue().getString());
    }

    @Test
    public void testMapPropertiesNested() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/mapProperties").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/mapProperties").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertEquals("map-property-3", rewrittenNode.getProperty("map-property-nested").getValue().getString());
    }

    @Test
    public void testMapPropertiesNegation() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/mapProperties").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/mapProperties").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertFalse(rewrittenNode.getProperty("map-property-negation").getValue().getBoolean());
    }

    @Test
    public void testMapPropertiesDefault() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/mapProperties").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/mapProperties").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertEquals("default", rewrittenNode.getProperty("map-property-default").getValue().getString());
        assertEquals("default", rewrittenNode.getProperty("map-property-default-quoted").getValue().getString());
    }

    @Test
    public void testMapPropertiesMultiple() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/mapProperties").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/mapProperties").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<Node>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        assertEquals("map-property-1", rewrittenNode.getProperty("map-property-multiple").getValue().getString());
        assertEquals("default", rewrittenNode.getProperty("map-property-multiple-default").getValue().getString());
        assertFalse(rewrittenNode.getProperty("map-property-multiple-negation").getValue().getBoolean());
    }

    @Test
    public void testRewriteOptional() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/rewriteOptional").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/rewriteOptional").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);

        assertTrue(rule.matches(rootNode));

        // remove the cq:rewriteOptional property on the pattern items node and check it no longer matches
        Node itemsNode = ruleNode.getNode("patterns/pattern/items");
        itemsNode.getProperty("cq:rewriteOptional").remove();
        rule = new NodeBasedComponentRewriteRule(ruleNode);

        assertFalse(rule.matches(rootNode));
    }

    @Test
    public void testGetRanking() {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/simple").adaptTo(Node.class);
        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        assertEquals(Integer.MAX_VALUE, rule.getRanking());
    }

    @Test
    public void testRewriteRanking() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/rewriteRanking").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/simple").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<Node>();
        rule.applyTo(rootNode, finalNodes);

        assertEquals(3, rule.getRanking());

        // remove the cq:rewriteRanking property on the rule node and check against expected value
        ruleNode.getProperty("cq:rewriteRanking").remove();
        rule = new NodeBasedComponentRewriteRule(ruleNode);

        assertEquals(Integer.MAX_VALUE, rule.getRanking());
    }

    @Test
    public void testRewriteMapChildren() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/rewriteMapChildren").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/rewriteMapChildren").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        Node itemsNode = rewrittenNode.getNode("items");

        // the items were copied to the rewritten tree
        assertTrue(itemsNode.hasNodes());

        NodeIterator nodeIterator = itemsNode.getNodes();
        int itemCount = 0;

        while(nodeIterator.hasNext()) {
            itemCount++;
            nodeIterator.next();
        }

        assertEquals(2, itemCount);
    }

    @Test
    public void testRewriteFinal() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/rewriteFinal").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/simple").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<Node>();

        rule.applyTo(rootNode, finalNodes);

        Node[] finalNodesArray = finalNodes.toArray(new Node[0]);

        assertEquals(1, finalNodesArray.length);
        assertEquals("simple", finalNodesArray[0].getName());
    }

    @Test
    public void testRewriteFinalOnReplacement() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/rewriteFinalOnReplacement").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/simple").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<Node>();

        rule.applyTo(rootNode, finalNodes);

        Node[] finalNodesArray = finalNodes.toArray(new Node[0]);

        assertEquals(2, finalNodesArray.length);
        assertEquals("simple", finalNodesArray[0].getName());
        assertEquals("items", finalNodesArray[1].getName());
    }

    @Test
    public void testRewriteProperties() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/rewriteProperties").adaptTo(Node.class);
        Node rootNode = context.resourceResolver().getResource(ROOTS_PATH + "/rewriteProperties").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        Set<Node> finalNodes = new LinkedHashSet<Node>();
        Node rewrittenNode = rule.applyTo(rootNode, finalNodes);

        String removePrefix = rewrittenNode.getProperty("rewrite-remove-prefix").getValue().getString();
        String removeSuffix = rewrittenNode.getProperty("rewrite-remove-suffix").getValue().getString();
        String concatTokens = rewrittenNode.getProperty("rewrite-concat-tokens").getValue().getString();
        String singleOperand = rewrittenNode.getProperty("rewrite-single-operand").getValue().getString();
        String rewriteBoolean = rewrittenNode.getProperty("rewrite-boolean").getValue().getString();

        // cq:rewriteProperties node discarded
        assertFalse(rewrittenNode.hasNode("cq:rewriteProperties"));

        // check some pattern/replacement use cases
        assertEquals("token", removePrefix);
        assertEquals("token", removeSuffix);
        assertEquals("token1token2", concatTokens);

        // missing replacement operand - expect original mapping
        assertEquals("prefix-token", singleOperand);

        // properties not of type string aren't rewritten
        assertEquals("true", rewriteBoolean);
    }

    @Test
    public void testToString() {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/simple").adaptTo(Node.class);

        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);
        String expected = "NodeBasedComponentRewriteRule[path=" + RULES_PATH + "/simple,ranking=" + Integer.MAX_VALUE + "]";
        assertEquals(expected, rule.toString());
    }

    @Test
    public void testGetSlingResourceTypes() throws Exception {
        Node ruleNode = context.resourceResolver().getResource(RULES_PATH + "/simple").adaptTo(Node.class);
        NodeBasedComponentRewriteRule rule = new NodeBasedComponentRewriteRule(ruleNode);

        Set<String> types = rule.getSlingResourceTypes();

        assertFalse(types.isEmpty());
        assertEquals(1, types.size());
        assertEquals("geometrixx/components/simple", types.toArray()[0]);
    }
}