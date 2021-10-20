package com.adobe.aem.modernize.servlet;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.Servlet;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.day.cq.wcm.api.Page;
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
        SLING_SERVLET_SELECTORS + "=component"
    }
)
public class ListComponentRulesServlet extends AbstractListRulesServlet {

  @Reference
  private ComponentRewriteRuleService rewriteRuleService;

  protected Set<String> getResourceTypes(Page page) {
    Resource resource = page.getContentResource();
    final ResourceResolver rr = resource.getResourceResolver();
    Set<String> paths = rewriteRuleService.findResources(resource);
    return paths.stream().map(p -> {
      Resource r = rr.getResource(p);
      String type = null;
      if (r != null) {
        type = r.getValueMap().get(ResourceResolver.PROPERTY_RESOURCE_TYPE, String.class);
      }
      return type;
    }).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  protected List<RuleInfo> getRules(final ResourceResolver rr, Set<String> resourceTypes) {
    return rewriteRuleService.listRules(rr, resourceTypes.toArray(new String[] {})).stream()
        .map(r -> new RuleInfo(r.getId(), r.getTitle()))
        .collect(Collectors.toList());
  }

}
