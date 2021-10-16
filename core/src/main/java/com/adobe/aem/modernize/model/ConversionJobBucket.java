package com.adobe.aem.modernize.model;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.Required;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import lombok.Getter;
import lombok.Setter;

@Model(
    adaptables = Resource.class
)
@Getter
@Setter
public class ConversionJobBucket {
  public static final String RESOURCE_TYPE = "aem-modernize/components/job/bucket";
  public static final String PN_JOB_ID = "jobId";
  public static final String PN_PATHS = "paths";
  public static final String PN_SUCCESS = "success";
  public static final String PN_FAILED = "failed";
  public static final String PN_NOT_FOUND = "notFound";

  @Self
  @Required
  private Resource resource;

  @ValueMapValue
  @Named(PN_JOB_ID)
  @Optional
  private String jobId;

  @ValueMapValue
  @Named(PN_PATHS)
  @Required
  private List<String> paths;

  @ValueMapValue
  @Named(PN_SUCCESS)
  @Optional
  private List<String> success = new ArrayList<>();

  @ValueMapValue
  @Named(PN_FAILED)
  @Optional
  private List<String> failed = new ArrayList<>();

  @ValueMapValue
  @Named(PN_NOT_FOUND)
  @Optional
  private List<String> notFound = new ArrayList<>();


}
