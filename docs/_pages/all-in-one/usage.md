---
layout: doc-page
title: Usage
tool: All-in-One Converter
description: How to use the All-in-One conversion tool.
order: 2
image: using-the-tools.svg
videoId: 338802
---
<p class="padded">
This page describes how to use the All-in-One conversion tool. The tool can be found via the AEM Tools primary navigation, and selecting the <code>AEM Modernize Tools</code> menu item.
</p>

## Job Listing

<p class="image right">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/job-card-view.png" alt="All-in-One Job Card View"/>
</p>

<p class="padded">
Selecting the All-in-One Conversion tool displays a list of all the previously scheduled jobs. From here the user can view the details of a specific job, or create a new one.
</p>

## Job Detail View

<p class="image right">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/job-detail-view.png" alt="All-in-One Job Detail View"/>
</p>

When a job has been scheduled and is running or completed, it can be viewed via the Job Detail view. This will show the paths selected for processing and their current state.

If the job has been scheduled but not executed, or is mid-execution, you can refresh the page to check the status/state.

### Job (Buckets) List

If a given scheduling request has over 500 paths that need to be processed, then multiple jobs will be scheduled. The job detail view will allow users to select which job to view the details, using the left panel.

## Job Creation

The following configuration options are available when scheduling an All-in-One Conversion Job.

### Job Title

<p class="image right">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/job-title.png" alt="All-in-One Job Title"/>
</p>

As with all the tools, the All-in-One conversion tool requires a name for the Job to be created. This helps with identifying the job once it is scheduled and completed.

### Page Handling
An all-in-one conversion has the option of processing the pages using different options.

#### No Handling

This option will simply process the page as-is, in place in the repository.

#### Restore

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/page-handling-restore.png" alt="Page Handling Restore Selected"/>
</p>

<div class="padded">
This option will restore a page to a previous state. When a page is processed using the All-in-One conversion or Structure conversion tools, a version is created prior to the execution.

This option will restore the page to the most recent version that was marked as "Pre-Modernization." This allows for reprocessing pages based on updated rules.
</div>

#### Copy to Target

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/page-handling-copy.png" alt="Page Handling Copy Selected"/>
</p>

This option will copy a page from the source location, to a new path in the repository. Each page must exist under the `source` path selection. When processing that portion of the path will be replaced with the `target` path.

The user scheduling the job must have *write* access to the target location in the repository, or the job will not be scheduled.

The target path must not have a page that already exists in that location, or that page conversion will fail.

### Configuration Target

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/conf-target.png" alt="Policy Configuration Target"/>
</p>

<p class="padded">
An All-in-One job also requires a target location into which the new policies will be created. This will make them available to any Editable templates. The user scheduling the job must have *write* access to this location in the repository, or the job will not be scheduled.
</p>

### SuperTypes

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/include-supertypes.png" alt="Policy Include Supertypes"/>
</p>

This option is required by default, as this tool automatically applies the found policies.

Components using a design are not necessarily using a direct reference. Legacy designs are inheritable through the _page's supertypes_. Thus, it is possible when searching for a design the direct reference does not contain the information used to render a component.

Checking this property will task the job to search the page's hierarchy to find the specific Style node that a component references. Unchecked, if the Style is not a direct reference, it will be ignored for import.

### Overwrite

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/overwrite.png" alt="Policy Overwrite"/>
</p>

<div class="padded">
Since Designs and Policies are not stored in the same location in the repository, it's possible to reprocess a Policy import. This tool marks already imported designs with the created Policy location.

Checking this option import all found designs as Policies, regardless of whether they have already been imported. This can result in duplicate policies, if selected.
</div>

### Page Selection

<p class="image right">
    <img src="{{ site.baseurl }}/pages/all-in-one/images/page-selection.png" alt="All-in-One Page List"/>
</p>

The final step is to select the pages to process. The list will show how many structure, component, and policy conversion rules are found that match the page.

The user may select as many pages as they want; depending on the number more than one job may be scheduled.
