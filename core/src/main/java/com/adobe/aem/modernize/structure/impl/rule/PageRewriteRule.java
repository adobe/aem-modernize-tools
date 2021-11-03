/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.adobe.aem.modernize.structure.impl.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.day.cq.wcm.api.Page;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.day.cq.wcm.api.NameConstants.*;

/**
 * Rewrites the jcr:content node of a Page.
 * Updates the <code>cq:template</code> reference to the mapped value
 * Removes the <code>cq:designPath</code> property, as it is unnecessary on Editable Templates
 * Creates a root responsive layout component and moves all children nodes (e.g. components) to this container.
 */
@Component(
    service = { StructureRewriteRule.class },
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = {
        "service.ranking=10"
    }
)
@Designate(ocd = PageRewriteRule.Config.class, factory = true)
public class PageRewriteRule implements StructureRewriteRule {

  protected static final String NN_ROOT_CONTAINER = "root";
  private static final Logger logger = LoggerFactory.getLogger(PageRewriteRule.class);
  private String id = PageRewriteRule.class.getName();
  private int ranking = Integer.MAX_VALUE;

  private String staticTemplate;
  private String editableTemplate;
  private String containerResourceType;
  private Map<String, List<String>> componentOrdering = new HashMap<>();
  private Map<String, String> componentRenamed;
  private List<String> componentsToRemove;
  private String slingResourceType;

  @Override
  public String getTitle() {
    return String.format("PageRewriteRule (%s -> %s)", staticTemplate, editableTemplate);
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public boolean matches(@NotNull Node root) throws RepositoryException {

    if (root.getPrimaryNodeType().isNodeType(NT_PAGE) && root.hasNode(NN_CONTENT)) {
      root = root.getNode(NN_CONTENT);
    }

    if (!root.hasProperty(PN_TEMPLATE)) {
      return false;
    }
    String template = root.getProperty(PN_TEMPLATE).getString();
    return StringUtils.equals(staticTemplate, template);
  }

  @Nullable
  @Override
  public Node applyTo(@NotNull Node pageContent, @NotNull Set<String> finalPaths) throws RepositoryException, RewriteException {

    if (pageContent.getPrimaryNodeType().isNodeType(NT_PAGE)) {
      pageContent = pageContent.getNode(NN_CONTENT);
    }

    // Remove Design Property.
    if (pageContent.hasProperty(PN_DESIGN_PATH)) {
      pageContent.getProperty(PN_DESIGN_PATH).remove();
    }

    // Remove specified node & Build node name list for processing
    List<String> names = new ArrayList<>();
    NodeIterator children = pageContent.getNodes();
    while (children.hasNext()) {
      Node child = children.nextNode();
      String name = child.getName();
      if (componentsToRemove.contains(name)) {
        child.remove();
      } else {
        names.add(name);
      }
    }

    Property template = pageContent.getProperty(NN_TEMPLATE);
    template.setValue(editableTemplate);
    pageContent.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, slingResourceType);

    Node container = pageContent.addNode(NN_ROOT_CONTAINER, JcrConstants.NT_UNSTRUCTURED);
    container.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, containerResourceType);
    pageContent.orderBefore(NN_ROOT_CONTAINER, null);

