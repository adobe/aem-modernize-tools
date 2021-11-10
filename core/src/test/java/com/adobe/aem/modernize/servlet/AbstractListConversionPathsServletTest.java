package com.adobe.aem.modernize.servlet;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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

import com.day.cq.wcm.api.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Tested;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class AbstractListConversionPathsServletTest {

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  @Tested
  private MockListPathsServlet servlet;


  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", "/content/test");
  }

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Path
    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
  }

  @Test
  public void invalidPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/does/not/exist");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
  }

  @Test
  public void pathNotPage() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Set<String> paths = new HashSet<>();
    paths.add("/content/test/jcr:content/simple");
    paths.add("/content/test/jcr:content/copyChildren");
    List<Resource> capture = new ArrayList<>();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/test/jcr:content");
    request.setParameterMap(params);

    servlet.doGet(request, response);
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
  }


  @Test
  public void testDoGetSuccess() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/content/test");
    request.setParameterMap(params);

    servlet.doGet(request, response);

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    ListComponentsServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ListComponentsServlet.ResponseData.class);
    assertEquals(1, result.getTotal(), "Correct number of components");
    assertTrue( result.getPaths().contains("/content/test"), "Test output correct");
  }

  private static class MockListPathsServlet extends AbstractListConversionPathsServlet {
    @Override
    protected @NotNull List<String> listPaths(@NotNull Map<String, String[]> params, @NotNull Page page) {
      List<String> paths = new ArrayList<>();
      paths.add("/content/test");
      return paths;
    }
  }


}
