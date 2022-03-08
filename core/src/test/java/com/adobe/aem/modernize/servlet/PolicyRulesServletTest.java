package com.adobe.aem.modernize.servlet;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.version.Version;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.MockStyle;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class PolicyRulesServletTest {
  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
  private static final String CONTENT_PATH = "/content/test";
  private static final String DESIGN_PATH = "/etc/designs/test";

  @Injectable
  private PolicyImportRuleService policyImportRuleService;
  
  @Tested
  private PolicyRulesServlet servlet;

  @Mocked
  private RewriteRule nodeRule;

  @Mocked
  private RewriteRule serviceRule;

  @Mocked
  private Designer designer;
  
  @Mocked
  private Design design;

  @Mocked
  private Cell cell;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", CONTENT_PATH);
    context.load().json("/servlet/design-content.json", DESIGN_PATH);
  }

  @Test
  public <R extends ResourceResolver> void testNoStyle() throws Exception {

    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
      }
    };
    new Expectations() {{
      designer.getStyle(withInstanceOf(Resource.class));
      result = null;
    }};

    Page page = context.resourceResolver().adaptTo(PageManager.class).getPage(CONTENT_PATH);
    Map<String, String[]> params = new HashMap<>();
    params.put("path", new String[] { "/content/test" });

    assertTrue(servlet.listPaths(params, page).isEmpty(), "No Styles");
    
  }
  
  @Test
  public <R extends ResourceResolver> void listPathsSuccess() {
    Page page = context.resourceResolver().adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Map<String, String[]> params = new HashMap<>();

    Resource resource = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");
    Style homepageStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/homepage",  resource);

    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
      }
    };
    
    new Expectations() {{
      designer.getStyle(with(new Delegate<Resource>() {
        void delegate(Resource r) {
          assertEquals("/content/test/jcr:content", r.getPath(), "Get style call.");
        }
      }));
      result = homepageStyle;
      designer.getDesign(page);
      result = design;
      cell.paths();
      result = new Delegate<Iterator<String>>() {
        Iterator<String> delegate() {
          return Arrays.stream(new String[] { "homepage", "page", "basepage" }).iterator();
        }
      };
      design.getStyle("homepage");
      result = homepageStyle;
    }};
    
    Set<String> paths = servlet.listPaths(params, page);
    assertEquals(3, paths.size(), "Paths list size.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/homepage/title"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/homepage/rightpar"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/homepage/rightpar/title"), "Path list contents.");
  }
  
  @Test
  public <R extends ResourceResolver> void listPathsSupertype() {
    Page page = context.resourceResolver().adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Map<String, String[]> params = new HashMap<>();
    params.put(PolicyRulesServlet.PARAM_INCLUDE_SUPER_TYPES, new String[] { "true" });

    Resource resource = context.resourceResolver().getResource("/etc/designs/test/jcr:content/homepage");
    Style homepageStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/homepage",  resource);

    resource = context.resourceResolver().getResource("/etc/designs/test/jcr:content/page");
    Style pageStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/page", resource);

    Style baseStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/basepage");

    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
      }
    };

    new Expectations() {{
      designer.getStyle(with(new Delegate<Resource>() {
        void delegate(Resource r) {
          assertEquals("/content/test/jcr:content", r.getPath(), "Get style call.");
        }
      }));
      result = homepageStyle;
      designer.getDesign(page);
      result = design;
      cell.paths();
      result = new Delegate<Iterator<String>>() {
        Iterator<String> delegate() {
          return Arrays.stream(new String[] { "homepage", "page", "basepage" }).iterator();
        }
      };
      design.getStyle("homepage");
      result = homepageStyle;
      design.getStyle("page");
      result = pageStyle;
      design.getStyle("basepage");
      result = baseStyle;
    }};

    Set<String> paths = servlet.listPaths(params, page);
    assertEquals(8, paths.size(), "Paths list size.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/homepage/title"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/homepage/rightpar"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/homepage/rightpar/title"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/page/title"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/page/par"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/page/par/title"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/page/rightpar"), "Path list contents.");
    assertTrue(paths.contains(DESIGN_PATH + "/jcr:content/page/rightpar/title"), "Path list contents.");

  }
  
}
