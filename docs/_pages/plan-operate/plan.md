---
layout: doc-page
title: Modernization Project Planing
description: The first step in your Modernization effort.
order: 1
image: project-planning.svg
---

## Overview

Just as an AEM Upgrade project needs to be managed properly to ensure the continued stability of a deployment, so too does a modernization effort. The execution of the tools contained in this suite will irrevocably transform a site's content. Improper planning & validation can lead to an inability for Authors manage content, or worse: content loss.

This guide is intended to provide a basis for creating a project plan, defining the objective, phases, and output of the modernization activity. While complete modernization can be achieved through the execution of one large effort, it is highly recommended that the process be executed iteratively. Identify the largest scope of changes that fall within an acceptable risk tolerance.

## Plan Phases

The following are the phases generally executed during a modernization effort.  

### Author Training

Part of a modernization effort is moving from Static to Editable templates. The process for creating new templates differs significantly through this evolution. It's important that the Authors are trained in new features, and prepare for any changes to their operational processes that these updates might bring.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/author-training-plan.png" alt="Author Training Plan"/>
</p>

### Testing & Validation

While the services in the modernization tool suite have been tested, they operate exactly as the provided configurations specify. Each AEM implementation is unique, and thus the configurations will be specific to a given project. Additionally, this does not account for any custom service implementations. Therefore it is critical to identify what changes will be made during a tool execution, so that a test plan can be created for validation.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/test-validate-plan.png" alt="Testing & Validation Plan"/>
</p>

The test data should contain a representative sample of content from production to validate all expected content transformations. This will ensure when the process is executed on the full content set, there is a high degree of confidence it a successful outcome.

### Execution & Rollback Planning

The execution of the plan will completely and irrevocably change the content in the repository. It is essential that a plan be created for preparing the content, executing the transformation, and then merging the updated content into the Production system. A rollback plan should also be created to revert to a known valid state, should anything go awry. 

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/execution-rollback-plan.png" alt="Execution & Rollback Plan"/>
</p>

Each of these plans, the rollout and rollback, will be unique to the individual modernization project. Each customer's needs and approach will differ on where the content will be modified, and how it will moved/merged back to the system of record.

### Evaluating Changes

In this phase the project team will need to identify what changes they are targeting for this project, and therefore what needs to be created to support those changes.

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/evaluation-plan.png" alt="Evaluation Plan"/>
</p>

We have provided a guide on <a href="{{ site.baseurl }}/pages/plan-operate/evaluation.html">evaluating Templates & Components</a> to help. 

### Project Planning

With the output from the previous phases, a full project plan can be defined to outline timelines for building, testing, and validating the transformations. 

<p class="image">
    <img src="{{ site.baseurl }}/pages/plan-operate/images/project-plan.png" alt="Project Plan"/>
</p>

This project plan should include:

* Configuration, Development & Test Plans
* QA Transformation cycle
* QA Validation & Fix
* Production Execution Plan (clone or other)
* Go Live

