# Phabricator Branch Source plugin for Jenkins

## Introduction

The Phabricator Branch Source plugin allows you to create a new project based on a repository that is available in a
[Phabricator](https://www.phacility.com/phabricator/) instance. This plugin scans the repository for all branches
and turns them into Pipeline jobs. To define a Pipeline job, you create a Pipeline script in a `Jenkinsfile` in the root
directory of the project or branch. Each repository becomes a folder in Jenkins with each branch with a `Jenkinsfile` as
a different job.

### Differential Revisions

The Phabricator Branch Source plugin is able to build
[Differential revisions](https://www.phacility.com/phabricator/differential/) containing a `Jenkinsfile` and with
their changes staged in a Staging Area. Each revision will be added to Jenkins as a unique job.

**This does not report the status of a build back to Phabricator.**

## Configuration

This plugin adds a new type of Multibranch Project. To use this plugin, you must have a running Phabricator install,
credentials with Phabricator API access and credentials that allow access to the repository. Setup your job as follows:

Go to Jenkins > New Item > Multibranch Pipeline. Next is Add source. Choose the Phabricator source.

Select or add new Phabricator Credentials. The repositories will load in the next selectbox. Select a repository and
configure the Repository Credentials.

Save, and wait for the Multibranch Pipeline Scan to run. Job progress is displayed to the left hand side. When
everything is done, you may need to refresh the page to see your branches and revisions.
