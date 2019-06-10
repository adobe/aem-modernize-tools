---
layout: doc-page
title: Dialog Converter
description: How to configure Dialog conversion node definitions (just like Components & Policies!). 
order: 4
image: config-dialog-converter.svg
---

Dialog rewrite rule configurations are managed as repository node structures. These nodes must exist below the path: `/apps/cq/modernize/dialog/rules` in order to be applied. 

This project provides a standard set of dialogs to convert the AEM Product provided Classic (ExtJS) and Coral2 dialog widgets to their Coral3 counter parts. These can be found in the `classic` and `coral2` subfolders, respectively.

Subfolders are supported to allow for delineating between multi-tenant configurations. It is important to note however, that pattern rules have no feature to distinguish between node paths for application. Any pattern that matches a node, from any tenant's rule set, will be displayed on the conversion search results.

{% include rules/configuration.html %}


### Copy Common Attributes

<p class="image">
  <img src="{{ site.baseurl }}/pages/configuration/images/node-copy-common-definition.png" alt="Copy Common Attributes Definition"/>
</p>

This is a flag that can be set on dialog conversions. This tells the rewrite engine to convert known common legacy dialog properties to their Coral3 counterparts, eliminating the need for users to specify each property individually.


### Copy Render Conditions

<p class="image">
  <img src="{{ site.baseurl }}/pages/configuration/images/node-copy-rendercondition-definition.png" alt="Copy Render Conditions Definition"/>
</p>

This is another flag unique to dialog conversions. It informs the rewrite engine to copy any render conditions that may exist on the source node. Any Coral2 references are modified to reference their Coral3 equivalent.
