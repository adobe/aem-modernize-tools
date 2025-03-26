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

import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.component.job.ComponentJobExecutor.*;

@Component(
    service = { JobExecutor.class },
    property = {
        JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC
    }
)
public abstract class ComponentJobExecutor extends AbstractConversionJobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/component";

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  void processPathsWithRules(
          @NotNull JobExecutionContext context,
          @NotNull ConversionJobBucket bucket,
          @NotNull Set<String> rules) {

    Resource rootResource = bucket.getResource();
    ResourceResolver resolver = rootResource.getResourceResolver();
    List<String> paths = bucket.getPaths();

    for (String path : paths) {
      try {
        Resource resource = resolver.getResource(path);

        if (resource == null) {
          context.log("Resource not found: " + path);
          bucket.getNotFound().add(path);
          continue;
        }

        // Apply rules to the resource
        boolean applied = componentService.apply(resource, rules);

        if (applied) {
          context.log("Successfully converted: " + path);
          bucket.getSuccess().add(path);
        } else {
          context.log("No matching rules applied to: " + path);
          bucket.getNotFound().add(path);
        }

      } catch (RewriteException e) {
        String errorMessage = "Component conversion error for " + path + ": " + e.getMessage();
        logger.error(errorMessage, e);
        context.log(errorMessage);
        bucket.getFailed().add(path);
      } catch (Exception e) {
        // Catch any unexpected exceptions to prevent job failure
        String errorMessage = "Unexpected error processing " + path + ": " + e.getMessage();
        logger.error(errorMessage, e);
        context.log(errorMessage);
        bucket.getFailed().add(path);
      } finally {
        context.incrementProgressCount(1);
      }
    }
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }

  protected abstract void logCompletionSummary(JobExecutionContext context, ConversionJobBucket bucket);

}
