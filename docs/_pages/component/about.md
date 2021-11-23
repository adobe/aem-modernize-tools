---
layout: doc-page
title: About 
tool: Component Converter
description: About the Component conversion tool.
order: 1
image: tools-component-converter.svg
videoId: 338800
---

This conversion tool is used to rewrite one component's node/tree to another. The intent is to upgrade or transform a component, primarily to move from legacy components (AEM or custom) to AEM Core Component proxies or modern implementations.

The operations referenced here are performed on the content in the repository, this tool does not modify any code. There is an implicit expectation that new components have been created to support the content updated by these conversions. See the <a href="{{ site.baseurl }}/pages/plan-operate.html">Plan & Operate</a> section for more information.

Here you'll find information on what conversion operations occur. For information on how to configure the tool, see the associated <a href="config.html">configuration page</a>. For information on how the services perform the changes, and options for extending or enhancing, see the <a href="{{ site.baseurl }}/pages/development.html">developer detail pages</a>.


## Conversions

The following operations are performed during the component transformation. Using this out-of-the-box feature replaces the original matched component node  matched, with a new node. If the component's parent is an *ordered* node, the component's position in that order is preserved.

{% include node-rewrites/conversions.html %}
