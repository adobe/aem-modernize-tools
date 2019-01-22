/*
 *  (c) 2014 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.component.impl.rules;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteUtils;
import com.adobe.aem.modernize.component.ComponentRewriteException;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rule that rewrites a tree based on a given node structure. The node structure
 * has the following form:
 *
 * <pre>
 * rule
 *   - jcr:primaryType = nt:unstructured
 *   - cq:rewriteRanking = 4
 *   + patterns
 *     - jcr:primaryType = nt:unstructured
 *     + foo
 *       - ...
 *       + ...
 *     + foo1
 *       - ...
 *       + ...
 *   + replacement
 *     + bar
 *       - ...
 *       + ...
 * </pre>
 *
 * <p>This example defines a rule containing two patterns (the trees rooted at <code>foo</code> and <code>foo1</code>)
 * and a replacement (the tree rooted at <code>bar</code>). The pattern and replacement trees are arbitrary trees
 * containing nodes and properties. The rule matches a subtree if any of the defined patterns matches. In order for
 * a pattern to match, the tree in question must contain the same nodes as the pattern (matching names, except for the
 * root), and all properties defined in the pattern must match the properties on the tree. A node in a pattern
 * can be marked as optional by setting <code>cq:rewriteOptional</code> to <code>true</code>, in which case it
 * doesn't necessarily have to present for a tree to match.</p>
 *
 * <p>In the case of a match, the matched subtree (called original tree) will be substituted by the replacement. The
 * replacement tree can define mapped properties that will inherit the value of a property in the original tree. They
 * need to be of type <code>string</code> and have the following format: <code>${&lt;path&gt;}</code>. If the referenced
 * property doesn't exist in the original tree, then the property is omitted. Alternatively, a default value can be
 * specified for that case (only possible for <code>string</code> properties):
 * <code>${&lt;path&gt;:&lt;default&gt;}</code>. Properties that contain ':' characters can be single quoted to avoid
 * conflict with providing a default value. Boolean properties are negated if the expression is prefixed with
 * <code>!</code>. Mapped properties can also be multivalued, in which case they will be assigned the value of the first
 * property that exists in the original tree. The following example illustrates mapping properties:</p>
 *
 * <pre>
 * rule
 *   ...
 *   + replacement
 *     + bar
 *       - prop = ${./some/prop}
 *         // 'prop' will be assigned the value of 'some/prop' in the original tree
 *       - negated = !${./some/boolean/prop}
 *         // 'negated' will be assigned the negated value of 'some/boolean/prop' in the original tree
 *       - default = ${./some/prop:default string value}
 *         // 'default' will be assigned the value of 'some/prop' if it exists, else the string 'default string'
 *       - multi = [${./some/prop1}, ${./some/prop2}]
 *         // 'multi' will be assigned the value of 'some/prop1' if it exists, else the value of 'some/prop2'
 * </pre>
 *
 * The replacement tree supports following special properties:
 *
 * <ul>
 *     <li>
 *         <code>cq:rewriteMapChildren</code> (string)<br />
 *         Copies the children of the referenced node in the original tree to the node containing this property
 *         (e.g. <code>cq:rewriteMapChildren=./items</code> will copy the children of <code>./items</code> to the
 *         current node).
 *     </li>
 *     <li>
 *         <code>cq:rewriteFinal</code> (boolean)<br />
 *         Set this property on a node that is final and can be disregarded for the rest of the conversion as an
 *         optimization measure. When placed on the replacement node itself (i.e. on <code>rule/replacement</code>),
 *         the whole replacement tree is considered final.
 *     </li>
 * </ul>
 *
 * In addition, a special <code><cq:rewriteProperties></code> node can be added to a replacement node to define
 * string rewrites for mapped properties in the result. The node is removed from the replacement.
 * The properties of the <code><cq:rewriteProperties></code> node must
 * be named the same as those which they are rewriting and accept a string array with two parameters:
 *
 * - pattern: regexp to match against. e.g. "(?:coral-Icon--)(.+)"
 * - replacement: provided to the matcher <code>replaceAll</code> function. e.g. "$1"
 *
 * Example:
 *
 * <pre>
 * rule
 *   ...
 *   + replacement
 *     + bar
 *       - icon = ${./icon}
 *       + cq:rewriteProperties
 *         - icon = [(?:coral-Icon--)(.+), $1]
 * </pre>
 *
 */
