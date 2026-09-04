# Mattermost plugin for Jenkins

This plugin allows users to send build notifications to a self-hosted
[Mattermost](http://www.mattermost.org/) installation.

It is based on a fork of the Slack plugin:
https://github.com/jenkinsci/slack-plugin/

The Slack plugin was a fork of the HipChat plugin:
https://github.com/jlewallen/jenkins-hipchat-plugin

Which in turn, was a fork of the Campfire plugin.

It includes [Jenkins Pipeline](https://github.com/jenkinsci/workflow-plugin) support as of version 2.0:

```
mattermostSend color: 'good', message: 'Message from Jenkins Pipeline', text: 'optional for @here mentions and searchable text'
```

# Jenkins Instructions

1. The first step is to set up a Mattermost server
2. Then Configure an incoming webhook
3. Install this plugin on your Jenkins server
4. **Add it as a Post-build action** in your Jenkins job.

# Developer instructions

Make sure you have Maven and JDK installed.

Run unit tests

    mvn test

Run findbugs:

    mvn findbugs:check

Create an HPI file to install in Jenkins (HPI file will be in `target/mattermost.hpi`).

    mvn package
