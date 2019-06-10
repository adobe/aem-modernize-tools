---
layout: doc-page
title: Page Structure Converter
description: Details on how to define page content and column control conversion services to transform your pages.
order: 1
image: config-page-structure-converter.svg
---

The Page Structure rewrite tool consists of three *rewrite rule* OSGi services. Here we'll review the configurations needed for each.


## Page Rewrite Rule Service

The Page Rewrite Rewrite Rule service is a factory service intended for creating one rule per static template to be converted.

<p class="image">
    <img src="{{ site.baseurl }}/pages/configuration/images/page-rewrite-rule-service.png" alt="Page Rewrite Rule Service Configuration"/>
</p>

### Static Template

This is the only value used by the service to match pages for conversion. Any page which has a matching `cq:template` property value will be listed in the search results.

### Editable Template

This will be the new value of thee `cq:template` property after the conversion process. The existence of this template is not validated, it is assumed to exist.

### Sling Resource Type

The specifies the new value of the `sling:resourceType` for the converted page's `jcr:content` node.

### Node Ordering

This property allows users to define the order of the nodes on the new root *layout container*. The ordering supports nested components through the use of a colon (`:`) to separate parent/child relationships. In this example, the `lead` node will be moved to the first position of the `responsivegrid` node, which is a child of the root layout container.

<p class="image">
    <img src="{{ site.baseurl }}/pages/configuration/images/node-ordering-configuration.png" alt="Node Ordering Configuration"/>
</p>

Any nodes found but not listed in here will be added to the end of associated container in which it was originally found. For example, nodes found as direct descendants of the `jcr:content` node will be appended to the end of the `root` layout container in the order found. Nodes found in a parsys will be appended, in order, to the parsys after it is moved.


### Node Renaming & Relocation

This configuration property allows users to rename a node as it is moved & transformed. The primary use case for this is renaming parsys nodes to their editable template counterparts, which will be layout containers (oddly named `responsivegrid`). In this example the page's `par` and `rightpar` nodes are renamed the values shown, intermediate needing to be created are defined as layout containers.

<p class="image">
    <img src="{{ site.baseurl }}/pages/configuration/images/node-renaming-configuration.png" alt="Node Renaming Configuration"/>
</p>

It's important to note that these values are found by reviewing the structure for the editable template created by the template editors. An incorrect definition will lead content not rendering on the transformed pages. 


### Node Removal

This list specifies nodes which are removed if found, typically because they are leftover from previous template refactoring and no longer needed. 

## Column Control Rewrite Rule Service

The Column Control Rewrite Rule is another factory service, this one designed to convert column definitions into individual layout containers, containing the respective contents.

<p class="image">
    <img src="{{ site.baseurl }}/pages/configuration/images/column-control-rewrite-rule-service.png" alt="Column Control Rewrite Rule Service Configuration"/>
</p>

### Column Control Resource Type

This configuration property is one of two values used to match against a node's `sling:resourceType` to indicate it is a *Column Control*.


### Layout Property Value

This is the second of two values which uniquely identifies a rewrite rule. A customer's implementation or site's content may contain several different column control definitions. However, each will have a unique *layout* to specify the column count and size. 

### Column Widths

This property manages how the new layout containers are created and configured for the AEM Grid. The number of entries needs to match the expected number of columns for the configured column control. Each entry in this array is used to configure the layout container with the representative AEM Responsive Grid values to achieve the same visual experience.

In the pictured example, each column in the referenced component is 50% width of the original 12 column grid - thus 6 columns. 

## Paragraph System Rewrite Rule Service

This service does not require any configuration, it will automatically and always perform its operations for any page being transformed.