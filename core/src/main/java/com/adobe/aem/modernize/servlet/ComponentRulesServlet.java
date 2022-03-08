package com.adobe.aem.modernize.servlet;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.servlet.Servlet;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.sling.api.resource.Resource;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.day.cq.wcm.api.Page;
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
        SLING_SERVLET_SELECTORS + "=component.rules"
    }
)
public class ComponentRulesServlet extends AbstractRulesServlet {

  @Reference
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Override
  @NotNull
  protected Set<String> listPaths(@NotNull Map<String, String[]> params, @NotNull Page page) {

    Resource pageContent = getPageContent(params, page);
    if (pageContent == null) {
      return Collections.emptySet();
    }
    return gatherPaths(pageContent);
  }

  @Override
  @NotNull 
  protected RewriteRuleService getRewriteRuleService() {
    return componentRewriteRuleService;
  }
}
