package com.adobe.aem.modernize;

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
