package com.adobe.aem.modernize.model;

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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.Self;

import lombok.Value;
import lombok.experimental.Delegate;

@Model(adaptables = SlingHttpServletRequest.class)
public class ConversionJobNavItem {

  @Self
  private SlingHttpServletRequest request;

  @Inject
  @Optional
  private String path;

  @Delegate
  private ConversionJob delegate;

  private int activeBucket;
  private List<ConversionJobBucketNavItem> buckets;

  @PostConstruct
  protected void init() {
    Resource job = request.getResourceResolver().getResource(this.path);
    if (job == null) {
      return;
    }
    delegate = job.adaptTo(ConversionJob.class);
    String bucket = request.getParameter("bucket");
    activeBucket = NumberUtils.toInt(bucket, 0);
  }

  public List<ConversionJobBucketNavItem> getNavBuckets() {
    if (buckets == null) {
      buckets = new ArrayList<>();
      List<ConversionJobBucket> list = delegate.getBuckets();
      for (int i = 0; i < list.size(); i++) {
        ConversionJobBucket b = list.get(i);
        buckets.add(new ConversionJobBucketNavItem(b.getPaths().size(), i == activeBucket, b.getStatus()));
      }
    }
    return buckets;
  }

  @Value
  public static class ConversionJobBucketNavItem {
    int count;
    boolean selected;
    ConversionJob.Status status;

    public String getIcon() {
      switch (getStatus()) {
        case FAILED:
          return "closeCircle";
        case SUCCESS:
          return "checkCircle";
        case WARN:
          return "alert";
        case ACTIVE:
          return "clockCheck";
        case WAITING:
          return "clock";
        default:
          return "helpCircle";
      }
    }

    public String getStatusClass() {
      switch (getStatus()) {
        case FAILED:
          return "error";
        case SUCCESS:
          return "success";
        case WARN:
          return "warn";
        case ACTIVE:
        case WAITING:
        default:
          return "info";
      }
    }
  }
}
