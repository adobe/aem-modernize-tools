---
layout: doc-page
title: Security & Permissions
description: Granting Access to the Modernize Tools
order: 5
image: plan-and-operate.svg
---

## Overview

By default, the Modernize Tools are only accessible to Administrators. Since the conversions are run as scheduled jobs, they are performed using a service user `aem-modernize-convert-service`. This user is granted write access to the necessary paths within the repository. Therefore, Authors will not be able to access the tools UI nor schedule a job through an API without the proper permissions.

## Requisite Permissions

### All Jobs

The user must have the specified access to the following paths:

* **/apps/aem-modernize**
  * `jcr:read`
* **/apps/cq/core/content/nav/tools/aem-modernize**
  * `jcr:read`
* **/var/aem-modernize/job-data**
  * `jcr:read`
  * `rep:write`

The first two will grant the user access to see the user interface for configuring jobs. The third will grant the user access to create a job definition and initiate it.


### All-in-One Jobs

This job type is an aggregate of the other jobs, therefore users must have all the rights defined in the remaining job types.


### Page Structure Jobs

The user must have `jcr:read` and `rep:write`, to the paths of the pages that they which to convert. This can usually be granted by adding the user to an _Authors_ group.

Additionally, if the user chooses to select a target path for copying pages prior to transformation; then the user must have the same rights to that path as well.


### Component Conversion Jobs

The user must have `jcr:read` and `rep:write`, to the paths of the pages that they which to convert. This can usually be granted by adding the user to an _Authors_ group.


### Policy Import Jobs

The user must have `jcr:read` to the pages referenced by the job. In addition, the user must also have `jcr:read` and `rep:write` to the Page's *design location* (i.e. `/etc/design/...`), as well as the desired `/conf` path output location.   
