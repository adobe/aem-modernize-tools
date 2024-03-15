package com.adobe.aem.modernize.job;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.component.impl.ComponentRewriteRuleServiceImpl;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.*;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.*;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.*;

import static com.adobe.aem.modernize.model.ConversionJob.JOB_DATA_LOCATION;
import static com.adobe.aem.modernize.model.ConversionJob.PN_PRE_MODERNIZE_VERSION;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
public class FormConversionJobExecutorTest {
    private static final int pathCount = 2;
    private static final String version = "987654321";
    private final AemContext context = new AemContext(ResourceResolverType.JCR_OAK);
    private final FormConversionJobExecutor executor = new FormConversionJobExecutor();
    private final ComponentRewriteRuleService componentService = new ComponentRewriteRuleServiceImpl();

    @Mocked
    private Job job;

    @Mocked
    private JobExecutionContext jobExecutionContext;

    @Mocked
    private Revision revision;

    @BeforeEach
    public void beforeEach() {
        context.load().json("/job/initial-form-content.json", "/content/forms/af/sourcefolder");
        context.load().json("/job/initial-form-asset-content.json", "/content/dam/formsanddocuments/sourcefolder/initialform");
        context.create().resource("/content/dam/formsanddocuments/targetfolder");
        context.load().json("/job/form-job-data.json", JOB_DATA_LOCATION + "/form");
        context.load().json("/job/component-job-data.json", ConversionJob.JOB_DATA_LOCATION + "/component");
        context.load().json("/job/test-form-rules.json", "/var/aem-modernize/rules");


        Map<String, Object> props = new HashMap<>();
        props.put("search.paths", new String[] { "/var/aem-modernize/rules" });
        context.registerInjectActivateService(componentService, props);
        context.registerInjectActivateService(executor);
    }

    @Test
    public void testPathsNotForm() {
        final String path = ConversionJob.JOB_DATA_LOCATION + "/component/buckets/bucket0";
        new Expectations() {{
            jobExecutionContext.initProgress(3, -1);
        }};
        ResourceResolver rr = context.resourceResolver();
        ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
        executor.doProcess(job, jobExecutionContext, bucket);
        List<String> notFound = bucket.getNotFound();
        assertEquals(3, notFound.size(), "Not found list size");
        assertTrue(notFound.contains("/content/test/first-page/jcr:content/component"), "Not found item");
        assertTrue(notFound.contains("/content/test/second-page/jcr:content/component"), "Not found item");
        assertTrue(notFound.contains("/content/test/not-found-page/jcr:content/component"), "Not found item");
    }

    @Test
    public void testSourceRootMissing() throws RepositoryException {
        final String path = ConversionJob.JOB_DATA_LOCATION + "/form/noReprocess/buckets/bucket0";
        new Expectations() {{
            jobExecutionContext.initProgress(pathCount, -1);
        }};
        ResourceResolver rr = context.resourceResolver();
        Node n = rr.getResource(ConversionJob.JOB_DATA_LOCATION + "/form/noReprocess").adaptTo(Node.class);
        n.setProperty("sourceRoot", "");
        ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
        executor.doProcess(job, jobExecutionContext, bucket);
        List<String> failed = bucket.getFailed();
        assertEquals(1, failed.size(), "Failed list size");
        assertTrue(failed.contains("/content/forms/af/sourcefolder/initialform"), "Failed item");

        List<String> notFound = bucket.getNotFound();
        assertEquals(1, notFound.size(), "Not found list size");
        assertTrue(notFound.contains("/content/forms/af/not-found-form"), "Not found item");
    }

    @Test
    public <P extends PageManager> void createVersionManagementFails() {
        final String path = ConversionJob.JOB_DATA_LOCATION + "/form/noReprocess/buckets/bucket0";
        ResourceResolver rr = context.resourceResolver();

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

        ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
        executor.doProcess(job, jobExecutionContext, bucket);
        List<String> failed = bucket.getFailed();
        assertEquals(1, failed.size(), "Failed list size");
        assertTrue(failed.contains("/content/forms/af/sourcefolder/initialform"), "Failed item");

        List<String> notFound = bucket.getNotFound();
        assertEquals(1, notFound.size(), "Not found list size");
        assertTrue(notFound.contains("/content/forms/af/not-found-form"), "Not found item");
    }

    @Test
    public <P extends PageManager> void testWhenFormCopyFails() {
        final String path = ConversionJob.JOB_DATA_LOCATION + "/form/noReprocess/buckets/bucket0";
        ResourceResolver rr = context.resourceResolver();

        new MockUp<P>() {
            @Mock
            public Revision createRevision(Page page, String label, String desc) {
                assertNotNull(page);
                assertNotNull(label);
                assertNotNull(desc);
                return revision;
            }

            @Mock
            public Page copy(Page page, String dest, String before, boolean shallow,
                             boolean resolve, boolean commit) throws RewriteException {
                throw new RewriteException("Exception");
            }
        };

        new Expectations() {{
            jobExecutionContext.initProgress(pathCount, -1);
            revision.getId();
            result = version;
            jobExecutionContext.incrementProgressCount(1);
            times = pathCount;
        }};

        ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
        executor.doProcess(job, jobExecutionContext, bucket);
        List<String> failed = bucket.getFailed();
        assertEquals(1, failed.size(), "Failed list size");
        assertTrue(failed.contains("/content/forms/af/sourcefolder/initialform"), "Failed item");

        List<String> notFound = bucket.getNotFound();
        assertEquals(1, notFound.size(), "Not found list size");
        assertTrue(notFound.contains("/content/forms/af/not-found-form"), "Not found item");
    }

