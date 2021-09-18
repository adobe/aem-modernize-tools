package com.adobe.aem.modernize.impl;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.flat.TreeTraverser;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;

/**
 * Performs deep rewrites based on specified rules.
 *
 */
public class TreeRewriter {
  private final List<RewriteRule> rules;

  public TreeRewriter(@NotNull List<RewriteRule> rules) {
    this.rules = rules;
  }

  /**
   * Rewrites the specified tree according to the provided set of rules.
   *
   * Rules are applied according to {@link TreeTraverser} order.
   *
   * @param root The root of the tree to be rewritten
   * @return the root node of the rewritten tree, or null if it was removed
   * @throws RewriteException If the rewrite operation fails
   * @throws RepositoryException If there is a problem with the repository
   */
  public Node rewrite(Node root) throws RewriteException, RepositoryException {

    return root;
  }

}
