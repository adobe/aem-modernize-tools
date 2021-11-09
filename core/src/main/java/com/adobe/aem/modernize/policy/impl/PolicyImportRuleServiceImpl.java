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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.policy.PolicyImportRule;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.policy.rule.impl.NodeBasedPolicyImportRule;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.AbstractRewriteRuleService;
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

  /**
   * Keeps track of OSGi services implementing component rewrite rules
   */
  private final RankedServices<PolicyImportRule> rules = new RankedServices<>(Order.ASCENDING);
  private Config config;

  @Override
  public void apply(@NotNull Resource src, @NotNull String dest, @NotNull Set<String> rulePaths, boolean deep, boolean overwrite) throws RewriteException {
    ResourceResolver rr = src.getResourceResolver();
    List<RewriteRule> rules = create(rr, rulePaths);

    try {
      if (deep) {
        importStyles(src, dest, rules, overwrite);
      } else {
        Node node = src.adaptTo(Node.class);
        if (overwrite || !node.hasProperty(PN_IMPORTED)) {
          for (RewriteRule rule : rules) {
            if (rule.matches(node)) {
              importStyle(rr, node, dest, rule, new HashSet<>());
            }
          }
        }
      }
    } catch (RepositoryException e) {
      logger.error("Error occurred while trying to perform a rewrite operation.", e);
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  @NotNull
  @Override
  protected List<String> getSearchPaths() {
    return Arrays.asList(config.search_paths());
  }

  @Override
  protected @NotNull List<PolicyImportRule> getServiceRules() {
    return Collections.unmodifiableList(rules.getList());
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
      name = "AEM Modernization Tools - Policy Import Rule Service",
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
