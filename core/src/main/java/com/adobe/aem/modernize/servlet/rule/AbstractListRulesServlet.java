package com.adobe.aem.modernize.servlet.rule;

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

import com.adobe.aem.modernize.servlet.RuleInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import static javax.servlet.http.HttpServletResponse.*;

public abstract class AbstractListRulesServlet extends SlingAllMethodsServlet {

  private static final String PARAM_PATH = "path";

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
    writeResponse(response, SC_OK, true, "", getRules(rr, resourceTypes));
  }

  private void writeResponse(SlingHttpServletResponse response, int code, boolean success, String message, List<RuleInfo> rules) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), new ResponseData(success, message, rules));
  }

  @NotNull
  protected abstract List<RuleInfo> getRules(@NotNull final ResourceResolver rr, @NotNull Set<String> resourceTypes);

  @Value
  static class ResponseData {
    boolean success;
    String message;
    List<RuleInfo> rules;
  }
}
