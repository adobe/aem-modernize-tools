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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.Servlet;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=GET",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listdesigns"
    }
)
public class ListDesignsServlet extends AbstractListConversionPathsServlet {

  protected static final String PARAM_INCLUDE_SUPER_TYPES = "includeSuperTypes";

  @Reference
  private PolicyImportRuleService importRuleService;

  @Override
  protected @NotNull List<String> listPaths(@NotNull Map<String, String[]> params, @NotNull Page page) {

    String[] includeSuper = params.getOrDefault(PARAM_INCLUDE_SUPER_TYPES, new String[] { "false" });
    boolean include = BooleanUtils.toBoolean(includeSuper[0]);

    List<String> paths = new ArrayList<>();
    ResourceResolver rr = page.getContentResource().getResourceResolver();
    Designer designer = rr.adaptTo(Designer.class);
    Style style = designer.getStyle(page.getContentResource());
    if (style == null) {
      return paths;
    }

    Design design = designer.getDesign(page);
    Cell cell = style.getCell();
    Resource resource;
    Iterator<String> cellRoots = cell.paths();
    do {
      String id = cellRoots.next();
      style = design.getStyle(id);
      resource = rr.getResource(style.getPath());
      if (resource != null) {
        paths.addAll(importRuleService.find(resource));
      }
    } while (include && cellRoots.hasNext());

    return paths;
  }
}
