package com.adobe.aem.modernize.rule.impl;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;

public class NodeBasedRewriteRule implements RewriteRule {

  private final Node node;
  private final String id;

  /**
   * Creates a new RewriteRule referencing the specified path.
   *
   * @param node
   */
  public NodeBasedRewriteRule(@NotNull Node node) throws RepositoryException {
    this.node = node;
    this.id = node.getPath();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean matches(Node root) throws RepositoryException {
    return false;
  }

  @Override
  public Node applyTo(Node root, Set<Node> finalNodes) throws RepositoryException {
    return null;
  }
}
