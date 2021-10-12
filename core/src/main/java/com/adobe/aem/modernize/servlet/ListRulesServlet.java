package com.adobe.aem.modernize.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.model.ConversionJob;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static javax.servlet.http.HttpServletResponse.*;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_PATHS + "=/bin/modernize/rules.json"
    }
)
public class ListRulesServlet extends SlingSafeMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(ListRulesServlet.class);
  private static final String PARAM_PATH = "path";
  private static final String PARAM_OPERATION = "operation";

  @Reference
  private ComponentRewriteRuleService componentService;

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {

    RequestData data = getRequestData(request);
    if (data == null) {
      writeResponse(response, SC_BAD_REQUEST, false, "Error processing request parameters.", null);
      return;
    }
    ResourceResolver rr = request.getResourceResolver();
    Resource resource = getResource(rr, data.getPath());

    if (resource == null) {
      logger.warn("Requested rules for path that did not exist ({})", data.getPath());
      writeResponse(response, SC_BAD_REQUEST, false, "Error processing request parameters.", null);
      return;
    }
    RuleList rules = new RuleList();
    rules.setComponentRules(getComponentRules(resource));
    writeResponse(response, SC_OK, true, "", rules);
  }

  @Nullable
  private RequestData getRequestData(SlingHttpServletRequest request) {
    String path = request.getParameter(PARAM_PATH);
    if (StringUtils.isBlank(path)) {
      return null;
    }
    String op = request.getParameter(PARAM_OPERATION);
    if (StringUtils.isBlank(op)) {
      return null;
    }
    try {
      ConversionJob.Type type = ConversionJob.Type.valueOf(op.toUpperCase());
      return new RequestData(path, type);
    } catch (IllegalArgumentException e) {
      logger.warn("Operation was not a valid type: ({})", op);
      return null;
    }
  }

  @Nullable
  private Resource getResource(@NotNull ResourceResolver rr, @NotNull String path) {
    Resource resource = rr.getResource(path);
    if (resource == null) {
      return resource;
    }
    Page page = resource.adaptTo(Page.class);

    return page == null ? resource : page.getContentResource();
  }

  private List<RuleInfo> getComponentRules(@NotNull Resource resource) {
    final ResourceResolver rr = resource.getResourceResolver();
    Set<String> paths = componentService.find(resource);
    return paths.stream().map(p -> {
      Resource r = rr.getResource(p);
      RuleInfo info = null;
      if (r != null) {
        String title = StringUtils.defaultIfBlank(r.getValueMap().get(NameConstants.PN_TITLE, String.class), p);
        info = new RuleInfo(p, title);
      }
      return info;
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private void writeResponse(SlingHttpServletResponse response, int code, boolean success, String message, RuleList list) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String result = mapper.writeValueAsString(new ResponseData(success, message, list));
    response.setStatus(code);
    response.setContentType("application/json");
    response.getWriter().write(result);
  }

  @Value
  static class RequestData {
    String path;
    ConversionJob.Type type;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  static final class ResponseData {
    private boolean success;
    private String message;
    private RuleList rules = new RuleList();
  }

  @Getter
  @Setter
  static final class RuleList {
    private List<RuleInfo> templateRules = new ArrayList<>();
    private List<RuleInfo> componentRules = new ArrayList<>();
    private List<RuleInfo> policyRules = new ArrayList<>();
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  static final class RuleInfo {
    private String path;
    private String title;
  }
}