public class NodeBasedRewriteRule implements ComponentRewriteRule {

    // pattern that matches the regex for mapped properties: ${<path>}
    private static final Pattern MAPPED_PATTERN = Pattern.compile("^(\\!{0,1})\\$\\{(\'.*?\'|.*?)(:(.+))?\\}$");

    // special properties
    private static final String PROPERTY_RANKING = "cq:rewriteRanking";
    private static final String PROPERTY_OPTIONAL = "cq:rewriteOptional";
    private static final String PROPERTY_MAP_CHILDREN = "cq:rewriteMapChildren";
    private static final String PROPERTY_IS_FINAL = "cq:rewriteFinal";

    // special nodes
    private static final String NN_CQ_REWRITE_PROPERTIES = "cq:rewriteProperties";

    private Logger logger = LoggerFactory.getLogger(NodeBasedRewriteRule.class);

    private Node ruleNode;
    private Integer ranking = null;

    public NodeBasedRewriteRule(Node ruleNode) {
        this.ruleNode = ruleNode;
    }

    public boolean matches(Node root)
            throws RepositoryException {
        if (!ruleNode.hasNode("patterns")) {
            // the 'patterns' subnode does not exist
            return false;
        }
        Node patterns = ruleNode.getNode("patterns");
        if (!patterns.hasNodes()) {
            // no patterns are defined
            return false;
        }
        // iterate over all defined patterns
        NodeIterator iterator = patterns.getNodes();
        while (iterator.hasNext()) {
            Node pattern = iterator.nextNode();
            if (matches(root, pattern)) {
                return true;
            }
        }
        // no pattern matched
        return false;
    }

    private boolean matches(Node root, Node pattern)
            throws RepositoryException {
        // check if the primary types match
        if (!ComponentRewriteUtils.hasPrimaryType(root, pattern.getPrimaryNodeType().getName())) {
            return false;
        }

        // check that all properties of the pattern match
        PropertyIterator propertyIterator = pattern.getProperties();
        while (propertyIterator.hasNext()) {
            Property property = propertyIterator.nextProperty();
            String name = property.getName();
            if (property.getDefinition().isProtected()) {
                // skip protected properties
                continue;
            }
            if (PROPERTY_OPTIONAL.equals(name)) {
                // skip cq:rewriteOptional property
                continue;
            }
            if (!root.hasProperty(name)) {
                // property present on pattern does not exist in tree
                return false;
            }
            if (!root.getProperty(name).getValue().equals(property.getValue())) {
                // property values on pattern and tree differ
                return false;
            }
        }

        // check that the tree contains all children defined in the pattern (optimization measure, before
        // checking all children recursively)
        NodeIterator nodeIterator = pattern.getNodes();
        while (nodeIterator.hasNext()) {
            Node child = nodeIterator.nextNode();
            // if the node is marked as optional, we can skip the check
            if (child.hasProperty(PROPERTY_OPTIONAL) && child.getProperty(PROPERTY_OPTIONAL).getBoolean()) {
                continue;
            }
            if (!root.hasNode(child.getName())) {
                // this child is not present in subject tree
                return false;
            }
        }

        // check child nodes recursively
        nodeIterator = pattern.getNodes();
        while (nodeIterator.hasNext()) {
            Node child = nodeIterator.nextNode();
            // if the node is marked as optional and is not present, then we skip it
            if (child.hasProperty(PROPERTY_OPTIONAL) && child.getProperty(PROPERTY_OPTIONAL).getBoolean()
                    && !root.hasNode(child.getName())) {
                continue;
            }
            return matches(root.getNode(child.getName()), child);
        }

        // base case (leaf node)
        return true;
    }

