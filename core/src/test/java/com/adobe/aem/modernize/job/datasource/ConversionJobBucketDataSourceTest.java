package com.adobe.aem.modernize.job.datasource;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.model.ConversionJob.Status.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ConversionJobBucketDataSourceTest {

  private static final String jobViewPath = "/apps/aem-modernize/job/view";
  private static String jobDataPath;
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);
  public ConversionJobBucketDataSource dataSource = new ConversionJobBucketDataSource();

  @Mocked
  private ExpressionResolver expressionResolver;
  @Mocked
  private JobManager jobManager;


  @BeforeAll
  public static void beforeAll() {
    Calendar today = Calendar.getInstance();
    jobDataPath = String.format("%s/%s/%d/%d/%d",
        ConversionJob.JOB_DATA_LOCATION,
        ConversionJob.Type.FULL.name().toLowerCase(),
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH));
  }

  @BeforeEach
  public void beforeEach() {
    context.load().json("/job/datasource/test-component-jobs.json", jobDataPath);
    context.load().json("/job/datasource/test-job-detail-view.json", jobViewPath);
    context.registerService(ExpressionResolver.class, expressionResolver);
    context.registerService(JobManager.class, jobManager);

    context.registerInjectActivateService(dataSource);
  }

  @Test
  public void testNoPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(jobViewPath + "/missingPath");
    request.setResource(resource);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
  }

  @Test
  public void testPathNotFound() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(jobViewPath + "/valid");
    request.setResource(resource);
    MockRequestPathInfo rpi = (MockRequestPathInfo) request.getRequestPathInfo();
    rpi.setSuffix("/path/does/not/exist");
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
  }

  @Test
  public void testBucket() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(jobViewPath + "/valid");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);
    MockRequestPathInfo rpi = (MockRequestPathInfo) request.getRequestPathInfo();
    rpi.setSuffix(jobDataPath + "/geodemo-components1");

    new Expectations() {{
      expressionResolver.resolve("${requestPathInfo.suffix}", Locale.ENGLISH, String.class, request);
      result = jobDataPath + "/geodemo-components1";
      expressionResolver.resolve("${requestPathInfo.selectors[0]}", Locale.ENGLISH, Integer.class, request);
      result = 5;
      expressionResolver.resolve("${empty requestPathInfo.selectors[1] ? &quot;51&quot; : requestPathInfo.selectors[1]}", Locale.ENGLISH, Integer.class, request);
      result = 5;
    }};

    Map<String, Object> params = new HashMap<>();
    params.put("bucket", 0);
    request.setParameterMap(params);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());

    Iterator<Resource> it = ds.iterator();
    ValueMap vm = it.next().adaptTo(ValueMap.class);
    assertEquals("/content/geometrixx/en/jcr:content/parsys/title_2", vm.get("path", String.class), "Path set");
    assertEquals("Success", vm.get("status", String.class), "Status set");
    assertEquals("success", vm.get("statusClass", String.class), "Status class set");
    assertEquals("checkCircle", vm.get("icon", String.class), "Icon set");

    vm = it.next().adaptTo(ValueMap.class);
    assertEquals("/content/geometrixx/en/jcr:content/parsys/text_0", vm.get("path", String.class), "Path set");
    assertEquals("Success", vm.get("status", String.class), "Status set");
    assertEquals("success", vm.get("statusClass", String.class), "Status class set");
    assertEquals("checkCircle", vm.get("icon", String.class), "Icon set");

    vm = it.next().adaptTo(ValueMap.class);
    assertEquals("/content/geometrixx/en/jcr:content/lead", vm.get("path", String.class), "Path set");
    assertEquals("Success", vm.get("status", String.class), "Status set");
    assertEquals("success", vm.get("statusClass", String.class), "Status class set");
    assertEquals("checkCircle", vm.get("icon", String.class), "Icon set");

    vm = it.next().adaptTo(ValueMap.class);
    assertEquals("/content/geometrixx/en/jcr:content/image", vm.get("path", String.class), "Path set");
    assertEquals("Failed", vm.get("status", String.class), "Status set");
    assertEquals("error", vm.get("statusClass", String.class), "Status class set");
    assertEquals("closeCircle", vm.get("icon", String.class), "Icon set");

    assertFalse(it.hasNext(), "List empty");
  }

  @Test
  public void testBucket2() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(jobViewPath + "/valid");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);
    MockRequestPathInfo rpi = (MockRequestPathInfo) request.getRequestPathInfo();
    rpi.setSuffix(jobDataPath + "/geodemo-components1");

    new Expectations() {{
      expressionResolver.resolve("${requestPathInfo.suffix}", Locale.ENGLISH, String.class, request);
      result = jobDataPath + "/geodemo-components1";
      expressionResolver.resolve("${requestPathInfo.selectors[0]}", Locale.ENGLISH, Integer.class, request);
      result = 0;
      expressionResolver.resolve("${empty requestPathInfo.selectors[1] ? &quot;51&quot; : requestPathInfo.selectors[1]}", Locale.ENGLISH, Integer.class, request);
      result = 1;
    }};

    Map<String, Object> params = new HashMap<>();
    params.put("bucket", 0);
    request.setParameterMap(params);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());

    Iterator<Resource> it = ds.iterator();
    ValueMap vm = it.next().adaptTo(ValueMap.class);
    assertEquals("/content/geometrixx/en/jcr:content/header", vm.get("path", String.class), "Path set");
    assertEquals("Not Found", vm.get("status", String.class), "Status set");
    assertEquals("warn", vm.get("statusClass", String.class), "Status class set");
    assertEquals("alert", vm.get("icon", String.class), "Icon set");

    assertFalse(it.hasNext(), "List empty");
  }
}
