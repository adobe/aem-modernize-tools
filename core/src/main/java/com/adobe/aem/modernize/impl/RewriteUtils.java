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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.api.resource.ModifiableValueMap;

import com.adobe.aem.modernize.RewriteException;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import static com.adobe.aem.modernize.model.ConversionJob.*;

public class RewriteUtils {
  public static final String VERSION_LABEL = "Pre-Modernization";
  public static final String VERSION_DESC = "Version of content before the modernization process was performed.";

  /**
   * Traverses the entire tree rooted at the provided node; Any property found with the search term will have that literal
   * replaced with the replacement value.
   */
  public static void updateReferences(Node root, String search, String replacement) throws RepositoryException {

    ValueFactory vf = root.getSession().getValueFactory();
    TreeTraverser traverser = new TreeTraverser(root);
    Iterator<Node> iterator = traverser.iterator();
    while (iterator.hasNext()) {
      Node node = iterator.next();
      PropertyIterator pi = node.getProperties();
      while (pi.hasNext()) {
        Property p = pi.nextProperty();
        if (p.getType() == PropertyType.STRING) {
          Value[] values;
          if (p.isMultiple()) {
            values = p.getValues();
            for (int i = 0; i < values.length; i++) {
              values[i] = vf.createValue(values[i].getString().replaceAll(search, replacement));
            }
            p.setValue(values);
          } else {
            p.setValue(p.getValue().getString().replaceAll(search, replacement));
          }
        }
      }
    }
  }


  public static Page restore(PageManager pm, Page page) throws WCMException {
    String version = page.getProperties().get(PN_PRE_MODERNIZE_VERSION, String.class);
    if (StringUtils.isNotBlank(version)) {
      page = pm.restore(page.getPath(), version);
      ModifiableValueMap mvm = page.getContentResource().adaptTo(ModifiableValueMap.class);
      mvm.put(PN_PRE_MODERNIZE_VERSION, version);
    }
    return page;
  }

  public static void createVersion(PageManager pm, Page page) throws WCMException {
    String version = page.getProperties().get(PN_PRE_MODERNIZE_VERSION, String.class);
    if (StringUtils.isBlank(version)) {
      String date = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS").format(new Date());
      String label = String.format("%s - %s", VERSION_LABEL, date);
      Revision revision = pm.createRevision(page, label, VERSION_DESC);
      ModifiableValueMap mvm = page.getContentResource().adaptTo(ModifiableValueMap.class);
      mvm.put(PN_PRE_MODERNIZE_VERSION, revision.getId());
    }
  }

  public static Page copyPage(PageManager pm, Page source, String sourceRoot, String targetRoot) throws WCMException, RewriteException {

    String target = source.getPath().replace(sourceRoot, targetRoot);
    Page page = pm.getPage(target);
    if (page != null) {
      throw new RewriteException(String.format("Target page already exists for requested copy: {}", target));
    }
    return pm.copy(source, target, null, true, false, false); // Copy but only this page, fail if in conflict, don't save.
  }

}
