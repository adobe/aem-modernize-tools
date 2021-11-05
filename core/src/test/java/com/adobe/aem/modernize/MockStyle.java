package com.adobe.aem.modernize;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Style;
import lombok.Getter;
import lombok.experimental.Delegate;

@Getter
public class MockStyle implements Style {

  private final Design design;
  @Delegate
  private final ValueMap vm;

  private final Cell cell;
  private final String path;

  public MockStyle(Design design, Cell cell, String path) {
    this(design, cell, path, null);
  }

  public MockStyle(Design design, Cell cell, String path, Resource resource) {
    this.design = design;
    this.cell = cell;
    this.path = path;
    if (resource != null) {
      this.vm = resource.adaptTo(ValueMap.class);
    } else {
      vm = null;
    }
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public Cell getCell() {
    return cell;
  }

  @Override
  public Resource getDefiningResource(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDefiningPath(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Style getSubStyle(String s) {
    throw new UnsupportedOperationException();
  }
}
