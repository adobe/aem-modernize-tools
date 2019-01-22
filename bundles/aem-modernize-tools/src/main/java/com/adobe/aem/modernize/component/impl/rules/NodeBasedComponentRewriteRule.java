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

import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.impl.impl.AbstractNodeBasedRewriteRule;

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
 * <p>
 * The replacement tree supports following special properties:
 *
 * <ul>
 * <li>
 * <code>cq:rewriteMapChildren</code> (string)<br />
 * Copies the children of the referenced node in the original tree to the node containing this property
 * (e.g. <code>cq:rewriteMapChildren=./items</code> will copy the children of <code>./items</code> to the
 * current node).
 * </li>
 * <li>
 * <code>cq:rewriteFinal</code> (boolean)<br />
 * Set this property on a node that is final and can be disregarded for the rest of the conversion as an
 * optimization measure. When placed on the replacement node itself (i.e. on <code>rule/replacement</code>),
 * the whole replacement tree is considered final.
 * </li>
 * </ul>
 * <p>
 * In addition, a special <code><cq:rewriteProperties></code> node can be added to a replacement node to define
 * string rewrites for mapped properties in the result. The node is removed from the replacement.
 * The properties of the <code><cq:rewriteProperties></code> node must
 * be named the same as those which they are rewriting and accept a string array with two parameters:
 * <p>
 * - pattern: regexp to match against. e.g. "(?:coral-Icon--)(.+)"
 * - replacement: provided to the matcher <code>replaceAll</code> function. e.g. "$1"
 * <p>
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
 */
public class NodeBasedComponentRewriteRule extends AbstractNodeBasedRewriteRule implements ComponentRewriteRule {

    public NodeBasedComponentRewriteRule(Node ruleNode) {
        super(ruleNode);
    }

    @Override
    protected void doAdditionalApplyTo(Node root, Node copy, Node replacementNode) {
        // No custom logic here.
        return;
    }

    public Set<String> getSlingResourceTypes() throws RepositoryException {
        Set<String> types = new HashSet<>();

        if (!getRuleNode().hasNode("patterns")) {
            return types;
        }

        NodeIterator patterns = getRuleNode().getNode("patterns").getNodes();
        while (patterns.hasNext()) {
            Node pnode = patterns.nextNode();
            if (pnode.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
                types.add(pnode.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString());
            }
        }
        return types;
    }

}
