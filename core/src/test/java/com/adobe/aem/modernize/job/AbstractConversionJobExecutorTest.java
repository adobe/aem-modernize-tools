package com.adobe.aem.modernize.job;

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

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.model.ConversionJob;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class AbstractConversionJobExecutorTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);
  private static String[] paths;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @Mocked
  private JobExecutionContext.ResultBuilder resultBuilder;

  @Mocked
  private JobExecutionResult executionResult;

  @BeforeAll
  public static void beforeAll() {
    paths = new String[] { "/content/test/first-page", "/content/test/second-page" };
  }

  @BeforeEach
  public void beforeEach() {
    context.load().json("/job/job-data.json", ConversionJob.JOB_DATA_LOCATION + "/job");
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
        resultBuilder.failed();
      }
      result = executionResult;
    }};
  }

  @Test
  public void testMissingJobData() {
    JobExecutor executor = new NoOpJobExecutor();
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public <R extends ResourceResolverFactory> void testLoginFails() throws Exception {

    new MockUp<R>() {
      @Mock
      public ResourceResolver getServiceResourceResolver(Map<String, Object> authenticationInfo) throws LoginException {
        throw new LoginException("Login Failed.");
      }
    };
    new Expectations() {{
      job.getProperty(PN_PATHS, String[].class);
      result = paths;
    }};

    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public <R extends ResourceResolver> void testCommitFails() throws Exception {
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
      job.getProperty(PN_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = ConversionJob.JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";
    }};
    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);
    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public void testSavesJobData() {
    new Expectations() {{
      job.getProperty(PN_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = ConversionJob.JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";
    }};
    setupResult(true);
    JobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");

    ValueMap vm = context.resourceResolver().getResource(ConversionJob.JOB_DATA_LOCATION + "/job").getValueMap();
    assertEquals("JobId", vm.get(PN_JOB_ID, String.class), "Job Id saved");
  }

  @Test
  public <R extends ResourceResolver> void testDoProcessException() throws Exception {
    NoOpJobExecutor executor = new NoOpJobExecutor();
    context.registerInjectActivateService(executor);

    new MockUp<R>() {
      @Mock
      public void revert() {}
    };

    new Expectations(executor) {{
      job.getProperty(PN_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = ConversionJob.JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";
      executor.doProcess(job, jobExecutionContext, withInstanceOf(ResourceResolver.class));
      result = new RewriteException("Exception");
    }};

    setupResult(false);
    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Component(service = { JobExecutor.class })
  public static class NoOpJobExecutor extends AbstractConversionJobExecutor {

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doProcess(Job job, JobExecutionContext context, ResourceResolver resourceResolver) throws RewriteException, PersistenceException {}

    @Override
    protected ResourceResolverFactory getResourceResolverFactory() {
      return resourceResolverFactory;
    }
  }
}
