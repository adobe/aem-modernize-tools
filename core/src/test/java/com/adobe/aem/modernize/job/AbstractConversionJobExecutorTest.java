package com.adobe.aem.modernize.job;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.job.AbstractConversionJobExecutor.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class AbstractConversionJobExecutorTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);
  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @Mocked
  private JobExecutionContext.ResultBuilder resultBuilder;

  @Mocked
  private JobExecutionResult executionResult;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/job/component-job-data.json", ConversionJob.JOB_DATA_LOCATION + "/job");
  }

  private void setupResult(boolean success) {
    new Expectations() {{
      jobExecutionContext.result();
      result = resultBuilder;
      resultBuilder.message(anyString);
      result = resultBuilder;
      if (success) {
        resultBuilder.succeeded();
      } else {
        resultBuilder.cancelled();
      }
      result = executionResult;
    }};
  }

  @Test
  public void testMissingTrackingPath() {
    JobExecutor executor = new NoOpJobExecutor();
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public <R extends ResourceResolverFactory> void testLoginFails() {
    new MockUp<R>() {
      @Mock
      public ResourceResolver getServiceResourceResolver(Map<String, Object> authenticationInfo) throws LoginException {
        throw new LoginException("Login Failed.");
      }
    };
    new Expectations() {{
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = ConversionJob.JOB_DATA_LOCATION + "/job/buckets/bucket0";
    }};

    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public <R extends ResourceResolverFactory> void testTrackingNotFound() {
    new Expectations() {{
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = "/path/to/job/does/not/exist";
    }};

    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public <R extends ResourceResolver> void testCommitFails() {

    new MockUp<R>() {
      @Mock
      public void commit() throws PersistenceException {
        throw new PersistenceException("Failure");
      }

      @Mock
      public void revert() {

      }
    };
    new Expectations() {{
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = ConversionJob.JOB_DATA_LOCATION + "/job/buckets/bucket0";
      job.getId();
      result = "JobId";
      job.getProcessingStarted();
      result = Calendar.getInstance();
    }};
    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public void testSavesJobData() {
    new Expectations() {{
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = ConversionJob.JOB_DATA_LOCATION + "/job/buckets/bucket0";
      job.getId();
      result = "JobId";
      job.getProcessingStarted();
      result = Calendar.getInstance();
    }};
    setupResult(true);
    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");

    ValueMap vm = context.resourceResolver().getResource(ConversionJob.JOB_DATA_LOCATION + "/job/buckets/bucket0").getValueMap();
    assertEquals("JobId", vm.get(PN_JOB_ID, String.class), "Job Id saved");
    vm = context.resourceResolver().getResource(ConversionJob.JOB_DATA_LOCATION + "/job/buckets/bucket0").getValueMap();
    assertEquals("/content/test/first-page", vm.get("success", String[].class)[0], "Success saved");
    assertEquals("/content/test/second-page", vm.get("failed", String[].class)[0], "Failed saved");
    assertEquals("/content/test/page-not-found", vm.get("notFound", String[].class)[0], "Not Found saved");
    assertNotNull(vm.get("started", Date.class), "Bucket Start time set");
    assertNotNull(vm.get("finished", Date.class), "Bucket Finished time set");

    vm = context.resourceResolver().getResource(ConversionJob.JOB_DATA_LOCATION + "/job").getValueMap();
    assertNotNull(vm.get("finished", Date.class), "Aggregate finished time set");
  }

  @Component(service = { JobExecutor.class })
  public static class NoOpJobExecutor extends AbstractConversionJobExecutor {

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doProcess(Job job, JobExecutionContext context, ConversionJobBucket bucket) {
      bucket.getSuccess().add("/content/test/first-page");
      bucket.getFailed().add("/content/test/second-page");
      bucket.getNotFound().add("/content/test/page-not-found");
    }

    @Override
    protected ResourceResolverFactory getResourceResolverFactory() {
      return resourceResolverFactory;
    }
  }
}
