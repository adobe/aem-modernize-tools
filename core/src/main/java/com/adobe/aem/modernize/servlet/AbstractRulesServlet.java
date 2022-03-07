package com.adobe.aem.modernize.servlet;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static javax.servlet.http.HttpServletResponse.*;

public abstract class AbstractRulesServlet extends SlingSafeMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(AbstractRulesServlet.class);
  
  protected static final String PARAM_REPROCESS = "reprocess";
  private static final String PARAM_PATH = "path";

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    String path = request.getParameter(PARAM_PATH);
    ResponseData data = new ResponseData();
    if (StringUtils.isBlank(path)) {
      data.setMessage("No paths to process.");
      writeResponse(response, SC_BAD_REQUEST, data);
      return;
    }
    ResourceResolver rr = request.getResourceResolver();
    Page page = rr.adaptTo(PageManager.class).getPage(path);
    if (page == null) {
      data.setMessage("Path not a page.");
      writeResponse(response, SC_BAD_REQUEST, data);
      return;
    }

    Set<String> foundPaths = listPaths(request.getParameterMap(), page);
    if (foundPaths.isEmpty()) {
      data.setSuccess(true);
      data.setMessage("No paths to process.");
      writeResponse(response, SC_OK, data);
      return;
    }
            
    Set<String> paths = new HashSet<>();
    Set<RuleInfo> infos = foundPaths.stream()
        .map(p -> {
          Resource resource = rr.getResource(p);
          Set<RuleInfo> rules = Collections.emptySet();
          if (resource != null) {
             rules = listRules(resource);
          }
          if (!rules.isEmpty()) {
            paths.add(p);
          }
          return rules;
        })
        .reduce(new HashSet<>(), (l, r) -> {
          l.addAll(r);
          return l;
        });
    data.setSuccess(true);
    data.setMessage("Success");
    data.setRules(infos);
    data.setPaths(paths);
    writeResponse(response, SC_OK, data);
  }

  @Nullable
  protected Resource getPageContent(@NotNull Map<String, String[]> params, @NotNull Page page) {
    boolean reprocess = BooleanUtils.toBoolean(params.getOrDefault(PARAM_REPROCESS, new String[] { "false" })[0]);
    Resource pageContent = page.getContentResource();
    if (reprocess) {
      logger.debug("Page reprocess requested.");
      pageContent = getOriginalPageContent(page);
    }
    return pageContent;
  }
  
  @Nullable
  protected Resource getOriginalPageContent(@NotNull Page page) {
    String versionId = page.getProperties().get(ConversionJob.PN_PRE_MODERNIZE_VERSION, String.class);
    if (StringUtils.isBlank(versionId)) {
      logger.debug("Page does not contain previous version. Processing as-is.");
      return page.getContentResource();
    }

    try {
      PageManager pm = page.getPageManager();
      ResourceResolver rr = page.getContentResource().getResourceResolver();
      Revision rev = pm.getRevisions(page.getPath(), null).stream().filter(r -> r.getId().equals(versionId)).findFirst().orElse(null);
      if (rev == null) {
        return null;
      }
      return rr.getResource(rev.getVersion().getFrozenNode().getPath());
    } catch (WCMException | RepositoryException ex) {
      logger.warn("Unable to determine revision of page.");
      return null;
    }
  }

  @NotNull
  protected Set<String> gatherPaths(@NotNull Resource root) {
    Set<String> paths = new HashSet<>();
    Iterator<Resource> children = root.listChildren();
    while (children.hasNext()) {
      new AbstractResourceVisitor() {
        @Override
        protected void visit(@NotNull Resource resource) {
          ValueMap vm = resource.adaptTo(ValueMap.class);
          if (vm != null &&
              StringUtils.isNotBlank(vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class))) {
            
            paths.add(resource.getPath());
          }
        }
      }.accept(children.next());
    }
    return paths;
  }

  @NotNull
  protected Set<RuleInfo> listRules(@NotNull Resource resource) {
    final String path = resource.getPath();
    logger.debug("Listing rules for resource: {}", path);

    ResourceResolver rr = resource.getResourceResolver();
    Set<String> ids = getRewriteRuleService().listRules(resource);
    Set<RuleInfo> infos = new HashSet<>();
    for (String id: ids) {
      RewriteRule rule = getRewriteRuleService().getRule(rr, id);
      if (rule != null) {
        logger.trace("Found rule [{}], for resource [{}].", id, path);
        infos.add(new RuleInfo(rule.getId(), rule.getTitle()));
      }
    }
    return infos;
  }

  private void writeResponse(SlingHttpServletResponse response, int code, ResponseData data) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), data);
  }

  @NotNull
  protected abstract Set<String> listPaths(@NotNull Map<String, String[]> requestParameters, @NotNull Page page);
  
  @NotNull
  protected abstract RewriteRuleService getRewriteRuleService();
  
  @Getter
  @Setter
  @NoArgsConstructor
  private static final class ResponseData {
    boolean success;
    String message;
    Set<String> paths = Collections.emptySet();
    Set<RuleInfo> rules = Collections.emptySet();
  }
}
