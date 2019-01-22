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
package com.adobe.aem.modernize.component.impl;

import com.adobe.aem.modernize.component.ComponentRewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.jackrabbit.commons.flat.TreeTraverser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
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
     * @throws ComponentRewriteException If the rewrite operation fails
     * @throws RepositoryException If there is a problem with the repository
     */
    public Node rewrite(Node root) throws ComponentRewriteException, RepositoryException {
        String rootPath = root.getPath();
        logger.debug("Rewriting component's content tree rooted at {}", rootPath);

        /*
         * Description of the algorithm:
         * - traverse the tree rooted at root's parent in pre-order
         * - skip any node that isn't in the original root search tree
         * - for each node we check if one of the rules match
         * - on a match, the (sub)tree rooted at that node is rewritten according to that rule,
         *   and we restart the traversal from 'root'
         * - the algorithm stops when the whole tree has been traversed and no node has matched any rule
         * - some special care has to be taken to keep the orderings of child nodes when rewriting subtrees
         */

        long tick = System.currentTimeMillis();
        Session session = root.getSession();
        // reference to the node where the pre-order traversal is started from.
        Node parent = root.getParent();
        Node startNode = root;
        String startNodePath = startNode.getPath();
        // keeps track of whether or not there was a match during a traversal
        boolean foundMatch;
        // keeps track of whether or not the rewrite operation succeeded
        boolean success = false;
        // collect paths of nodes that are final and can be skipped by the algorithm
        Set<String> finalPaths = new LinkedHashSet<>();

        try {
            // do a pre-order tree traversal until we found no match
            do {
                foundMatch = false;
                TreeTraverser traverser = new TreeTraverser(parent);
                Iterator<Node> iterator = traverser.iterator();
                logger.debug("Starting new pre-order tree traversal");
                // traverse the tree in pre-order
                while (iterator.hasNext()) {
                    Node node = iterator.next();

                    // if this node and its siblings are ordered..
                    if (node.getParent().getPrimaryNodeType().hasOrderableChildNodes()) {
                        // ..then we move it to the end of its parent's list of children. This is necessary because
                        // any of its siblings might be rewritten (which might move it to the end of the list). Thus
                        // we do this for all siblings in order to keep the order.
                        node.getParent().orderBefore(node.getName(), null);
                    }

                    // we have previously found a match (and will start a new traversal from the start node)
                    // but we still need to finish this traversal in order not to change the order of nodes
                    if (foundMatch || !node.getPath().contains(rootPath)) {
                        continue;
                    }

                    // check if we should skip this node
                    if (finalPaths.contains(node.getPath())) {
                        continue;
                    }

                    // traverse all available rules
                    Set<Node> finalNodes = new LinkedHashSet<>();
                    for (ComponentRewriteRule rule : rules) {
                        // check for a match
                        if (rule.matches(node)) {
                            String nodePath = node.getPath();
                            logger.debug("Rule {} matched subtree rooted at {}", rule, nodePath);
                            // the rule matched, rewrite the tree
                            Node result = rule.applyTo(node, finalNodes);
                            // set the start node in case it was rewritten
                            // Issues abound when using the Nodes directly.... they vanish!
                            if (nodePath.equals(startNodePath)) {
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
