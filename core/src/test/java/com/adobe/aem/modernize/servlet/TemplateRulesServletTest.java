package com.adobe.aem.modernize.servlet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Injectable;
import mockit.Tested;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class TemplateRulesServletTest {
  
  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
  private static final String CONTENT_PATH = "/content/test";
  
  @Injectable
  private StructureRewriteRuleService structureRewriteRuleService;
  
  @Tested
  private TemplateRulesServlet servlet;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", CONTENT_PATH);
  }


  @Test
  public void listPathsSuccess() {
    Page page = context.resourceResolver().adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Map<String, String[]> params = new HashMap<>();
    params.put("path", new String[] { "/content/test" });

    Set<String> paths = servlet.listPaths(params, page);
    assertEquals(1, paths.size(), "Paths list size.");
    assertTrue(paths.contains(CONTENT_PATH + "/jcr:content"), "Path list contents.");
  }

  @Test
  public void listPathsNotFound() {
    
    TemplateRulesServlet servlet = new TemplateRulesServlet() {
      @Override
      protected @Nullable Resource getPageContent(@NotNull Map<String, String[]> params, @NotNull Page page) {
        return null;
      }
    };
    
    Page page = context.resourceResolver().adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Map<String, String[]> params = new HashMap<>();
    params.put("path", new String[] { "/content/test" });

    Set<String> paths = servlet.listPaths(params, page);
    assertTrue(paths.isEmpty(), "Path list contents.");
  }
}
