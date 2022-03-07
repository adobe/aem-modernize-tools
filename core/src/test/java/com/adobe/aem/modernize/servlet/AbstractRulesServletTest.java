package com.adobe.aem.modernize.servlet;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.RewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static javax.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class AbstractRulesServletTest {

  private static final String RULE_PATH = "/apps/rules/servlet";
  private static final String CONTENT_PATH = "/content/servlet";
  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
  
  @Mocked
  private RewriteRuleService rewriteRuleService;

  @Mocked
  private RewriteRule nodeRule;

  @Mocked
  private RewriteRule serviceRule;
  
  private final MockRulesServlet servlet = new MockRulesServlet();
  
  @BeforeEach
  public void beforeEach() {
    context.load().json("/servlet/page-content.json", CONTENT_PATH);
  }
  
  @Test
  public void getPageContentNoReprocess() {
    
    AbstractRulesServlet servlet = new MockRulesServlet();
    ResourceResolver rr = context.resourceResolver();
    Page page = rr.adaptTo(PageManager.class).getPage(CONTENT_PATH);
    Map<String, String[]> params = new HashMap<>();
    params.put(AbstractRulesServlet.PARAM_REPROCESS, new String[] { "false" });

    Resource pageContent = servlet.getPageContent(params, page);
    assertEquals(CONTENT_PATH + "/jcr:content", pageContent.getPath(), "Page Content pat");
  }
  
  @Test
  public void getPageContentReprocess() {
    
    final String path = CONTENT_PATH + "/version/jcr:content";
    
    AbstractRulesServlet servlet = new MockRulesServlet() {
      @Override
      protected @Nullable Resource getOriginalPageContent(@NotNull Page page) {
        return context.resourceResolver().getResource(path);
      }
    };
    
    ResourceResolver rr = context.resourceResolver();
    Page page = rr.adaptTo(PageManager.class).getPage(CONTENT_PATH);
    Map<String, String[]> params = new HashMap<>();
    params.put(AbstractRulesServlet.PARAM_REPROCESS, new String[] { "true" });

    Resource pageContent = servlet.getPageContent(params, page);
    assertEquals(path, pageContent.getPath(), "Page Content pat");
  }


  @Test
  public void getOriginalPageContentNoModernizeVersion() {

    final String path = CONTENT_PATH + "/products";
    ResourceResolver rr = context.resourceResolver();
    Page page = rr.adaptTo(PageManager.class).getPage(path);

    Resource resource = servlet.getOriginalPageContent(page);
    assertEquals(path + "/jcr:content", resource.getPath(), "Resource matches.");
  }
  
  @Test
  public <P extends PageManager> void getOriginalPageContentRevisionMissing(@Mocked Revision revision) {

    ResourceResolver rr = context.resourceResolver();

    new MockUp<P>() {
      @Mock
      public List<Revision> getRevisions(String path, Calendar cal) {
        assertEquals(CONTENT_PATH, path, "Revision path.");
        return Collections.singletonList(revision);
      }
    };
    new Expectations() {{
      revision.getId();
      result = 2;
    }};

    Page page = rr.adaptTo(PageManager.class).getPage(CONTENT_PATH);
    
    Resource resource = servlet.getOriginalPageContent(page);
    assertNull(resource, "Resource not found.");
  }


  @Test
  public <P extends PageManager> void getOriginalPageContentRevisionFails() {

    ResourceResolver rr = context.resourceResolver();

    new MockUp<P>() {
      @Mock
      public List<Revision> getRevisions(String path, Calendar cal) throws WCMException {
        assertEquals(CONTENT_PATH, path, "Revision path.");
        throw new WCMException("Error");
      }
    };

    Page page = rr.adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Resource resource = servlet.getOriginalPageContent(page);
    assertNull(resource, "Resource not found.");
  }


  @Test
  public <P extends PageManager> void getOriginalPageContentFrozenFails(@Mocked Revision revision, @Mocked Version version) throws Exception {

    ResourceResolver rr = context.resourceResolver();

    new MockUp<P>() {
      @Mock
      public List<Revision> getRevisions(String path, Calendar cal) {
        assertEquals(CONTENT_PATH, path, "Revision path.");
        return Collections.singletonList(revision);
      }
    };
    new Expectations() {{
      revision.getId();
      result = 1;
      revision.getVersion();
      result = version;
      version.getFrozenNode();
      result = new RepositoryException("Error");
    }};

    Page page = rr.adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Resource resource = servlet.getOriginalPageContent(page);
    assertNull(resource, "Resource not found.");
  }


  @Test
  public <P extends PageManager> void getOriginalPageContentSuccess(@Mocked Revision revision, @Mocked Version version) throws Exception {

    ResourceResolver rr = context.resourceResolver();
    final String path = CONTENT_PATH + "/version/jcr:content"; 
    Node frozen = rr.getResource(path).adaptTo(Node.class);

    new MockUp<P>() {
      @Mock
      public List<Revision> getRevisions(String path, Calendar cal) {
        assertEquals(CONTENT_PATH, path, "Revision path.");
        return Collections.singletonList(revision);
      }
    };
    new Expectations() {{
      revision.getId();
      result = 1;
      revision.getVersion();
      result = version;
      version.getFrozenNode();
      result = frozen;
    }};

    Page page = context.resourceResolver().adaptTo(PageManager.class).getPage(CONTENT_PATH);

    Resource resource = servlet.getOriginalPageContent(page);
    assertEquals(path, resource.getPath(), "Path matches.");
  }
  
  @Test
  public void invalidRequests() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    // No Paths
    final MockRulesServlet servlet = new MockRulesServlet(Collections.emptySet());
    servlet.doGet(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    
    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Rule list empty");

    // Path doesn't exist
    Map<String, Object> params = new HashMap<>();
    params.put("path", new String[] { "/does/not/exist" });
    request.setParameterMap(params);
    response.reset();
    
    servlet.doGet(request, response);
    result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Request Status");
    assertFalse(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Rule list empty");
    
  }
  
  @Test
  public void noListedPaths() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    Map<String, Object> params = new HashMap<>();
    params.put("path", CONTENT_PATH);
    request.setParameterMap(params);
    response.reset();

    final MockRulesServlet servlet = new MockRulesServlet(Collections.emptySet());
    servlet.doGet(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_OK, response.getStatus(), "Request Status");
    assertTrue(result.get("success").booleanValue(), "Success Status");
    assertNotNull(result.get("message").textValue(), "Message present");
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertFalse(ruleInfos.hasNext(), "Rule list empty");
  }
  
  @Test
  public void ruleInfoReturned() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    
    final MockRulesServlet servlet = new MockRulesServlet(Collections.singleton(CONTENT_PATH + "/jcr:content/simple"));
    
    Map<String, Object> params = new HashMap<>();
    params.put("path", CONTENT_PATH);
    request.setParameterMap(params);

    Resource resource = context.resourceResolver().getResource(CONTENT_PATH + "/jcr:content/simple");

    final String rulePath = "/path/to/rewrite/rule";
    final String servicePid = "ServicePid";
    Set<String> ids = new HashSet<>();
    ids.add(rulePath);
    ids.add(servicePid);


    new Expectations() {{
      rewriteRuleService.listRules(with(new Delegate<Resource>() {
        void delegate(Resource r) {
          assertEquals(resource.getPath(), r.getPath(), "API call expectation.");
        }
      }));
      result = ids;
      rewriteRuleService.getRule(withInstanceOf(ResourceResolver.class), rulePath);
      result = nodeRule;
      rewriteRuleService.getRule(withInstanceOf(ResourceResolver.class), servicePid);
      result = serviceRule;

      nodeRule.getId();
      result = rulePath;
      nodeRule.getTitle();
      result = rulePath;
      serviceRule.getId();
      result = servicePid;
      serviceRule.getTitle();
      result = servicePid;
    }};

    
    servlet.doGet(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    Iterator<JsonNode> paths = result.get("paths").elements();
    assertTrue(paths.hasNext(), "Path list populated");
    assertEquals(resource.getPath(), paths.next().textValue());
    
    
    Iterator<JsonNode> ruleInfos = result.get("rules").elements();
    assertTrue(ruleInfos.hasNext(), "Rule list populated");

    JsonNode ri = ruleInfos.next();
    if (rulePath.equals(ri.get("title").textValue())) {
      assertEquals(rulePath, ri.get("title").textValue());
      assertEquals(rulePath, ri.get("id").textValue());
    } else {
      assertEquals(servicePid, ri.get("title").textValue());
      assertEquals(servicePid, ri.get("id").textValue());
    }

    ri = ruleInfos.next();
    if (rulePath.equals(ri.get("title").textValue())) {
      assertEquals(rulePath, ri.get("title").textValue());
      assertEquals(rulePath, ri.get("id").textValue());
    } else {
      assertEquals(servicePid, ri.get("title").textValue());
      assertEquals(servicePid, ri.get("id").textValue());
    }
    assertFalse(ruleInfos.hasNext(), "Rule Info size.");
  }

  @Test
  public void listRulesNoMatching() {
    final MockRulesServlet servlet = new MockRulesServlet(Collections.emptySet());
    Resource resource = context.resourceResolver().getResource(CONTENT_PATH + "/jcr:content/simple");

    new Expectations() {{
      rewriteRuleService.listRules(resource);
      result = Collections.emptySet();
    }};

    Set<RuleInfo> results = servlet.listRules(resource);
    assertTrue(results.isEmpty(), "Rule Info list content.");
  }

  @Test
  public void listRulesSuccess() {
    final MockRulesServlet servlet = new MockRulesServlet(Collections.emptySet());
    Resource resource = context.resourceResolver().getResource(CONTENT_PATH + "/jcr:content/simple");
   
    final String rulePath = "/path/to/rewrite/rule";
    final String servicePid = "ServicePid";
    Set<String> ids = new HashSet<>();
    ids.add(rulePath);
    ids.add(servicePid);

    new Expectations() {{
      rewriteRuleService.listRules(resource);
      result = ids;
      rewriteRuleService.getRule(withInstanceOf(ResourceResolver.class), rulePath);
      result = nodeRule;
      rewriteRuleService.getRule(withInstanceOf(ResourceResolver.class), servicePid);
      result = serviceRule;

      nodeRule.getId();
      result = rulePath;
      nodeRule.getTitle();
      result = rulePath;
      serviceRule.getId();
      result = servicePid;
      serviceRule.getTitle();
      result = servicePid;
    }};

    Set<RuleInfo> results = servlet.listRules(resource);
    assertEquals(2, results.size(), "Rule Info list content.");
    assertTrue(results.contains(new RuleInfo(rulePath, rulePath)), "Node rule in results");
    assertTrue(results.contains(new RuleInfo(servicePid, servicePid)), "Service rule in results");
  }


  private class MockRulesServlet extends AbstractRulesServlet {
    
    private final Set<String> paths;
    
    public MockRulesServlet() {
      paths = Collections.emptySet();
    }
    
    public MockRulesServlet(Set<String> paths) {
      this.paths = paths;
    }
    
    @Override
    @NotNull
    protected Set<String> listPaths(@NotNull Map<String, String[]> requestParameters, @NotNull Page page) {
      return paths;
    }

    @Override
    @NotNull
    protected RewriteRuleService getRewriteRuleService() {
      return rewriteRuleService;
    }
  }
}
