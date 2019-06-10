---
layout: doc-page
title: Using the Tools
description: Everything is planned, evaluated, and configured - it's time to perform the transformations. 
order: 4
image: using-the-tools.svg
---

The tools were designed to be very easy to use. They will only display conversions that are available based on configurations in the designated repository locations, or service configurations that result in a resource match.


## Page Structure Conversion Tool

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/page-structure-conversion-tool.png" alt="Page Structure Conversion Interface"/>
</p>


The page structure conversion form input selects the root of a tree for finding pages. 

The search results display a list of pages which match any configured rules. Each result lists the page's title, repository path, template type, and links to view the page or it's location via the CRX/DE Lite repository browser. 

One or more pages can be selected for conversion, and once executed a success or error message is displayed indicating the status of the conversion. 


## Component Conversion Tool

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/component-conversion-tool.png" alt="Component Conversion Interface"/>
</p>

The component conversion form input selects the root of a tree for finding components to convert. Input selection can either be a page's `jcr:content` node, which will limit the search to that specific page, or a page itself, which will display all available components on that page and all of its descendants. 

Search results list all components matching existing rules. Each result lists the repository path to the content, the component type, and links to view the content or it's location via the CRX/DE Lite repository browser. 

One or more components can be selected for conversion, and once executed a success or error message is displayed indicating the status of the conversion. 


## Policy Importer Tool

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/policy-importer-tool.png" alt="Policy Importer Interface"/>
</p>


The policy import form input selects the root of a tree for finding design configurations to import. Input should be the root of a site's design definition.
 
Search results list all design nodes matching existing rules. Each result lists the repository path to the content, the component type, and links to view the content or it's location via the CRX/DE Lite repository browser.

One or more components can be selected for conversion, once a selection is made, another input is required: the destination Configuration container. With these inputs, the conversion can be executed, any success or errors are displayed to the user.


## Dialog Conversion Tool

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/dialog-conversion-tool.png" alt="Dialog Conversion Interface"/>
</p>


The dialgo conversion form input selects the root of a tree for finding dialogs to convert. Input selection will usually be a project's *components* folder. 

Search results list all components matching existing rules. Each result lists the repository path to the dialog, the dialog version/type, and links to view the it or it's location via the CRX/DE Lite repository browser. 

One or more dialogs can be selected for conversion, and once executed a success or error message is displayed indicating the status of the conversion. 

