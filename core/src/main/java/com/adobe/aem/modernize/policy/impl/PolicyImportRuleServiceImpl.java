/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.adobe.aem.modernize.policy.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.AbstractRewriteRuleService;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Style;
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

  /**
   * Keeps track of OSGi services implementing component rewrite rules
   */
  private final RankedServices<PolicyImportRule> rules = new RankedServices<>(Order.ASCENDING);
  private Config config;

  @Override
  public void apply(@NotNull Style style, @NotNull Design dest, @NotNull Set<String> rules, boolean deep, boolean overwrite) throws RewriteException {
    ResourceResolver rr = dest.getContentResource().getResourceResolver();
    List<RewriteRule> rewrites = create(rr, rules);
    Resource src = rr.getResource(style.getPath());
    try {
      if (deep) {
        new PolicyTreeImporter(dest, rewrites, overwrite).importStyles(style);
      } else {
        Node node = src.adaptTo(Node.class);
        if (overwrite || !node.hasProperty(PN_IMPORTED)) {
          node = applyTo(rewrites, dest, node);
          if (node != null) {
            markImported(rr.getResource(src.getPath()), node.getPath());
            ModifiableValueMap mvm = src.adaptTo(ModifiableValueMap.class);
            mvm.put(PN_IMPORTED, node.getPath());
          }
        }
      }
    } catch (RepositoryException e) {
      logger.error("Error occurred while trying to perform a rewrite operation.", e);
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  @Override
  protected String[] getSearchPaths() {
    return config.search_paths();
  }

  @Override
  protected List<PolicyImportRule> getServiceRules() {
    return rules.getList();
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

  /*
    Apply the rule, returns true if it was applied.
   */
  private Node applyTo(List<RewriteRule> rewrites, Design dest, Node node) throws RepositoryException, RewriteException {
    for (RewriteRule rule : rewrites) {
      if (rule.matches(node)) {
        if (rule instanceof PolicyImportRule) {
          ((PolicyImportRule) rule).setTargetDesign(dest);
        } else if (rule instanceof NodeBasedPolicyImportRule) {
          ((NodeBasedPolicyImportRule) rule).setTargetDesign(dest);
        } else {
          logger.warn("Unknown Rule type in Policy Import service:  {}", rule.getId());
          continue;
        }
        return rule.applyTo(node, new HashSet<>());
      }
    }
    return null;
  }

  private void markImported(Resource source, String dest) throws RepositoryException {
  }

  @SuppressWarnings("unused")
  public void bindRule(PolicyImportRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
  }

  @SuppressWarnings("unused")
  public void unbindRule(PolicyImportRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
  }

  @Activate
  @Modified
  @SuppressWarnings("unused")
  protected void activate(PolicyImportRuleServiceImpl.Config config) {
    this.config = config;
  }

  @ObjectClassDefinition(
      name = "Policy Import Rule Service",
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
