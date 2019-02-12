# Phabricator SCM plugin for Jenkins

## Introduction

The Phabricator SCM plugin allows you to create a new project based on a repository that is available in a
[Phabricator](https://www.phacility.com/phabricator/) instance. This plugin finds branches and revisions within the
repository. When possible it turns those branches and revisions into
[Pipeline jobs](https://jenkins.io/doc/book/pipeline/).

To define a Pipeline job, you create a Pipeline script in a `Jenkinsfile` in the root directory of the project or
branch. The repository becomes a folder in Jenkins with each branch or revision with a `Jenkinsfile` as
a separate job.

### Differential Revisions

The Phabricator Branch Source plugin is able to build
[Differential revisions](https://www.phacility.com/phabricator/differential/) containing a `Jenkinsfile` only if
their changes are staged in a Staging Area. Each revision will be added to Jenkins as a unique job.

Currently, applying Differential revisions that are not staged in a Staging Area have a lot of edge cases that are hard
to cover. To apply Differential revisions that are not staged, you might want to have a look at the 
[Phabricator-Jenkins plugin](https://github.com/uber/phabricator-jenkins-plugin).

**Caution** This plugin does not report the status of a build back to Phabricator. For reporting to Phabricator from
a Pipeline job, you could use the 
[`PhabricatorNotifier` build step](https://github.com/uber/phabricator-jenkins-plugin/blob/master/docs/advanced.md#pipeline)
from the Phabricator-Jenkins plugin.

## Configuration

This plugin adds a new type of Multibranch Project. To use this plugin, you must have a running Phabricator install,
credentials with Phabricator API access and credentials that allow access to the repository. Setup your job as follows:

Go to Jenkins > New Item > Multibranch Pipeline.

On the next page click the button Add source. Choose the Phabricator source.

Select or add new Phabricator Credentials. The repositories will load in the next selectbox. Select a repository and
configure the Repository Credentials.

Save, and wait for the Multibranch Pipeline Scan to run. Job progress is displayed to the left hand side. When
everything is done, you may need to refresh the page to see your branches and revisions.
