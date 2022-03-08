package com.adobe.aem.modernize.servlet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
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
public class ComponentRulesServletTest {

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
  private static final String CONTENT_PATH = "/content/test";

  @Injectable
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Tested
  private ComponentRulesServlet servlet;

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
    assertEquals(2, paths.size(), "Paths list size.");
    assertTrue(paths.contains(CONTENT_PATH + "/jcr:content/simple"), "Path list contents.");
    assertTrue(paths.contains(CONTENT_PATH + "/jcr:content/copyChildren"), "Path list contents.");
  }

  @Test
  public void listPathsNotFound() {

    ComponentRulesServlet servlet = new ComponentRulesServlet() {
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
