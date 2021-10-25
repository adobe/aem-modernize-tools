package com.adobe.aem.modernize.component.job;

import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentJobExecutorTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  private final ComponentJobExecutor executor = new ComponentJobExecutor();

  @Mocked
  private ComponentRewriteRuleService componentService;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  public void beforeEach() {
    context.registerService(ComponentRewriteRuleService.class, componentService);
    context.registerInjectActivateService(executor);
    context.load().json("/job/page-content.json", "/content/test");
    context.load().json("/job/component-job-data.json", "/var/aem-modernize/job-data/component");

  }

  @Test
  public void testDoProcessSuccesses() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/component/buckets/bucket0";
    new Expectations() {{
      jobExecutionContext.initProgress(3, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 3;
      componentService.apply(withInstanceOf(Resource.class), withInstanceOf(Set.class), false);
      times = 2;
    }};

    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    tracking.getResourceResolver().commit();
    assertEquals(2, bucket.getSuccess().size(), "Success count");
    assertEquals("/content/test/first-page/jcr:content/component", bucket.getSuccess().get(0), "Success path");
    assertEquals("/content/test/second-page/jcr:content/component", bucket.getSuccess().get(1), "Success path");

    assertEquals(1, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/content/test/not-found-page/jcr:content/component", bucket.getNotFound().get(0), "Not Found path");
  }

  @Test
  public void testDoProcessFailures() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/component/buckets/bucket0";
    new Expectations() {{
      jobExecutionContext.initProgress(3, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 3;
      componentService.apply(withInstanceOf(Resource.class), withInstanceOf(Set.class), false);
      result = new RewriteException("Error");
      times = 2;
    }};

    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    tracking.getResourceResolver().commit();

    assertEquals(2, bucket.getFailed().size(), "Failed count");
    assertEquals("/content/test/first-page/jcr:content/component", bucket.getFailed().get(0), "Failed path");
    assertEquals("/content/test/second-page/jcr:content/component", bucket.getFailed().get(1), "Failed path");

    assertEquals(1, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/content/test/not-found-page/jcr:content/component", bucket.getNotFound().get(0), "Not Found path");
  }
}
