package com.adobe.aem.modernize.policy.impl;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.wcm.api.designer.Design;

public class NodeBasedPolicyImportRule implements RewriteRule {

  public NodeBasedPolicyImportRule(Node node) throws RepositoryException {

  }

  /**
   * This method is used by services consuming this rule to set the root Policy location before the RewriteRules are applied.
   *
   * This method may be called at any time before the {@link #applyTo} method is called.
   *
   * @param destination the Design in which to save the new Policies
   */
  public void setTargetDesign(Design destination) {

  }

  @Override
  public String getId() {
    return null;
  }

  @Override
  public boolean matches(Node root) throws RepositoryException {
    return false;
  }

  @Override
  public Node applyTo(Node root, Set<String> finalPaths) throws RewriteException, RepositoryException {
    return null;
  }
}
