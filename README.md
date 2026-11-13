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

Requires Jenkins 2.555.3 or newer running on Java 21 or newer.

1. The first step is to set up a Mattermost server
2. Then Configure an incoming webhook
3. Install this plugin on your Jenkins server
4. **Add it as a Post-build action** in your Jenkins job.

# Developer instructions

## Nix environment

Use a recent Nixpkgs channel that provides `jdk21`. Enter the development shell:

```sh
nix-shell
```

The shell provides the full JDK 21, Maven, Git, and fonts for the browser-based
Jenkins tests. It sets `JAVA_HOME` automatically.

To run the tests and package the plugin without entering an interactive shell:

```sh
nix-shell --run 'mvn clean package'
```

With [direnv](https://direnv.net/) installed and hooked into your shell, run
`direnv allow` once in this directory. The `.envrc` file then loads `shell.nix`
automatically when you enter the project.

## Manual environment

Make sure you have Maven 3.9.6 or newer and a full JDK 21 installed, including
font support for the browser-based Jenkins tests.
Set `JAVA_HOME` to your JDK 21 installation before running Maven:

```sh
export JAVA_HOME=/path/to/jdk21
```

## Development commands

Start a local Jenkins instance with the plugin:

    mvn hpi:run

Run unit tests

    mvn test

Run SpotBugs:

    mvn spotbugs:check

Create an HPI file to install in Jenkins (HPI file will be in `target/mattermost.hpi`).

    mvn package
