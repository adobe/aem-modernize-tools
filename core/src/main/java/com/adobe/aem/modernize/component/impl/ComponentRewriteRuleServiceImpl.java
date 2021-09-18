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

package com.adobe.aem.modernize.component.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.impl.TreeRewriter;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.impl.NodeBasedRewriteRule;
import org.jetbrains.annotations.NotNull;
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
    service = { ComponentRewriteRuleService.class },
    reference = {
        @Reference(
            name = "rule",
            service = ComponentRewriteRule.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            bind = "bindRule",
            unbind = "unbindRule"
        )
    }
)
@Designate(ocd = ComponentRewriteRuleServiceImpl.Config.class)
public class ComponentRewriteRuleServiceImpl implements ComponentRewriteRuleService {

  private static final Logger logger = LoggerFactory.getLogger(ComponentRewriteRuleServiceImpl.class);
  /**
   * Keeps track of OSGi services implementing component rewrite rules
   */
  private final RankedServices<ComponentRewriteRule> rules = new RankedServices<>(Order.ASCENDING);
  private Config config;

  @SuppressWarnings("unused")
  public void bindRule(ComponentRewriteRule rule, Map<String, Object> properties) {
    rules.bind(rule, properties);
  }

  @SuppressWarnings("unused")
  public void unbindRule(ComponentRewriteRule rule, Map<String, Object> properties) {
    rules.unbind(rule, properties);
  }

  @Override
  public void apply(@NotNull Resource resource, @NotNull String[] rules, boolean deep) throws RewriteException {
    ResourceResolver rr = resource.getResourceResolver();

    List<RewriteRule> rewrites = create(rr, rules);
    Node node = resource.adaptTo(Node.class);
    try {
      if (deep) {
        new TreeRewriter(rewrites).rewrite(node);
      } else {
        for (RewriteRule rule : rewrites) {
          if (rule.matches(node)) {
            rule.applyTo(node, new HashSet<>());
            break;
          }
        }
      }
    } catch (RepositoryException e) {
      logger.error("Error occurred while trying to perform a rewrite operation.", e);
      throw new RewriteException("Repository exception while performing rewrite operation.", e);
    }
  }

  @Activate
  @Modified
  protected void activate(Config config) {
    this.config = config;
  }

  private List<RewriteRule> create(ResourceResolver rr, String[] rules) {
    List<String> searchPaths = Arrays.asList(config.search_paths());

    return Arrays.stream(rules).map(id -> {
          RewriteRule rule = null;
          if (PathUtils.isAbsolute(id)) {
            Resource r = rr.getResource(id);
            if (r != null && searchPaths.contains(PathUtils.getParentPath(r.getPath()))) {
              try {
                rule = new NodeBasedRewriteRule(r.adaptTo(Node.class));
              } catch (RepositoryException e) {
                logger.error(String.format("Unable to create RewriteRule for path: {}", id), e);
              }
            }
          } else { // Assume non-absolute path is a PID
            rule = StreamSupport.stream(this.rules.spliterator(), false).filter(r -> StringUtils.equals(id, r.getId())).findFirst().orElse(null);
          }
          return rule;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

  }

  @ObjectClassDefinition(
      name = "Component Rewrite Rule Service",
      description = "Manages operations for performing component-level rewrites for Modernization tasks."
  )
  @interface Config {
    @AttributeDefinition(
        name = "Component Rule Paths",
        description = "List of paths to find node-based Component Rewrite Rules",
        cardinality = Integer.MAX_VALUE
    )
    String[] search_paths();
  }

}
