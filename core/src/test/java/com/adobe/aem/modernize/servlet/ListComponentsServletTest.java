package com.adobe.aem.modernize.servlet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ListComponentsServletTest {
  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  @Injectable
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Tested
  private ListComponentsServlet servlet;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", "/content/test");
  }

  @Test
  public void testDoGetSuccess() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Set<String> paths = new HashSet<>();
    paths.add("/content/test/jcr:content/simple");
    paths.add("/content/test/jcr:content/copyChildren");
    List<Resource> capture = new ArrayList<>();

    new Expectations() {{
      componentRewriteRuleService.find(withCapture(capture));
      result = paths;
    }};

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/test");
    request.setParameterMap(params);

    servlet.doGet(request, response);

    assertEquals("/content/test/jcr:content", capture.get(0).getPath(), "Resource search path");
    assertEquals(SC_OK, response.getStatus(), "Request Status");
    AbstractListConversionPathsServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), AbstractListConversionPathsServlet.ResponseData.class);
    assertEquals(2, result.getTotal(), "Correct number of components");
    assertTrue( result.getPaths().contains("/content/test/jcr:content/simple"), "Simple component node");
    assertTrue( result.getPaths().contains("/content/test/jcr:content/copyChildren"), "CopyChildren component node");
  }

}
