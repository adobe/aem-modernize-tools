# Contributing to AEM Modernization Tools

Here are some of the ways you can contribute to AEM Modernization Tools:

* Contribute new code: services, rules, whatever…
* File bug reports.
* Fix bugs.
* Develop ideas for new features and file them.
* Participate in code reviews.

## How to Contribute Code

New code contributions should be primarily made using GitHub pull requests. This involves you creating a personal fork of the project, adding your new code to a branch in your fork, and then triggering a pull request using the GitHub web UI (it's easier than it sounds). A pull request is both a technical process (to get the code from your branch into the main repository) and a framework for performing code reviews.

The branch naming conventions in your fork should follow projects name conventions.

*For new features:*

* **feature/**meaningful-feature-name
  * ex. `feature/component-rewrite-rule`

*For defects:*

* **defect/**feature-name/short-name-of-problem-being-fixed
  * ex. `defect/parsys-rewrite-rule/nesting-problem`


In many cases, it is worth having a discussion with the community before investing serious time in development. For these cases, create a [GitHub issue](https://github.com/adobe/aem-modernize-tools/issues) of type "feature review" with a description of the problem you are trying to solve.

If you already have commit rights, bug fixes and minor updates should just be made in the shared repository itself.

When making any change, either directly or via a pull request, please be sure to add an entry to the CHANGELOG file.

There's a good guide to performing pull requests at [https://help.github.com/articles/using-pull-requests](https://help.github.com/articles/using-pull-requests). In the terms used in that article, we use both the **Fork & Pull** and the **Shared Repository Model**.

### Before Contributing Code

The best pull request are small and focused. Don't try to change the world in one pull request. And while the focus of this project is reusability, that doesn't mean that every option under the sun needs to be available. Stick to the 80/20 rule and provide a way to extend for that extra 20% on a project.

* Check code quality proactively by using [CodeClimate CLI](https://github.com/codeclimate/codeclimate).
* Ensure license is applied correctly by running `mvn license:update-file-header`
* Add JUnit test for Java code. Our coverage ratio isn't great, but we don't want it to get worse.
* This tool is not intented to do *anything* automatically. Every action is a deliberate execution by the AEM actors to perform the transformation. **Keep to this design.**
* There should not be a need to rely on any AEM Services/Features: this tool set should be self-contained.

## Version Compatibility

The _master_ branch of AEM Modernization Tools (1.x) is expected to be installable on AEM 6.3+. 

## Participating in Code Reviews

Even if you don't have time to contribute code, reviewing code contributed by other people is an option. To do this, go to [https://github.com/adobe/aem-modernize-tools/pulls](https://github.com/adobe/aem-modernize-tools/pulls) to see the open pull requests.