    public Node applyTo(Node root, Set<Node> finalNodes)
            throws ComponentRewriteException, RepositoryException {
        // check if the 'replacement' node exists
        if (!ruleNode.hasNode("replacement")) {
            throw new ComponentRewriteException("The rule does not define a replacement node");
        }

        // if the replacement node has no children, we replace the tree by the empty tree,
        // i.e. we remove the original tree
        Node replacement = ruleNode.getNode("replacement");
        if (!replacement.hasNodes()) {
            root.remove();
            return null;
        }

        // true if the replacement tree is final and all its nodes are excluded from
        // further processing by the algorithm
        boolean treeIsFinal = false;
        if (replacement.hasProperty(PROPERTY_IS_FINAL)) {
            treeIsFinal = replacement.getProperty(PROPERTY_IS_FINAL).getBoolean();
        }

        /**
         * Approach:
         * - we move (rename) the tree to be rewritten to a temporary name
         * - we copy the replacement tree to be a new child of the original tree's parent
         * - we process the copied replacement tree (mapped properties, children etc)
         * - at the end, we remove the original tree
         */

        // move (rename) original tree
        Node parent = root.getParent();
        String rootName = root.getName();
        ComponentRewriteUtils.rename(root);

        // copy replacement to original tree under original name
        Node replacementNext = replacement.getNodes().nextNode();
        Node copy = JcrUtil.copy(replacementNext, parent, rootName);

        // collect mappings: (node in original tree) -> (node in replacement tree)
        Map<String, String> mappings = new HashMap<String, String>();
        // traverse nodes of newly copied replacement tree
        TreeTraverser traverser = new TreeTraverser(copy);
        Iterator<Node> nodeIterator = traverser.iterator();


        while (nodeIterator.hasNext()) {
            Node node = nodeIterator.next();
            // iterate over all properties
            PropertyIterator propertyIterator = node.getProperties();
            Node rewritePropertiesNode = null;

            if (node.hasNode(NN_CQ_REWRITE_PROPERTIES)) {
                rewritePropertiesNode = node.getNode(NN_CQ_REWRITE_PROPERTIES);
            }

            while (propertyIterator.hasNext()) {
                Property property = propertyIterator.nextProperty();
                // skip protected properties
                if (property.getDefinition().isProtected()) {
                    continue;
                }

                // add mapping to collection
                if (PROPERTY_MAP_CHILDREN.equals(property.getName())) {
                    mappings.put(property.getString(), node.getPath());
                    // remove property, as we don't want it to be part of the result
                    property.remove();
                    continue;
                }
                // add single node to final nodes
                if (PROPERTY_IS_FINAL.equals(property.getName())) {
                    if (!treeIsFinal) {
                        finalNodes.add(node);
                    }
                    property.remove();
                    continue;
                }
                // set value from original tree in case this is a mapped property
                Property mappedProperty = mapProperty(root, property);

                if (mappedProperty != null && rewritePropertiesNode != null) {
                    if (rewritePropertiesNode.hasProperty("./" + mappedProperty.getName())) {
                        rewriteProperty(property, rewritePropertiesNode.getProperty("./" + mappedProperty.getName()));
                    }
                }
            }

            // remove <cq:rewriteProperties> node post-mapping
            if (rewritePropertiesNode != null) {
                rewritePropertiesNode.remove();
            }
        }

        // copy children from original tree to replacement tree according to the mappings found
        Session session = root.getSession();
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            if (!root.hasNode(mapping.getKey())) {
                // the node specified in the mapping does not exist in the original tree
                continue;
            }
            Node source = root.getNode(mapping.getKey());
            Node destination = session.getNode(mapping.getValue());
            NodeIterator iterator = source.getNodes();
            // copy over the source's children to the destination
            while (iterator.hasNext()) {
                Node child = iterator.nextNode();
                JcrUtil.copy(child, destination, child.getName());
            }
        }

        // we add the complete subtree to the final nodes
        if (treeIsFinal) {
            traverser = new TreeTraverser(copy);
            nodeIterator = traverser.iterator();
            while (nodeIterator.hasNext()) {
                finalNodes.add(nodeIterator.next());
            }
        }

