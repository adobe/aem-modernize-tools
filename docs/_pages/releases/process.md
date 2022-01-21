---
layout: content-page
title: Release Process
---

This tool is released through a [Github Workflow](https://github.com/adobe/aem-modernize-tools/actions). All aspects of keeping documentation updated and releasing are automated to reduce overhead.

### [Build & Verify](https://github.com/adobe/aem-modernize-tools/actions/workflows/verify.yaml)

Runs on every PR to ensure stability of changes. Used by PR Review to ensure status checks for test success and code coverage metrics are met.

[Source](https://github.com/adobe/aem-modernize-tools/blob/main/.github/workflows/verify.yaml)

### [Snapshot Deploy](https://github.com/adobe/aem-modernize-tools/actions/workflows/snapshot-deploy.yaml)

Runs after each PR is merged to the main branch. This will deploy an updated SNAPSHOT release with the changes to Maven Central's snapshot repo, allowing developers to test the latest changes prior to a release.

[Source](https://github.com/adobe/aem-modernize-tools/blob/main/.github/workflows/snapshot-deploy.yaml)

### [Tag & Release](https://github.com/adobe/aem-modernize-tools/actions/workflows/release.yaml)

Manually initiated workflow to perform a release. This will perform a Maven release - creating a tag. By default, it will automatically use the next version of the project (i.e. bugfix). Once the tag is created the workflow will create a GitHub release, associating all build artifacts, and publish those same artifacts to Maven Central.

The initiator can specify the new version to use when starting the pipeline.

[Source](https://github.com/adobe/aem-modernize-tools/blob/main/.github/workflows/release.yaml)


### [Update Javadoc](https://github.com/adobe/aem-modernize-tools/actions/workflows/javadocs.yaml)

Runs after each release workflow. This builds the site's JavaDocs to reflect the latest changes. This ensures the API is always up-to-date with the releases.

[Source](https://github.com/adobe/aem-modernize-tools/blob/main/.github/workflows/javadocs.yaml)


### [Update Changelog](https://github.com/adobe/aem-modernize-tools/actions/workflows/changelog.yaml)

Runs after each PR and Release to update the [full Changelog](https://github.com/adobe/aem-modernize-tools/blob/main/CHANGELOG.md). By running after PR merges, the PR information is included in the Changelog as soon as the main branch has been updated. Runs after a release will update the Changelog to display the release details.

[Source](https://github.com/adobe/aem-modernize-tools/blob/main/.github/workflows/changelog.yaml)


