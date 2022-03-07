package com.adobe.aem.modernize.servlet.rule;

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

import javax.servlet.Servlet;

import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=POST",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listrules.policy"
    }
)
@Deprecated(since = "2.1.0")
public class ListPolicyRulesServlet extends AbstractListRulesServlet {

  @Reference
  private PolicyImportRuleService importRuleService;

  @Override
  protected @NotNull RewriteRuleService getRewriteRuleService() {
    return importRuleService;
  }
}
