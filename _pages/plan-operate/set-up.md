---
layout: doc-page
title: Modernization Suite Set-Up
description: Ready to start configuring the tools and install the suite.
order: 3
---

## Tool Installation

The AEM Modernization Tool suite is released as a standard AEM content package. It can be downloaded from the <a href="https://github.com/adobe/aem-modernize-tools/releases/" target="_blank">GitHub Release page</a> or included as a sub-package dependency of a configuration project.

## Configuration Project

This section isn't intended to provide details on the creating the configurations, instead it focuses on how to manage the configurations that will be created. The tools are configured via OSGi service definitions or repository nodes, depending on the type. Therefore, the best approach to manage these is to create an AEM Project structure using the <a href="https://github.com/adobe/aem-project-archetype" target="_blank">AEM Maven Archetype</a>.

The content package should contain all of the definitions needed to configure the out-of-the-box tools. For projects which require advanced capabilities, the bundle project can host the custom implementations of the rewrite service interfaces.

