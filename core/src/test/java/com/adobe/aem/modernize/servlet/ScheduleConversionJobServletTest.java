package com.adobe.aem.modernize.servlet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import javax.jcr.LoginException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.job.ConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJobItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import static org.apache.sling.api.SlingHttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ScheduleConversionJobServletTest {

  private static final String JOB_TITLE = "Test Job";
  private static String jobData;

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_MOCK);
  @Mocked
  private BundleContext bundleContext;

  @Mocked
  private ResourceResolver resourceResolver;

  @Mocked
  private Session session;

  @Mocked
  private AccessControlManager accessControlManager;

  @Injectable
  private SlingRepository slingRepository;

  @Injectable
  private JobManager jobManager;

  @Tested
  private ScheduleConversionJobServlet servlet;

  @BeforeAll
  public static void beforeAll() throws Exception {
    ScheduleConversionJobServlet.JobData data = new ScheduleConversionJobServlet.JobData();
    List<String> list = new ArrayList<>();

    data.setName(JOB_TITLE);
    list.add("/content/test/path");
    list.add("/content/other/path");
    for (int i = 0; i <= 500; i++) {
      list.add("/content/other/path" + i);
    }
    data.setPaths(list.toArray(new String[]{}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/template");
    data.setTemplateRules(list.toArray(new String[]{}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/component");
    data.setComponentRules(list.toArray(new String[]{}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/policy");
    data.setPolicyRules(list.toArray(new String[]{}));

    jobData = new ObjectMapper().writeValueAsString(data);
  }

  @BeforeEach
  public void before() throws Exception {
  }

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_BAD_REQUEST, response.getStatus());
    assertEquals("failure", result.get("status").asText());
    assertNotNull(result.get("message").asText());
  }

  @Test
  public void noPermissionsSinglePath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    request.setContent(jobData.getBytes());

    new Expectations() {{
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = false;
    }};

    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_FORBIDDEN, response.getStatus());
    assertEquals("failure", result.get("status").asText());
    assertNotNull(result.get("message").asText());
  }

  @Test
  public void noPermissionsMultiplePaths() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    request.setContent(jobData.getBytes());

    new Expectations() {{
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      times = 2;
      returns(true, false);
    }};

    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_FORBIDDEN, response.getStatus());
    assertEquals("failure", result.get("status").asText());
    assertNotNull(result.get("message").asText());
  }

  @Test
  public void testUnableToLoginService() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    request.setContent(jobData.getBytes());
    new Expectations() {{
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
      slingRepository.loginService(anyString, null);
      result = new LoginException("Error");
    }};

    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());

    assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    assertEquals("failure", result.get("status").asText());
    assertNotNull(result.get("message").asText());
  }

  @Test
  public void testTrackingState() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = slingContext.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    request.setContent(jobData.getBytes());
    new Expectations() {{
      resourceResolver.getUserID();
      result = userId;
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
      slingRepository.loginService(anyString, null);
      result = serviceSession;
    }};
    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    assertEquals(SC_OK, response.getStatus());
    assertEquals("success", result.get("status").asText());
    assertNotNull(result.get("message").asText());

    Calendar today = Calendar.getInstance();
    String path = String.format("%s/%d/%d/%d/%s",
        ConversionJobExecutor.JOB_DATA_LOCATION,
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH),
        "test-job");

    assertFalse(serviceSession.isLive());
    Field f = serviceSession.getClass().getDeclaredField("isLive");
    f.setAccessible(true);
    f.set(serviceSession, true);
    assertTrue(serviceSession.nodeExists(path));
    assertTrue(serviceSession.nodeExists(path + "/bucket"));
    assertTrue(serviceSession.nodeExists(path + "/bucket0"));
    assertEquals(JOB_TITLE, serviceSession.getProperty(path + "/" + ConversionJobItem.PN_TITLE).getString());
    assertEquals(userId, serviceSession.getProperty(path + "/" + ConversionJobItem.PN_INITIATOR).getString());
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJobItem.PN_TEMPLATE_RULES));
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJobItem.PN_COMPONENT_RULES));
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJobItem.PN_POLICY_RULES));
  }

  @Test
  public void testJobSchedulingFails() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = slingContext.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    List<Map<String, Object>> jobProperties = new ArrayList<>();

    request.setContent(jobData.getBytes());
    new Expectations() {{
      resourceResolver.getUserID();
      result = userId;
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
      slingRepository.loginService(anyString, null);
      result = serviceSession;
      jobManager.addJob(ConversionJobExecutor.JOB_TOPIC, (Map<String, Object>) any);
      result = null;
    }};
    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus());
    assertEquals("failure", result.get("status").asText());
    assertNotNull(result.get("message").asText());

  }

  @Test
  public void testJobScheduling() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = slingContext.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    List<Map<String, Object>> jobProperties = new ArrayList<>();

    request.setContent(jobData.getBytes());
    new Expectations() {{
      resourceResolver.getUserID();
      result = userId;
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
      slingRepository.loginService(anyString, null);
      result = serviceSession;
      jobManager.addJob(ConversionJobExecutor.JOB_TOPIC, withCapture(jobProperties));
    }};
    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    assertEquals(SC_OK, response.getStatus());
    assertEquals("success", result.get("status").asText());
    assertNotNull(result.get("message").asText());

    assertEquals(2, jobProperties.size());
    Map<String, Object> jobProps = jobProperties.get(0);
    assertNotNull(jobProps.get(ConversionJobItem.PN_PAGE_PATHS));
    assertEquals(500, ((String[]) jobProps.get(ConversionJobItem.PN_PAGE_PATHS)).length);
    assertNotNull(jobProps.get(ConversionJobItem.PN_TEMPLATE_RULES));
    assertNotNull(jobProps.get(ConversionJobItem.PN_COMPONENT_RULES));
    assertNotNull(jobProps.get(ConversionJobItem.PN_POLICY_RULES));
    assertNotNull(jobProps.get(ConversionJobItem.PN_TRACKING_PATH));
  }
}
