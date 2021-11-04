package com.adobe.aem.modernize.structure.job;

import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
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
public class PageStructureJobExecutorTest {

  private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
  private static final int pathCount = 3;
  private static final int pageCount = 2;
  private static final String version = "987654321";

  private final PageStructureJobExecutor executor = new PageStructureJobExecutor();

  @Mocked
  private StructureRewriteRuleService structureService;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @Mocked
  private Revision revision;

  @BeforeEach
  public void beforeEach() {
    context.registerService(StructureRewriteRuleService.class, structureService);
    context.registerInjectActivateService(executor);
    context.load().json("/job/page-content.json", "/content/test");
    context.load().json("/job/structure-job-data.json", ConversionJob.JOB_DATA_LOCATION + "/structure");
    context.load().json("/job/component-job-data.json", ConversionJob.JOB_DATA_LOCATION + "/component");
  }

  @Test
  public void pathsNotPage() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/component/buckets/bucket0";
    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
    }};

    ResourceResolver rr =  context.resourceResolver();
    ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    List<String> notFound = bucket.getNotFound();
    assertEquals(3, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/first-page/jcr:content/component"), "Not found item");
    assertTrue(notFound.contains("/content/test/second-page/jcr:content/component"), "Not found item");
    assertTrue(notFound.contains("/content/test/not-found-page/jcr:content/component"), "Not found item");
  }

  @Test
  public <P extends PageManager> void createVersionManagementFails() {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/structure/reprocess/buckets/bucket0";

    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String desc) throws WCMException {
        throw new WCMException("Exception");
      }
      @Mock
      public Page restore(String path, String id) throws WCMException {
        throw new WCMException("Exception");
      }
    };

    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = pathCount;
    }};

    ConversionJobBucket bucket = context.resourceResolver().getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    List<String> failed = bucket.getFailed();
    assertEquals(2, failed.size(), "Failed list size");
    assertTrue(failed.contains("/content/test/first-page"), "Failed item");
    assertTrue(failed.contains("/content/test/second-page"), "Failed item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");
  }

  @Test
  public <P extends PageManager> void doProcessNoReprocess() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/structure/noReprocess/buckets/bucket0";

    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String desc) {
        assertNotNull(page);
        assertNotNull(label);
        assertNotNull(desc);
        return revision;
      }
    };

    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = pathCount;
      structureService.apply(withInstanceOf(Page.class), withInstanceOf(Set.class));
      times = pageCount;
      revision.getId();
      result = version;
    }};

    ConversionJobBucket bucket = context.resourceResolver().getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    List<String> success = bucket.getSuccess();
    assertEquals(2, success.size(), "Success list size");
    assertTrue(success.contains("/content/test/first-page"), "Success item");
    assertTrue(success.contains("/content/test/second-page"), "Success item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");

    ValueMap vm = context.resourceResolver().getResource("/content/test/first-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    vm = context.resourceResolver().getResource("/content/test/second-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals("123456789", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
  }

  @Test
  public <P extends PageManager> void doProcessReprocess() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/structure/reprocess/buckets/bucket0";

    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String desc) {
        assertNotNull(page);
        assertNotNull(label);
        assertNotNull(desc);
        return revision;
      }
      @Mock
      public Page restore(String path, String id) throws RepositoryException {
        Node node = context.resourceResolver().getResource(path + "/jcr:content").adaptTo(Node.class);
        if (node.hasProperty(PN_PRE_MODERNIZE_VERSION)) {
          node.getProperty(PN_PRE_MODERNIZE_VERSION).remove();
        }
        return context.resourceResolver().getResource(path).adaptTo(Page.class);
      }
      @Mock
      public Page copy(Page page, String dest, String before, boolean shallow, boolean resolve, boolean commit) {
        return page;
      }
    };

    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = pathCount;
      structureService.apply(withInstanceOf(Page.class), withInstanceOf(Set.class));
      times = pageCount;
      revision.getId();
      result = version;
    }};

    ConversionJobBucket bucket = context.resourceResolver().getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    List<String> success = bucket.getSuccess();
    assertEquals(2, success.size(), "Success list size");
    assertTrue(success.contains("/content/test/first-page"), "Success item");
    assertTrue(success.contains("/content/test/second-page"), "Success item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");

    ValueMap vm = context.resourceResolver().getResource("/content/test/first-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    vm = context.resourceResolver().getResource("/content/test/second-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals("123456789", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
  }


  @Test
  public <P extends PageManager> void doProcessTargetPath() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/structure/reprocess/buckets/bucket0";

    new MockUp<P>() {
      @Mock
      public Revision createRevision(Page page, String label, String desc) {
        assertNotNull(page);
        assertNotNull(label);
        assertNotNull(desc);
        return revision;
      }
      @Mock
      public Page restore(String path, String id) throws RepositoryException {
        Node node = context.resourceResolver().getResource(path + "/jcr:content").adaptTo(Node.class);
        if (node.hasProperty(PN_PRE_MODERNIZE_VERSION)) {
          node.getProperty(PN_PRE_MODERNIZE_VERSION).remove();
        }
        return context.resourceResolver().getResource(path).adaptTo(Page.class);
      }
      @Mock
      public Page copy(Page page, String dest, String before, boolean shallow, boolean resolve, boolean commit) {
        return page;
      }
    };

    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = pathCount;
      structureService.apply(withInstanceOf(Page.class), withInstanceOf(Set.class));
      times = pageCount;
      revision.getId();
      result = version;
    }};

    ConversionJobBucket bucket = context.resourceResolver().getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    List<String> success = bucket.getSuccess();
    assertEquals(2, success.size(), "Success list size");
    assertTrue(success.contains("/content/test/first-page"), "Success item");
    assertTrue(success.contains("/content/test/second-page"), "Success item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");

    ValueMap vm = context.resourceResolver().getResource("/content/test/first-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    vm = context.resourceResolver().getResource("/content/test/second-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals("123456789", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
  }

}
