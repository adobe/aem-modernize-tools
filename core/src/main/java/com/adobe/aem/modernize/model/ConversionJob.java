package com.adobe.aem.modernize.model;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import com.adobe.aem.modernize.component.job.ComponentJobExecutor;
import com.adobe.aem.modernize.job.FullConversionJobExecutor;
import lombok.Getter;

@Model(
    adaptables = Resource.class,
    resourceType = ConversionJob.RESOURCE_TYPE
)
@Getter
public class ConversionJob {
  public static final String RESOURCE_TYPE = "aem-modernize/components/job";

  public static final String JOB_DATA_LOCATION = "/var/aem-modernize/job-data";

  public static final String PN_TITLE = "jcr:title";
  public static final String PN_TEMPLATE_RULES = "templateRules";
  public static final String PN_COMPONENT_RULES = "componentRules";
  public static final String PN_POLICY_RULES = "policyRules";
  public static final String PN_INITIATOR = "startedBy";
  public static final String PN_REPROCESS = "reprocess";
  public static final String PN_PRE_MODERNIZE_VERSION = "premodernizeVersion";
  public static final String PN_TYPE = "type";

  @ValueMapValue
  @Named(ComponentJob.PN_TITLE)
  private String title;

  @Inject
  private List<ConversionJobBucket> buckets;

  public Type getType() {
    return Type.FULL;
  }

  public enum Type {

    FULL(FullConversionJobExecutor.JOB_TOPIC),
    COMPONENT(ComponentJobExecutor.JOB_TOPIC),
    PAGE(""),
    POLICY("");

    private String topic;

    Type(String topic) {
      this.topic = topic;
    }

    public String getTopic() {
      return this.topic;
    }
  }

}
