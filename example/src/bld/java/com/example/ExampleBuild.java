package com.example;

import rife.bld.BuildCommand;
import rife.bld.Project;
import rife.bld.extension.SpotBugsOperation;

import java.util.List;

import static rife.bld.dependencies.Repository.*;
import static rife.bld.dependencies.Scope.*;

public class ExampleBuild extends Project {
    public ExampleBuild() {
        pkg = "com.example";
        name = "example";
        version = version(0, 1, 0);

        downloadSources = true;
        repositories = List.of(MAVEN_CENTRAL, RIFE2_RELEASES);
        scope(provided)
                .include(dependency("com.github.spotbugs", "spotbugs-annotations", version(4, 9, 8)));
        scope(test)
                .include(dependency("org.junit.jupiter", "junit-jupiter", version(6, 0, 1)))
                .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(6, 0, 1)));
    }

    public static void main(String[] args) {
        new ExampleBuild().start(args);
    }

    @BuildCommand(summary = "Runs SpotBugs on this project")
    public void spotbugs() throws Exception {
        new SpotBugsOperation()
                .fromProject(this, true) // check src/main and src/test
                .home("spotbugs-4.9.8")
                .exclude("excludeFilter.xml")
                .execute();
    }
}