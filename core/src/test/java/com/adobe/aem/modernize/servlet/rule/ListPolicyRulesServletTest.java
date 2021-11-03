package com.adobe.aem.modernize.servlet.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.fasterxml.jackson.databind.JsonNode;
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
public class ListPolicyRulesServletTest {

  private static final String DESIGN_PATH = "/etc/design/policy";

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  @Injectable
  private PolicyImportRuleService importRuleService;

  @Tested
  private ListPolicyRulesServlet servlet;

  @BeforeEach
  protected void beforeEach() {
    context.load().json("/servlet/design-content.json", DESIGN_PATH);
  }

  @Test
  public void testPolicyRules() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", new String[] { DESIGN_PATH + "/jcr:content/homepage/title", DESIGN_PATH + "/jcr:content/page/par/title" });
    request.setParameterMap(params);

    final Set<RewriteRule> rules = new HashSet<>();
    rules.add(new MockRule("/apps/rules/policy/title"));

    final List<String[]> capture = new ArrayList<>();
    new Expectations() {{
      importRuleService.listRules(withInstanceOf(ResourceResolver.class), withCapture(capture));
      result = rules;
    }};

    servlet.doPost(request, response);

    assertEquals(1, capture.size(), "Policy API calls");
    List<String> resourcePaths = Arrays.asList(capture.get(0));
    assertEquals(1, resourcePaths.size(), "Resource type call length");
    assertTrue(resourcePaths.contains("geometrixx/components/title"), "Correct resource used");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Policy Rule list populated");

    JsonNode ri = ruleInfos.next();
    assertEquals("/apps/rules/policy/title", ri.get("title").textValue(), "Rule title");
    assertEquals("/apps/rules/policy/title", ri.get("id").textValue(), "Rule Id");

    assertFalse(ruleInfos.hasNext(), "RuleInfo length");

  }
}
