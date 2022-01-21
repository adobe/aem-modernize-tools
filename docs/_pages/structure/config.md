---
layout: doc-page
title: Configuration
tool: Page/Structure Converter
description: How to configure Page Structure conversion service.
order: 3
image: config-page-structure-converter.svg
videoId: 338821
---

<p class="padded">
The Page Structure rewrite tool consists of a single <strong>rewrite rule</strong> OSGi service factory.
</p>

## Page Rewrite Rule Service

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/page-rewrite-rule-service.png" alt="Page Rewrite Rule Service Configuration"/>
</p>

<p class="padded">
The Page Rewrite Rule service is a factory service intended for creating one rule per static template to be converted.
</p>

### Static Template

This property is used as means to identify pages for conversion. This, along with the `sling.resoruceType` configuration property are used for matching. A page which has a matching `cq:template` property value will be listed in the search results.

### Sling Resource Type
This property is used as means to identify pages for conversion. This, along with the `static.template` configuration property are used for matching. A page which has a matching `sling:resourceType` property value will be listed in the search results.


### Editable Template

This will be the new value of thee `cq:template` property after the conversion process. This template is assumed to exist, no validation of its presence is performed. If it does not exist, then errors will be thrown during the Job resulting in failures.

### Container Resource Type

Since the Foundation Responsive Grid is not recommended be used directly, this property specifies the _Container_ Core Component proxy to use when modifying the page structure.


### Node Ordering

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/node-ordering-configuration.png" alt="Node Ordering Configuration"/>
</p>

This property allows users to define the order of the nodes on the new root *container*. The ordering supports nested components through the use of a colon (`:`) to separate parent/child relationships. In this example, the `lead` node will be moved to the first position of the `container` node, which is a child of the root layout container.

The names referenced here are those that either already exist in the page content (i.e. static references) or nodes which are renamed through the _Rename & Relocation_ configuration.

Any nodes found, but not listed in here, will be added to the end of the root container; i.e. nodes found as direct descendants of the `jcr:content` node will be appended to the end of the `root` container in the order found.

### Node Renaming & Relocation

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/node-renaming-configuration.png" alt="Node Renaming Configuration"/>
</p>

This configuration property allows users to rename a node as it is moved & transformed. The primary use case for this is renaming previous static parsys nodes to their editable template counterparts. In this example the page's `par` and `rightpar` nodes are renamed the values shown. If a node is specified to be renamed to relative child path, then the intermediate nodes will be created based on the structure in the Editable template definition.

It's important to note that these values are found by reviewing the structure for the editable template created by the template editors. An incorrect definition will lead content not rendering on the transformed pages.

### Node Ignoring

This list allows teams to specify nodes which should not be modified or moved from the root of the Page's content tree. There may some use of maintaining static references to nodes from the template types, and thus the references need to be maintained.

The Structure Conversion Tool ignores nodes named `cq:LiveSyncConfig` and `cq:BlueprintSyncConfig` by default, as they have special meaning in AEM and must be maintained at the root of the Page's content.

### Node Removal

This list specifies nodes which are removed if found, typically because they are leftover from previous template refactoring and no longer needed.
