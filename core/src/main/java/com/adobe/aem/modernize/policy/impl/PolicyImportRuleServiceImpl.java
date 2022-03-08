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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.policy.rule.impl.NodeBasedPolicyImportRule;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.AbstractRewriteRuleService;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.adobe.aem.modernize.policy.impl.PolicyTreeImporter.*;

@Component(
    service = { PolicyImportRuleService.class },
    reference = {
        @Reference(
            name = "rule",
            service = PolicyImportRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindRule",
            unbind = "unbindRule"
        )
    }
)
@Designate(ocd = PolicyImportRuleServiceImpl.Config.class)
public class PolicyImportRuleServiceImpl extends AbstractRewriteRuleService<PolicyImportRule> implements PolicyImportRuleService {

  private static final Logger logger = LoggerFactory.getLogger(PolicyImportRuleService.class);

  private Config config;

  @NotNull
  @Override
  protected List<String> getSearchPaths() {
    return Arrays.asList(config.search_paths());
  }

  @Override
  @Nullable
  protected RewriteRule getNodeRule(@NotNull Node node) {
    try {
      return new NodeBasedPolicyImportRule(node);
    } catch (RepositoryException e) {
      logger.error("Unable to create PolicyImportRule", e);
    }
    return null;
  }

  @Override
  @Deprecated(since = "2.1.0")
  public void apply(@NotNull Resource src, @NotNull String dest, @NotNull Set<String> ruleIds, boolean deep, boolean overwrite) throws RewriteException {
    ResourceResolver rr = src.getResourceResolver();

    try {
      if (deep) {
        List<RewriteRule> rules = create(rr, ruleIds);
        importStyles(src, dest, rules, overwrite);
      } else {
        apply(src, dest, ruleIds, overwrite);
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  @Override
  public boolean apply(@NotNull Resource src, @NotNull String dest, @NotNull Set<String> rulePaths, boolean overwrite) throws RewriteException {
    ResourceResolver rr = src.getResourceResolver();
    List<RewriteRule> rules = create(rr, rulePaths);
    boolean applied = false;

    try {
      Node node = src.adaptTo(Node.class);
      String prevDest = null;
      if (node.hasProperty(PN_IMPORTED)) {
        prevDest = node.getProperty(PN_IMPORTED).getString();
      }
      if (overwrite || StringUtils.isBlank(prevDest)) {
        for (RewriteRule rule : rules) {
          if (rule.matches(node)) {
            Node result = rule.applyTo(node, new HashSet<>());
            if (result != null) {
              populateMetadata(result);
              Node policy = createPolicy(rr, result, dest, prevDest);
              node.setProperty(PN_IMPORTED, policy.getPath());
            }
            applied = true;
            break;
          }
        }
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
    return applied;
  }

  private void populateMetadata(Node result) throws RepositoryException {
    String origPath = result.getPath();
    if (!result.hasProperty(NameConstants.PN_TITLE)) {
      result.setProperty(NameConstants.PN_TITLE, String.format("Imported (%s)", origPath));
    }
    if (!result.hasProperty(NameConstants.PN_DESCRIPTION)) {
      result.setProperty(NameConstants.PN_DESCRIPTION, String.format("Imported from: %s", origPath));
    }
  }

  private Node createPolicy(ResourceResolver rr, Node source, String conf, String dest) throws RepositoryException {

    Node parent;
    String name;
    Session session = source.getSession();
    if (StringUtils.isBlank(dest)) {
      String resourceType = source.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();
      if (resourceType.startsWith("/")) {
        for (String s : rr.getSearchPath()) {
          if (resourceType.startsWith(s)) {
            resourceType = resourceType.replaceFirst(s, "");
            break;
          }
        }
      }
      dest = PathUtils.concat(conf, POLICY_REL_PATH, resourceType);
      parent = JcrUtils.getOrCreateByPath(dest, JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED, session, false);
      name = JcrUtil.createValidChildName(parent, NN_POLICY);

    } else {
      name = PathUtils.getName(dest);
      dest = PathUtils.getParentPath(dest);
      parent = JcrUtils.getOrCreateByPath(dest, JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED, session, false);
      if (parent.hasNode(name)) {
        parent.getNode(name).remove();
      }
    }

    Node policy = JcrUtil.copy(source, parent, name, false);
    policy.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, POLICY_RESOURCE_TYPE);
    source.remove();
    return policy;
  }

  @SuppressWarnings("unused")
  public void bindRule(PolicyImportRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
    ruleMap.put(rule.getId(), rule);
  }

  @SuppressWarnings("unused")
  public void unbindRule(PolicyImportRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
    ruleMap.remove(rule.getId());
  }

  @Activate
  @Modified
  @SuppressWarnings("unused")
  protected void activate(PolicyImportRuleServiceImpl.Config config) {
    this.config = config;
  }

  @ObjectClassDefinition(
      name = "AEM Modernize Tools - Policy Import Rule Service",
      description = "Manages operations for performing policy-level import for Modernization tasks."
  )
  @interface Config {
    @AttributeDefinition(
        name = "Policy Rule Paths",
        description = "List of paths to find node-based Policy Import Rules",
        cardinality = Integer.MAX_VALUE
    )
    String[] search_paths();
  }
}
