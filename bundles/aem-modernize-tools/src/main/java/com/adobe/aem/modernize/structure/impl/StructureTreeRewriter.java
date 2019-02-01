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
package com.adobe.aem.modernize.structure.impl;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.flat.TreeTraverser;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructureTreeRewriter {

    private Logger logger = LoggerFactory.getLogger(StructureTreeRewriter.class);

    private List<StructureRewriteRule> rules;

    public StructureTreeRewriter(List<StructureRewriteRule> rules) {
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
        logger.debug("Rewriting page's content tree rooted at {}", root.getPath());

        /*
         * Description of the algorithm:
         * - traverse the tree rooted at root's parent in pre-order
         * - skip any node that isn't in the original root search tree
         * - for each node we check if one of the rules match
         * - on a match, the (sub)tree rooted at that node is rewritten according to that rule,
         *   and we restart the traversal from 'root'
         * - the algorithm stops when the whole tree has been traversed and no node has matched any rule
         */

        long tick = System.currentTimeMillis();
        Session session = root.getSession();

        // reference to the node where the pre-order traversal is started from.
        Node startNode = root;
        // collect paths of nodes that are final and can be skipped by the algorithm
        Set<String> finalPaths = new LinkedHashSet<String>();

        boolean foundMatch = false;
        boolean success = false;

        try {
            // do a pre-order tree traversal until we found no match
            do {
                foundMatch = false;
                TreeTraverser traverser = new TreeTraverser(startNode);
                Iterator<Node> iterator = traverser.iterator();
                logger.debug("Starting new pre-order tree traversal");
                // traverse the tree in pre-order
                while (iterator.hasNext()) {
                    Node node = iterator.next();

                    // we have previously found a match
                    // (and will start a new traversal from the start node)
                    // but we still need to finish this traversal in order not to change the order of nodes
                    if (foundMatch) {
                        continue;
                    }

                    // check if we should skip this node
                    if (finalPaths.contains(node.getPath())) {
                        continue;
                    }
                    // traverse all available rules
                    Set<Node> finalNodes = new LinkedHashSet<>();
                    for (StructureRewriteRule rule : rules) {
                        // check for a match
                        if (rule.matches(node)) {
                            logger.debug("Rule {} matched subtree rooted at {}", rule, node.getPath());
                            // the rule matched, rewrite the tree
                            Node result = rule.applyTo(node, finalNodes);
                            // set the start node in case it was rewritten
                            if (node.equals(startNode)) {
                                startNode = result;
                            }
                            addPaths(finalPaths, finalNodes);
                            foundMatch = true;
                            break;

                        }
                    }

                    // if we have found no match for this node, we can ignore it
                    // in subsequent traversals
                    if (!foundMatch) {
                        finalNodes.add(node);
                        addPaths(finalPaths, finalNodes);
                    }
                }
            } while (foundMatch && startNode != null);
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
        logger.debug("Rewrote component tree rooted at {} in {} ms", root.getPath(), tack - tick);

        return startNode;
    }

    private void addPaths(Set<String> paths, Set<Node> nodes)
            throws RepositoryException {
        for (Node node : nodes) {
            paths.add(node.getPath());
        }
    }
}
