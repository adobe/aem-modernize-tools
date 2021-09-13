package com.adobe.aem.modernize.job;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;

public class ConversionJobExecutor implements JobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert";
  public static final String JOB_DATA_LOCATION = "/var/aem-modernize/job-data";

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext jobExecutionContext) {
    return null;
  }
}
