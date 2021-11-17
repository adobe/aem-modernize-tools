package com.adobe.aem.modernize.job;

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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.MockStyle;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.component.impl.ComponentRewriteRuleServiceImpl;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.policy.impl.PolicyImportRuleServiceImpl;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.adobe.aem.modernize.structure.impl.StructureRewriteRuleServiceImpl;
import com.adobe.aem.modernize.structure.impl.rule.PageRewriteRule;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Invocation;
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

  private static final int pathCount = 3;
  private static final String version = "987654321";

  private final AemContext context = new AemContext(ResourceResolverType.JCR_OAK);

  private final FullConversionJobExecutor executor = new FullConversionJobExecutor();

  private StructureRewriteRule pageRewriteRule = new PageRewriteRule();

  private StructureRewriteRuleService structureService = new StructureRewriteRuleServiceImpl();

  private PolicyImportRuleService policyService = new PolicyImportRuleServiceImpl();

  private ComponentRewriteRuleService componentService = new ComponentRewriteRuleServiceImpl();

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @Mocked
  private Revision revision;

  @Mocked
  private Designer designer;

  @Mocked
  private Design design;

  @Mocked
  private Cell cell;

  @BeforeEach
  public void beforeEach() {
    context.load().json("/job/page-content.json", "/content/test");
    context.load().json("/job/design-content.json", "/etc/designs/test");
    context.load().json("/job/full-job-data.json", JOB_DATA_LOCATION + "/full");
    context.load().json("/job/component-job-data.json", ConversionJob.JOB_DATA_LOCATION + "/component");
    context.load().json("/job/test-rules.json", "/var/aem-modernize/rules");
    context.load().json("/job/conf-content.json", "/conf/test");


    Map<String, Object> props = new HashMap<>();
    props.put("static.template", "/apps/aem-modernize/template/homepage");
    props.put("sling.resourceType", "aem-modernize/components/homepage");
    props.put("editable.template", "/conf/test/settings/wcm/templates/homepage");
    props.put("container.resourceType", "aem-modernize/components/container");

    context.registerInjectActivateService(pageRewriteRule, props);
    context.registerInjectActivateService(structureService);

    props = new HashMap<>();
    props.put("search.paths", new String[] { "/var/aem-modernize/rules/policy" });
    context.registerInjectActivateService(policyService, props);

    props = new HashMap<>();
    props.put("search.paths", new String[] { "/var/aem-modernize/rules/component" });
    context.registerInjectActivateService(componentService, props);
    context.registerInjectActivateService(executor);

  }

  @Test
  public void testPathsNotPage() {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/component/buckets/bucket0";
    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
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
  public <P extends PageManager, R extends ResourceResolver> void createVersionManagementFails() {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/full/reprocess/buckets/bucket0";
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

    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
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
    assertEquals(2, failed.size(), "Failed list size");
    assertTrue(failed.contains("/content/test/first-page"), "Failed item");
    assertTrue(failed.contains("/content/test/second-page"), "Failed item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");
  }

  @Test
  public <P extends PageManager, R extends ResourceResolver> void doProcessNoReprocess() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/full/noReprocess/buckets/bucket0";
    ResourceResolver rr = context.resourceResolver();

    Resource styleRes = rr.getResource("/etc/designs/test/jcr:content/homepage/title");
    Style homepageStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/homepage/title", styleRes);

    styleRes = rr.getResource("/etc/designs/test/jcr:content/page/par/title");
    Style pageParStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/page/par/title", styleRes);


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
        String path = page.getPath().replace("/content/test", "/content/newpath/with/extra/tokens");
        assertEquals(path, dest, "New page path correct");
        return page;
      }
    };
    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
      }
    };

    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
      revision.getId();
      result = version;
      jobExecutionContext.incrementProgressCount(1);
      times = pathCount;
      designer.getStyle(withInstanceOf(Resource.class));
      result = new Delegate<Style>() {
        Style delegate(Resource resource) {
          if ("/content/test/first-page/jcr:content/title".equals(resource.getPath())) {
            return homepageStyle;
          } else if (resource.getPath().contains("jcr:content/par/title")) {
            return pageParStyle;
          }
          return null;
        }
      };
      cell.paths();
      result = new Delegate<Iterator<String>>() {
        Iterator<String> delegate() {
          return Arrays.stream(new String[] { "homepage", "page", "basepage" }).iterator();
        }
      };
      designer.getStyle(withInstanceOf(Resource.class), "homepage");
      result = new Delegate<Style>() {
        Style delegate(Resource resource, String id) {
          if ("/content/test/first-page/jcr:content/title".equals(resource.getPath())) {
            return homepageStyle;
          }
          return null;
        }
      };
      designer.getStyle(withInstanceOf(Resource.class), "page");
      result = new Delegate<Style>() {
        Style delegate(Resource resource, String id) {
          if (resource.getPath().contains("jcr:content/par/title")) {
            return pageParStyle;
          }
          return null;
        }
      };
    }};

    ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);

    rr.commit();
    List<String> success = bucket.getSuccess();
    assertEquals(2, success.size(), "Success list size");
    assertTrue(success.contains("/content/test/first-page"), "Success item");
    assertTrue(success.contains("/content/test/second-page"), "Success item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");

    Node page = rr.getResource("/content/test/first-page").adaptTo(Node.class);
    NodeIterator pageNodes = page.getNodes();
    assertEquals(NameConstants.NN_CONTENT, pageNodes.nextNode().getName(), "Content order");
    assertEquals("childPage", pageNodes.nextNode().getName(), "Content order");

    ValueMap vm = rr.getResource("/content/test/first-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class), "Page Version");
    assertEquals("aem-modernize/components/structure/homepage", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Page Sling Resource Type");
    assertEquals("/conf/test/settings/wcm/templates/homepage", vm.get(NameConstants.PN_TEMPLATE, String.class), "Page Template Type");

    vm = rr.getResource("/content/test/first-page/jcr:content/root/title").adaptTo(ValueMap.class);
    assertNull(vm.get("cq:policyPath"), "Temp property removed");


    vm = rr.getResource("/content/test/second-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals("123456789", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    assertEquals("aem-modernize/components/structure/homepage", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Page Sling Resource Type");
    assertEquals("/conf/test/settings/wcm/templates/homepage", vm.get(NameConstants.PN_TEMPLATE, String.class), "Page Template Type");


    Resource policyMapping = rr.getResource("/conf/test/settings/wcm/templates/homepage/policies/jcr:content/root/core/wcm/components/title/v2/title");
    assertNotNull(policyMapping, "Mapping created");
    vm = policyMapping.getValueMap();
    assertEquals("core/wcm/components/title/v2/title/policy", vm.get("cq:policy", String.class), "Mapping set");
    assertEquals("wcm/core/components/policies/mappings", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Mapping resource type");

    policyMapping = rr.getResource("/conf/test/settings/wcm/templates/homepage/policies/jcr:content/root/par/core/wcm/components/title/v2/title");
    assertNotNull(policyMapping, "Mapping created");
    vm = policyMapping.getValueMap();
    assertEquals("core/wcm/components/title/v2/title/policy_987654321", vm.get("cq:policy", String.class), "Mapping set");
    assertEquals("wcm/core/components/policies/mappings", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Mapping resource type");

    Resource policiesParent = rr.getResource("/conf/test/settings/wcm/policies/core/wcm/components/title/v2/title");
    Iterator<Resource> childResources = policiesParent.listChildren();
    assertTrue(childResources.hasNext(), "Created Policy count");
    childResources.next();
    assertTrue(childResources.hasNext(), "Created Policy count");
    childResources.next();
    assertFalse(childResources.hasNext(), "Created Policy count");

  }

  @Test
  public <P extends PageManager, R extends ResourceResolver> void doProcessReprocess() throws Exception {
    final String path = ConversionJob.JOB_DATA_LOCATION + "/full/reprocess/buckets/bucket0";
    ResourceResolver rr = context.resourceResolver();

    Resource styleRes = rr.getResource("/etc/designs/test/jcr:content/homepage/title");
    Style homepageStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/homepage/title", styleRes);

    styleRes = rr.getResource("/etc/designs/test/jcr:content/page/par/title");
    Style pageParStyle = new MockStyle(design, cell, "/etc/designs/test/jcr:content/page/par/title", styleRes);

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
    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == Designer.class) {
          return (T) designer;
        } else {
          return inv.proceed();
        }
      }
    };

    new Expectations() {{
      jobExecutionContext.initProgress(pathCount, -1);
      revision.getId();
      result = version;
      jobExecutionContext.incrementProgressCount(1);
      times = pathCount;
      designer.getStyle(withInstanceOf(Resource.class));
      result = new Delegate<Style>() {
        Style delegate(Resource resource) {
          if ("/content/test/first-page/jcr:content/title".equals(resource.getPath())) {
            return homepageStyle;
          } else if (resource.getPath().contains("jcr:content/par/title")) {
            return pageParStyle;
          }
          return null;
        }
      };
      cell.paths();
      result = new Delegate<Iterator<String>>() {
        Iterator<String> delegate() {
          return Arrays.stream(new String[] { "homepage", "page", "basepage" }).iterator();
        }
      };
      designer.getStyle(withInstanceOf(Resource.class), "homepage");
      result = new Delegate<Style>() {
        Style delegate(Resource resource, String id) {
          if ("/content/test/first-page/jcr:content/title".equals(resource.getPath())) {
            return homepageStyle;
          }
          return null;
        }
      };
      designer.getStyle(withInstanceOf(Resource.class), "page");
      result = new Delegate<Style>() {
        Style delegate(Resource resource, String id) {
          if (resource.getPath().contains("jcr:content/par/title")) {
            return pageParStyle;
          }
          return null;
        }
      };
    }};

    ConversionJobBucket bucket = rr.getResource(path).adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);

    rr.commit();
    List<String> success = bucket.getSuccess();
    assertEquals(2, success.size(), "Success list size");
    assertTrue(success.contains("/content/test/first-page"), "Success item");
    assertTrue(success.contains("/content/test/second-page"), "Success item");

    List<String> notFound = bucket.getNotFound();
    assertEquals(1, notFound.size(), "Not found list size");
    assertTrue(notFound.contains("/content/test/not-found-page"), "Not found item");

    ValueMap vm = rr.getResource("/content/test/first-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals(version, vm.get(PN_PRE_MODERNIZE_VERSION, String.class), "Page Version");
    assertEquals("aem-modernize/components/structure/homepage", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Page Sling Resource Type");
    assertEquals("/conf/test/settings/wcm/templates/homepage", vm.get(NameConstants.PN_TEMPLATE, String.class), "Page Template Type");

    vm = rr.getResource("/content/test/first-page/jcr:content/root/title").adaptTo(ValueMap.class);
    assertNull(vm.get("cq:policyPath"), "Temp property removed");

    vm = rr.getResource("/content/test/second-page/jcr:content").adaptTo(ValueMap.class);
    assertEquals("123456789", vm.get(PN_PRE_MODERNIZE_VERSION, String.class));
    assertEquals("aem-modernize/components/structure/homepage", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Page Sling Resource Type");
    assertEquals("/conf/test/settings/wcm/templates/homepage", vm.get(NameConstants.PN_TEMPLATE, String.class), "Page Template Type");

    // Root title policy will be new, and have index extension.
    Resource policyMapping = rr.getResource("/conf/test/settings/wcm/templates/homepage/policies/jcr:content/root/core/wcm/components/title/v2/title");
    assertNotNull(policyMapping, "Mapping created");
    vm = policyMapping.getValueMap();
    assertEquals("core/wcm/components/title/v2/title/policy0", vm.get("cq:policy", String.class), "Mapping set");
    assertEquals("wcm/core/components/policies/mappings", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Mapping resource type");


    policyMapping = rr.getResource("/conf/test/settings/wcm/templates/homepage/policies/jcr:content/root/par/core/wcm/components/title/v2/title");
    assertNotNull(policyMapping, "Mapping created");
    vm = policyMapping.getValueMap();
    assertEquals("core/wcm/components/title/v2/title/policy", vm.get("cq:policy", String.class), "Mapping set");
    assertEquals("wcm/core/components/policies/mappings", vm.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, String.class), "Mapping resource type");

    Resource policiesParent = rr.getResource("/conf/test/settings/wcm/policies/core/wcm/components/title/v2/title");
    Iterator<Resource> children = policiesParent.listChildren();
    assertTrue(children.hasNext(), "Created Policy count");
    children.next();
    assertTrue(children.hasNext(), "Created Policy count");
    children.next();
    assertTrue(children.hasNext(), "Created Policy count");
    children.next();
    assertFalse(children.hasNext(), "Created Policy count");
  }
}
