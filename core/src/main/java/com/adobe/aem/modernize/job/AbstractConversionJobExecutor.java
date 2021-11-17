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
import java.util.Calendar;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;

public abstract class AbstractConversionJobExecutor implements JobExecutor {

  public static final String PN_TRACKING_PATH = "tracking";
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private static final String SERVICE_NAME = "convert-content";

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext context) {
    String trackingPath = job.getProperty(PN_TRACKING_PATH, String.class);
    if (StringUtils.isBlank(trackingPath)) {
      return context.result().message("Invalid job state, no paths specified to process.").cancelled();
    }
    ResourceResolver resourceResolver = null;
    Resource tracking = null;
    try {
      resourceResolver = login();
      tracking = resourceResolver.getResource(trackingPath);
      if (tracking == null) {
        context.log("No tracking path found for required job details.");
        logger.warn("No tracking path found for required job details.");
        return context.result().message("No tracking path found for required job details.").cancelled();
      }
      ConversionJobBucket bucket = tracking.adaptTo(ConversionJobBucket.class);
      if (bucket == null) {
        context.log("Tracking path unable to be adapted to ConversionJobBucket.");
        logger.warn("Tracking path unable to be adapted to ConversionJobBucket.");
        return context.result().message("Tracking path unable to be adapted to ConversionJobBucket.").cancelled();
      }

      updateTracking(job, tracking);
      doProcess(job, context, bucket);
      Calendar finished = Calendar.getInstance();
      updateBucket(bucket, finished);
      logFinish(tracking, finished);
      resourceResolver.commit();
      return context.result().message("Successfully processed conversion job.").succeeded();
    } catch (LoginException e) {
      context.log("Unable to log in using service user: {0}", e.getLocalizedMessage());
      logger.error("Unable to log in using service user to perform conversion", e);
      return context.result().message("Unable to log in using service user.").cancelled();
    } catch (PersistenceException e) {
      context.log("Error when trying to update the requested content.", e.getLocalizedMessage());
      logger.error("Unable to make changes to repository.", e);
      resourceResolver.revert();
      attemptTrackingUpdate(tracking);
      return context.result().message("Error when trying to save the changes.").cancelled();
    } finally {
      if (resourceResolver != null && resourceResolver.isLive()) {
        resourceResolver.close();
      }
    }
  }

  /*
    Log into the repository with the Service user.
   */
  private ResourceResolver login() throws LoginException {
    return getResourceResolverFactory().getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME));
  }

  /*
    Set this JobID onto the job/bucket details node.
   */
  private void updateTracking(Job job, Resource tracking) throws PersistenceException {
    ModifiableValueMap mvm = tracking.adaptTo(ModifiableValueMap.class);
    mvm.put(PN_JOB_ID, job.getId());
    mvm.put(PN_STARTED, job.getProcessingStarted());
    tracking.getResourceResolver().commit();
  }

  /*
    Update the bucket path processing details.
   */
  private void updateBucket(ConversionJobBucket bucket, Calendar finished) {
    ModifiableValueMap mvm = bucket.getResource().adaptTo(ModifiableValueMap.class);
    mvm.put(PN_SUCCESS, bucket.getSuccess().toArray(new String[] {}));
    mvm.put(PN_FAILED, bucket.getFailed().toArray(new String[] {}));
    mvm.put(PN_NOT_FOUND, bucket.getNotFound().toArray(new String[] {}));
    mvm.put(ConversionJobBucket.PN_FINISHED, finished);
    mvm.put(PN_JOB_STATUS, Status.SUCCESS.name());
  }

  /*
    Set the finish time on the root node.
   */
  private void logFinish(Resource tracking, Calendar finished) {
    ModifiableValueMap mvm = tracking.getParent().getParent().adaptTo(ModifiableValueMap.class);
    mvm.put(ConversionJob.PN_FINISHED, finished);
  }

  /*
    Last ditch attempt to save tracking status for job when a rollback occurs.
   */
  private void attemptTrackingUpdate(Resource tracking) {
    if (tracking != null) {
      try {
        ModifiableValueMap mvm = tracking.adaptTo(ModifiableValueMap.class);
        mvm.put(PN_JOB_STATUS, Status.FAILED.name());
        tracking.getResourceResolver().commit();
      } catch (PersistenceException e) {
        logger.error("Unable to save job status as {} on tracking node [{}}", Status.FAILED.name(), tracking.getPath());
      }
    }
  }

  @Nullable
  protected ValueMap getTrackingInfo(ConversionJobBucket bucket) {
    Resource parent = bucket.getResource().getParent();
    if (parent == null) {
      return null;
    }
    parent = parent.getParent();
    if (parent == null) {
      return null;
    }
    return parent.getValueMap();
  }

  protected boolean isOverwrite(ConversionJobBucket bucket) {
    ValueMap vm = getTrackingInfo(bucket);
    if (vm == null) {
      return false;
    }
    return Boolean.TRUE.equals(vm.get(PN_OVERWRITE, Boolean.class));
  }

  protected PageHandling getPageHandling(ConversionJobBucket bucket) {
    ValueMap vm = getTrackingInfo(bucket);
    if (vm == null) {
      return PageHandling.NONE;
    }
    try {
      return PageHandling.valueOf(vm.get(PN_PAGE_HANDLING, PageHandling.NONE.name()));
    } catch (IllegalArgumentException e) {
      return PageHandling.NONE;
    }
  }

  @Nullable
  protected String getTargetConfPath(ConversionJobBucket bucket) {
    ValueMap vm = getTrackingInfo(bucket);
    if (vm == null) {
      return null;
    }
    return vm.get(PN_CONF_PATH, String.class);
  }

  @Nullable
  protected String getSourceRoot(ConversionJobBucket bucket) {
    ValueMap vm = getTrackingInfo(bucket);
    if (vm == null) {
      return null;
    }
    return vm.get(PN_SOURCE_ROOT, String.class);
  }

  @Nullable
  protected String getTargetRoot(ConversionJobBucket bucket) {
    ValueMap vm = getTrackingInfo(bucket);
    if (vm == null) {
      return null;
    }
    return vm.get(PN_TARGET_ROOT, String.class);
  }

  @NotNull
  protected Set<String> getTemplateRules(ConversionJobBucket bucket) {
    return getRules(bucket, PN_TEMPLATE_RULES);
  }

  @NotNull
  protected Set<String> getPolicyRules(ConversionJobBucket bucket) {
    return getRules(bucket, PN_POLICY_RULES);
  }

  @NotNull
  protected Set<String> getComponentRules(ConversionJobBucket bucket) {
    return getRules(bucket, PN_COMPONENT_RULES);
  }

  private Set<String> getRules(ConversionJobBucket bucket, String type) {
    Set<String> rules = Collections.emptySet();
    Resource parent = bucket.getResource().getParent();
    if (parent != null) {
      parent = parent.getParent();
    }
    if (parent != null) {
      String[] ruleList = parent.getValueMap().get(type, String[].class);
      if (ruleList != null) {
        rules = Arrays.stream(ruleList).collect(Collectors.toSet());
      }
    }
    return rules;
  }

  protected abstract void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull ConversionJobBucket bucket);

  protected abstract ResourceResolverFactory getResourceResolverFactory();
}
