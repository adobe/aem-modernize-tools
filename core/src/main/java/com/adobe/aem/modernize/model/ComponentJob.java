package com.adobe.aem.modernize.model;

import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import lombok.Getter;

@Model(
    adaptables = Resource.class,
    resourceType = ComponentJob.RESOURCE_TYPE
)
@Getter
public class ComponentJob extends ConversionJob {

  public static final String RESOURCE_TYPE = "aem-modernize/components/convert/job/component";
  public static final String JOB_DATA_LOCATION = PathUtils.concat(ConversionJob.JOB_DATA_LOCATION, Type.COMPONENT.toString().toLowerCase());


  @ValueMapValue
  private String[] componentRules;

  @Override
  public Type getType() {
    return Type.COMPONENT;
  }


}
