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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.servlet.Servlet;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.adobe.aem.modernize.servlet.RuleInfo;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=POST",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listrules.template"
    }
)
public class ListTemplateRulesServlet extends AbstractListRulesServlet {

  private static final Logger logger = LoggerFactory.getLogger(ListTemplateRulesServlet.class);

  private static final String PARAM_REPROCESS = "reprocess";

  @Reference
  private StructureRewriteRuleService structureRuleService;

  @Override
  protected @NotNull List<RuleInfo> getRules(@NotNull SlingHttpServletRequest request, @NotNull Set<String> resourceTypes) {

    if (BooleanUtils.toBoolean(request.getParameter(PARAM_REPROCESS))) {
      String path = request.getParameter(PARAM_PATH);
      addOriginalResourceType(request.getResourceResolver(), path, resourceTypes);
    }

    return super.getRules(request, resourceTypes);
  }

  private void addOriginalResourceType(ResourceResolver rr, String path, Set<String> resourceTypes) {
    PageManager pm = rr.adaptTo(PageManager.class);
    Page page = pm.getContainingPage(path);
    if (page == null) {
      return;
    }
    String version = page.getProperties().get(ConversionJob.PN_PRE_MODERNIZE_VERSION, String.class);
    if (version == null) {
      return;
    }

    try {
      Collection<Revision> revisions = pm.getRevisions(page.getPath(), null);
      for (Revision r : revisions) {
        if (r.getId().equals(version)) {
          String type = r.getProperties().get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class);
          if (type != null) {
            resourceTypes.add(type);
          }
          break;
        }
      }
    } catch (WCMException e) {
      logger.error("Unable to list revisions for page.", e);
    }
  }

  @Override
  protected @NotNull RewriteRuleService getRewriteRuleService() {
    return structureRuleService;
  }
}
