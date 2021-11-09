package com.adobe.aem.modernize.servlet.rule;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.adobe.aem.modernize.model.ConversionJob;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.api.designer.Designer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;
import static javax.servlet.http.HttpServletResponse.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=GET",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=pagedata"
    }
)
public class PageInfoServlet extends SlingSafeMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(PageInfoServlet.class);

  private static final String PARAM_PATH = "path";
  private static final String PARAM_REPROCESS = "reprocess";

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    String path = request.getParameter(PARAM_PATH);
    boolean reprocess = BooleanUtils.toBoolean(request.getParameter(PARAM_REPROCESS));

    PageManager pm = request.getResourceResolver().adaptTo(PageManager.class);
    Page page = pm.getPage(path);
    Map<String, String> info = new HashMap<>();
    if (page == null) {
      info.put(NameConstants.PN_TITLE, "");
      info.put(NameConstants.PN_DESIGN_PATH, "");
      writeResponse(response, SC_BAD_REQUEST, info);
      return;
    }
    info.put(NameConstants.PN_TITLE, page.getProperties().get(NameConstants.PN_TITLE, ""));
    String versionId = page.getProperties().get(ConversionJob.PN_PRE_MODERNIZE_VERSION, String.class);
    String designPath = null;
    try {
      if (reprocess && StringUtils.isNotBlank(versionId)) {
        ResourceResolver rr = page.getContentResource().getResourceResolver();
        Collection<Revision> revisions = pm.getRevisions(page.getPath(), null);
        for (Revision r : revisions) {
          if (r.getId().equals(versionId)) {
            Resource resource = rr.getResource(r.getVersion().getFrozenNode().getPath());
            if (resource != null) {
              designPath = resource.getValueMap().get(NameConstants.PN_DESIGN_PATH, "");
            }
            break;
          }
        }
      }
    } catch (RepositoryException | WCMException e) {
      logger.error("Encountered an error while trying to retrieve components on page version. Defaulting to current page.", e);
    } finally {
      if (designPath == null) {
        Designer designer = request.getResourceResolver().adaptTo(Designer.class);
        designPath = designer.getDesignPath(page);

      }
    }
    info.put(NameConstants.PN_DESIGN_PATH, StringUtils.defaultIfBlank(designPath, ""));
    writeResponse(response, SC_OK, info);
  }

  private void writeResponse(SlingHttpServletResponse response, int code, Map<String, String> info) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getWriter(), info);
  }

}
