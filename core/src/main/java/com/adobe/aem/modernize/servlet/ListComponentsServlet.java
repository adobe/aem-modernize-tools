package com.adobe.aem.modernize.servlet;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
        SLING_SERVLET_SELECTORS + "=listcomponents"
    }
)
public class ListComponentsServlet extends SlingSafeMethodsServlet {

  private static final String PARAM_PATH = "path";

  @Reference
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    String path = request.getParameter(PARAM_PATH);
    if (StringUtils.isBlank(path)) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptySet());
    }

    ResourceResolver rr = request.getResourceResolver();
    Resource resource = rr.getResource(path);
    if (resource == null) {
      writeResponse(response, SC_BAD_REQUEST, Collections.emptySet());
      return;
    }
    if (resource.adaptTo(Page.class) != null) {
      resource = resource.adaptTo(Page.class).getContentResource();
    }

    Set<String> paths = componentRewriteRuleService.find(resource);
    writeResponse(response, SC_OK, paths);
  }

  private void writeResponse(SlingHttpServletResponse response, int code, Set<String> paths) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), new ResponseData(paths, paths.size()));
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  static final class ResponseData {
    Set<String> paths = new HashSet<>();
    int total;
  }

}
