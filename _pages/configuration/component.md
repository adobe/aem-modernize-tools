---
layout: doc-page
title: Component Converter
description: How to configure Component conversion node definitions (just like Policies & Dialogs!). 
order: 2
---

Component rewrite rule configurations are managed as repository node structures. These nodes must exist below the path: `/apps/cq/modernize/component/rules` in order to be applied. 

Subfolders are supported to allow for delineating between multi-tenant configurations. It is important to note however, that pattern rules have no feature to distinguish between node paths for application. Any pattern that matches a node, from any tenant's rule set, will be displayed on the conversion search results.

{% include rules/configuration.html %}
