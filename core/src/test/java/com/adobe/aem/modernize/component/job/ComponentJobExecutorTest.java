package com.adobe.aem.modernize.component.job;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentJobExecutorTest {

  private static String[] paths;
  private static String[] componentRules;

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  private ComponentJobExecutor executor = new ComponentJobExecutor();

  @Mocked
  private ComponentRewriteRuleService componentService;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @BeforeAll
  public static void beforeAll() {
    paths = new String[] { "/content/test/first-page/jcr:content/component", "/content/test/second-page/jcr:content/component" };
    componentRules = new String[] { "/path/to/first/component/rule", "/path/to/second/component/rule" };
  }

  @BeforeEach
  public void beforeEach() {
    context.registerService(ComponentRewriteRuleService.class, componentService);
    context.registerInjectActivateService(executor);
    context.load().json("/job/executor-first-page.json", "/content/test/first-page");
    context.load().json("/job/executor-second-page.json", "/content/test/second-page");

  }

  @Test
  public void testDoProcess() throws Exception {
    new Expectations() {{
      job.getProperty(PN_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_COMPONENT_RULES, String[].class);
      result = componentRules;
      jobExecutionContext.initProgress(paths.length, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = paths.length;
      componentService.apply(withInstanceOf(Resource.class), componentRules, false);
      times = paths.length;
    }};

    executor.doProcess(job, jobExecutionContext, context.resourceResolver());
  }
}
