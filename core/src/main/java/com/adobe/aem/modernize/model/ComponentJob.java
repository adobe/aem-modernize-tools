package com.adobe.aem.modernize.model;

import org.apache.jackrabbit.oak.commons.PathUtils;

public class ComponentJob extends ConversionJob {

  public static final String RESOURCE_TYPE = "aem-modernize/components/convert/component/job";
  public static final String JOB_DATA_LOCATION = PathUtils.concat(ConversionJob.JOB_DATA_LOCATION, "component");

}
