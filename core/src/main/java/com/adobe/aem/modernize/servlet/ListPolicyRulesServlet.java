package com.adobe.aem.modernize.servlet;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.Servlet;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=GET",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listrules",
        SLING_SERVLET_SELECTORS + "=policy"
    }
)
public class ListPolicyRulesServlet extends AbstractListRulesServlet {

  @Reference
  private PolicyImportRuleService importRuleService;

  protected Set<String> getResourceTypes(Page page) {
    Resource pageContent = page.getContentResource();
    ResourceResolver rr = pageContent.getResourceResolver();
    Designer designer = rr.adaptTo(Designer.class);
    Style style = designer.getStyle(pageContent);
    Resource styleResource = rr.getResource(style.getPath());
    if (styleResource == null) {
      return Collections.emptySet();
    }
    return importRuleService.find(styleResource);

  }

  protected List<RuleInfo> getRules(final ResourceResolver rr, Set<String> resourceTypes) {
    return importRuleService.listRules(rr, resourceTypes.toArray(new String[] {})).stream()
        .map(r -> new RuleInfo(r.getId(), r.getTitle()))
        .collect(Collectors.toList());
  }

}
