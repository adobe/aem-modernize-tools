---
layout: doc-page
title: About
tool: All-in-One Converter
description: About the All-in-One conversion tool.
order: 1
image: tools-dialog-converter.svg
---

This conversion tool is used to apply all the other tools against selected pages, in a single process. The operations referenced here are performed on the content in the repository, this tool does not modify any code.

There is an implicit expectation that a new Editable Template has been created to support the content updated by these conversions. Policies are created in the user specified _Configuration_ location for use on associated templates. There is an implicit expectation that new components have been created to support the content updated by these conversions. See the <a href="{{ site.baseurl }}/pages/plan-operate.html">Plan & Operate</a> section for more information.

Here you'll find information on what conversion operations occur. For information on how to configure the tool, see the associated <a href="{{ site.baseurl }}/pages/configuration/full.html">configuration page</a>. For information on how the services perform the changes, and options for extending or enhancing, see the <a href="{{ site.baseurl }}/pages/development.html">developer detail pages</a>.


## Conversions

### Structure Conversions

This tool performs the same conversions described on the <a href="{{ site.baseurl}}/pages/structure.html">Structure Conversion Tool</a> page.

### Policy Imports

This tool performs the same processes described on the <a href="{{ site.baseurl}}/pages/policy.html">Policy Import Tool</a> page.

### Component Conversion

This tool performs the same conversions described on the <a href="{{ site.baseurl}}/pages/component.html">Component Conversion Tool</a> page.

### Policy Mapping

During the conversion of a page's template reference, if there exists a policy that is imported that has not been applied on the associated Editable Template, this tool will apply that policy. Thus, a component in a container should maintain a reference to the design properties on the updated Editable Template. This only holds true if the component does not already have a policy mapping.
