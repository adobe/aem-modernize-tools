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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.adobe.aem.modernize.servlet.RuleInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import static javax.servlet.http.HttpServletResponse.*;

@Deprecated(since = "2.1.0")
public abstract class AbstractListRulesServlet extends SlingAllMethodsServlet {

  protected static final String PARAM_PATH = "path";

  @Override
  protected void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws IOException {

    String[] paths = request.getParameterValues(PARAM_PATH);
    if (paths == null) {
      writeResponse(response, SC_BAD_REQUEST, false, "Error processing request parameters.", null);
      return;
    }

    if (paths.length == 0) {
      writeResponse(response, SC_OK, true, "No paths to process.", null);
      return;
    }
    ResourceResolver rr = request.getResourceResolver();
    Set<String> resourceTypes = Arrays.stream(paths).map(p -> {
      Resource r = rr.getResource(p);
      String type = null;
      if (r != null) {
        type = r.getValueMap().get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class);
      }
      return type;
    }).filter(Objects::nonNull).collect(Collectors.toSet());

    if (resourceTypes.isEmpty()) {
      writeResponse(response, SC_OK, true, "No paths to process.", null);
      return;
    }
    writeResponse(response, SC_OK, true, "", getRules(request, resourceTypes));
  }

  private void writeResponse(SlingHttpServletResponse response, int code, boolean success, String message, List<RuleInfo> rules) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), new ResponseData(success, message, rules));
  }

  @NotNull
  protected List<RuleInfo> getRules(final @NotNull SlingHttpServletRequest request, @NotNull Set<String> resourceTypes) {
    ResourceResolver rr = request.getResourceResolver();
    return getRewriteRuleService().listRules(rr, resourceTypes.toArray(new String[] {})).stream()
        .map(r -> new RuleInfo(r.getId(), r.getTitle()))
        .collect(Collectors.toList());
  }

  @NotNull
  protected abstract RewriteRuleService getRewriteRuleService();

  @Value
  static class ResponseData {
    boolean success;
    String message;
    List<RuleInfo> rules;
  }
}
