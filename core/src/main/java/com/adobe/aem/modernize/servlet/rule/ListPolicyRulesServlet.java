package com.adobe.aem.modernize.servlet.rule;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.Servlet;

import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.servlet.RuleInfo;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=POST",
        SLING_SERVLET_EXTENSIONS + "=json",
        SLING_SERVLET_SELECTORS + "=listrules.policy"
    }
)
public class ListPolicyRulesServlet extends AbstractListRulesServlet {

  @Reference
  private PolicyImportRuleService importRuleService;

  protected List<RuleInfo> getRules(@NotNull final ResourceResolver rr, @NotNull Set<String> resourceTypes) {
    return importRuleService.listRules(rr, resourceTypes.toArray(new String[] {})).stream()
        .map(r -> new RuleInfo(r.getId(), r.getTitle()))
        .collect(Collectors.toList());
  }

}
