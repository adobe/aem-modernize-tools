package com.adobe.aem.modernize.model;

import java.util.List;
import javax.inject.Named;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Required;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import lombok.Getter;

@Model(
    adaptables = Resource.class
)
@Getter
public class ConversionJobBucket {
  public static final String RESOURCE_TYPE = "aem-modernize/components/job/bucket";
  public static final String PN_JOB_ID = "jobId";
  public static final String PN_PATHS = "paths";
  public static final String PN_SUCCESS = "success";
  public static final String PN_FAILED = "failed";
  public static final String PN_NOT_FOUND = "notFound";

  @ValueMapValue
  @Named(PN_JOB_ID)
  @Required
  private String jobId;

  @ValueMapValue
  @Named(PN_PATHS)
  @Required
  private List<String> paths;

  @ValueMapValue
  @Named(PN_SUCCESS)
  @Required
  private List<String> success;

  @ValueMapValue
  @Named(PN_FAILED)
  @Required
  private List<String> failed;

  @ValueMapValue
  @Named(PN_NOT_FOUND)
  @Required
  private List<String> notMatched;


}
