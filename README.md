This fork was mainly created to deploy Mongobee on OSGi (tested it with Karaf 3.0.2). It also reduces complexity by removing Spring-related (and a few others...) stuff.
### What's in this fork:
* OSGi ready bundle.
* Spring-free, no refs or deps to anything Spring-related.
* Switch to JUL.


=======


![mongobee](https://raw.githubusercontent.com/mongobee/mongobee/master/misc/mongobee_min.png)
=======

### What for?

mongobee is a Java tool which can help you to *manage changes* in your MongoDB and *synchronize* them with your application.
The concept is very similar to other db migration tools such as [Liquibase](http://www.liquibase.org),
[Flyway](http://flywaydb.org), [mongeez](https://github.com/secondmarket/mongeez) but *without XML files*.

The goal is to keep this tool simple and comfortable to use.

### What's special?

mongobee provides new approach for adding changes (change sets) based on Java classes and methods with appropriate annotations.

### How to use?

Check out [wiki page](https://github.com/aaranost/mongobee/wiki/How-to-use-mongobee)


---
[![Build Status](https://travis-ci.org/mongobee/mongobee.svg?branch=master)](https://travis-ci.org/mongobee/mongobee) [![Coverity Scan Build Status](https://scan.coverity.com/projects/2721/badge.svg)](https://scan.coverity.com/projects/2721) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.mongobee/mongobee/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mongobee/mongobee)
