---
layout: doc-page
title: Policy Importer
description: How to configure Policy importer node definitions (just like Components & Dialogs!). 
order: 3
---

Policy import & rewrite rule configurations are managed as repository node structures. These nodes must exist below the path: `/apps/cq/modernize/design/rules` in order to be applied. 

Subfolders are supported to allow for delineating between multi-tenant configurations. It is important to note however, that pattern rules have no feature to distinguish between node paths for application. Any pattern that matches a node, from any tenant's rule set, will be displayed on the conversion search results.

{% include rules/configuration.html %}
