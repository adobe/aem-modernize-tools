---
layout: doc-page
title: Usage
tool: Component Converter
description: How to use the Component conversion tool.
order: 2
image: using-the-tools.svg
videoId: 338788
---

<p class="padded">
This page describes how to use the Component conversion tool. The tool can be found via the AEM Tools primary navigation, and selecting the <code>AEM Modernize Tools</code> menu item.
</p>


## Job Listing

<p class="image right">
    <img src="{{ site.baseurl }}/pages/component/images/job-card-view.png" alt="Component Job Card View"/>
</p>

<p class="padded">
Selecting the Component Conversion tool displays a list of all the previously scheduled jobs. From here the user can view the details of a specific job, or create a new one.
</p>

## Job Detail View

<p class="image right">
    <img src="{{ site.baseurl }}/pages/component/images/job-detail-view.png" alt="Component Job Detail View"/>
</p>

<p class="padded">
When a job has been scheduled and is running or completed, it can be viewed via the Job Detail view. This will show the paths selected for processing and their current state.

If the job has been scheduled but not executed, or is mid-execution, you can refresh the page to check the status/state.
</p>

### Job (Buckets) List

If a given scheduling request has over 500 paths that need to be processed, then multiple jobs will be scheduled. The job detail view will allow users to select which job to view the details, using the left panel.

## Job Creation

The following configuration options are available when scheduling a Component Conversion Job.

### Job Title

<p class="image right">
    <img src="{{ site.baseurl }}/pages/component/images/job-title.png" alt="Component Job Title"/>
</p>

<p class="padded">
As with all the tools, the Component conversion tool requires a name for the Job to be created. This helps with identifying the job once it is scheduled and completed.
</p>

### Page Selection

<p class="image right">
    <img src="{{ site.baseurl }}/pages/component/images/page-selection.png" alt="Component Page List"/>
</p>

<p class="padded">
The final step is to select the pages to process. The list will show how many conversion component rules are found that match the page's components.

The user may select as many pages as they want; depending on the number more than one job may be scheduled.
</p>
