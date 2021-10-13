package com.adobe.aem.modernize.impl;

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
    return paths;
  }
}
