---
layout: doc-page
title: Evaluating a Site
description: Evaluating existing Templates & Components for a conversion to modern capabilities.
order: 2
image: evaluate.svg
---

## Overview

When planning the modernization of a site, existing templates and components need to be reviewed methodically and analytically to identify the correct transformation. The conversion tools are only as good as the configuration provided, therefore thorough definition is critical. 

Through some examples, we'll provide guidance on how to evaluate content & structure to create a successful conversion plan.



## Templates

Throughout this documentation we reiterate that Static templates have their content structured through the code, the repository organization does not accurately reflect the visual rendering order. Even with this limitation, both the visual and repository representation are used as input to a template's evaluation, along with a third item: the template's code.


Each of the different elements inform the review process of the others. Therefore, the order of review isn't as important as insuring that the information output from one review is validated against the others. 

### Source Review

This evaluation focuses on the code's embedded components. Editable templates rarely have any directly embedded components, other than the root container, so any components included through code are candidates to be managed by template editors. Reviewing the code also identifies the order in which components must be specified. 

#### Geometrixx Shapes 

This is the source for the *Geometrix Shapes* home page. For simplicity's sake we have aggregated the distinct `jsp` sources into one representation for easier review.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-shapes-homepage-body.png" alt="Geometrixx Shapes Body JSP"/>
</p>

We've annotated the image indicating where each portion of code is managed - the script include from which it was sourced. With this information, we can begin to identify which resources should be specified in a **Template Type**, and which should be managed by Template Editors.

A possible outcome of this evaluation is the following conclusions:

1. A new **Template Type** is required: *Base Template*
  * This template type will contain the header, footer, a few misc. components, and the main container for the template editor to manage.
1. A new **Editable Template** is required: *Home Page Template*
    1. This template be derived from the *Base Template* type.
    1. This will contain some template editor managed components, in this order:
        * Carousel
        * Lead
        * Main container
        * Right Rail container
    1. This template uses a 16 column layout - this is important as it affects the AEM Grid system.


#### Geometrixx Outdoors

This is the source for the *Geometrix Outdoors* home page. Again, we aggregated the distinct `jsp` sources into one representation for easier review.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-outdoors-homepage-body.png" alt="Geometrixx Outdoors Body JSP"/>
</p>

These notes also show the same general findings as the previous example:

1. A new **Template Type** is required: *Base Template*
  * This template type will be will contain the header, footer, a few misc. components, and the main container for the template editor to manage.
1. A new **Editable Template** is required: *Home Page Template*
    1. This template be derived from the *Base Template* type.
    1. Currently the evaluation doesn't show any new template editor managed components other than the root container. This may change after further review.
     

### Repository Review

A repository review incorporates taking a look at the content tree for an exemplar page that will be converted. This will identify what exists beyond what is represented in the code.

This review is the only means for identifying *Column Controls* which need to be converted. Reviewing the content tree also helps identifying content which should be managed by a template editor vs. normal content authoring. Finally, resources defined in the repository, but not referenced by the template, are candidates for removal - they likely are leftovers from previous iterations of the template.


#### Geometrixx Shapes

This image depicts the Geometrixx English home page's repository tree, showing resources defined beneath the `jcr:content` node.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-shapes-homepage-tree.png" alt="Geometrixx Shapes Body JSP"/>
</p>

We've already made teh following notes:

1. *Paragraph System* components which will be rewritten as a *Container*, and thus will require renaming.
1. A *Column Control*, which manages content organization within a parsys
1. Content ordering which does not reflect the static template referencing, thereby requiring an order configuration
1. Unused resources which can be deleted.


#### Geometrixx Outdoors

This image depicts the Geometrixx Outdoors English home page's repository tree.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-outdoors-homepage-tree.png" alt="Geometrixx Shapes Body JSP"/>
</p>

As with the previous example, we've noted our assessment. This evaluation shows a fairly simple conversion definition. 

1. *Paragraph System* component which will be rewritten as a *Container*, and thus will require renaming.
1. A *Column Control*, which manages content organization within a parsys.

### Visual Review

Content management on pages must be delineated between that which is managed by *Template Editors* and that managed by *Content Authors*. A visual review of exemplar pages helps identify this breakout. This review also helps with identifying placement and number of containers.

#### Geometrixx Shapes

Here's the Geometrixx Shapes home page annotated with containers, embedded and template editor managed components.


<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-shapes-homepage-annotated.png" alt="Geometrixx Shapes Home Page Annotated" width="400px" class="inline" />
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-shapes-homepage-containers.png" alt="Geometrixx Shapes Home Page Containers" width="400px" class="inline" />
</p>

#### Geometrixx Outdoors

Here's the Geometrixx Outdoors home page, again annotated with containers, embedded and template editor managed components.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/geometrixx-outdoors-homepage-annotated.png" alt="Geometrixx Outdoors Homepage Annotated" width="600px"/>
</p>


### Additional Considerations

#### New Template Types

The first rounds of template evaluations will likely lead to new **Template Type** definitions. While there may be some reusable code from the original components that comprised the static templates, there is likely new development needed to conform to editable template best practices. 

At a minimum these changes will be updating any *Paragraph System* to a container proxy, as provided by in the AEM Core Components. Other changes may include reorganizing the script references or changing markup to accommodate for a change in the content hierarchy.

#### MSM Relationships

When converting a site managed by MultiSite Manager, Blue Prints and Live Copies, it is important to convert both the BluePrint and all LiveCopies simultaneously.
 
## Components

Component evaluation is relatively simple compared to templates. Evaluation consists of identifying replacement components for existing legacy versions, then determing the correct data transformations. The evaluation is the same for both the component's standard authoring mode, as well as design to policy conversion.

### Replacement Selection

As stated elsewhere in this documentation, the purpose of the component conversion is to transform from legacy version fo components. Thus, the most likely scenario is that a deprecated component is being replaced with a newer or more robust version. Selection for conversion is relatively straight forward.

### Content Manipulations

The component and design conversions support a several options for data copy or transformations between node definitions. As part of the evaluation, every possible property that can be set on a component needs to be identified, along with its new property on the replacement node.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/example-image-component-before.png" alt="Example Component - Before" width="400px" class="inline" />
    <img src="{{ site.baseurl }}/pages/plan-operate/images/example-image-component-after.png" alt="Example Component - After" width="400px" class="inline" />
</p>

The above example shows the before and after node for an *image* component conversion. The original component was a Foundation Image, which contained a few simple values. As you can see from the output, the desired transformations are simple property copies.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/example-title-design-before.png" alt="Example Design - Before" width="400px" class="inline" />
    <img src="{{ site.baseurl }}/pages/plan-operate/images/example-title-design-after.png" alt="Example Design - After" width="400px" class="inline" />
</p>

This second example shows before/after for *Title* component's design configuration. This example is also from Geometrixx Shapes. It shows that one of the properties needs to be converted during the transformation. This informs the conversion configuration definitions. 



