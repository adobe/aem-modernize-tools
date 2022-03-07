package com.adobe.aem.modernize.policy.job;

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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class PolicyJobExecutorTest {
  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  private final String CONF_PATH = "/conf/test";

  private final PolicyJobExecutor executor = new PolicyJobExecutor();
  @Mocked
  private PolicyImportRuleService policyService;

  @Mocked
  private Job job;

  @Mocked
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  public void beforeEach() {
    context.registerService(PolicyImportRuleService.class, policyService);
    context.registerInjectActivateService(executor);
    context.load().json("/job/design-content.json", "/etc/designs/test");
    context.load().json("/job/policy-job-data.json", "/var/aem-modernize/job-data/policy");
  }

  @Test
  public <R extends ResourceResolver> void testProcessSuccessesNoOverwrite() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/policy/donotoverwrite/buckets/bucket0";

    new Expectations() {{
      jobExecutionContext.initProgress(3, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 3;
      policyService.apply(withInstanceOf(Resource.class), CONF_PATH, withInstanceOf(Set.class), false);
      returns(true, false);
      times = 2;
    }};

    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    tracking.getResourceResolver().commit();

    assertEquals(1, bucket.getSuccess().size(), "Success count");
    assertEquals("/etc/designs/test/jcr:content/homepage/par", bucket.getSuccess().get(0), "Success path");

    assertEquals(2, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/etc/designs/test/jcr:content/homepage/title", bucket.getNotFound().get(0), "Found, no rule");
    assertEquals("/etc/designs/test/jcr:content/homepage/component-not-found", bucket.getNotFound().get(1), "Not Found path");

  }

  @Test
  public <R extends ResourceResolver> void testProcessSuccessesOverwrite() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/policy/overwrite/buckets/bucket0";

    new Expectations() {{
      jobExecutionContext.initProgress(3, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 3;
      policyService.apply(withInstanceOf(Resource.class), CONF_PATH, withInstanceOf(Set.class), true);
      result = true;
      times = 2;
    }};

    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    tracking.getResourceResolver().commit();

    assertEquals(2, bucket.getSuccess().size(), "Success count");
    assertEquals("/etc/designs/test/jcr:content/homepage/par", bucket.getSuccess().get(0), "Success path");
    assertEquals("/etc/designs/test/jcr:content/homepage/title", bucket.getSuccess().get(1), "Success path");

    assertEquals(1, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/etc/designs/test/jcr:content/homepage/component-not-found", bucket.getNotFound().get(0), "Not Found path");

  }

  @Test
  public <R extends ResourceResolver> void testDoProcessFailures() throws Exception {
    final String jobPath = "/var/aem-modernize/job-data/policy/overwrite/buckets/bucket0";

    new Expectations() {{
      jobExecutionContext.initProgress(3, -1);
      jobExecutionContext.incrementProgressCount(1);
      times = 3;
      policyService.apply(withInstanceOf(Resource.class), CONF_PATH, withInstanceOf(Set.class), true);
      result = new RewriteException("Error");
      times = 2;
    }};

    Resource tracking = context.resourceResolver().getResource(jobPath);
    ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);
    executor.doProcess(job, jobExecutionContext, bucket);
    tracking.getResourceResolver().commit();

    assertEquals(2, bucket.getFailed().size(), "Failure count");
    assertEquals("/etc/designs/test/jcr:content/homepage/par", bucket.getFailed().get(0), "Failed path");
    assertEquals("/etc/designs/test/jcr:content/homepage/title", bucket.getFailed().get(1), "Failed path");

    assertEquals(1, bucket.getNotFound().size(), "NotFound count");
    assertEquals("/etc/designs/test/jcr:content/homepage/component-not-found", bucket.getNotFound().get(0), "Not Found path");
  }
}
