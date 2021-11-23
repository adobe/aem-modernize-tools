---
layout: doc-page
title: Extending the Tools
pageTitle: Extending the Tools
order: 2
image: extending-services.svg
---

This tool suite is designed to be extensible, allowing consumers to specify custom implementations should the framework not provide enough flexibility. Users of this suite can extend the feature set through registering OSGi services implementing specific API interfaces.

All of the following interfaces extend the **RewriteRule** interface - which defines the API for matching and rewriting content.

### Page Structure

Page & Structure rewrites are managed through two interface definitions:

The <a href="{{ site.baseurl}}/apidocs/com/adobe/aem/modernize/structure/StructureRewriteRule.html">StructureRewriteRule</a> interface is used to define the transformations at the page structure level. These services are used during the transformation of a page's node structure. Service ranking is not used at this time, as there should only ever be one matching service per structure to convert. 

The <a href="{{ site.baseurl}}/apidocs/com/adobe/aem/modernize/structure/PageStructureRewriteRule.html">PageStructureRewriteRule</a> is the interface that should *also* be implemented and registered, in order for matching page's to be displayed on the administration page.

### Components


The <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/component/ComponentRewriteRule.html">ComponentRewriteRule</a> interface identifies service implementations to be used to match & transform Component instances. 


### Policies

The <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/design/PoliciesImportRule.html">PoliciesImportRule</a> interface identifies service implementations to be used to match, transform and import Policy definitions. 


### Dialogs 

The <a href="{{ site.baseurl }}/apidocs/com/adobe/aem/modernize/dialog/DialogRewriteRule.html">DialogRewriteRule</a> interface identifies service implementations to be used to match & transform Dialog widgets . 
