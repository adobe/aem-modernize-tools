package com.adobe.aem.modernize.impl;

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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import org.jetbrains.annotations.NotNull;

/**
 * Visits each resource and generates a list of those which are pages.
 *
 * TODO: make this more robust.
 */
public class ListPageVisitor extends AbstractResourceVisitor {

  private static final List<String> PARENT_TYPES = Arrays.asList(
      JcrConstants.NT_FOLDER,
      JcrResourceConstants.NT_SLING_FOLDER,
      JcrResourceConstants.NT_SLING_ORDERED_FOLDER,
      NameConstants.NT_PAGE
  );

  private final List<String> paths = new ArrayList<>();
  private final int depth;

  public ListPageVisitor() {
    this(10);
  }

  public ListPageVisitor(int depth) {
    this.depth = depth;
  }

  @Override
  protected void visit(@NotNull Resource resource) {
    if (resource.adaptTo(Page.class) != null) {
      paths.add(resource.getPath());
    }
  }

  @Override
  protected void traverseChildren(@NotNull Iterator<Resource> children) {
    while(children.hasNext()) {
      Resource child = children.next();
      // End traverse if this depth is the limit.
      if (PathUtils.getDepth(child.getPath()) > depth) {
        break;
      }
      if (PARENT_TYPES.contains(child.getResourceType())) {
        this.accept(child);
      }
    }
  }

  public List<String> getPaths() {
    return new ArrayList<>(paths);
  }
}
