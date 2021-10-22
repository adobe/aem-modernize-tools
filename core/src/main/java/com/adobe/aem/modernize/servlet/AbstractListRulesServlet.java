package com.adobe.aem.modernize.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.day.cq.wcm.api.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static javax.servlet.http.HttpServletResponse.*;

public abstract class AbstractListRulesServlet extends SlingSafeMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(AbstractListRulesServlet.class);
  private static final String PARAM_PATH = "path";

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws IOException {

    String path = request.getParameter(PARAM_PATH);
    if (StringUtils.isBlank(path)) {
      writeResponse(response, SC_BAD_REQUEST, false, "Error processing request parameters.", null);
      return;
    }
    ResourceResolver rr = request.getResourceResolver();
    Page page = getPage(rr, path);

    if (page == null) {
      logger.warn("Requested rules for path that was not a page ({})", path);
      writeResponse(response, SC_BAD_REQUEST, false, "Error processing request parameters.", null);
      return;
    }

    Set<String> resourceTypes = getResourceTypes(page);
    List<RuleInfo> rules = getRules(rr, resourceTypes);
    writeResponse(response, SC_OK, true, "", rules);
  }

  private Page getPage(ResourceResolver rr, String path) {
    Resource resource = rr.getResource(path);
    if (resource == null) {
      return null;
    }
    return resource.adaptTo(Page.class);
  }

  private void writeResponse(SlingHttpServletResponse response, int code, boolean success, String message, List<RuleInfo> rules) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), new ResponseData(success, message, rules));
  }

  protected abstract Set<String> getResourceTypes(Page page);

  protected abstract List<RuleInfo> getRules(ResourceResolver rr, Set<String> resourceTypes);

  @Value
  static class ResponseData {
    boolean success;
    String message;
    List<RuleInfo> rules;
  }
}
