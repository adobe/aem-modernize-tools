package com.adobe.aem.modernize.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.model.ConversionJobItem.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class FullConversionJobExecutorTest {

  private static String[] paths;
  private static String[] templateRules;
  private static String[] componentRules;
  private static String[] policyRules;

  private static final String version = "987654321";

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  private FullConversionJobExecutor executor  = new FullConversionJobExecutor();

  @Mocked
  private StructureRewriteRuleService structureService;

  @Mocked
  private PoliciesImportRuleService policyService;

  @Mocked
  private ComponentRewriteRuleService componentService;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @Mocked
  private JobExecutionContext.ResultBuilder resultBuilder;

  @Mocked
  private JobExecutionResult executionResult;

  @Mocked
  private Resource resource;

  @BeforeAll
  public static void beforeAll() {
    paths = new String[]{ "/content/test/first-page", "/content/test/second-page" };
    templateRules = new String[]{ "/path/to/first/template/rule", "/path/to/second/template/rule" };
    componentRules = new String[]{ "/path/to/first/component/rule", "/path/to/second/component/rule" };
    policyRules = new String[]{ "/path/to/first/policy/rule", "/path/to/second/policy/rule" };

  }

  @BeforeEach
  public void before() {
    context.registerService(StructureRewriteRuleService.class, structureService);
    context.registerService(PoliciesImportRuleService.class, policyService);
    context.registerService(ComponentRewriteRuleService.class, componentService);
    context.registerInjectActivateService(executor);
    context.load().json("/job/executor-first-page.json", "/content/test/first-page");
    context.load().json("/job/executor-second-page.json", "/content/test/second-page");
    context.load().json("/job/job-data.json", JOB_DATA_LOCATION + "/job");
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
      job.getProperty(PN_PAGE_PATHS, String[].class);
      result = paths;
    }};
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
    };
    setupResult(false);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public void testPathsNotPage() {
    new Expectations() {{
      job.getProperty(PN_PAGE_PATHS, String[].class);
      result = new String[]{ "/content/test/first-page/jcr:content/component", "/content/test/second-page/jcr:content/component" };
      job.getProperty(PN_REPROCESS, false);
      result = false;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";

      jobExecutionContext.initProgress(paths.length * 2, -1);
    }};
    setupResult(false);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");

    ValueMap vm = context.resourceResolver().getResource(JOB_DATA_LOCATION + "/job").getValueMap();
    assertEquals("JobId", vm.get(PN_JOB_ID, String.class), "Job Id saved");

  }

  @Test
  public <P extends PageManager> void testPreparePageFails() {
    new Expectations() {{
      jobExecutionContext.initProgress(paths.length * 2, -1);
      job.getProperty(PN_PAGE_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_REPROCESS, false);
      result = false;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";
    }};
    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String comment) throws WCMException {
        throw new WCMException("Failed");
      }
    };
    setupResult(false);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
  }

  @Test
  public <P extends PageManager> void testPagesNotReprocess(@Mocked Revision revision) throws Exception {

    final List<String> revisions = new ArrayList<>();
    new Expectations() {{
      job.getProperty(PN_PAGE_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_TEMPLATE_RULES, String[].class);
      result = templateRules;
      job.getProperty(PN_COMPONENT_RULES, String[].class);
      result = componentRules;
      job.getProperty(PN_POLICY_RULES, String[].class);
      result = policyRules;
      job.getProperty(PN_REPROCESS, false);
      result = false;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";
      jobExecutionContext.initProgress(paths.length * 2, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = paths.length * 2;
      policyService.apply(withNotNull(), policyRules, true, false);
      times = paths.length;
      structureService.apply(withNotNull(), templateRules);
      times = paths.length;
      componentService.apply(withNotNull(), componentRules, true);
      times = paths.length;
      revision.getId();
      result = version;
    }};
    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String comment) throws WCMException {
        revisions.add(page.getPath());
          return revision;
      }
    };
    setupResult(true);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");
    ValueMap vm = context.resourceResolver().getResource(JOB_DATA_LOCATION + "/job").getValueMap();
    assertEquals("JobId", vm.get(PN_JOB_ID, String.class), "Job Id saved");
    for (String path : paths) {
      vm = context.resourceResolver().getResource(path + "/jcr:content").getValueMap();
      assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    }
    assertEquals(paths.length, revisions.size(), "Revision call count.");
  }

  @Test
  public <P extends PageManager> void testPagesReprocess(@Mocked Revision revision) throws Exception {
    final List<String> revisions = new ArrayList<>();
    final List<String> restored = new ArrayList<>();
    new Expectations() {{
      job.getProperty(PN_PAGE_PATHS, String[].class);
      result = paths;
      job.getProperty(PN_TEMPLATE_RULES, String[].class);
      result = templateRules;
      job.getProperty(PN_COMPONENT_RULES, String[].class);
      result = componentRules;
      job.getProperty(PN_POLICY_RULES, String[].class);
      result = policyRules;
      job.getProperty(PN_REPROCESS, false);
      result = true;
      job.getProperty(PN_TRACKING_PATH, String.class);
      result = JOB_DATA_LOCATION + "/job";
      job.getId();
      result = "JobId";
      jobExecutionContext.initProgress(paths.length * 2, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = paths.length * 2;
      policyService.apply(withNotNull(), policyRules, true, true);
      times = paths.length;
      structureService.apply(withNotNull(), templateRules);
      times = paths.length;
      componentService.apply(withNotNull(), componentRules, true);
      times = paths.length;
      revision.getId();
      result = version;
    }};
    new MockUp<P>() {

      @Mock
      public Page restore(String path, String version) throws WCMException {
        restored.add(path);
        return context.resourceResolver().getResource(path).adaptTo(Page.class);
      }

      @Mock
      public Revision createRevision(Page page, String label, String comment) throws WCMException {
        revisions.add(page.getPath());
        return revision;
      }
    };
    setupResult(true);

    assertEquals(executionResult, executor.process(job, jobExecutionContext), "Result was created.");

    ValueMap vm = context.resourceResolver().getResource(JOB_DATA_LOCATION + "/job").getValueMap();
    assertEquals("JobId", vm.get(PN_JOB_ID, String.class), "Job Id saved");
    vm = context.resourceResolver().getResource("/content/test/first-page/jcr:content").getValueMap();
    assertEquals("1234567890", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    vm = context.resourceResolver().getResource("/content/test/second-page/jcr:content").getValueMap();
    assertEquals("987654321", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));

    assertEquals(1, restored.size(), "Restore page call count");
    assertEquals(paths.length, revisions.size(), "Revision call count");
  }

}
