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

import com.adobe.aem.modernize.job.FullConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import static org.apache.sling.api.SlingHttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;

@ExtendWith(SlingContextExtension.class)
public class ScheduleConversionJobServletTest {

  private static final String JOB_TITLE = "Test Job";

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

  public ScheduleConversionJobServlet.JobData buildJobData() {
    List<String> list = new ArrayList<>();
    ScheduleConversionJobServlet.JobData jobData = new ScheduleConversionJobServlet.JobData();
    jobData.setName(JOB_TITLE);
    list.add("/content/test/path");
    list.add("/content/other/path");
    for (int i = 0; i <= 500; i++) {
      list.add("/content/other/path" + i);
    }
    jobData.setPaths(list.toArray(new String[]{}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/template");
    jobData.setTemplateRules(list.toArray(new String[]{}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/component");
    jobData.setComponentRules(list.toArray(new String[]{}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/policy");
    jobData.setPolicyRules(list.toArray(new String[]{}));
    return jobData;
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
    ScheduleConversionJobServlet.JobData jobData = buildJobData();
    request.setContent(new ObjectMapper().writeValueAsString(jobData).getBytes());

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
    ScheduleConversionJobServlet.JobData jobData = buildJobData();
    request.setContent(new ObjectMapper().writeValueAsString(jobData).getBytes());

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
    ScheduleConversionJobServlet.JobData jobData = buildJobData();
    request.setContent(new ObjectMapper().writeValueAsString(jobData).getBytes());
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

    assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus(), "Correct response code.");
    assertEquals("failure", result.get("status").asText(), "Correct result status.");
    assertNotNull(result.get("message").asText(), "Message was set.");
  }

  @Test
  public void testTrackingState() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = slingContext.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    ScheduleConversionJobServlet.JobData jobData = buildJobData();
    jobData.setType(ConversionJob.Type.FULL);
    request.setContent(new ObjectMapper().writeValueAsString(jobData).getBytes());
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

    assertEquals(SC_OK, response.getStatus(), "Correct response code.");
    assertEquals("success", result.get("status").asText(), "Correct result status.");
    assertNotNull(result.get("message").asText(), "Message was set.");

    Calendar today = Calendar.getInstance();
    String path = String.format("%s/%d/%d/%d/%s",
        ConversionJob.JOB_DATA_LOCATION,
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH),
        "test-job");

    assertFalse(serviceSession.isLive(), "Session was closed");
    Field f = serviceSession.getClass().getDeclaredField("isLive");
    f.setAccessible(true);
    f.set(serviceSession, true);
    assertTrue(serviceSession.nodeExists(path), "Tracking node was created.");
    assertTrue(serviceSession.nodeExists(path + "/bucket"), "Bucket 1 was created");
    assertTrue(serviceSession.nodeExists(path + "/bucket0"), "Bucket 2 was created");
    assertEquals(JOB_TITLE, serviceSession.getProperty(path + "/" + ConversionJob.PN_TITLE).getString(), "Title was set");
    assertEquals(userId, serviceSession.getProperty(path + "/" + ConversionJob.PN_INITIATOR).getString(), "Initiated by was set");
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJob.PN_TEMPLATE_RULES), "Template rules were set.");
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJob.PN_COMPONENT_RULES), "Component rules were set.");
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJob.PN_POLICY_RULES), "Policy rules were set.");
  }

  @Test
  public void testJobSchedulingFails() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = slingContext.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    ScheduleConversionJobServlet.JobData jobData = buildJobData();
    jobData.setType(ConversionJob.Type.FULL);
    request.setContent(new ObjectMapper().writeValueAsString(jobData).getBytes());
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
      jobManager.addJob(FullConversionJobExecutor.JOB_TOPIC, (Map<String, Object>) any);
      result = null;
    }};
    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus(), "Correct response code.");
    assertEquals("failure", result.get("status").asText(), "Correct result status.");
    assertNotNull(result.get("message").asText(), "Message was set");

  }

  @Test
  public void testFullJobScheduling() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = slingContext.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    List<Map<String, Object>> jobProperties = new ArrayList<>();
    ScheduleConversionJobServlet.JobData jobData = buildJobData();
    jobData.setType(ConversionJob.Type.FULL);
    request.setContent(new ObjectMapper().writeValueAsString(jobData).getBytes());

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
      jobManager.addJob(FullConversionJobExecutor.JOB_TOPIC, withCapture(jobProperties));
    }};
    servlet.doPost(request, response);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode result = mapper.readTree(response.getOutputAsString());
    assertEquals(SC_OK, response.getStatus(), "Correct response code.");
    assertEquals("success", result.get("status").asText(), "Correct result status.");
    assertNotNull(result.get("message").asText(), "Message was set.");

    assertEquals(2, jobProperties.size(), "Number of jobs created.");
    Map<String, Object> jobProps = jobProperties.get(0);
    assertNotNull(jobProps.get(PN_PATHS), "Job paths are set.");
    assertEquals(500, ((String[]) jobProps.get(PN_PATHS)).length, "Number of paths correct on first job.");
    assertNotNull(jobProps.get(ConversionJob.PN_TEMPLATE_RULES), "Job has template rules.");
    assertNotNull(jobProps.get(ConversionJob.PN_COMPONENT_RULES), "Job has component rules.");
    assertNotNull(jobProps.get(ConversionJob.PN_POLICY_RULES), "Job has policy rules.");
    assertNotNull(jobProps.get(ConversionJob.PN_TRACKING_PATH), "Job has tracking node path.");
  }
}
