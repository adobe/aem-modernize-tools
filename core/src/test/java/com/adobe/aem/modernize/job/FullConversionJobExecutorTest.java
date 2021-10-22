package com.adobe.aem.modernize.job;

import java.util.ArrayList;
import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class FullConversionJobExecutorTest {

  private static final String rootJobPath = ConversionJob.JOB_DATA_LOCATION + "/job/full";
  private static final String bucketPath = rootJobPath + "/buckets/bucket0";
  private static final int pathCount = 3;
  private static final int pageCount = 2;
  private static final String version = "987654321";

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  private FullConversionJobExecutor executor = new FullConversionJobExecutor();

  @Mocked
  private StructureRewriteRuleService structureService;

  @Mocked
  private PolicyImportRuleService policyService;

  @Mocked
  private ComponentRewriteRuleService componentService;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @Mocked
  private Resource resource;

  @BeforeEach
  public void beforeEach() {
    context.registerService(StructureRewriteRuleService.class, structureService);
    context.registerService(PolicyImportRuleService.class, policyService);
    context.registerService(ComponentRewriteRuleService.class, componentService);
    context.registerInjectActivateService(executor);
    context.load().json("/job/page-content.json", "/content/test");
    context.load().json("/job/full-job-data.json", rootJobPath);
    context.load().json("/job/component-job-data.json", ConversionJob.JOB_DATA_LOCATION + "/job/component");
  }

  @Test
  public void testPathsNotPage() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/job/component/buckets/bucket0";
    new Expectations() {{
      job.getProperty(PN_REPROCESS, false);
      result = false;
      jobExecutionContext.initProgress(pathCount * 2, -1);
    }};

    ConversionJobBucket bucket = context.resourceResolver().getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
  }

  @Test
  public <P extends PageManager> void testPreparePageFails() throws Exception {
    new Expectations() {{
      jobExecutionContext.initProgress(pathCount * 2, -1);
      job.getProperty(PN_REPROCESS, false);
      result = false;
    }};
    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String comment) throws WCMException {
        throw new WCMException("Failed");
      }
    };
    ConversionJobBucket bucket = context.resourceResolver().getResource(bucketPath).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
  }

  @Test
  public <P extends PageManager> void testPagesNotReprocess(@Mocked Revision revision) throws Exception {

    final List<String> revisions = new ArrayList<>();
    new Expectations() {{
      job.getProperty(PN_REPROCESS, false);
      result = false;
      jobExecutionContext.initProgress(pathCount * 2, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 5;
      policyService.apply(withNotNull(), withNotNull(), withNotNull(), true, false);
      times = pageCount;
      structureService.apply(withNotNull(), withNotNull());
      times = pageCount;
      componentService.apply(withNotNull(), withNotNull(), true);
      times = pageCount;
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

    ConversionJobBucket bucket = context.resourceResolver().getResource(bucketPath).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    ValueMap vm = context.resourceResolver().getResource("/content/test/first-page/jcr:content").getValueMap();
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    vm = context.resourceResolver().getResource("/content/test/second-page/jcr:content").getValueMap();
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    assertEquals(2, revisions.size(), "Revision call count.");
  }

  @Test
  public <P extends PageManager> void testPagesReprocess(@Mocked Revision revision) throws Exception {
    final List<String> revisions = new ArrayList<>();
    final List<String> restored = new ArrayList<>();
    new Expectations() {{
      job.getProperty(PN_REPROCESS, false);
      result = true;
      jobExecutionContext.initProgress(pathCount * 2, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 5;
      policyService.apply(withNotNull(), withNotNull(), withNotNull(), true, true);
      times = pageCount;
      structureService.apply(withNotNull(), withNotNull());
      times = pageCount;
      componentService.apply(withNotNull(), withNotNull(), true);
      times = pageCount;
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

    ConversionJobBucket bucket = context.resourceResolver().getResource(bucketPath).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);

    ValueMap vm = context.resourceResolver().getResource("/content/test/first-page/jcr:content").getValueMap();
    assertEquals("1234567890", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    vm = context.resourceResolver().getResource("/content/test/second-page/jcr:content").getValueMap();
    assertEquals("987654321", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));

    assertEquals(1, restored.size(), "Restore page call count");
    assertEquals(2, revisions.size(), "Revision call count");
  }

}
