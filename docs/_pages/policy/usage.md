---
layout: doc-page
title: Usage
tool: Policy Importer
description: How to use the Policy Import tool.
order: 2
image: using-the-tools.svg
videoId: 338797
---
<p class="padded">
This page describes how to use the Policy Importer tool. The tool can be found via the AEM Tools primary navigation, and selecting the <code>AEM Modernize Tools</code> menu item.
</p>

## Job Listing

<p class="image right">
    <img src="{{ site.baseurl }}/pages/policy/images/job-card-view.png" alt="Policy Job Card View"/>
</p>

<p class="padded">
Selecting the Policy Conversion tool displays a list of all the previously scheduled jobs. From here the user can view the details of a specific job, or create a new one.
</p>

## Job Detail View

<p class="image right">
    <img src="{{ site.baseurl }}/pages/policy/images/job-detail-view.png" alt="Policy Job Detail View"/>
</p>

<div class="padded">
When a job has been scheduled and is running or completed, it can be viewed via the Job Detail view. This will show the paths selected for processing and their current state.

If the job has been scheduled but not executed, or is mid-execution, you can refresh the page to check the status/state. 
</div>

### Job (Buckets) List

If a given scheduling request has over 500 paths that need to be processed, then multiple jobs will be scheduled. The job detail view will allow users to select which job to view the details, using the left panel. 

## Job Creation

The following configuration options are available when scheduling a Policy Import Job.

### Job Title

<p class="image right">
    <img src="{{ site.baseurl }}/pages/policy/images/job-title.png" alt="Policy Job Title"/>
</p>

<p class="padded">
As with all the tools, the Policy import tool requires a name for the Job to be created. This helps with identifying the job once it is scheduled and completed.
</p>

### Configuration Target

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/policy/images/conf-target.png" alt="Policy Configuration Target"/>
</p>

<div class="padded">
A Policy job also requires a target location into which the new policies will be created. This will make them available to any Editable templates. The user scheduling the job must have *write* access to this location in the repository, or the job will not be scheduled.

The user scheduling the job must have *write* access to this location in the repository, or the job will not be scheduled.
</div>


### SuperTypes

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/policy/images/include-supertypes.png" alt="Policy Include Supertypes"/>
</p>

<div class="padded">
Components using a design are not necessarily using a direct reference. Legacy designs are inheritable through the _page's supertypes_. Thus, it is possible when searching for a design the direct reference does not contain the information used to render a component.

Checking this property will task the job to search the page's hierarchy to find the specific Style node that a component references. Unchecked, if the Style is not a direct reference, it will be ignored for import.
</div>

### Overwrite

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/policy/images/overwrite.png" alt="Policy Overwrite"/>
</p>

<div class="padded">
Since Designs and Policies are not stored in the same location in the repository, it's possible to reprocess a Policy import. This tool marks already imported designs with the created Policy location.

Checking this option import all found designs as Policies, regardless of whether they have already been imported. This can result in duplicate policies, if selected.
</div>

### Page Selection

<p class="image right">
    <img src="{{ site.baseurl }}/pages/policy/images/page-selection.png" alt="Policy Page List"/>
</p>

Importing is done by selecting the source pages from which Designs are referenced. The page list will show how many designs are found, and how many rules will apply to those create the associated policies.

The user may select as many pages as they want; depending on the number more than one job may be scheduled. 

