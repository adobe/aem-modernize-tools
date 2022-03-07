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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.job.FullConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.designer.Designer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import static com.adobe.aem.modernize.model.ConversionJob.PageHandling.*;
import static org.apache.sling.api.SlingHttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class ScheduleConversionJobServletTest {

  private static final String JOB_TITLE = "Test Job";

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
  @Mocked
  private BundleContext bundleContext;

  @Mocked
  private PageManager pageManager;

  @Mocked
  private Designer designer;

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
    
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    servlet.doPost(request, response);
    ScheduleConversionJobServlet.ResponseData result = new ObjectMapper().readValue(response.getOutputAsString(), ScheduleConversionJobServlet.ResponseData.class);

    assertEquals(SC_BAD_REQUEST, response.getStatus(), "Response status code");
    assertFalse(result.isSuccess(), "Response status");
    assertNotNull(result.getMessage(), "Response message");
    assertTrue(StringUtils.isBlank(result.getJob()), "Tracking path");
  }

  @Test
  public <S extends Session> void noPermissionsJobDataPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };

    new Expectations() {{
      accessControlManager.hasPrivileges(ConversionJob.JOB_DATA_LOCATION, withInstanceOf(Privilege[].class));
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
  public <S extends Session> void noPermissionsSinglePath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };

    new Expectations() {{
      accessControlManager.hasPrivileges(ConversionJob.JOB_DATA_LOCATION, withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/content/test/path", withInstanceOf(Privilege[].class));
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
  public <S extends Session> void noPermissionsMultiplePaths() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };
    
    new Expectations() {{
      accessControlManager.hasPrivileges(ConversionJob.JOB_DATA_LOCATION, withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/content/test/path", withInstanceOf(Privilege[].class));
      result = true;
      accessControlManager.hasPrivileges("/content/other/path", withInstanceOf(Privilege[].class));
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
  public <S extends Session, R extends ResourceResolver> void noPermissionDesign() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
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

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };

    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == PageManager.class) {
          return (T) pageManager;
        } else if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
      }
    };

    new Expectations() {{
      pageManager.getPage(withInstanceOf(String.class));
      result = page;
      designer.getDesignPath(page);
      returns("/etc/designs/test/path", "/etc/designs/other/path");
      accessControlManager.hasPrivileges(ConversionJob.JOB_DATA_LOCATION, withInstanceOf(Privilege[].class));
      result = true;
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
  public <S extends Session> void noPermissionConf() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
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

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };
    
    new Expectations() {{
      accessControlManager.hasPrivileges(ConversionJob.JOB_DATA_LOCATION, withInstanceOf(Privilege[].class));
      result = true;
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
  public <S extends Session> void testTrackingState() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    String userId = "admin";

    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    requestData.setType(ConversionJob.Type.FULL);
    requestData.setPageHandling(RESTORE);
    requestData.setSourceRoot("/content/test/source");
    requestData.setTargetRoot("/content/test/target");
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };
  
    new Expectations() {{
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
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

    Session session = context.resourceResolver().adaptTo(Session.class);
    
    assertTrue(session.nodeExists(path), "Tracking node was created.");
    assertTrue(session.nodeExists(path + "/buckets/bucket"), "Bucket 1 was created");
    assertTrue(session.nodeExists(path + "/buckets/bucket0"), "Bucket 2 was created");
    assertEquals(ConversionJob.RESOURCE_TYPE, session.getProperty(path + "/" + JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Resource type was set");
    assertEquals(JOB_TITLE, session.getProperty(path + "/" + ConversionJob.PN_TITLE).getString(), "Title was set");
    assertEquals(RESTORE.name(), session.getProperty(path + "/" + ConversionJob.PN_PAGE_HANDLING).getString(), "Page handling was set");
    assertEquals("/content/test/source", session.getProperty(path + "/" + ConversionJob.PN_SOURCE_ROOT).getString(), "Source copy path set");
    assertEquals("/content/test/target", session.getProperty(path + "/" + ConversionJob.PN_TARGET_ROOT).getString(), "Target copy path set");
    assertEquals(userId, session.getProperty(path + "/" + ConversionJob.PN_INITIATOR).getString(), "Initiated by was set");
    assertTrue(session.propertyExists(path + "/" + ConversionJob.PN_TEMPLATE_RULES), "Template rules were set.");
    assertTrue(session.propertyExists(path + "/" + ConversionJob.PN_COMPONENT_RULES), "Component rules were set.");
    assertTrue(session.propertyExists(path + "/" + ConversionJob.PN_POLICY_RULES), "Policy rules were set.");

    assertEquals(ConversionJobBucket.RESOURCE_TYPE, session.getProperty(path + "/buckets/bucket/" + JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Bucket Resource type was set");
    assertEquals(ConversionJobBucket.RESOURCE_TYPE, session.getProperty(path + "/buckets/bucket0/" + JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString(), "Bucket Resource type was set");
  }

  @Test
  public <S extends Session> void testJobSchedulingFails() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = context.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    requestData.setType(ConversionJob.Type.FULL);
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };
    
    new Expectations() {{
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
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
  public <S extends Session> void testFullJobScheduling() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), bundleContext);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
    Session serviceSession = context.resourceResolver().adaptTo(Session.class);
    String userId = "TestUser";

    List<Map<String, Object>> jobProperties = new ArrayList<>();
    ScheduleConversionJobServlet.RequestData requestData = buildJobData();
    requestData.setType(ConversionJob.Type.FULL);
    requestData.setPageHandling(RESTORE);
    Map<String, Object> params = new HashMap<>();
    params.put("data", new ObjectMapper().writeValueAsString(requestData));
    request.setParameterMap(params);

    new MockUp<S>() {
      @Mock
      public AccessControlManager getAccessControlManager() {
        return accessControlManager;
      }
    };

    new Expectations() {{
      accessControlManager.hasPrivileges(anyString, withInstanceOf(Privilege[].class));
      result = true;
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
