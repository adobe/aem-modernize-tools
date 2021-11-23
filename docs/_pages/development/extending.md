---
layout: doc-page
title: Extending the Tools
pageTitle: Extending the Tools
order: 2
image: extending-services.svg
videoId: ?????
---

This tool suite is designed to be extensible, allowing consumers to specify custom implementations should the framework not provide enough flexibility. Users of this suite can extend the feature set through registering OSGi services implementing specific API interfaces.

Each tool has an interface which, when implemented and registered as a service, will automatically be included as potential rules for a conversion process. The video on the right demonstrates implementing one of these interfaces, for a custom service-based Component RewriteRule.


### Page Structure

Page Structure rewrites are managed through two interface definitions:

The <a href="{{ site.baseurl}}/apidocs/com/adobe/aem/modernize/structure/StructureRewriteRule.html">Structure Rewrite Rule</a> is used to perform the conversions of a given page's structure. The <a href="{{ site.baseurl}}/apidocs/com/adobe/aem/modernize/structure/rule/PageRewriteRule.html">Page Rewrite Rule</a> is a factory that implements this interface. If necessary, teams can extend this factory and override implementation methods to perform custom transformations.

### Components

The <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/component/ComponentRewriteRule.html">Component Rewrite Rule</a> interface identifies service implementations to be used to match & transform Component instances. <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/component/rule/ColumnControlRewriteRule.html">Column Control Rewrite Rule</a> implements this interface, and is a good starting point for teams that may want an example for creating custom rules.


### Policies

The <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/policy/PolicyImportRule.html">Policy Import Rule</a> interface identifies service implementations to be used to match, transform and import Policy definitions. 

