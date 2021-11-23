---
layout: doc-page
title: Configuration
tool: Component Converter
description: How to configure Component conversion node definitions (just like Policies & Dialogs!).
order: 3
image: config-component-converter.svg
videoId: 338801
---

Component rewrite rule configurations are managed as repository node structures. The Component Rewrite Rule Service has a reference to all available Rewrite rules that are either Node based or Service based.

Node based rewrite rule are found by searching the repository in the specified search paths. These are managed as an OSGi configuration property of the <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/component/ComponentRewriteRuleService.html">Component Rewrite Rule Service</a>. This service has a pid of `com.adobe.aem.modernize.component.impl.ComponentRewriteRuleServiceImpl`, and the configuration options are:

{% highlight properties %}
search.paths=["/path/to/rule/root"]
{% endhighlight %}

Only rules found which are direct descendants of these paths will be used for search & transformations.  It is important to note however, that pattern rules have no feature to distinguish between node paths for application. Any pattern that matches a node, from any tenant's rule set, will be displayed on the conversion search results.


Teams may also register custom _Service Based_ rules. As long as they implement the <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/component/ComponentRewriteRule.html">ComponentRewriteRule interface</a> and are registered as OSGi services, they will be found and applied.

{% include rules/configuration.html %}


