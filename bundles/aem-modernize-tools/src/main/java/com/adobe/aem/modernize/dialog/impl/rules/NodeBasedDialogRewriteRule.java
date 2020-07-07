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
package com.adobe.aem.modernize.dialog.impl.rules;

import com.adobe.aem.modernize.dialog.DialogRewriteRule;
import com.adobe.aem.modernize.impl.AbstractNodeBasedRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import java.util.Iterator;

/**
 * An rule that rewrites a tree based on a given node structure. The node structure follows all
 * of the rules as found in {@link AbstractNodeBasedRewriteRule} with the following additional functionality:
 *
 * <ul>
 * <li>
 * <code>cq:rewriteCommonAttrs</code> (boolean)<br />
 * Set this property on the replacement node (<code>rule/replacement</code>) to map relevant properties
 * of the original root node to Granite common attribute equivalents in the copy root. It will handle data
 * attributes by copying/creating the granite:data subnode on the target and writing data-* properties there.
 * </li>
 * <li>
 * <code>cq:rewriteRenderCondition</code> (boolean)<br />
 * Set this property on the replacement node (<code>rule/replacement</code>) to copy any render condition
 * (rendercondition or granite:rendercondition) child node from the original root node to a
 * granite:rendercondition child of the copy root.
 * </li>
 * </ul>
 */
public class NodeBasedDialogRewriteRule extends AbstractNodeBasedRewriteRule implements DialogRewriteRule {
    
    // special properties
    private static final String PROPERTY_COMMON_ATTRS = "cq:rewriteCommonAttrs";
    private static final String PROPERTY_RENDER_CONDITION = "cq:rewriteRenderCondition";
    
    // node names
    private static final String NN_RENDER_CONDITION = "rendercondition";
    private static final String NN_GRANITE_RENDER_CONDITION = "granite:rendercondition";
    private static final String NN_GRANITE_DATA = "granite:data";
    
    // Granite
    private static final String[] GRANITE_COMMON_ATTR_PROPERTIES = {"id", "rel", "class", "title", "hidden", "itemscope", "itemtype", "itemprop"};
    private static final String RENDER_CONDITION_CORAL2_RESOURCE_TYPE_PREFIX = "granite/ui/components/foundation/renderconditions";
    private static final String RENDER_CONDITION_CORAL3_RESOURCE_TYPE_PREFIX = "granite/ui/components/coral/foundation/renderconditions";
    private static final String DATA_PREFIX = "data-";
    
    
    public NodeBasedDialogRewriteRule(Node ruleNode) {
        super(ruleNode);
    }
    
    public void doAdditionalApplyTo(Node root, Node copy, Node replacementRules) throws RepositoryException {
        
        // common attribute mapping
        if (replacementRules.hasProperty(PROPERTY_COMMON_ATTRS)) {
            addCommonAttrMappings(root, copy);
        }
        
        // render condition mapping
        if (replacementRules.hasProperty(PROPERTY_RENDER_CONDITION)) {
            if (root.hasNode(NN_GRANITE_RENDER_CONDITION) || root.hasNode(NN_RENDER_CONDITION)) {
                Node renderConditionRoot = root.hasNode(NN_GRANITE_RENDER_CONDITION) ?
                        root.getNode(NN_GRANITE_RENDER_CONDITION) : root.getNode(NN_RENDER_CONDITION);
                Node renderConditionCopy = JcrUtil.copy(renderConditionRoot, copy, NN_GRANITE_RENDER_CONDITION);
                
                // convert render condition resource types recursively
                TreeTraverser renderConditionTraverser = new TreeTraverser(renderConditionCopy);
                Iterator<Node> renderConditionIterator = renderConditionTraverser.iterator();
                
                while (renderConditionIterator.hasNext()) {
                    Node renderConditionNode = renderConditionIterator.next();
                    String resourceType = renderConditionNode.getProperty(ResourceResolver.PROPERTY_RESOURCE_TYPE).getString();
                    if (resourceType.startsWith(RENDER_CONDITION_CORAL2_RESOURCE_TYPE_PREFIX)) {
                        resourceType = resourceType.replace(RENDER_CONDITION_CORAL2_RESOURCE_TYPE_PREFIX, RENDER_CONDITION_CORAL3_RESOURCE_TYPE_PREFIX);
                        renderConditionNode.setProperty(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
                    }
                }
            }
        }
    }
    
    /**
     * Adds property mappings on a replacement node for Granite common attributes.
     *
     * @param root the root node
     * @param node the replacement node
     */
    private void addCommonAttrMappings(Node root, Node node) throws RepositoryException {
        for (String property : GRANITE_COMMON_ATTR_PROPERTIES) {
            String[] mapping = {"${./" + property + "}", "${'./granite:" + property + "'}"};
            mapProperty(root, node.setProperty("granite:" + property, mapping));
        }
        
        if (root.hasNode(NN_GRANITE_DATA)) {
            // the root has granite:data defined, copy it before applying data-* properties
            JcrUtil.copy(root.getNode(NN_GRANITE_DATA), node, NN_GRANITE_DATA);
        }
        
        // map data-* prefixed properties to granite:data child
        PropertyIterator propertyIterator = root.getProperties(DATA_PREFIX + "*");
        while (propertyIterator.hasNext()) {
            Property property = propertyIterator.nextProperty();
            String name = property.getName();
            
            // skip protected properties
            if (property.getDefinition().isProtected()) {
                continue;
            }
            
            // add the granite:data child if necessary
            if (!node.hasNode(NN_GRANITE_DATA)) {
                node.addNode(NN_GRANITE_DATA);
            }
            
            // set up the property mapping
            if (node.hasNode(NN_GRANITE_DATA)) {
                Node dataNode = node.getNode(NN_GRANITE_DATA);
                String nameWithoutPrefix = name.substring(DATA_PREFIX.length());
                mapProperty(root, dataNode.setProperty(nameWithoutPrefix, "${./" + name + "}"));
            }
        }
    }
}
