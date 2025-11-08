# [bld](https://rife2.com/bld) Extension to Perform Static Code Analysis with [SpotBugs](https://spotbugs.github.io/)

[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![bld](https://img.shields.io/badge/2.3.0-FA9052?label=bld&labelColor=2392FF)](https://rife2.com/bld)
[![Release](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/releases/com/uwyn/rife2/bld-spotbugs/maven-metadata.xml?color=blue)](https://repo.rife2.com/#/releases/com/uwyn/rife2/bld-spotbugs)
[![Snapshot](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/snapshots/com/uwyn/rife2/bld-spotbugs/maven-metadata.xml?label=snapshot)](https://repo.rife2.com/#/snapshots/com/uwyn/rife2/bld-spotbugs)
[![GitHub CI](https://github.com/rife2/bld-spotbugs/actions/workflows/bld.yml/badge.svg)](https://github.com/rife2/bld-spotbugs/actions/workflows/bld.yml)

To install the latest version, add the following to the `lib/bld/bld-wrapper.properties` file:

```properties
bld.extension-pmd=com.uwyn.rife2:bld-spotbugs
```

For more information, please refer to the [extensions](https://github.com/rife2/bld/wiki/Extensions) documentation.

To install a binary distribution of SpotBugs please referer to its
[installation instruction](https://spotbugs.readthedocs.io/en/latest/installing.html).

## Check Source with SpotBugs

To check for bugs in the main source code, add the following to your build file:

```java
@BuildCommand(summary = "Runs SpotBugs on this project")
public void spotbugs() throws Exception {
    new SpotBugsOperation()
            .fromProject(this)
            .home("/path/to/spotbugs/home")
            .execute();
}
```

```console
./bld compile spotbugs
```

The output will look something like:

```console
[spotbugs] auxclasspath[build/main, lib/compile/foo-2.3.0.jar, ...]
[spotbugs] sourcepath[src/main/java, src/main/resources...]
[spotbugs] analyze[build/main]
[spotbugs] Found 17 potential bugs
[spotbugs] file:///dev/example/src/main/org/example/Example.java:251
    Method: adjustPriorities, Class: com.example.Example, Priority: 2, Rank: 18, Type: EI_EXPOSE_REP, Category: MALICIOUS_CODE
        --> May expose internal representation by returning reference to mutable object
[spotbugs] file:///dev/example/src/main/org/example/Example.java:343
    Method: foo, Class: com.example.Example, Priority: 2, Rank: 17, Type: DCN_NULLPOINTER_EXCEPTION, Category: STYLE
        --> NullPointerException caught
...
```

To also check the test source code, add the following to your build file:

```java
@BuildCommand(summary = "Runs SpotBugs on this project")
public void spotbugs() throws Exception {
    new SpotBugsOperation()
            .fromProject(this, true) // check src/main and src/test
            .spotBugsJar("/path/to/spotbugs/home/lib/spotbugs.jar")
            .execute();
}
```

```console
./bld compile spotbugs
```

Please check the [SpotBugsOperation documentation](https://rife2.github.io/bld-spotbugs/rife/bld/extension/SpotBugsOperation.html#method-summary) for all available configuration options.
