Java Client etc for Fedora 4
============================

This project uses the Fedora 4 java client library for walking a repository and verifying are resources response successfully.

[![Build Status](https://travis-ci.org/awoods/fcrepo-java-client-etc.png?branch=master)](https://travis-ci.org/awoods/fcrepo-java-client-etc)

Usage Examples
--------------

Walking a repository:
```java
java -jar fcrepo-java-client-etc -b http://host:port/context/rest/ -u username -p password
```

Getting description of command line options
```java
java -jar fcrepo-java-client-etc -b http://host:port/context/rest/ -u username -p password
```