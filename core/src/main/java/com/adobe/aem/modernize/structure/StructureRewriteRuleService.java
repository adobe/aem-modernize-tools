package com.adobe.aem.modernize.structure;

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

import org.apache.sling.api.resource.Resource;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.day.cq.wcm.api.Page;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides a mechanism for listing all the configured rules either via Nodes or custom implementations.
 */
@ProviderType
public interface StructureRewriteRuleService extends RewriteRuleService {

  /**
   * Applies the indicated rules to the provided page.
   * <p>
   * Transformations are performed but not saved.
   * <p>
   * Implementations decide how to handle rule paths which are invalid for their context.
   *
   * @param page  page to process
   * @param rules the ids of the rules to apply
   * @throws RewriteException if any errors occur while updating the page.
   */
  @Deprecated(since = "2.1")
  void apply(@NotNull final Page page, @NotNull final Set<String> rules) throws RewriteException;

  /**
   * Applies the indicated rules to the provided resource.
   * <p>
   * Transformations are performed but not saved.
   *
   * @param resource resource of the page to process
   * @param rules the ids of the rules to apply
   * @return {@code true} if one of the specified rules was successfully applied, false otherwise
   * @throws RewriteException if any errors occur while updating the page.
   */
  boolean apply(@NotNull final Resource resource, @NotNull final Set<String> rules) throws RewriteException;
}
