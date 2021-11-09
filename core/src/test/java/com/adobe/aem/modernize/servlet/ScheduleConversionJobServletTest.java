package com.adobe.aem.modernize.servlet;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jcr.LoginException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.job.FullConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
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

@ExtendWith(SlingContextExtension.class)
public class ScheduleConversionJobServletTest {

  private static final String JOB_TITLE = "Test Job";

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);
  @Mocked
  private BundleContext bundleContext;

  @Mocked
  private ResourceResolver resourceResolver;

  @Mocked
  private PageManager pageManager;

  @Mocked
  private Designer designer;

  @Mocked
  private Session session;

  @Mocked
  private AccessControlManager accessControlManager;

  @Mocked
  private Page page;

  @Injectable
  private SlingRepository slingRepository;

  @Injectable
  private JobManager jobManager;

  @Tested
  private ScheduleConversionJobServlet servlet;

  public ScheduleConversionJobServlet.RequestData buildJobData() {
    List<String> list = new ArrayList<>();
    ScheduleConversionJobServlet.RequestData requestData = new ScheduleConversionJobServlet.RequestData();
    requestData.setName(JOB_TITLE);
    list.add("/content/test/path");
    list.add("/content/other/path");
    for (int i = 0; i <= 500; i++) {
      list.add("/content/other/path" + i);
    }
    requestData.setPaths(list.toArray(new String[] {}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/template");
    requestData.setTemplateRules(list.toArray(new String[] {}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/component");
    requestData.setComponentRules(list.toArray(new String[] {}));

    list = new ArrayList<>();
    list.add("/apps/site/rules/policy");
    requestData.setPolicyRules(list.toArray(new String[] {}));
    return requestData;
  }

  @Test
  public void invalidJobData() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    servlet.doPost(request, response);
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Response status code");
    assertFalse(result.isSuccess(), "Response status");
    assertNotNull(result.getMessage(), "Response message");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }

  @Test
  public void noPermissionsSinglePath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new Expectations() {{
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = false;
    }};

    servlet.doPost(request, response);
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_FORBIDDEN, response.getStatus(), "Response status code");
    assertFalse(result.isSuccess(), "Response status");
    assertNotNull(result.getMessage(), "Response message");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }

  @Test
  public void noPermissionsMultiplePaths() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

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
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_FORBIDDEN, response.getStatus(), "Response status code");
    assertFalse(result.isSuccess(), "Response status");
    assertNotNull(result.getMessage(), "Response message");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }

  @Test
  public  void noPermissionDesign() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = new ScheduleConversionJobServlet.RequestData();

    List<String> list = new ArrayList<>();
    requestData.setName(JOB_TITLE);
    list.add("/content/test/path");
    list.add("/content/other/path");
    requestData.setPaths(list.toArray(new String[] {}));

    list = new ArrayList<>();
    list.add("/var/aem-modernize/rules/policy/title");
    requestData.setPolicyRules(list.toArray( new String[]{}));

    requestData.setConfPath("/conf/test");

    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);


    new Expectations() {{
      resourceResolver.adaptTo(PageManager.class);
      result = pageManager;
      pageManager.getPage(withInstanceOf(String.class));
      result = page;
      resourceResolver.adaptTo(Designer.class);
      result = designer;
      designer.getDesignPath(page);
      returns("/etc/designs/test/path", "/etc/designs/other/path");
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges("/content/test/path", withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/content/other/path", withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/etc/designs/test/path", withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/etc/designs/other/path", withInstanceOf(Privilege[].class));
      result = false;
    }};
    servlet.doPost(request, response);
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_FORBIDDEN, response.getStatus(), "Response status code");
    assertFalse(result.isSuccess(), "Response status");
    assertNotNull(result.getMessage(), "Response message");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }

  @Test
  public void noPermissionConf() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = new ScheduleConversionJobServlet.RequestData();
    List<String> list = new ArrayList<>();
    requestData.setName(JOB_TITLE);
    list.add("/content/test/path");
    list.add("/content/other/path");
    requestData.setPaths(list.toArray(new String[] {}));
    requestData.setConfPath("/conf/test");

    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new Expectations() {{
      resourceResolver.adaptTo(Session.class);
      result = session;
      session.getAccessControlManager();
      result = accessControlManager;
      accessControlManager.hasPrivileges("/content/test/path", withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/content/other/path", withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/conf/test", withInstanceOf(Privilege[].class));
      result = false;
    }};

    servlet.doPost(request, response);
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_FORBIDDEN, response.getStatus(), "Response status code");
    assertFalse(result.isSuccess(), "Response status");
    assertNotNull(result.getMessage(), "Response message");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }


  @Test
  public void testUnableToLoginService() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

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
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus(), "Correct response code.");
    assertFalse(result.isSuccess(), "Correct result status.");
    assertNotNull(result.getMessage(), "Message was set.");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }

  @Test
  public void testTrackingState() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = context.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    requestData.setType(ConversionJob.Type.FULL);
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);
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
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    Calendar today = Calendar.getInstance();
    String path = String.format("%s/%s/%s/%s",
        ConversionJob.JOB_DATA_LOCATION,
        ConversionJob.Type.FULL.name().toLowerCase(),
        new SimpleDateFormat("yyyy/MM/dd").format(today.getTime()),
        "test-job");

    assertEquals(SC_OK, response.getStatus(), "Correct response code.");
    assertTrue(result.isSuccess(), "Correct result status.");
    assertNotNull(result.getMessage(), "Message was set.");
    assertEquals(path, result.getJob(), "Tracking path");

    assertFalse(serviceSession.isLive(), "Session was closed");
    Field f = serviceSession.getClass().getDeclaredField("isLive");
    f.setAccessible(true);
    f.set(serviceSession, true);
    assertTrue(serviceSession.nodeExists(path), "Tracking node was created.");
    assertTrue(serviceSession.nodeExists(path + "/buckets/bucket"), "Bucket 1 was created");
    assertTrue(serviceSession.nodeExists(path + "/buckets/bucket0"), "Bucket 2 was created");
    assertEquals(ConversionJob.RESOURCE_TYPE, serviceSession.getProperty(path + "/" + JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource type was set");
    assertEquals(JOB_TITLE, serviceSession.getProperty(path + "/" + ConversionJob.PN_TITLE).getString(), "Title was set");
    assertEquals(userId, serviceSession.getProperty(path + "/" + ConversionJob.PN_INITIATOR).getString(), "Initiated by was set");
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJob.PN_TEMPLATE_RULES), "Template rules were set.");
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJob.PN_COMPONENT_RULES), "Component rules were set.");
    assertTrue(serviceSession.propertyExists(path + "/" + ConversionJob.PN_POLICY_RULES), "Policy rules were set.");

    assertEquals(ConversionJobBucket.RESOURCE_TYPE, serviceSession.getProperty(path + "/buckets/bucket/" + JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Bucket Resource type was set");
    assertEquals(ConversionJobBucket.RESOURCE_TYPE, serviceSession.getProperty(path + "/buckets/bucket0/" + JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Bucket Resource type was set");
  }

  @Test
  public void testJobSchedulingFails() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = context.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    requestData.setType(ConversionJob.Type.FULL);
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);
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
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);
    assertEquals(SC_INTERNAL_SERVER_ERROR, response.getStatus(), "Correct response code.");
    assertFalse(result.isSuccess(), "Correct result status.");
    assertNotNull(result.getMessage(), "Message was set");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");

  }

  @Test
  public void testFullJobScheduling() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver, bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = context.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    List<Map<String, Object>> jobProperties = new ArrayList<>();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    requestData.setType(ConversionJob.Type.FULL);
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

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

    Calendar today = Calendar.getInstance();
    String path = String.format("%s/%s/%s/%s",
        ConversionJob.JOB_DATA_LOCATION,
        ConversionJob.Type.FULL.name().toLowerCase(),
        new SimpleDateFormat("yyyy/MM/dd").format(today.getTime()),
        "test-job");

    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_OK, response.getStatus(), "Correct response code.");
    assertTrue(result.isSuccess(), "Correct result status.");
    assertNotNull(result.getMessage(), "Message was set.");
    assertEquals(path, result.getJob(), "Tracking path");

    assertEquals(2, jobProperties.size(), "Number of jobs created.");
    Map<String, Object> jobProps = jobProperties.get(0);
    assertEquals(path + "/buckets/bucket", jobProps.get(AbstractConversionJobExecutor.PN_TRACKING_PATH).toString(), "Job has tracking node path.");
  }
}
