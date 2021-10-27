package com.adobe.aem.modernize.servlet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.Servlet;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.day.cq.wcm.api.Page;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
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
public class ListComponentsServlet extends AbstractListConversionPathsServlet {
  @Reference
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Override
  protected @NotNull List<String> listPaths(@NotNull Map<String, String[]> params, @NotNull Page page) {
    List<String> paths = new ArrayList<>();
    paths.addAll(componentRewriteRuleService.find(page.getContentResource()));
    return paths;
  }

}
