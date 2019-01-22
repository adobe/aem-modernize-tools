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
package com.adobe.aem.modernize.dialog;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Interface for services that implement a dialog rewrite rule. A rewrite rule matches certain subtrees of the
 * dialog tree (usually corresponding to one dialog component) and rewrites (i.e. modifies or replaces) them.
 */
public interface DialogRewriteRule {

    /**
     * Returns true if this rule matches the given subtree.
     *
     * @param root The root of the subtree to be checked for a match
     * @return true if this rule applies, false otherwise
     */
    boolean matches(Node root)
            throws RepositoryException;

    /**
     * <p>Applies this rule to the subtree rooted at the specified <code>root</code> node. The implementation of this
     * method may either modify the properties and nodes contained in that tree, or replace it by adding a new child
     * to the parent of <code>root</code>. In the latter case, the implementation is responsible for removing the
     * original subtree (without saving).</p>
     *
     * <p>Rewrite rules must not rewrite trees in a circular fashion, as this might lead to infinite loops.</p>
     *
     * <p>Optionally, the implementation can indicate which nodes of the resulting tree are final and therefore
     * safe for the algorithm to skip in subsequent traversals of the tree. Add the paths of final nodes to the
     * specified set.
     *
     * <p>{@link DialogRewriteUtils} provides utility methods that can
     * be used to temporarily rename (move) the original subtree, so that the resulting subtree can be built
     * while still having the original around.</p>
     *
     * @param root The root of the subtree to be rewritten
     * @return the root node of the rewritten tree, or null if it was removed
     * @throws DialogRewriteException if the rewrite operation failed or cannot be completed
     */
    Node applyTo(Node root, Set<Node> finalNodes)
            throws DialogRewriteException, RepositoryException;

    /**
     * Returns the ranking of this rule. The larger the returned value, the lower the priority of the rule (the lowest
     * priority corresponds to <code>Integer.MAX_VALUE</code>. The order of rules with equal rankings is arbitrary.
     *
     * @return The ranking
     */
    int getRanking();

}
