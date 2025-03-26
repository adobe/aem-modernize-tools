package com.adobe.aem.modernize.component.job;

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

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
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
import mockit.Tested;
import mockit.Injectable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentJobExecutorTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  @Mocked
  private ComponentRewriteRuleService componentService;

  @Mocked
  private ResourceResolverFactory resourceResolverFactory;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  // Create a test subclass that exposes protected methods for testing
  private abstract class TestComponentJobExecutor extends ComponentJobExecutor {
    public void testDoProcess(Job job, JobExecutionContext context, ConversionJobBucket bucket) {
      doProcess(job, context, bucket);
    }

    // Method to access the protected fields
    public ComponentRewriteRuleService getComponentService() {
      return componentService;
    }

    // Override to avoid dependency on ResourceResolverFactory in tests
    @Override
    protected ResourceResolverFactory getResourceResolverFactory() {
      return resourceResolverFactory;
    }

    // Expose the processPathsWithRules method for testing
    // Fixed - removed 'super' keyword since this method is defined in ComponentJobExecutor
    public void testProcessPathsWithRules(
            JobExecutionContext context,
            ConversionJobBucket bucket,
            Set<String> rules) {
      processPathsWithRules(context, bucket, rules);
    }

    // Expose the logCompletionSummary method for testing
    // Fixed - removed 'super' keyword since this method is defined in ComponentJobExecutor
    public void testLogCompletionSummary(
            JobExecutionContext context,
            ConversionJobBucket bucket) {
      logCompletionSummary(context, bucket);
    }
  }

  private TestComponentJobExecutor testExecutor;

  @BeforeEach
  public void beforeEach() {
    context.registerService(ComponentRewriteRuleService.class, componentService);

    // Create and register our test executor
    testExecutor = new TestComponentJobExecutor() {
      @Override
      protected void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull ConversionJobBucket bucket) {

      }

      @Override
      protected void logCompletionSummary(JobExecutionContext context, ConversionJobBucket bucket) {

      }
    };
    context.registerInjectActivateService(testExecutor);

    // Load test resources
    context.load().json("/job/page-content.json", "/content/test");
    context.load().json("/job/component-job-data.json", "/var/aem-modernize/job-data/component");
  }

  /**
   * Test for successful component processing
   */
  @Test
  public void testProcessPathsWithRulesSuccesses() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/component/buckets/bucket0";
    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);

    // Define the rules to test with
    Set<String> rules = new HashSet<>(Arrays.asList("rule1", "rule2"));

    new Expectations() {{
      // Progress reporting expectations
      jobExecutionContext.log(withAny(""));
      minTimes = 0;

      jobExecutionContext.incrementProgressCount(1);
      times = 3;

      // Service call expectations
      componentService.apply(withInstanceOf(Resource.class), withInstanceOf(Set.class));
      returns(true, false);
      times = 2;
    }};

    // Call the processPathsWithRules method directly now through our test subclass
    testExecutor.testProcessPathsWithRules(jobExecutionContext, bucket, rules);

    tracking.getResourceResolver().commit();

    // Verify results
    assertEquals(1, bucket.getSuccess().size(), "Success count");
    assertEquals("/content/test/first-page/jcr:content/component", bucket.getSuccess().get(0), "Success path");

    assertEquals(2, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/content/test/second-page/jcr:content/component", bucket.getNotFound().get(0), "Found No rule path");
    assertEquals("/content/test/not-found-page/jcr:content/component", bucket.getNotFound().get(1), "Not Found path");
  }

  /**
   * Test for component processing with failures
   */
  @Test
  public void testProcessPathsWithRulesFailures() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/component/buckets/bucket0";
    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);

    // Define the rules to test with
    Set<String> rules = new HashSet<>(Arrays.asList("rule1", "rule2"));

    new Expectations() {{
      // Progress reporting expectations
      jobExecutionContext.log(withAny(""));
      minTimes = 0;

      jobExecutionContext.incrementProgressCount(1);
      times = 3;

      // Service call expectations - throw exception
      componentService.apply(withInstanceOf(Resource.class), withInstanceOf(Set.class));
      result = new RewriteException("Error");
      times = 2;
    }};

    // Call the processPathsWithRules method directly now through our test subclass
    testExecutor.testProcessPathsWithRules(jobExecutionContext, bucket, rules);

    tracking.getResourceResolver().commit();

    // Verify results
    assertEquals(2, bucket.getFailed().size(), "Failed count");
    assertEquals("/content/test/first-page/jcr:content/component", bucket.getFailed().get(0), "Failed path");
    assertEquals("/content/test/second-page/jcr:content/component", bucket.getFailed().get(1), "Failed path");

    assertEquals(1, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/content/test/not-found-page/jcr:content/component", bucket.getNotFound().get(0), "Not Found path");
  }

  /**
   * Test for completion summary logging
   */
  @Test
  public void testLogCompletionSummary() throws Exception {
    // Prepare a bucket with some data
    final String jobPath = "/var/aem-modernize/job-data/component/buckets/bucket0";
    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);

    // Add test data to the bucket
    bucket.getSuccess().add("/path/to/success");
    bucket.getFailed().add("/path/to/failure");
    bucket.getNotFound().add("/path/to/not/found");

    new Expectations() {{
      // Expect summary logs
      jobExecutionContext.log("Component conversion completed: 1 successful, 1 failed, 1 not found");
      jobExecutionContext.log("Failed paths: /path/to/failure");
    }};

    // Call the logCompletionSummary method directly
    testExecutor.testLogCompletionSummary(jobExecutionContext, bucket);
  }

  /**
   * Test the full doProcess method (integration test)
   */
  @Test
  public void testDoProcess() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/component/buckets/bucket0";
    new Expectations() {{
      // Progress initialization and reporting
      jobExecutionContext.log(withAny("Starting component conversion job with 3 paths"));
      jobExecutionContext.initProgress(3, -1);

      // Allow any logging
      jobExecutionContext.log(withAny(""));
      minTimes = 0;

      // Progress increments
      jobExecutionContext.incrementProgressCount(1);
      times = 3;

      // Service calls
      componentService.apply(withInstanceOf(Resource.class), withInstanceOf(Set.class));
      returns(true, false);
      times = 2;
    }};

    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);

    // Call doProcess through our test subclass
    testExecutor.testDoProcess(job, jobExecutionContext, bucket);

    tracking.getResourceResolver().commit();

    // Verify results
    assertEquals(1, bucket.getSuccess().size(), "Success count");
    assertEquals("/content/test/first-page/jcr:content/component", bucket.getSuccess().get(0), "Success path");

    assertEquals(2, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/content/test/second-page/jcr:content/component", bucket.getNotFound().get(0), "Found No rule path");
    assertEquals("/content/test/not-found-page/jcr:content/component", bucket.getNotFound().get(1), "Not Found path");
  }
}