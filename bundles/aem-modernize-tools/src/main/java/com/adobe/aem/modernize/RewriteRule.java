package com.adobe.aem.modernize;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;


public interface RewriteRule {
    /**
     * Returns true if this rule matches the given subtree.
     *
     * @param root The root of the subtree to be checked for a match
     * @return true if this rule applies, false otherwise
     */
    boolean matches(Node root) throws RepositoryException;

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
     **
     * @param root The root of the subtree to be rewritten
     * @return the root node of the rewritten tree, or null if it was removed
     * @throws RewriteException if the rewrite operation failed or cannot be completed
     */
    Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException;

    /**
     * Returns the ranking of this rule. The larger the returned value, the lower the priority of the rule (the lowest
     * priority corresponds to <code>Integer.MAX_VALUE</code>. The order of rules with equal rankings is arbitrary.
     *
     * @return The ranking
     */
    default int getRanking() { return Integer.MAX_VALUE; }
}
