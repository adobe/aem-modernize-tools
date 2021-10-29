package com.adobe.aem.modernize.policy.rule.impl;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.JcrUtils;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
  Policy import rule based on JCR structure. Differentiated from the base NodeBasedRewriteRule as this copies the original
  node to a new location before processing. Policy Service will move the resulting node, but the original design must be preserved.
 */
public class NodeBasedPolicyImportRule extends NodeBasedRewriteRule {

  public NodeBasedPolicyImportRule(Node node) throws RepositoryException {
    super(node);
  }

  @Nullable
  @Override
  public Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RewriteException, RepositoryException {
    Node parent = root.getParent();
    String name = JcrUtil.createValidChildName(parent, root.getName());
    Node newRoot = JcrUtil.copy(root, parent, name);
    return super.applyTo(newRoot, finalPaths);
  }
}
