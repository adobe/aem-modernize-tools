---
layout: doc-page
title: Configuration
tool: Policy Importer
description: How to configure Policy import service.
order: 3
image: config-policy-importer.svg
videoId: 338801
---

Component rewrite rule configurations are managed as repository node structures. The Component Rewrite Rule Service has a reference to all available Rewrite rules that are either Node based or Service based.

Node based rewrite rule are found by searching the repository in the specified search paths. These are managed as an OSGi configuration property of the <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/policy/PolicyImportRuleService.html">Policy Import Rule Service</a>. This service has a pid of `com.adobe.aem.modernize.policy.impl.PolicyImportRuleServiceImpl`, and the configuration options are:

{% highlight properties %}
search.paths=["/path/to/rule/root"]
{% endhighlight %}

Only rules found which are direct descendants of these paths will be used for search & transformations.  It is important to note however, that pattern rules have no feature to distinguish between node paths for application. Any pattern that matches a node, from any tenant's rule set, will be displayed on the conversion search results.

Teams may also register custom _Service Based_ rules. As long as they implement the <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/policy/PolicyImportRule.html">PolicyImportRule interface</a> and are registered as OSGi services, they will be found and applied.

{% include rules/configuration.html %}
