package com.adobe.aem.modernize.servlet.rule;

import javax.servlet.Servlet;

import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRuleService;
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

  @Override
  protected @NotNull RewriteRuleService getRewriteRuleService() {
    return importRuleService;
  }
}
