---
layout: doc-page
title: Usage
tool: Page Structure Converter
description: How to use the Page Structure conversion tool.
order: 2
image: using-the-tools.svg
videoId: 338799
---
<p class="padded">
This page describes how to use the Page Structure conversion tool. The tool can be found via the AEM Tools primary navigation, and selecting the <code>AEM Modernize Tools</code> menu item.
</p>

## Job Listing

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/job-card-view.png" alt="Structure Job Card View"/>
</p>

<p class="padded">
Selecting the Structure Conversion tool displays a list of all the previously scheduled jobs. From here the user can view the details of a specific job, or create a new one.
</p>

## Job Detail View

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/job-detail-view.png" alt="Structure Job Detail View"/>
</p>

<div class="padded">
When a job has been scheduled and is running or completed, it can be viewed via the Job Detail view. This will show the paths selected for processing and their current state.

If the job has been scheduled but not executed, or is mid-execution, you can refresh the page to check the status/state.
</div>

### Job (Buckets) List

If a given scheduling request has over 500 paths that need to be processed, then multiple jobs will be scheduled. The job detail view will allow users to select which job to view the details, using the left panel.

## Job Creation

The following configuration options are available when scheduling a Structure Conversion Job.

### Job Title

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/job-title.png" alt="Structure Job Title"/>
</p>

As with all the tools, the Structure conversion tool requires a name for the Job to be created. This helps with identifying the job once it is scheduled and completed.

### Page Handling
A page structure conversion has the option of processing the pages using different options.

#### No Handling

This option will simply process the page as-is in place in the repository.

#### Restore

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/structure/images/page-handling-restore.png" alt="Page Handling Restore Selected"/>
</p>

<div class="padded">
This option will restore a page to a previous state. When a page is processed using the All-in-One conversion or Structure conversion tools, a version is created prior to the execution

This option will restore the page to the most recent version that was marked as "Pre-Modernization." This allows for reprocessing pages based on updated rules.
</div>

#### Copy to Target

<p class="image right small">
    <img src="{{ site.baseurl }}/pages/structure/images/page-handling-copy.png" alt="Page Handling Copy Selected"/>
</p>

This option will copy a page from the source location, to a new path in the repository. Each page must exist under the `source` path selection. When processing that portion of the path will be replaced with the `target` path. 

The user scheduling the job must have *write* access to the target location in the repository, or the job will not be scheduled.

The target path must not have a page that already exists in that location, or that page conversion will fail.

### Page Selection

<p class="image right">
    <img src="{{ site.baseurl }}/pages/structure/images/page-selection.png" alt="Structure Page List"/>
</p>

The final step is to select the pages to process. The list will show how many conversion structure rules are found that match the page (Hint, should only be one).

The user may select as many pages as they want; depending on the number more than one job may be scheduled.