        // remove original tree and return rewritten tree
        root.remove();
        return copy;
    }

    /**
     * Replaces the value of a mapped property with a value from the original tree.
     *
     * @param root the root node of the original tree
     * @param property the (potentially) mapped property in the replacement copy tree
     * @return the mapped property if there was a successful mapping, null otherwise
     */
    private Property mapProperty(Node root, Property property)
            throws RepositoryException {
        if (property.getType() != PropertyType.STRING) {
            // a mapped property must be of type string
            return null;
        }

        // array containing the expressions: ${<path>}
        Value[] values;
        if (property.isMultiple()) {
            values = property.getValues();
        } else {
            values = new Value[1];
            values[0] = property.getValue();
        }

        boolean deleteProperty = false;
        for (Value value : values) {
            Matcher matcher = MAPPED_PATTERN.matcher(value.getString());
            if (matcher.matches()) {
                // this is a mapped property, we will delete it if the mapped destination
                // property doesn't exist
                deleteProperty = true;
                String path = matcher.group(2);
                // unwrap quoted property paths
                path = StringUtils.removeStart(StringUtils.stripEnd(path,"\'"),"\'");
                if (root.hasProperty(path)) {
                    // replace property by mapped value in the original tree
                    Property originalProperty = root.getProperty(path);
                    String name = property.getName();
                    Node parent = property.getParent();
                    property.remove();
                    Property newProperty = JcrUtil.copy(originalProperty, parent, name);

                    // negate boolean properties if negation character has been set
                    String negate = matcher.group(1);
                    if ("!".equals(negate) && originalProperty.getType() == PropertyType.BOOLEAN) {
                        newProperty.setValue(!newProperty.getBoolean());
                    }

                    // the mapping was successful
                    deleteProperty = false;
                    break;
                } else {
                    String defaultValue = matcher.group(4);
                    if (defaultValue != null) {
                        if (property.isMultiple()) {
                            // the property is multiple in the replacement,
                            // recreate it so we can set the property to the default
                            String name = property.getName();
                            Node parent = property.getParent();
                            property.remove();
                            parent.setProperty(name, defaultValue);
                        } else {
                            property.setValue(defaultValue);
                        }

                        deleteProperty = false;
                        break;
                    }
                }
            }
        }
        if (deleteProperty) {
            // mapped destination does not exist, we don't include the property in replacement tree
            property.remove();
            return null;
        }

        return property;
    }

    public Set<String> getSlingResourceTypes() throws RepositoryException {
        Set<String> types = new HashSet<>();

        if (!ruleNode.hasNode("patterns")) {
            return types;
        }

        NodeIterator patterns = ruleNode.getNode("patterns").getNodes();
        while (patterns.hasNext()) {
            Node pnode = patterns.nextNode();
            if (pnode.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
                types.add(pnode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
            }
        }
        return types;
    }

    /**
     * Applies a string rewrite to a property.
     *
     * @param property the property to rewrite
     * @param rewriteProperty the property that defines the string rewrite
     */
    private void rewriteProperty(Property property, Property rewriteProperty) throws RepositoryException {
        if (property.getType() == PropertyType.STRING) {
            if (rewriteProperty.isMultiple() && rewriteProperty.getValues().length == 2) {
                Value[] rewrite = rewriteProperty.getValues();

                if (rewrite[0].getType() == PropertyType.STRING && rewrite[1].getType() == PropertyType.STRING) {
                    String pattern = rewrite[0].toString();
                    String replacement = rewrite[1].toString();

                    Pattern compiledPattern = Pattern.compile(pattern);
                    Matcher matcher = compiledPattern.matcher(property.getValue().toString());
                    property.setValue(matcher.replaceAll(replacement));
                }
            }
        }
    }

    @Override
    public String toString() {
        String path = null;
        try {
            path = ruleNode.getPath();
        } catch (RepositoryException e) {
            // ignore
        }
        return "NodeBasedRewriteRule[" + (path == null ? "" : "path=" +path + ",") + "ranking=" + getRanking() + "]";
    }

    public int getRanking() {
        if (ranking == null) {
            try {
                if (ruleNode.hasProperty(PROPERTY_RANKING)) {
                    long ranking = ruleNode.getProperty(PROPERTY_RANKING).getLong();
                    this.ranking = new Long(ranking).intValue();
                } else {
                    this.ranking = Integer.MAX_VALUE;
                }
            } catch (RepositoryException e) {
                logger.warn("Caught exception while reading the " + PROPERTY_RANKING + " property");
            }
        }
        return this.ranking;
    }
}
