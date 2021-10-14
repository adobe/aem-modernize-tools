package com.adobe.aem.modernize.servlet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Injectable;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ListChildrenServletTest {
  private final AemContext aemContext = new AemContext(ResourceResolverType.JCR_MOCK);

  @Injectable
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Tested
  private ListChildrenServlet servlet;

  @BeforeEach
  protected void beforeEach() {
    aemContext.load().json("/servlet/page-content.json", "/content/test");
  }

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Path
    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
  }

  @Test
  public void invalidPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/does/not/exist");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
  }

  @Test
  public void testDoGetDirect() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/test");
    params.put("direct", "true");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    assertEquals(SC_OK, response.getStatus(), "Request Status");

    JsonNode json = new ObjectMapper().readTree(response.getOutputAsString());
    assertEquals(1, json.get("total").asInt(), "Correct total");
    JsonNode path = json.get("paths").elements().next();
    assertEquals("/content/test/products", path.asText(), "Path set");
  }

  @Test
  public void testDoGetDepth() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(aemContext.resourceResolver(), aemContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/test");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    assertEquals(SC_OK, response.getStatus(), "Request Status");

    JsonNode json = new ObjectMapper().readTree(response.getOutputAsString());
    assertEquals(4, json.get("total").asInt(), "Correct total");
    Iterator<JsonNode> paths = json.get("paths").elements();
    while (paths.hasNext()) {
      JsonNode path = paths.next();
      String p = path.asText();
      if (!p.equals("/content/test/products") &&
          !p.equals("/content/test/products/square") &&
          !p.equals("/content/test/products/triangle") &&
          !p.equals("/content/test/products/circle")) {
        fail("Path matched");
      }
    }
  }
}
