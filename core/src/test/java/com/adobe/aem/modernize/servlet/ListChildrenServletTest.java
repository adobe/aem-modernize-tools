package com.adobe.aem.modernize.servlet;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import mockit.Tested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ListChildrenServletTest {
  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_MOCK);

  @Tested
  private ListChildrenServlet servlet;

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(slingContext.resourceResolver(), slingContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Path
    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");

  }

  @Test
  public void invalidPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(slingContext.resourceResolver(), slingContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/does/not/exist");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");

  }
}
