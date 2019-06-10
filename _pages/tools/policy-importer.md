---
layout: doc-page
title: Policy Importer
description: Imports design configurations as Editable Template policies.
order: 3
image: tools-policy-importer.svg
---

## About


This conversion tool is used to create a component *Policy* from a *Design Configuration*. Editable templates do not support any of the legacy design definitions, and thus an equivalent policy is needed to continue to support cross-page authoring rules.

The operations referenced here are performed on the content in the repository, this tool does not modify any code. Policies are created in the user specified input location for use on associated templates. There is an implicit expectation that new components have been created to support the content updated by these conversions. See the <a href="{{ site.baseurl }}/pages/plan-operate.html">Plan & Operate</a> section for more information.

Here you'll find information on what conversion operations occur. For information on how to configure the tool, see the associated <a href="{{ site.baseurl }}/pages/configuration/component.html">configuration page</a>. For information on how the services perform the changes, and options for extending or enhancing, see the <a href="{{ site.baseurl }}/pages/development.html">developer detail pages.</a>


## Conversions

The following operations are performed during the component transformation. They do require that the node be rewritten however, original parent ordering is preserved during the process.

{% include node-rewrites/conversions.html %}
