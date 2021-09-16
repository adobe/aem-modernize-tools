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

package com.adobe.aem.modernize.structure;

import java.util.List;
import java.util.Set;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.day.cq.wcm.api.Page;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a mechanism for listing all the configured rules either via Nodes or custom implementations.
 */
public interface StructureRewriteRuleService {

  /**
   * Applies the indicated rules to the provided resource. In the event more than one rule applies to the resource, they are applied in priority order.
   *
   * Transformations are performed but not saved.
   *
   * The rules can be either a fully qualified path to a rule or a Service PID depending on the implementation.
   *
   * Implementations decide how to handle rule paths which are invalid for their context.
   *
   * @param page  the page for applying rules
   * @param rules the rules to apply
   */
  void apply(@NotNull final Page page, @NotNull final String[] rules);

  /**
   * Lists all the cq:template properties identified by the patterns.
   *
   * @return set of templates which this service will process
   */
  Set<String> getTemplates();

  // TODO: REmove this
  List<StructureRewriteRule> getRules(ResourceResolver resolver) throws RepositoryException;
}
