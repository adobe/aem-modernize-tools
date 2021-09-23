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
package com.adobe.aem.modernize.component.datasource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

/**
 * Returns a list of components found on the given path
 */
@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/components/convert/component/datasource",
        SLING_SERVLET_METHODS + "=GET"
    }
)
public final class ComponentDataSource extends SlingSafeMethodsServlet {

  public static final String ITEM_RESOURCE_TYPE = "aem-modernize/components/convert/component/item";

  private final static Logger log = LoggerFactory.getLogger(ComponentDataSource.class);

  @Reference
  private ExpressionResolver expressionResolver;

  @Reference
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    Resource resource = request.getResource();
    ValueMap vm = resource.getValueMap();
    String path = vm.get("path", String.class);
    path = expressionResolver.resolve(path, request.getLocale(), String.class, request);

    if (StringUtils.isEmpty(path)) {
      log.warn("Path for component data source search was not specified.");
      return;
    }

    ResourceResolver resourceResolver = request.getResourceResolver();
    Resource searchRoot = resourceResolver.getResource(path);
    if (searchRoot == null) {
      log.warn("Search path [{}] for component datasource was not found.", path);
      return;
    }
    Set<String> paths = componentRewriteRuleService.find(searchRoot);
    if (paths.isEmpty()) {
      log.info("No components matched rules under provided path: [{}]", path);
    }

    AtomicInteger i = new AtomicInteger();
    List<Resource> resources = paths.stream().sorted().map(p -> {

      Map<String, Object> map = new HashMap<>();
      map.put("path", p);
      map.put("ruleHref", "To Do");
      i.getAndIncrement();
      return new ValueMapResource(request.getResourceResolver(), request.getResource() + "/component_" + i, ITEM_RESOURCE_TYPE, new ValueMapDecorator(map));
    }).collect(Collectors.toList());
    DataSource ds = new SimpleDataSource(resources.iterator());
    request.setAttribute(DataSource.class.getName(), ds);

  }
}
