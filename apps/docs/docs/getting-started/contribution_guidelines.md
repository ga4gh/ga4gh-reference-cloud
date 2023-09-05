---
sidebar_position: 4
---

# Contributing Guidelines

Thank you for your interest in contributing to the GA4GH Starter Kit, we appreciate it!

There are 2 ways to contribute to the development of the Starter Kit:
1. By submitting an **issue**, which begins a discussion about changes to be made to the codebase.
2. By submitting a **pull request**, which is a concrete proposal of a code change that solves an issue.

## Issues

The GA4GH Starter Kit is split across multiple Github repositories, each with their own issue page. Each issue page is a forum to discuss both major and minor issues related to developing the codebase. It also serves as a means for collaborating with interested groups and contributors that will ultimately drive improvements of the Starter Kit.

Examples of the types of issues that can be submitted are:

* Attributes to be added, revised, or deleted in certain data models
* Modifications to the behavior of an API endpoint
* Performance-related issues of the API (e.g. slow API calls)
* Reported bugs that need to be fixed

See the [Issue / Pull request table](#issue-and-pull-request-links) for links to the issue page for each Starter Kit codebase.

## Pull Requests

Code contribution to the Starter Kit repositories is done via Github pull requests. For reference, [here is an overview from Github](https://docs.github.com/en/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request) on how to create a pull request.

Some general rules to follow for the Starter Kit repositories:

* Create an issue in Github to track your work and start a conversation. Make a note of the number, you'll need it when naming your branch below.
* Fork the repository to your personal or organizational Github namespace. Please do not track feature branches on the `ga4gh` fork. 
* We follow GitFlow, which means:
  + We use a feature branch strategy. Feature branches must be started off of the `ga4gh:develop` branch, and pull requests are to be made to the `ga4gh:develop` branch.
  + Release branches are started off the `ga4gh:develop` branch. After the release has been made, the release branch is merged into both `ga4gh:main` and `ga4gh:develop`.
* Create a "feature" branch for each update that you're working on. The branches should start with "feature/issue-[number]-[description]". For example "feature/issue-123-improve-endpoint".
* When you're happy with your feature branch, make a Pull Request to the `ga4gh:develop` fork and branch. Provide a helpful description of the changes made and link back to the original issue.
* Keep pull requests as small as possible, as large pull requests are hard to review. Try to make your branches/pull requests self-contained and incremental.
* The first line of commit messages should be a short (<80 character) summary, followed by an empty line and then any details you want to share about the commit.

See the [Issue / Pull request table](#issue-and-pull-request-links) for links to the pull request page for each Starter Kit codebase.

## Travis CI

We use Travis CI for automated testing and builds. If you enable Travis to run on your fork of any Starter Kit repo, you will see automated builds.

Automated builds will also run for a pull request raised to the `ga4gh` fork: once when the PR is created, and each time there is a commit/push made to the incoming branch of the PR.

## Releases

From time to time the group will make a release. This is done with the GitFlow release process, which involves creating a "release-[version]" branch off of `ga4gh:develop`. Once the release codebase is tested (and any bugs fixed as necessary), artifacts are pushed to Github and Dockerhub, and the release branch is merged back into `main` and `develop`. The release branch is removed after a successful GitFlow release.

## Issue and Pull Request Links

The following table links out to the issue and pull request pages for each Starter Kit codebase:

| Name | Issues | Pull Requests |
|------|--------|---------------|
| GA4GH Starter Kit DRS | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-drs/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-drs/pulls) |
| GA4GH Starter Kit WES | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-wes/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-wes/pulls) |
| GA4GH Starter Kit Passport UI | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-passport-ui/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-passport-ui/pulls) |
| GA4GH Starter Kit Passport Broker | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-passport-broker/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-passport-broker/pulls) |
| GA4GH Starter Kit Data Connect | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-data-connect/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-data-connect/pulls) | 
| GA4GH Starter Kit UI | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-ui/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-ui/pulls) |
| GA4GH Starter Kit Utils | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-utils/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-utils/pulls) |
| GA4GH Starter Kit Common | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-common/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-common/pulls) |
| GA4GH Starter Kit Docs | [Issues](https://github.com/ga4gh/ga4gh-starter-kit-docs/issues) | [Pull Requests](https://github.com/ga4gh/ga4gh-starter-kit-docs/pulls) |