    @Test
    public <P extends PageManager> void doProcessNoReprocess() throws Exception {
        final String path = ConversionJob.JOB_DATA_LOCATION + "/form/noReprocess/buckets/bucket0";
        ResourceResolver rr = context.resourceResolver();

        new MockUp<P>() {
            @Mock
            public Revision createRevision(Page page, String label, String desc) {
                assertNotNull(page);
                assertNotNull(label);
                assertNotNull(desc);
                return revision;
            }
            @Mock
            public Page copy(Page page, String dest, String before, boolean shallow, boolean resolve, boolean commit) {
                String path = page.getPath().replace("/content/forms/af/sourcefolder", "/content/forms/af/targetfolder");
                assertEquals(path, dest, "New form path correct");
                return page;
            }
        };

        new Expectations() {{
            jobExecutionContext.initProgress(pathCount, -1);
            revision.getId();
            result = version;
            jobExecutionContext.incrementProgressCount(1);
            times = pathCount;
        }};

        ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
        executor.doProcess(job, jobExecutionContext, bucket);

        rr.commit();
        List<String> success = bucket.getSuccess();
        assertEquals(1, success.size(), "Success list size");
        assertTrue(success.contains("/content/forms/af/sourcefolder/initialform"), "Success item");

        List<String> notFound = bucket.getNotFound();
        assertEquals(1, notFound.size(), "Not found list size");
        assertTrue(notFound.contains("/content/forms/af/not-found-form"), "Not found item");

        Node page = rr.getResource("/content/forms/af/sourcefolder/initialform").adaptTo(Node.class);
        NodeIterator pageNodes = page.getNodes();
        assertEquals(NameConstants.NN_CONTENT, pageNodes.nextNode().getName(), "Content order");

        ValueMap vm = rr.getResource("/content/forms/af/sourcefolder/initialform/jcr:content").adaptTo(ValueMap.class);
        assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));

        vm = rr.getResource("/content/forms/af/sourcefolder/initialform/jcr:content/guideContainer/rootPanel/items/guidetextbox")
                .adaptTo(ValueMap.class);
        assertNull(vm.get("mandatory"), "Temp property removed");
        assertEquals(true, vm.get("required", Boolean.class));
        assertEquals("text-input", vm.get("fieldType", String.class));
        assertEquals("aem-modernize/components/form/textinput",
                vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Text Input Sling Resource Type");

        Node formDamAssetNode = rr.getResource("/content/dam/formsanddocuments/targetfolder").adaptTo(Node.class);
        assertTrue(formDamAssetNode.hasNode("initialform"));
        vm = rr.getResource("/content/dam/formsanddocuments/targetfolder/initialform/jcr:content/metadata").adaptTo(ValueMap.class);
        assertEquals("testFormConversion", vm.get("title"));
    }

    @Test
    public <P extends PageManager> void doProcessReprocess() throws Exception {
        final String path = ConversionJob.JOB_DATA_LOCATION + "/form/reprocess/buckets/bucket0";
        ResourceResolver rr = context.resourceResolver();

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
                Node node = rr.getResource(path + "/jcr:content").adaptTo(Node.class);
                if (node.hasProperty(PN_PRE_MODERNIZE_VERSION)) {
                    node.getProperty(PN_PRE_MODERNIZE_VERSION).remove();
                }
                return rr.getResource(path).adaptTo(Page.class);
            }
        };

        new Expectations() {{
            jobExecutionContext.initProgress(pathCount, -1);
            revision.getId();
            result = version;
            jobExecutionContext.incrementProgressCount(1);
            times = pathCount;
        }};

        ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
        executor.doProcess(job, jobExecutionContext, bucket);

        rr.commit();
        List<String> success = bucket.getSuccess();
        assertEquals(1, success.size(), "Success list size");
        assertTrue(success.contains("/content/forms/af/sourcefolder/initialform"), "Success item");

        List<String> notFound = bucket.getNotFound();
        assertEquals(1, notFound.size(), "Not found list size");
        assertTrue(notFound.contains("/content/forms/af/not-found-form"), "Not found item");

        Node page = rr.getResource("/content/forms/af/sourcefolder/initialform").adaptTo(Node.class);
        NodeIterator pageNodes = page.getNodes();
        assertEquals(NameConstants.NN_CONTENT, pageNodes.nextNode().getName(), "Content order");

        ValueMap vm = rr.getResource("/content/forms/af/sourcefolder/initialform/jcr:content").adaptTo(ValueMap.class);
        assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class));

        vm = rr.getResource("/content/forms/af/sourcefolder/initialform/jcr:content/guideContainer/rootPanel/items/guidetextbox")
                .adaptTo(ValueMap.class);
        assertNull(vm.get("mandatory"), "Temp property removed");
        assertEquals(true, vm.get("required", Boolean.class));
        assertEquals("text-input", vm.get("fieldType", String.class));
        assertEquals("aem-modernize/components/form/textinput",
                vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Text Input Sling Resource Type");
    }
}
