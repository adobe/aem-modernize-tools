package com.adobe.aem.modernize;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.adobe.aem.modernize.rule.RewriteRule;

public class MockRule implements RewriteRule {

  private final String id;

  public MockRule(String id) {
    this.id = id;
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
  public Node applyTo(Node root, Set<String> finalPaths) throws RewriteException, RepositoryException {
    return null;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return id.equals(((RewriteRule) obj).getId());
  }
}
