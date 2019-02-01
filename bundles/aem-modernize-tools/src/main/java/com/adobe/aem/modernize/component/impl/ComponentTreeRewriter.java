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

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import org.apache.jackrabbit.commons.flat.TreeTraverser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ComponentTreeRewriter {

    private Logger logger = LoggerFactory.getLogger(ComponentTreeRewriter.class);

    private List<ComponentRewriteRule> rules;

    public ComponentTreeRewriter(List<ComponentRewriteRule> rules) {
        this.rules = rules;
    }

    /**
     * Rewrites the specified component tree according to the set of rules passed to the constructor.
     *
     * @param root The root of the component be rewritten
     * @return the root node of the rewritten component tree, or null if it was removed
     * @throws RewriteException If the rewrite operation fails
     * @throws RepositoryException If there is a problem with the repository
     */
    public Node rewrite(Node root) throws RewriteException, RepositoryException {
        String rootPath = root.getPath();
        logger.debug("Rewriting component's content tree rooted at {}", rootPath);

        long tick = System.currentTimeMillis();
        Session session = root.getSession();
        boolean success = false;
        Node startNode = root;
        try {
            Node parent = root.getParent();

            boolean ordered = parent.getPrimaryNodeType().hasOrderableChildNodes();
            NodeIterator siblings = parent.getNodes();
            while (siblings.hasNext()) {
                Node node = siblings.nextNode();

                // If this is the node to update.
                if (startNode.getPath().equals(node.getPath())) {

                    // Apply the matching rule.
                    for (ComponentRewriteRule rule : rules) {
                        // check for a match
                        if (rule.matches(node)) {
                            logger.debug("Rule {} matched subtree rooted at {}", rule, node.getPath());
                            // the rule matched, rewrite the tree
                            node = rule.applyTo(node, new HashSet<>());
                            startNode = node;
                            break;
                        }
                    }
                }

                // Keep the parent in order as we are updating one of it's children.
                if (ordered) {
                    parent.orderBefore(node.getName(), null);
                }
            }
            success = true;
        } finally {
            if (!success) {
                // an exception has been thrown: try to revert changes
                try {
                    session.refresh(false);
                } catch (RepositoryException e) {
                    logger.warn("Could not revert changes", e);
                }
            }
        }

        // save changes
        session.save();

        long tack = System.currentTimeMillis();
        logger.debug("Rewrote component tree rooted at {} in {} ms", rootPath, tack - tick);

        return startNode;
    }

    private void addPaths(Set<String> paths, Set<Node> nodes)
            throws RepositoryException {
        for (Node node : nodes) {
            paths.add(node.getPath());
        }
    }
}
