package com.adobe.aem.modernize.policy.impl;

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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import org.jetbrains.annotations.NotNull;
import static com.adobe.aem.modernize.policy.PolicyImportRuleService.*;

@Deprecated(since = "2.1")
class PolicyTreeImporter {

  static final String POLICY_RESOURCE_TYPE = "wcm/core/components/policy/policy";

  static final String NN_POLICY = "policy";

  @NotNull
  static void importStyles(@NotNull Resource root, @NotNull String dest, @NotNull List<RewriteRule> rules, boolean overwrite) throws RewriteException, RepositoryException {

    ResourceResolver rr = root.getResourceResolver();
    Iterator<Node> iterator = new TreeTraverser(root.adaptTo(Node.class)).iterator();
    Set<String> finalPaths = new HashSet<>();
    while (iterator.hasNext()) {
      Node node = iterator.next();
      String origPath = node.getPath();
      if (finalPaths.contains(origPath)) {
        continue;
      }
      if (overwrite || !node.hasProperty(PN_IMPORTED)) {
        for (RewriteRule rule : rules) {
          if (rule.matches(node)) {
            importStyle(rr, node, dest, rule, finalPaths);
          }
        }
      }
    }
  }

  @NotNull
  static Node importStyle(@NotNull ResourceResolver rr, @NotNull Node node, @NotNull String dest, @NotNull RewriteRule rule, @NotNull Set<String> finalPaths) throws RepositoryException, RewriteException {
    String origPath = node.getPath();
    Session session = node.getSession();
    Node result = rule.applyTo(node, finalPaths);

    if (result == null) {
      throw new RewriteException("Policy import cannot result in a null.");
    }
    if (!result.hasProperty(NameConstants.PN_TITLE)) {
      result.setProperty(NameConstants.PN_TITLE, String.format("Imported (%s)", origPath));
    }
    if (!result.hasProperty(NameConstants.PN_DESCRIPTION)) {
      result.setProperty(NameConstants.PN_DESCRIPTION, String.format("Imported from: %s", origPath));
    }
    result = moveTo(rr, result, dest);
    // Rule may delete original.
    if (session.itemExists(origPath)) {
      Node orig = session.getNode(origPath);
      orig.setProperty(PN_IMPORTED, result.getPath());
    }
    return result;
  }

  /*
   * Moves the updated Design Style to the Conf destination.
   */
  private static Node moveTo(@NotNull ResourceResolver rr, @NotNull Node source, @NotNull String dest) throws RepositoryException {

    String resourceType = source.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();
    if (resourceType.startsWith("/")) {
      for (String s : rr.getSearchPath()) {
        if (resourceType.startsWith(s)) {
          resourceType = resourceType.replaceFirst(s, "");
          break;
        }
      }
    }
    String path = PathUtils.concat(dest, POLICY_REL_PATH, resourceType);
    Node parent = JcrUtils.getOrCreateByPath(path, JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED, source.getSession(), false);
    String name = JcrUtil.createValidChildName(parent, NN_POLICY);
    Node policy = JcrUtil.copy(source, parent, name, false);
    policy.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, POLICY_RESOURCE_TYPE);
    source.remove();
    return policy;
  }
}
