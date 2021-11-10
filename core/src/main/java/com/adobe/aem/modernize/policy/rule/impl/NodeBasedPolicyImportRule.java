package com.adobe.aem.modernize.policy.rule.impl;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

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
