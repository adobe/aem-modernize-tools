package com.adobe.aem.modernize.servlet;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.model.ConversionJob;
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
        SLING_SERVLET_METHODS + "=GET",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listcomponents"
    }
)
@Deprecated(since = "2.1.0")
public class ListComponentsServlet extends AbstractListConversionPathsServlet {

  protected static final String PARAM_REPROCESS = "reprocess";
  private static final Logger logger = LoggerFactory.getLogger(ListComponentsServlet.class);
  @Reference
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Override
  protected @NotNull List<String> listPaths(@NotNull Map<String, String[]> params, @NotNull Page page) {

    boolean reprocess = BooleanUtils.toBoolean(params.getOrDefault(PARAM_REPROCESS, new String[] { "false" })[0]);
    String versionId = page.getProperties().get(ConversionJob.PN_PRE_MODERNIZE_VERSION, String.class);

    Resource resource = null;
    try {
      if (reprocess && StringUtils.isNotBlank(versionId)) {
        PageManager pm = page.getPageManager();
        ResourceResolver rr = page.getContentResource().getResourceResolver();
        Collection<Revision> revisions = pm.getRevisions(page.getPath(), null);
        for (Revision r : revisions) {
          if (r.getId().equals(versionId)) {
            resource = rr.getResource(r.getVersion().getFrozenNode().getPath());
            break;
          }
        }
      }
    } catch (RepositoryException | WCMException e) {
      logger.error("Encountered an error while trying to retrieve components on page version. Defaulting to current page.", e);
    } finally {
      if (resource == null) {
        resource  = page.getContentResource();
      }
    }

    List<String> paths = new ArrayList<>();
    paths.addAll(componentRewriteRuleService.find(resource));
    return paths;
  }

}
