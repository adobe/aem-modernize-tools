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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.day.cq.wcm.api.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import static javax.servlet.http.HttpServletResponse.*;

public abstract class AbstractListConversionPathsServlet extends SlingSafeMethodsServlet {

  protected static final String PARAM_PATH = "path";

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    String path = request.getParameter(PARAM_PATH);
    if (StringUtils.isBlank(path)) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptyList());
    }
    ResourceResolver rr = request.getResourceResolver();
    Resource resource = rr.getResource(path);
    if (resource == null) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptyList());
      return;
    }

    if (resource.adaptTo(Page.class) == null) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptyList());
      return;
    }
    Page page = resource.adaptTo(Page.class);

    List<String> paths = listPaths(request.getParameterMap(), page);
    Collections.sort(paths);
    writeResponse(response, SC_OK, paths);
  }

  @NotNull
  protected abstract List<String> listPaths(@NotNull Map<String, String[]> requestParameters, @NotNull Page page);

  private void writeResponse(SlingHttpServletResponse response, int code, List<String> paths) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), new ResponseData(paths, paths.size()));
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  static final class ResponseData {
    List<String> paths = new ArrayList<>();
    int total;
  }
}
