# InfluxDB Plugin for Graylog

[![Github Downloads](https://img.shields.io/github/downloads/nitrotm/graylog-plugin-influxdb/total.svg)](https://github.com/nitrotm/graylog-plugin-influxdb/releases)
[![GitHub Release](https://img.shields.io/github/release/nitrotm/graylog-plugin-influxdb.svg)](https://github.com/nitrotm/graylog-plugin-influxdb/releases)
[![Build Status](https://travis-ci.com/nitrotm/graylog-plugin-influxdb.svg?branch=master)](https://travis-ci.com/nitrotm/graylog-plugin-influxdb)
[![License](https://img.shields.io/github/license/nitrotm/graylog-plugin-influxdb)](https://www.apache.org/licenses/LICENSE-2.0.txt)

An output plugin for integrating [InfluxDB](http://www.influxdata.com) with [Graylog](https://www.graylog.org).

**Required Graylog version:** 2.0 and later

## Getting started

This project is using Maven 3 and requires Java 8 or higher.

## Installation

[Download the release](https://github.com/nitrotm/graylog-plugin-influx)

* Copy JAR file in target directory to your Graylog plugin directory.
* Restart the Graylog.

The plugin directory is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

## Usage

You should now be able to add an InfluxDB output to your streams through the option `Manage outputs`.

Parameters:

* influx_url: InfluxDB server url (eg. `http://localhost:8086`)
* influx_user, influx_password: InfluxDB credentials
* influx_database: InfluxDB database name
* influx_measurement: InfluxDB measurement name
* influx_filters: list of filters to match against fields
* influx_tags: list of fields attached as tags on data points (eg. `source,service`)
* influx_fields: list of numeric fields attached as values on data points (eg. `duration,started_at`)

### Filters

A message will be streamed to InfluxDB only if all filters are matching positively the source message.

* `myfield`: match if field `myfield` exists in message.
* `!myfield`: match if field `myfield` doesn't exist in message.
* `myfield=val`: match if field `myfield` exists and is equals to `val`.
* `myfield!=val`: match if field `myfield` doesn't exist nor is equal to `val`.
* `myfield~regex`: match if field `myfield` exists and matches regular expression `regex`.
* `myfield!~regex`: match if field `myfield` doesn't exist nor matches regular expression `regex`.

### Tags

Tags are simply mapped as strings from source message fields.

### Fields

Fields are mapped by default as numbers from source message fields. To override the InfluxDB field type:

* `B:myfield`: map as boolean
* `I:myfield`: map as integer (long)
* `F:myfield`: map as float (double)
* `S:myfield`: map as string

Note that the field's type must be consistent across measurements.

It's possible to map a text-field to a numeric value (`0` or `1`) by matching a specific value for equality (eg. `duration,status=up` will store graylog message field `duration` as a number and map field `status` to `1` when the value is `up` otherwise `0`).

Alternatively, the boolean match can be done with a regular expression (eg. `status~one|two` will map field `status` to `1` when the value contains `one` or `two` otherwise `0`).

## Build

This project is using Maven 3 and requires Java 8 or higher.

* Clone this repository.
* Run `mvn package` to build a JAR file.

## Plugin Release

We are using the maven release plugin:

```
$ mvn release:prepare
[...]
$ mvn release:perform
```

This sets the version numbers, creates a tag and pushes to GitHub. Travis CI will build the release artifacts and upload to GitHub automatically.
