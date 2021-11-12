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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.impl.ListPageVisitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static javax.servlet.http.HttpServletResponse.*;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=GET",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listchildren"
    }
)
public class ListChildrenServlet extends SlingSafeMethodsServlet {

  private static final String PARAM_PATH = "path";
  private static final String PARAM_DIRECT = "direct";

  @Reference
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    RequestData data = getRequestData(request);
    if (data == null) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptyList());
      return;
    }

    ResourceResolver rr = request.getResourceResolver();
    Resource resource = rr.getResource(data.getPath());

    if (resource == null) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptyList());
      return;
    }

    ListPageVisitor visitor;
    if (data.direct) {
      int depth = PathUtils.getDepth(resource.getPath()) + 1;
      visitor = new ListPageVisitor(depth);
    } else {
      visitor = new ListPageVisitor();
    }
    visitor.accept(resource);
    List<String> paths = visitor.getPaths();
    paths.remove(resource.getPath());
    writeResponse(response, SC_OK, paths);
  }

  private ListChildrenServlet.RequestData getRequestData(SlingHttpServletRequest request) {
    String path = request.getParameter(PARAM_PATH);
    if (StringUtils.isBlank(path)) {
      return null;
    }
    String directStr = request.getParameter(PARAM_DIRECT);
    boolean direct = StringUtils.isNotBlank(directStr);
    return new RequestData(path, direct);
  }

  private void writeResponse(SlingHttpServletResponse response, int code, List<String> paths) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), new ResponseData(paths, paths.size()));
  }

  @Value
  static class RequestData {
    String path;
    boolean direct;
  }

  @Value
  static class ResponseData {
    List<String> paths;
    int total;
  }

}
