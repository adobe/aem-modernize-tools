---
layout: doc-page
title: Dialog Converter
description: Converts Classic and Coral2 dialogs to Coral3.
order: 4
image: tools-dialog-converter.svg
---

## About

This conversion tool is used to rewrite one dialog's tree to another. The intent is to upgrade from Classic or Coral2 Dialogs to the Coral3 equivalent. This is the only tool in the suite meant to be executed on a developer's system - as it does code transformations to ease developer operations.

The operations referenced here are performed on the content in the repository. This tool creates a new `cq:dialog` definition a component's folder. This will allow developers to export the new definition, to be added to their SCM tool. See the <a href="{{ site.baseurl }}/pages/plan-operate.html">Plan & Operate</a> section for more information. 

Here you'll find information on what conversion operations occur. For information on how to configure the tool, see the associated <a href="{{ site.baseurl }}/pages/configuration/dialog.html">configuration page</a>. For information on how the services perform the changes, and options for extending or enhancing, see the <a href="{{ site.baseurl }}/pages/development.html">developer detail pages.</a>


## Conversions

The following operations are performed during dialog creation. They do require that the node be rewritten however, original tree ordering is preserved during the process.

{% include node-rewrites/conversions.html %}

### Common Attributes

This is a special transformation only for Dialog conversion; it converts a special attributes of a dialog to their equivalent granite definitions.

### Render Conditions

This is another transformation only valid for Dialog conversion; it converts a `rendercondition` node tree to the its respective granite definition.
