package com.adobe.aem.modernize.impl;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.flat.TreeTraverser;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs deep rewrites based on specified rules.
 */
public class TreeRewriter {

  private static final Logger logger = LoggerFactory.getLogger(TreeRewriter.class);

  private final List<RewriteRule> rules;

  public TreeRewriter(@NotNull List<RewriteRule> rules) {
    this.rules = rules;
  }

  /**
   * Rewrites the specified tree according to the provided set of rules. Rules are applied according to {@link TreeTraverser} order.
   *
   * Changes are not saved.
   *
   * An exception is thrown if any error occurs terminating the rewrite at that location in the traversal. Changes are not reverted.
   *
   * @param root The root of the tree to be rewritten
   * @return the root node of the rewritten tree, or null if it was removed
   * @throws RewriteException if the rewrite operation fails
   * @throws RepositoryException if there is a problem with the repository
   */
  public Node rewrite(Node root) throws RewriteException, RepositoryException {
    String rootPath = root.getPath();
    logger.debug("Rewriting content tree rooted at: {}", rootPath);
    long tick = System.currentTimeMillis();
    Node startNode = root;
    boolean matched;

    Set<String> finalPaths = new LinkedHashSet<>();

    do {
      matched = false;
      TreeTraverser traverser = new TreeTraverser(startNode);
      Iterator<Node> iterator = traverser.iterator();
      logger.debug("Starting new pre-order tree traversal at root: {}", startNode.getPath());
      while (iterator.hasNext()) {
        Node node = iterator.next();

        // If parent is ordered, move to the end to preserve order. Rules may delete/create new nodes on parent.
        if (node.getParent().getPrimaryNodeType().hasOrderableChildNodes()) {
          node.getParent().orderBefore(node.getName(), null);
        }

        // If we already matched, skip back to the root for next traversal
        if (matched) {
          continue;
        }

        // If any rule indicated that the path is final
        if (finalPaths.contains(node.getPath())) {
          continue;
        }

        // Apply the rules
        Set<Node> finalNodes = new LinkedHashSet<>();
        for (RewriteRule rule : rules) {
          if (rule.matches(node)) {
            logger.debug("Rule [{}] matched subtree at [{}]", rule.getId(), node.getPath());
            Node result = rule.applyTo(node, finalPaths);
            // set the start node in case it was rewritten
            if (node.equals(startNode)) {
              startNode = result;
            }
            addPaths(finalPaths, finalNodes);
            matched = true;
            // Only one rule is allowed to match.
            break;
          }
        }

      }
    } while (matched && startNode != null);

    long tock = System.currentTimeMillis();
    logger.debug("Rewrote content tree rooted at [{}] in {}ms", root.getPath(), tock - tick);

    return startNode;
  }

  private void addPaths(Set<String> paths, Set<Node> nodes)
      throws RepositoryException {
    for (Node node : nodes) {
      paths.add(node.getPath());
    }
  }
}
