package com.adobe.aem.modernize.component.datasource;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.component.datasource.ComponentDataSource.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentDataSourceTest {

  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  public ComponentDataSource dataSource = new ComponentDataSource();

  @Mocked
  private ComponentRewriteRuleService componentService;

  @Mocked
  private ExpressionResolver expressionResolver;

  @BeforeEach
  public void beforeEach() {
    context.registerService(ComponentRewriteRuleService.class, componentService);
    context.registerService(ExpressionResolver.class, expressionResolver);
    context.registerInjectActivateService(dataSource);
    context.load().json("/datasource/test-component.json", "/apps/aem-modernize/component");
  }

  @Test
  public void testNoPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource("/apps/aem-modernize/component/table");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${param.path}", Locale.ENGLISH, String.class, request);
      result = "";
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }


  @Test
  public void testUnresolvedSearchRoot() throws Exception {
    final String contentPath = "/content/does-not-exist";
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource("/apps/aem-modernize/component/table");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${param.path}", Locale.ENGLISH, String.class, request);
      result = contentPath;
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public void testNoMatches() throws Exception {
    final String contentPath = "/content/test";
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource("/apps/aem-modernize/component/table");
    context.load().json("/datasource/test-content.json", contentPath);
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${param.path}", Locale.ENGLISH, String.class, request);
      result = contentPath;
      componentService.find(withInstanceOf(Resource.class));
      result = Collections.emptySet();
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public void testDoGet() throws Exception {
    final String contentPath = "/content/test";
    final Set<String> paths = new HashSet<>();
    paths.add("/content/test/component/path2");
    paths.add("/content/test/component/path1");

    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource("/apps/aem-modernize/component/table");
    context.load().json("/datasource/test-content.json", contentPath);

    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${param.path}", Locale.ENGLISH, String.class, request);
      result = contentPath;
      componentService.find(withInstanceOf(Resource.class));
      result = paths;
    }};

    dataSource.doGet(request, context.response());;
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    Iterator<Resource> it = ds.iterator();
    Resource r = it.next();
    ValueMap vm = r.getValueMap();
    assertEquals("/content/test/component/path1", vm.get("path", String.class), "Item path");
    assertNotNull(vm.get("ruleHref", String.class), "Href Set");
    assertEquals(ITEM_RESOURCE_TYPE, r.getResourceType(), "Item Resource type");

    r = it.next();
    vm = r.getValueMap();
    assertEquals("/content/test/component/path2", vm.get("path", String.class), "Item path");
    assertNotNull(vm.get("ruleHref", String.class), "Href Set");
    assertEquals(ITEM_RESOURCE_TYPE, r.getResourceType(), "Item Resource type");
  }
}
