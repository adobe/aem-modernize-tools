package com.adobe.aem.modernize.servlet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.servlet.Servlet;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=GET",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=policy.rules"
    }
)
public class PolicyRulesServlet extends AbstractRulesServlet {

  protected static final String PARAM_INCLUDE_SUPER_TYPES = "includeSuperTypes";
  private static final Logger logger = LoggerFactory.getLogger(PolicyRulesServlet.class);

  @Reference
  private PolicyImportRuleService importRuleService;
  
  @Override
  @NotNull
  protected Set<String> listPaths(@NotNull Map<String, String[]> params, @NotNull Page page) {

    String[] includeSuper = params.getOrDefault(PARAM_INCLUDE_SUPER_TYPES, new String[] { "false" });
    boolean include = BooleanUtils.toBoolean(includeSuper[0]);

    Resource pageContent = page.getContentResource();
    ResourceResolver rr = pageContent.getResourceResolver();
    Designer designer = rr.adaptTo(Designer.class);
    Style style = designer.getStyle(pageContent);
    if (style == null) {
      logger.info("Page Content did not have a Style associated: [{}]", pageContent.getPath());
      return Collections.emptySet();
    }

    Set<String> paths = new HashSet<>();
    Design design = designer.getDesign(page);
    Cell cell = style.getCell();
    Resource resource;
    Iterator<String> cellRoots = cell.paths();
    do {
      String id = cellRoots.next();
      style = design.getStyle(id);
      resource = rr.getResource(style.getPath());
      if (resource != null) {
        paths.addAll(gatherPaths(resource));
      }
    } while (include && cellRoots.hasNext());

    return paths;
  }

  @Override
  @NotNull
  protected RewriteRuleService getRewriteRuleService() {
    return importRuleService;
  }
}
