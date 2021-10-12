package com.adobe.aem.modernize.servlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.servlet.ListRulesServlet.*;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ListRulesServletTest {

  private static final String RULE_PATH = "/apps/rules";
  private static final String CONTENT_PATH = "/content";
  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_MOCK);

  @Injectable
  private ComponentRewriteRuleService componentRewriteRuleService;

  @Tested
  private ListRulesServlet servlet;

  @BeforeEach
  protected void beforeEach() {
    slingContext.load().json("/component/test-rules.json", RULE_PATH + "/component");
    slingContext.load().json("/component/all-content.json", CONTENT_PATH + "/component");
  }

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(slingContext.resourceResolver(), slingContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Path
    servlet.doGet(request, response);
    ObjectMapper mapper = new ObjectMapper();
    ResponseData result = mapper.readValue(response.getOutputAsString(), ResponseData.class);

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.isSuccess(), "Success Status");
    assertNotNull(result.getMessage(), "Message present");
    assertNull(result.getRules(), "Rules present");

    // No Operation
    Map<String, Object> params = new HashMap<>();
    params.put("path", "/path/to/content");

    servlet.doGet(request, response);
    result = mapper.readValue(response.getOutputAsString(), ResponseData.class);

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.isSuccess(), "Success Status");
    assertNotNull(result.getMessage(), "Message present");
    assertNull(result.getRules(), "Rules present");

    // Invalid Operation
    params = new HashMap<>();
    params.put("path", "/path/to/content");
    params.put("operation", "INVALID");

    servlet.doGet(request, response);
    result = mapper.readValue(response.getOutputAsString(), ResponseData.class);

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.isSuccess(), "Success Status");
    assertNotNull(result.getMessage(), "Message present");
    assertNull(result.getRules(), "Rules present");
  }

  @Test
  public void invalidResourcePath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(slingContext.resourceResolver(), slingContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", "/does/not/exist");
    params.put("operation", ConversionJob.Type.FULL.toString());
    request.setParameterMap(params);

    servlet.doGet(request, response);
    ObjectMapper mapper = new ObjectMapper();
    ResponseData result = mapper.readValue(response.getOutputAsString(), ResponseData.class);

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.isSuccess(), "Success Status");
    assertNotNull(result.getMessage(), "Message present");
    assertNull(result.getRules(), "Rules present");
  }

  @Test
  public void componentResourceListEmpty() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(slingContext.resourceResolver(), slingContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    final String path = CONTENT_PATH + "/component/simple";
    Map<String, Object> params = new HashMap<>();
    params.put("path", path);
    params.put("operation", ConversionJob.Type.COMPONENT.toString());
    request.setParameterMap(params);

    final Set<String> resources = Collections.emptySet();
    final List<Resource> capture = new ArrayList<>();
    new Expectations() {{
      componentRewriteRuleService.findResources(withCapture(capture));
      result = resources;
    }};


    servlet.doGet(request, response);

    assertEquals(1, capture.size(), "Component API calls");
    assertEquals(path, capture.get(0).getPath(), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    ResponseData result = mapper.readValue(response.getOutputAsString(), ResponseData.class);

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.isSuccess(), "Success Status");
    assertNotNull(result.getMessage(), "Message present");
    RuleList ruleResults = result.getRules();
    assertNotNull(ruleResults, "Rules present");
    assertTrue(ruleResults.getTemplateRules().isEmpty(), "Component Rule list empty");
    assertTrue(ruleResults.getPolicyRules().isEmpty(), "Component Rule list empty");
    assertTrue(ruleResults.getComponentRules().isEmpty(), "Component Rule list empty");
  }

  @Test
  public void componentRules() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(slingContext.resourceResolver(), slingContext.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    final String path = CONTENT_PATH + "/component/simple";
    Map<String, Object> params = new HashMap<>();
    params.put("path", path);
    params.put("operation", ConversionJob.Type.COMPONENT.toString());
    request.setParameterMap(params);

    final Set<String> resources = new HashSet<>();
    resources.add(CONTENT_PATH + "/component/simple");
    resources.add(CONTENT_PATH + "/component/copyChildren");

    final Set<RewriteRule> rules = new HashSet<>();
    rules.add(new MockRule(RULE_PATH + "/component/simple"));
    rules.add(new MockRule(RULE_PATH + "/component/copyChildren"));

    final List<Resource> capture = new ArrayList<>();
    new Expectations() {{
      componentRewriteRuleService.findResources(withCapture(capture));
      result = resources;
      componentRewriteRuleService.listRules(withInstanceOf(ResourceResolver.class), withNotNull());
      result = rules;
    }};

    servlet.doGet(request, response);

    assertEquals(1, capture.size(), "Component API calls");
    assertEquals(path, capture.get(0).getPath(), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    ResponseData result = mapper.readValue(response.getOutputAsString(), ResponseData.class);

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.isSuccess(), "Success Status");
    assertNotNull(result.getMessage(), "Message present");
    RuleList ruleResults = result.getRules();
    assertNotNull(ruleResults, "Rules present");
    assertTrue(ruleResults.getTemplateRules().isEmpty(), "Component Rule list empty");
    assertTrue(ruleResults.getPolicyRules().isEmpty(), "Component Rule list empty");

    List<RuleInfo> componentRules = ruleResults.getComponentRules();
    assertFalse(componentRules.isEmpty(), "Component Rule list empty");

    RuleInfo ri = componentRules.get(0);
    assertEquals("/apps/rules/component/simple", ri.getTitle());
    assertEquals("/apps/rules/component/simple", ri.getPath());

    ri = componentRules.get(1);
    assertEquals("/apps/rules/component/copyChildren", ri.getTitle());
    assertEquals("/apps/rules/component/copyChildren", ri.getPath());

  }
}
