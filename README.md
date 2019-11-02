# InfluxDB Plugin for Graylog

An output plugin for integrating [InfluxDB](http://www.influxdata.com) with [Graylog](https://www.graylog.org).

**Required Graylog version:** 2.0 and later

## Getting started

This project is using Maven 3 and requires Java 8 or higher.

## Installation

[Download the plugin](https://github.com/nitrotm/graylog-plugin-influx)

* Clone this repository.
* Run `mvn package` to build a JAR file.
* Copy generated JAR file in target directory to your Graylog plugin directory.
* Restart the Graylog.

The plugin directory is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

## Usage

You should now be able to add an Influx output to your streams through the option `Manage outputs`.

Parameters:

* influx_host: hostname.

## Build

This project is using Maven 3 and requires Java 8 or higher.

You can build a plugin (JAR) with `mvn package`.

## Plugin Release (TODO)

We are using the maven release plugin:

```
$ mvn release:prepare
[...]
$ mvn release:perform
```

This sets the version numbers, creates a tag and pushes to GitHub. Travis CI will build the release artifacts and upload to GitHub automatically.