    moveRenamedNodes(pageContent, container, names);
    moveRemainingNodes(pageContent, container, names);
    orderNodes(pageContent);
    return pageContent;
  }

  @Override
  public int getRanking() {
    return this.ranking;
  }

  @Override
  public @NotNull Set<String> findMatches(@NotNull Resource resource) {
    Set<String> match = new HashSet<>();
    Page page = resource.adaptTo(Page.class);
    if (page == null) {
      return match;
    }
    Resource content = page.getContentResource();
    if (content == null) {
      return match;
    }
    ValueMap vm = content.getValueMap();
    if (StringUtils.equals(staticTemplate, vm.get(PN_TEMPLATE, String.class))) {
      match.add(resource.getPath());
    }
    return match;
  }

  @Override
  public boolean hasPattern(@NotNull String... slingResourceTypes) {
    return Arrays.asList(slingResourceTypes).contains(slingResourceType);
  }

  // This will list any intermediate nodes as needed by the order or rename logic.
  private void moveRenamedNodes(final Node source, final Node target, final List<String> nodeNames) throws RepositoryException {
    Session session = source.getSession();

    // Create intermediate nodes for renamed ones;
    for (Map.Entry<String, String> entry : componentRenamed.entrySet()) {
      int idx = entry.getValue().lastIndexOf('/');
      if (idx > 0) {
        String[] tokens = entry.getValue().substring(0, idx).split("/");
        Node parent = target;
        String relPath = "";
        for (String t : tokens) {
          relPath = PathUtils.concat(relPath, t);
          if (source.hasNode(t)) {
            session.move(PathUtils.concat(source.getPath(), t), PathUtils.concat(parent.getPath(), t));
            parent = target.getNode(t);
            nodeNames.remove(t);
          } else if (componentRenamed.containsValue(relPath)) {
            // Find the source node for this rename
            for (Map.Entry<String, String> nestedEntry : componentRenamed.entrySet()) {
              if (nestedEntry.getValue().equals(relPath)) {
                session.move(PathUtils.concat(source.getPath(), nestedEntry.getKey()), PathUtils.concat(parent.getPath(), t));
                parent = parent.getNode(t);
                nodeNames.remove(nestedEntry.getKey());
                break;
              }
            }
          } else {
            parent = target.addNode(t, JcrConstants.NT_UNSTRUCTURED);
            parent.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, containerResourceType);
          }
        }
      }
      // All intermediate nodes were created or renames moved to.
      session.move(PathUtils.concat(source.getPath(), entry.getKey()), PathUtils.concat(target.getPath(), entry.getValue()));
      nodeNames.remove(entry.getKey());
    }
  }

  private void moveRemainingNodes(final Node source, final Node target, final List<String> names) throws RepositoryException {
    Session session = source.getSession();

    Iterator<String> iterator = names.iterator();
    while (iterator.hasNext()) {
      String name = iterator.next();
      boolean found = false;
      for (Map.Entry<String, List<String>> entry : componentOrdering.entrySet()) {
        if (entry.getValue().contains(name)) {
          String path = entry.getKey().replace(NN_ROOT_CONTAINER + "/", "");
          String[] tokens = path.split("/");
          Node parent = target;
          for (String t : tokens) {
            if (target.hasNode(t)) {
              parent = target.getNode(t);
            } else {
              parent = parent.addNode(t, JcrConstants.NT_UNSTRUCTURED);
              parent.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, containerResourceType);
            }
          }
          session.move(PathUtils.concat(source.getPath(), name), PathUtils.concat(parent.getPath(), name));
          found = true;
        }
      }
      if (!found) {
        session.move(PathUtils.concat(source.getPath(), name), PathUtils.concat(target.getPath(), name));
      }
      iterator.remove();
    }
  }

  private void orderNodes(Node pageContent) throws RepositoryException {
    for (Map.Entry<String, List<String>> entry : componentOrdering.entrySet()) {
      // Find the list of all the children in this container
      Node parent = pageContent.getNode(entry.getKey());
      for (String name : entry.getValue()){
        parent.orderBefore(name, null);
      }

      NodeIterator children = parent.getNodes();
      while (children.hasNext()) {
        String name = children.nextNode().getName();
        if (entry.getValue().contains(name)) {
          continue;
        }
        parent.orderBefore(name, null);
      }
    }
  }

  @Activate
  @Modified
  @SuppressWarnings("unused")
  protected void activate(ComponentContext context, Config config) throws ConfigurationException {
    @SuppressWarnings("unchecked")
    Dictionary<String, Object> props = context.getProperties();
    // read service ranking property
    this.ranking = PropertiesUtil.toInteger(props.get("service.ranking"), this.ranking);
    this.id = PropertiesUtil.toString(props.get("service.pid"), this.id);
    this.ranking = Converters.standardConverter().convert(props.get("service.ranking")).defaultValue(Integer.MAX_VALUE).to(Integer.class);

    staticTemplate = config.static_template();
    if (StringUtils.isBlank(staticTemplate)) {
      throw new ConfigurationException("static.template", "Static template is required.");
    }

    slingResourceType = config.sling_resourceType();
    if (StringUtils.isBlank(slingResourceType)) {
      throw new ConfigurationException("sling.resourceType", "Sling Resource Type is required.");
    }


    editableTemplate = config.editable_template();
    if (StringUtils.isBlank(editableTemplate)) {
      throw new ConfigurationException("editable.template", "Editable template is required.");
    }

    containerResourceType = config.container_resourceType();
    if (StringUtils.isBlank(containerResourceType)) {
      throw new ConfigurationException("container.resourceType", "Container's Sling Resource Type is required.");
    }

    // Determine Component order.
    componentOrdering = new LinkedHashMap<>();
    String[] orders = config.order_components();
    if (orders != null) {
      for (String o : orders) {
        String[] tmp = StringUtils.split(o, ":", 2);
        if (tmp.length < 1) {
          continue;
        }
        String key;
        String value;

        if (tmp.length == 2) {
          key = PathUtils.concatRelativePaths(NN_ROOT_CONTAINER, tmp[0]);
          value = tmp[1];
        } else {
          key = NN_ROOT_CONTAINER;
          value = tmp[0];
        }
        componentOrdering.computeIfAbsent(key, k -> new ArrayList<>());
        componentOrdering.get(key).add(value);
      }
    }
    componentsToRemove = new ArrayList<>();
    if (config.rename_components() != null) {
      componentsToRemove = Arrays.stream(config.remove_components()).collect(Collectors.toList());
    }

    componentRenamed = new LinkedHashMap<>();
    if (config.rename_components() != null) {

      for (String cfg : config.rename_components()) {
        String[] kv = StringUtils.split(cfg, "=", 2);
        if (kv.length != 2) {
          continue;
        }
        String key = StringUtils.trimToNull(kv[0]);
        String value = StringUtils.trimToNull(kv[1]);
        if (key != null && value != null) {
          componentRenamed.put(key, value);
        }
      }
    }

  }

  @ObjectClassDefinition(
      name = "AEM Modernization Tools - Page Rewrite Rule",
      description = "Rewrites a page template & structure to use new responsive grid layout."
  )
  @interface Config {
    @AttributeDefinition(
        name = "Static Template",
        description = "The static template which will be updated by this Page Rewrite Rule"
    )
    String static_template();

    @AttributeDefinition(
        name = "Sling Resource Type",
        description = "The resource type to match on a page for this rule to apply."
    )
    String sling_resourceType();

    @AttributeDefinition(
        name = "Editable Template",
        description = "The value to update the cq:template with, this should be the new Editable Template.")
    String editable_template();

    @AttributeDefinition(
        name = "Container Resource Type",
        description = "The sling:resourceType of the container to use for the root of the Editable template")
    String container_resourceType();

    @AttributeDefinition(
        name = "Component Order",
        description = "Specify the order of the components in the new root container. " +
            "Any found and unspecified are moved to the end in arbitrary order.",
        cardinality = Integer.MAX_VALUE,
        required = false
    )
    String[] order_components();

    @AttributeDefinition(
        name = "Remove Components",
        description = "Specify any components that may exist on the static page that can be removed.",
        cardinality = Integer.MAX_VALUE,
        required = false
    )
    String[] remove_components();

    @AttributeDefinition(
        name = "Rename Components",
        description = "Specify new name for components as they are moved into the root responsive grid.",
        cardinality = Integer.MAX_VALUE,
        required = false
    )
    String[] rename_components();
  }

}
