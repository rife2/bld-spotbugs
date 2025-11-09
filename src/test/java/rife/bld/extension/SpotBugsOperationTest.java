/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rife.bld.extension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import rife.bld.BaseProject;
import rife.bld.extension.spotbugs.Effort;
import rife.bld.extension.spotbugs.Priority;
import rife.bld.extension.testing.LoggingExtension;
import rife.bld.extension.testing.TestLogHandler;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static rife.bld.extension.SpotBugsOperation.INVALID_SPOTBUGS_LOCATION;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class SpotBugsOperationTest {
    private static final String SPOTBUGS_VERSION = "4.9.8";

    static SpotBugsOperation newBaseOperation() {
        return new SpotBugsOperation()
                .fromProject(new BaseProject())
                .home("example/spotbugs-" + SPOTBUGS_VERSION);
    }

    @Test
    @SuppressWarnings("ExtractMethodRecommender")
    void validateSupportedCliCommands() throws IOException {
        var supported = List.of(
                "-adjustExperimental",
                "-adjustPriority",
                "-analyzeFromFile",
                "-applySuppression",
                "-auxclasspathFromFile",
                "-bugCategories",
                "-bugReporters",
                "-choosePlugins",
                "-chooseVisitors",
                "-dontCombineWarnings",
                "-effort",
                "-emacs",
                "-exclude",
                "-excludeBugs",
                "-experimental",
                "-high",
                "-html",
                "-include",
                "-jar",
                "-longBugCodes",
                "-low",
                "-maxHeap",
                "-maxRank",
                "-medium",
                "-nested",
                "-noClassOk",
                "-omitVisitors",
                "-onlyAnalyze",
                "-pluginList",
                "-progress",
                "-projectName",
                "-relaxed",
                "-release",
                "-sarif",
                "-sortByClass",
                "-sourceInfo",
                "-sourcepath",
                "-textui",
                "-timestampNow",
                "-visitors",
                "-workHard",
                "-xml"
        );

        var unsupported = List.of(
                "-auxclasspath",
                "-auxclasspathFromInput",
                "-conserveSpace",
                "-exitcode",
                "-home",
                "-javahome",
                "-jvmArgs",
                "-name",
                "-output",
                "-printConfiguration",
                "-project",
                "-quiet",
                "-reanalyze",
                "-redoAnalysis",
                "-showPlugins",
                "-train",
                "-userPrefs",
                "-useTraining",
                "-version",
                "-xargs",
                "-xdocs"
        );

        var all = new ArrayList<>(supported);
        all.addAll(unsupported);

        var cli = Files.readAllLines(Path.of("src", "test", "resources", "spotbugs-args.txt"));
        assertFalse(cli.isEmpty(), "spotbugs-args is empty");

        cli.forEach(cmd ->
                assertTrue(all.contains(cmd), cmd + " is not supported")
        );
    }

    @Nested
    @DisplayName("Bugs")
    @SuppressWarnings("all")
    class Bugs {
        void nullPointerExceptionCaughtBug() {
            try {
                throw new NullPointerException();
            } catch (NullPointerException e) {
                fail("NullPointerException caught");
            }
        }

        void selfAssignmentBug() {
            var x = 3;
            x = x;
        }
    }

    @Nested
    @DisplayName("Execute Tests")
    class ExecuteTests {
        @SuppressWarnings("LoggerInitializedWithForeignClass")
        private static final Logger LOGGER = Logger.getLogger(SpotBugsOperation.class.getName());
        private static final TestLogHandler TEST_LOG_HANDLER = new TestLogHandler();

        @RegisterExtension
        @SuppressWarnings("unused")
        private static final LoggingExtension LOGGING_EXTENSION = new LoggingExtension(
                LOGGER,
                TEST_LOG_HANDLER,
                Level.FINEST
        );

        @Test
        void execute() {
            LOGGER.setLevel(Level.WARNING);
            var op = newBaseOperation();
            assertThrows(ExitStatusException.class, op::execute);
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage("[spotbugs] Found"));
        }

        @Test
        void executeIgnoreFailureAndSilent() throws Exception {
            newBaseOperation().ignoreFailure(true).silent(true).execute();
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void executeIncludeTest() {
            var op = new SpotBugsOperation()
                    .fromProject(new BaseProject(), true)
                    .home("example/spotbugs-" + SPOTBUGS_VERSION);
            assertThrows(ExitStatusException.class, op::execute);
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage("[spotbugs] Found"),
                    "'[spotbugs]' Found not found");
            assertTrue(TEST_LOG_HANDLER.containsMessage("Class: rife.bld.extension.SpotBugsOperationTest$Bugs"),
                    "SpotBugsOperationTest class not found");
            assertTrue(TEST_LOG_HANDLER.containsMessage("Method: selfAssignmentBug"),
                    "selfAssignmentBug method not found");
            assertTrue(TEST_LOG_HANDLER.containsMessage("Method: nullPointerExceptionCaughtBug"),
                    "nullPointerExceptionCaughtBug method not found");
        }

        @Test
        void executeNoLogging() throws Exception {
            LOGGER.setLevel(Level.OFF);
            newBaseOperation().ignoreFailure(true).execute();
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void executeSilent() {
            var op = newBaseOperation().silent(true);
            assertThrows(ExitStatusException.class, op::execute);
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void executeWithDetailedMessage() {
            LOGGER.setLevel(Level.WARNING);
            var op = newBaseOperation().detailedMessage(true);
            assertThrows(ExitStatusException.class, op::execute);
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage("--> rife.bld.extension"));
        }

        @Test
        void executeWithExclude() {
            var op = newBaseOperation();

            assertThrows(ExitStatusException.class, op::execute);
            assertTrue(TEST_LOG_HANDLER.containsMessage("EI_EXPOSE_REP2"), "EI_EXPOSE_REP2 not found");

            TEST_LOG_HANDLER.clear();

            op.exclude("src/test/resources/excludeFilter.xml");
            assertThrows(ExitStatusException.class, op::execute);

            assertFalse(TEST_LOG_HANDLER.containsMessage("EI_EXPOSE_REP2"), "EI_EXPOSE_REP2 found");
        }

        @Test
        void executeWithIgnoreFailure() {
            assertDoesNotThrow(() -> newBaseOperation().ignoreFailure(true).execute());
            assertTrue(TEST_LOG_HANDLER.containsMessage("[spotbugs] Found"));
        }

        @Test
        void executeWithInvalidHome() {
            var op = new SpotBugsOperation()
                    .fromProject(new BaseProject())
                    .home("lib");

            var e = assertThrows(RuntimeException.class, op::execute);
            assertTrue(e.getMessage().contains(INVALID_SPOTBUGS_LOCATION), "message is " + e.getMessage());
        }

        @Test
        void executeWithInvalidJar() {
            var op = new SpotBugsOperation()
                    .fromProject(new BaseProject())
                    .spotBugsJar("spotbugs.jar");

            var e = assertThrows(RuntimeException.class, op::execute);
            assertTrue(e.getMessage().contains(INVALID_SPOTBUGS_LOCATION), "message is " + e.getMessage());
        }

        @Test
        void executeWithJar() throws Exception {
            new SpotBugsOperation()
                    .fromProject(new BaseProject())
                    .spotBugsJar("example/spotbugs-" + SPOTBUGS_VERSION + "/lib/spotbugs.jar")
                    .ignoreFailure(true)
                    .execute();
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage("[spotbugs] Found"));
        }

        @Test
        void executeWithLineNumbers() {
            var op = newBaseOperation().includeLineNumber(true);
            assertThrows(ExitStatusException.class, op::execute);
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage(".java:"));
        }

        @Test
        void executeWithRank() throws Exception {
            LOGGER.setLevel(Level.WARNING);
            newBaseOperation().maxRank(10).execute();
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage("[spotbugs] No potential bugs found"));
        }

        @Test
        void executeWithTempOutput() throws IOException {
            var output = Files.createTempFile("spotbugs", ".xml").toFile();
            output.deleteOnExit();

            var op = newBaseOperation().output(output);
            assertThrows(ExitStatusException.class, op::execute);
            assertTrue(output.length() > 0, "output file is empty");
            assertTrue(TEST_LOG_HANDLER.containsMessage("[spotbugs] Found"));
        }

        @Test
        void executeWithoutAnalyze() {
            var op = newBaseOperation();
            op.analyze().clear();
            assertThrows(ExitStatusException.class, op::execute);
        }

        @Test
        void executeWithoutAuxClasspath() {
            var op = newBaseOperation();
            op.auxClasspath().clear();
            assertThrows(ExitStatusException.class, op::execute);
        }

        @Test
        void executeWithoutJar() {
            var op = new SpotBugsOperation().fromProject(new BaseProject());
            var e = assertThrows(Exception.class, op::execute);
            assertInstanceOf(RuntimeException.class, e);
            assertTrue(e.getMessage().contains(INVALID_SPOTBUGS_LOCATION), "message is " + e.getMessage());
        }

        @Test
        void executeWithoutLineNumbers() {
            var op = newBaseOperation().includeLineNumber(false);
            assertThrows(ExitStatusException.class, op::execute);
            TEST_LOG_HANDLER.printLogMessages();
            assertTrue(TEST_LOG_HANDLER.containsMessage("[Line "));
        }

        @Test
        void executeWithoutProject() {
            var op = new SpotBugsOperation();
            var e = assertThrows(Exception.class, op::execute);
            assertInstanceOf(IllegalArgumentException.class, e);
            assertTrue(e.getMessage().contains("A project must be specified."),
                    "message is " + e.getMessage());
        }

        @Test
        void executeWithoutSourcePath() {
            var op = newBaseOperation();
            op.sourcePath().clear();
            assertThrows(ExitStatusException.class, op::execute);
        }
    }

    @Nested
    @DisplayName("Getters/Setters Test")
    class GettersSettersTests {
        @Test
        void adjustExperimental() {
            var op = newBaseOperation();

            assertFalse(op.adjustExperimental(), "adjustExperimental should be false");

            op = op.adjustExperimental(true);
            assertTrue(op.adjustExperimental(), "adjustExperimental should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-adjustExperimental"),
                    "-adjustExperimental is not present in command list: " + commandList);
        }

        @Test
        void adjustPriority() {
            var name = "detector1";
            var op = newBaseOperation();

            assertTrue(op.adjustPriorities().isEmpty(), "adjustPriorities is not empty");

            op.adjustPriority(name, 1);
            assertEquals(1, op.adjustPriorities().size(), "size is not 1");
            assertTrue(op.adjustPriorities().contains(name + "=1"), name + "=1 not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-adjustPriority"),
                    "-adjustPriority is not present in command list: " + commandList);
            assertTrue(commandList.contains(name + "=1"),
                    name + "=1 is not present in command list: " + commandList);
        }

        @Test
        void adjustPriorityWithPriorityEnum() {
            var name = "detector1";
            var op = newBaseOperation();

            assertTrue(op.adjustPriorities().isEmpty(), "adjustPriorities is not empty");

            op.adjustPriority(name, Priority.RAISE);
            assertEquals(1, op.adjustPriorities().size(), "size is not 1");
            assertTrue(op.adjustPriorities().contains(name + "=raise"), name + "=raise not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-adjustPriority"),
                    "-adjustPriority is not present in command list: " + commandList);
            assertTrue(commandList.contains(name + "=raise"),
                    name + "=raise is not present in command list: " + commandList);
        }

        @Test
        void analyze() {
            var foo = new File("foo");
            var bar = new File("bar");
            var op = newBaseOperation();

            op.analyze(foo, bar);
            assertEquals(3, op.analyze().size(), "size is not 3");
            assertTrue(op.analyze().contains(foo), "foo not found");
            assertTrue(op.analyze().contains(bar), "bar not found");
        }

        @Test
        void analyzeAsFileArray() {
            var foo = new File("foo");
            var bar = new File("bar");
            var op = newBaseOperation();

            op.analyze(foo, bar);
            assertEquals(3, op.analyze().size(), "size is not 3");
        }

        @Test
        void analyzeAsFileCollection() {
            var foo = new File("foo");
            var bar = new File("bar");
            var op = newBaseOperation();

            op.analyze(List.of(foo, bar));
            assertEquals(3, op.analyze().size(), "size is not 3");
            assertTrue(op.analyze().contains(foo), "foo not found");
            assertTrue(op.analyze().contains(bar), "bar not found");
        }

        @Test
        void analyzeAsPathArray() {
            var foo = Path.of("foo");
            var bar = Path.of("bar");
            var op = newBaseOperation();

            op = op.analyze(foo, bar);
            assertEquals(3, op.analyze().size(), "size is not 3");
        }

        @Test
        void analyzeAsPathCollection() {
            var foo = Path.of("foo");
            var bar = Path.of("bar");
            var op = newBaseOperation();

            op.analyze(List.of(foo, bar));
            assertEquals(3, op.analyze().size(), "size is not 3");
            assertTrue(op.analyze().contains(foo.toFile()), "foo not found");
            assertTrue(op.analyze().contains(bar.toFile()), "bar not found");
        }

        @Test
        void analyzeAsStringArray() {
            var foo = "foo";
            var bar = "bar";
            var op = newBaseOperation();

            op.analyze(foo, bar);
            assertEquals(3, op.analyze().size(), "size is not 3");
        }

        @Test
        void analyzeAsStringCollection() {
            var foo = "foo";
            var bar = "bar";
            var op = newBaseOperation();

            op.analyze(List.of(foo, bar));
            assertEquals(3, op.analyze().size(), "size is not 3");
            assertTrue(op.analyze().contains(new File(foo)), "foo not found");
            assertTrue(op.analyze().contains(new File(bar)), "bar not found");
        }

        @Test
        void applySuppression() {
            var op = newBaseOperation();

            assertFalse(op.applySuppression(), "applySuppression should be false");

            op = op.applySuppression(true);
            assertTrue(op.applySuppression(), "applySuppression should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-applySuppression"),
                    "-applySuppression is not present in command list: " + commandList);
        }

        @Test
        void auxClasspath() {
            var path1 = "/path/one";
            var path2 = "/path/two";
            var op = newBaseOperation();

            assertFalse(op.auxClasspath().isEmpty(), "auxClasspath should empty");

            op.auxClasspath().clear();
            op.auxClasspath(path1, path2);

            assertEquals(2, op.auxClasspath().size(), "size is not 2");
            assertTrue(op.auxClasspath().contains(path1), path1 + " not found");
            assertTrue(op.auxClasspath().contains(path2), path2 + " not found");
        }

        @Test
        void auxClasspathAsCollection() {
            var path1 = "/path/one";
            var path2 = "/path/two";
            var op = newBaseOperation();

            op.auxClasspath(List.of(path1, path2));
            assertEquals(3, op.auxClasspath().size(), "size is not 3");
            assertTrue(op.auxClasspath().contains(path1), path1 + " not found");
            assertTrue(op.auxClasspath().contains(path2), path2 + " not found");
        }

        @Test
        void bugCategories() {
            var cat1 = "SECURITY";
            var cat2 = "PERFORMANCE";
            var op = newBaseOperation();

            assertTrue(op.bugCategories().isEmpty(), "bugCategories is not empty");

            op.bugCategories(cat1, cat2);
            assertEquals(2, op.bugCategories().size(), "size is not 2");
            assertTrue(op.bugCategories().contains(cat1), cat1 + " not found");
            assertTrue(op.bugCategories().contains(cat2), cat2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-bugCategories"),
                    "-bugCategories is not present in command list: " + commandList);
            assertTrue(commandList.contains(cat1 + ',' + cat2),
                    "categories are not present in command list: " + commandList);
        }

        @Test
        void bugCategoriesAsCollection() {
            var cat1 = "SECURITY";
            var cat2 = "PERFORMANCE";
            var op = newBaseOperation();

            assertTrue(op.bugCategories().isEmpty(), "bugCategories is not empty");

            op.bugCategories(List.of(cat1, cat2));
            assertEquals(2, op.bugCategories().size(), "size is not 2");
            assertTrue(op.bugCategories().contains(cat1), cat1 + " not found");
            assertTrue(op.bugCategories().contains(cat2), cat2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-bugCategories"),
                    "-bugCategories is not present in command list: " + commandList);
            assertTrue(commandList.contains(cat1 + ',' + cat2),
                    "categories are not present in command list: " + commandList);
        }

        @Test
        void bugReporters() {
            var rep1 = "xml";
            var rep2 = "-html";
            var op = newBaseOperation();

            assertTrue(op.bugReporters().isEmpty(), "bugReporters is not empty");

            op.bugReporters(rep1, rep2);
            assertEquals(2, op.bugReporters().size(), "size is not 2");
            assertTrue(op.bugReporters().contains(rep1), rep1 + " not found");
            assertTrue(op.bugReporters().contains(rep2), rep2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-bugReporters"),
                    "-bugReporters is not present in command list: " + commandList);
            assertTrue(commandList.contains(rep1 + ',' + rep2),
                    "reporters are not present in command list: " + commandList);
        }

        @Test
        void bugReportersAsCollection() {
            var rep1 = "xml";
            var rep2 = "-html";
            var op = newBaseOperation();

            assertTrue(op.bugReporters().isEmpty(), "bugReporters is not empty");

            op.bugReporters(List.of(rep1, rep2));
            assertEquals(2, op.bugReporters().size(), "size is not 2");
            assertTrue(op.bugReporters().contains(rep1), rep1 + " not found");
            assertTrue(op.bugReporters().contains(rep2), rep2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-bugReporters"),
                    "-bugReporters is not present in command list: " + commandList);
            assertTrue(commandList.contains(rep1 + ',' + rep2),
                    "reporters are not present in command list: " + commandList);
        }

        @Test
        void choosePlugins() {
            var plugin1 = "plugin1";
            var plugin2 = "-plugin2";
            var op = newBaseOperation();

            assertTrue(op.choosePlugins().isEmpty(), "choosePlugins is not empty");

            op.choosePlugins(plugin1, plugin2);
            assertEquals(2, op.choosePlugins().size(), "size is not 2");
            assertTrue(op.choosePlugins().contains(plugin1), plugin1 + " not found");
            assertTrue(op.choosePlugins().contains(plugin2), plugin2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-choosePlugins"),
                    "-choosePlugins is not present in command list: " + commandList);
            assertTrue(commandList.contains(plugin1 + ',' + plugin2),
                    "plugins are not present in command list: " + commandList);
        }

        @Test
        void choosePluginsAsCollection() {
            var plugin1 = "plugin1";
            var plugin2 = "-plugin2";
            var op = newBaseOperation();

            assertTrue(op.choosePlugins().isEmpty(), "choosePlugins is not empty");

            op.choosePlugins(List.of(plugin1, plugin2));
            assertEquals(2, op.choosePlugins().size(), "size is not 2");
            assertTrue(op.choosePlugins().contains(plugin1), plugin1 + " not found");
            assertTrue(op.choosePlugins().contains(plugin2), plugin2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-choosePlugins"),
                    "-choosePlugins is not present in command list: " + commandList);
            assertTrue(commandList.contains(plugin1 + ',' + plugin2),
                    "plugins are not present in command list: " + commandList);
        }

        @Test
        void chooseVisitors() {
            var visitor1 = "visitor1";
            var visitor2 = "-visitor2";
            var op = newBaseOperation();

            assertTrue(op.chooseVisitors().isEmpty(), "chooseVisitors is not empty");

            op.chooseVisitors(visitor1, visitor2);
            assertEquals(2, op.chooseVisitors().size(), "size is not 2");
            assertTrue(op.chooseVisitors().contains(visitor1), visitor1 + " not found");
            assertTrue(op.chooseVisitors().contains(visitor2), visitor2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-chooseVisitors"),
                    "-chooseVisitors is not present in command list: " + commandList);
            assertTrue(commandList.contains(visitor1 + ',' + visitor2),
                    "visitors are not present in command list: " + commandList);
        }

        @Test
        void chooseVisitorsAsCollection() {
            var visitor1 = "visitor1";
            var visitor2 = "-visitor2";
            var op = newBaseOperation();

            assertTrue(op.chooseVisitors().isEmpty(), "chooseVisitors is not empty");

            op.chooseVisitors(List.of(visitor1, visitor2));
            assertEquals(2, op.chooseVisitors().size(), "size is not 2");
            assertTrue(op.chooseVisitors().contains(visitor1), visitor1 + " not found");
            assertTrue(op.chooseVisitors().contains(visitor2), visitor2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-chooseVisitors"),
                    "-chooseVisitors is not present in command list: " + commandList);
            assertTrue(commandList.contains(visitor1 + ',' + visitor2),
                    "visitors are not present in command list: " + commandList);
        }

        @Test
        void debug() {
            var op = newBaseOperation();

            assertFalse(op.debug(), "debug should be false");

            op = op.debug(true);

            assertTrue(op.debug(), "debug should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-Dfindbugs.debug=true"),
                    "-Dfindbugs.debug=true is not present in command list: " + commandList);
        }

        @Test
        void detailedMessage() {
            var op = newBaseOperation();

            assertFalse(op.detailedMessage(), "detailedMessage should be false");

            op = op.detailedMessage(true);
            assertTrue(op.detailedMessage(), "detailedMessage should be true");
        }

        @Test
        void dontCombineWarnings() {
            var op = newBaseOperation();

            assertFalse(op.dontCombineWarnings(), "dontCombineWarnings should be false");

            op = op.dontCombineWarnings(true);
            assertTrue(op.dontCombineWarnings(), "dontCombineWarnings should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-dontCombineWarnings"),
                    "-dontCombineWarnings is not present in command list: " + commandList);
        }

        @Test
        void effort() {
            var op = newBaseOperation();

            assertNull(op.effort(), "effort should be null");

            op = op.effort(Effort.MAX);
            assertEquals(Effort.MAX, op.effort(), "effort should be MAX");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-effort:max"),
                    "-effort:max is not present in command list: " + commandList);
        }

        @Test
        void emacs() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.emacs(), "emacs should be null");

            op = op.emacs(foo);
            assertEquals(foo, op.emacs(), "emacs should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-emacs=" + foo.getAbsolutePath()),
                    "-emacs=foo is not present in command list: " + commandList);
        }

        @Test
        void emacsAsPath() {
            var foo = Path.of("foo");
            var op = newBaseOperation();

            assertNull(op.emacs(), "emacs should be null");

            op = op.emacs(foo);
            assertEquals(foo.toFile(), op.emacs(), "emacs should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-emacs=" + foo.toAbsolutePath()),
                    "-emacs=foo is not present in command list: " + commandList);
        }

        @Test
        void emacsAsString() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.emacs(), "emacs should be null");

            op.emacs(foo.getName());
            assertEquals(foo.getName(), op.emacs().toString(), "emacs should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-emacs=" + foo.getAbsolutePath()),
                    "-emacs=foo is not present in command list: " + commandList);
        }

        @Test
        void exclude() {
            var op = new SpotBugsOperation();
            assertNull(op.exclude());
        }

        @Test
        void excludeAsFile() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.exclude(), "exclude should be null");

            op.exclude(foo);
            assertEquals(foo, op.exclude(), "exclude should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-exclude"),
                    "-exclude is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void excludeAsPath() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.exclude(), "exclude should be null");

            op.exclude(foo.toPath());
            assertEquals(foo, op.exclude(), "exclude should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-exclude"),
                    "-exclude is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void excludeAsString() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.exclude(), "exclude should be null");

            op.exclude(foo.getName());
            assertEquals(foo.getName(), op.exclude().toString(), "exclude should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-exclude"),
                    "-exclude is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void excludeBugs() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.excludeBugs(), "excludeBugs should be null");

            op.excludeBugs(foo);
            assertEquals(foo, op.excludeBugs(), "excludeBugs should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-excludeBugs"),
                    "-excludeBugs is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void excludeBugsAsPath() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.excludeBugs(), "excludeBugs should be null");

            op.excludeBugs(foo.toPath());
            assertEquals(foo, op.excludeBugs(), "excludeBugs should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-excludeBugs"),
                    "-excludeBugs is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void excludeBugsAsString() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.excludeBugs(), "excludeBugs should be null");

            op.excludeBugs(foo.getName());
            assertEquals(foo.getName(), op.excludeBugs().toString(), "excludeBugs should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-excludeBugs"),
                    "-excludeBugs is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void experimental() {
            var op = newBaseOperation();

            assertFalse(op.experimental(), "experimental should be false");

            op = op.experimental(true);
            assertTrue(op.experimental(), "detailedMessage should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-experimental"),
                    "-experimental is not present in command list: " + commandList);
        }

        @Test
        void fileInfo() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.sourceInfo(), "sourceInfo should be null");
            op.sourceInfo(foo.getName());
            assertEquals(foo.getName(), op.sourceInfo().toString(), "sourceInfo should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sourceInfo"),
                    "-sourceInfo is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void fileInfoAsFile() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.sourceInfo(), "sourceInfo should be null");
            op.sourceInfo(foo);
            assertEquals(foo, op.sourceInfo(), "sourceInfo should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sourceInfo"),
                    "-sourceInfo is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void fileInfoAsPath() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.sourceInfo(), "sourceInfo should be null");
            op.sourceInfo(foo.toPath());
            assertEquals(foo, op.sourceInfo(), "sourceInfo should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sourceInfo"),
                    "-sourceInfo is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void fromProject() {
            var project = new BaseProject();
            var op = new SpotBugsOperation().fromProject(project);

            assertEquals("main.xml", op.output().getName(), "output should be main.xml");

            fromProjectDefaultsValidate(op, project);
        }

        void fromProjectDefaultsValidate(SpotBugsOperation op, BaseProject project) {
            assertTrue(op.analyze().contains(project.buildMainDirectory()),
                    "analyze should contain buildMainDirectory");
            assertTrue(op.sourcePath().containsAll(List.of(project.srcMainJavaDirectory().getAbsolutePath(),
                            project.srcMainResourcesDirectory().getAbsolutePath())),
                    "sourcePath should contain srcMainJavaDirectory and srcMainResourcesDirectory");
            assertTrue(op.auxClasspath().containsAll(project.compileMainClasspath()),
                    "auxClasspath should contain compileMainClasspath");
            assertTrue(op.timestampNow(), "timestampNow should be true");
            assertTrue(op.nested(), "nested should be true");
        }

        @Test
        void fromProjectWithTest() {
            var project = new BaseProject();
            var op = new SpotBugsOperation().fromProject(project, true);

            assertEquals("all.xml", op.output().getName(), "output should be all.xml");

            fromProjectDefaultsValidate(op, project);

            assertTrue(op.analyze().contains(project.buildTestDirectory()),
                    "analyze should contain buildTestDirectory");
            assertTrue(op.sourcePath().containsAll(List.of(project.srcTestJavaDirectory().getAbsolutePath(),
                            project.srcTestResourcesDirectory().getAbsolutePath())),
                    "sourcePath should contain srcTestJavaDirectory and srcTestResourcesDirectory");
            assertTrue(op.auxClasspath().containsAll(project.compileTestClasspath()),
                    "auxClasspath should contain compileTestClasspath");
        }

        @Test
        void fromProjectWithoutTest() {
            var project = new BaseProject();
            var op = new SpotBugsOperation().fromProject(project, false);

            assertEquals("main.xml", op.output().getName(), "output should be main.xml");

            fromProjectDefaultsValidate(op, project);
        }

        @Test
        void high() {
            var op = newBaseOperation();

            assertFalse(op.high(), "high should be false");

            op = op.high(true);
            assertTrue(op.high(), "high should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-high"),
                    "-high is not present in command list: " + commandList);
        }

        @Test
        void home() {
            var home = Path.of("example/spotbugs-home");
            var op = newBaseOperation();

            assertNotNull(op.home(), "home should not be null");

            op.home(home);
            assertEquals(home, op.home(), "home should match");
        }

        @Test
        void homeAsFile() {
            var home = new File("example/spotbugs-home");
            var op = newBaseOperation();

            assertNotNull(op.home(), "home should not be null");

            op.home(home);
            assertEquals(home.toPath(), op.home(), "home should match");
        }

        @Test
        void homeAsString() {
            var home = "example/spotbugs-home";
            var op = newBaseOperation();

            assertNotNull(op.home(), "home should not be null");

            op.home(home);
            assertEquals(Path.of(home), op.home(), "home should match");
        }

        @Test
        void html() {
            var foo = new File("foo.html");
            var op = newBaseOperation();

            assertNull(op.html(), "html should be null");

            op.html(foo);
            assertEquals(foo, op.html(), "html should be foo.html");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-html=" + foo.getAbsolutePath()),
                    "-html=foo.html is not present in command list: " + commandList);
        }

        @Test
        void htmlAsPath() {
            var foo = Path.of("foo.html");
            var op = newBaseOperation();

            assertNull(op.html(), "html should be null");

            op.html(foo);
            assertEquals(foo, op.html().toPath(), "html should be foo.html");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-html=" + foo.toAbsolutePath()),
                    "-html=foo.html is not present in command list: " + commandList);
        }

        @Test
        void htmlAsString() {
            var foo = new File("foo.html");
            var op = newBaseOperation();

            assertNull(op.html(), "html should be null");

            op.html(foo.getName());
            assertEquals(foo.getName(), op.html().toString(), "html should be foo.html");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-html=" + foo.getAbsolutePath()),
                    "-html=foo.html is not present in command list: " + commandList);
        }

        @Test
        void htmlWithStylesheet() {
            var foo = new File("foo.html");
            var stylesheet = "fancy.xsl";
            var op = newBaseOperation();

            assertNull(op.html(), "html should be null");

            op.html(foo, stylesheet);
            assertEquals(foo, op.html(), "html should be foo.html");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-html:" + stylesheet + "=" + foo.getAbsolutePath()),
                    "-html:fancy.xsl=foo.html is not present in command list: " + commandList);
        }

        @Test
        void htmlWithStylesheetAsPath() {
            var foo = Path.of("foo.html");
            var stylesheet = "fancy.xsl";
            var op = newBaseOperation();

            assertNull(op.html(), "html should be null");

            op.html(foo, stylesheet);
            assertEquals(foo, op.html().toPath(), "html should be foo.html");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-html:" + stylesheet + "=" + foo.toAbsolutePath()),
                    "-html:fancy.xsl=foo.html is not present in command list: " + commandList);
        }

        @Test
        void htmlWithStylesheetAsString() {
            var foo = new File("foo.html");
            var stylesheet = "fancy.xsl";
            var op = newBaseOperation();

            assertNull(op.html(), "html should be null");

            op.html(foo.getName(), stylesheet);
            assertEquals(foo.getName(), op.html().toString(), "html should be foo.html");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-html:" + stylesheet + "=" + foo.getAbsolutePath()),
                    "-html:fancy.xsl=foo.html is not present in command list: " + commandList);
        }

        @Test
        void ignoreFailures() {
            var op = newBaseOperation();

            assertFalse(op.ignoreFailures(), "ignoreFailures should be false");

            op = op.ignoreFailure(true);

            assertTrue(op.ignoreFailures(), "ignoreFailures should be true");
        }

        @Test
        void include() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.include(), "include should be null");

            op.include(foo);
            assertEquals(foo, op.include(), "include should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-include"),
                    "-include is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void includeAsPath() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.include(), "include should be null");

            op.include(foo.toPath());
            assertEquals(foo, op.include(), "include should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-include"),
                    "-include is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void includeAsString() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.include(), "include should be null");

            op.include(foo.getName());
            assertEquals(foo.getName(), op.include().toString(), "include should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-include"),
                    "-include is not present in command list: " + commandList);
            assertTrue(commandList.contains(foo.getAbsolutePath()),
                    "foo is not present in command list: " + commandList);
        }

        @Test
        void includeLineNumber() {
            var op = newBaseOperation();
            assertTrue(op.includeLineNumber(), "includeLineNumber should be true");

            op.includeLineNumber(false);
            assertFalse(op.includeLineNumber(), "includeLineNumber should be false");
        }

        @Test
        void jvmArgs() {
            var arg1 = "-Xms512m";
            var arg2 = "-Xmx1024m";
            var op = newBaseOperation();

            assertTrue(op.jvmArgs().isEmpty(), "jvmArgs is not empty");

            op.jvmArgs(arg1, arg2);
            assertEquals(2, op.jvmArgs().size(), "size is not 2");
            assertTrue(op.jvmArgs().contains(arg1), arg1 + " not found");
            assertTrue(op.jvmArgs().contains(arg2), arg2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains(arg1),
                    arg1 + " is not present in command list: " + commandList);
            assertTrue(commandList.contains(arg2),
                    arg2 + " is not present in command list: " + commandList);
        }

        @Test
        void jvmArgsAsCollection() {
            var arg1 = "-Xms512m";
            var arg2 = "-Xmx1024m";
            var op = newBaseOperation();

            assertTrue(op.jvmArgs().isEmpty(), "jvmArgs is not empty");

            op.jvmArgs(List.of(arg1, arg2));
            assertEquals(2, op.jvmArgs().size(), "size is not 2");
            assertTrue(op.jvmArgs().contains(arg1), arg1 + " not found");
            assertTrue(op.jvmArgs().contains(arg2), arg2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains(arg1),
                    arg1 + " is not present in command list: " + commandList);
            assertTrue(commandList.contains(arg2),
                    arg2 + " is not present in command list: " + commandList);
        }

        @Test
        void longBugCodes() {
            var op = newBaseOperation();

            assertFalse(op.longBugCodes(), "longBugCodes should be false");

            op = op.longBugCodes(true);
            assertTrue(op.longBugCodes(), "longBugCodes should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-longBugCodes"),
                    "-longBugCodes is not present in command list: " + commandList);
        }

        @Test
        void low() {
            var op = newBaseOperation();

            assertFalse(op.low(), "low should be false");

            op = op.low(true);
            assertTrue(op.low(), "low should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-low"),
                    "-low is not present in command list: " + commandList);
        }

        @Test
        void maxHeap() {
            var op = newBaseOperation();

            assertEquals(0, op.maxHeap(), "maxHeap should be 0");

            op = op.maxHeap(512);
            assertEquals(512, op.maxHeap(), "maxHeap should be 512");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-Xmx"),
                    "-Xmx is not present in command list: " + commandList);
            assertTrue(commandList.contains("512m"),
                    "512m is not present in command list: " + commandList);
        }

        @Test
        void maxRank() {
            var op = newBaseOperation();

            assertEquals(0, op.maxRank(), "maxRank should be 0");

            op = op.maxRank(15);
            assertEquals(15, op.maxRank(), "maxRank should be 15");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-maxRank"),
                    "-maxRank is not present in command list: " + commandList);
            assertTrue(commandList.contains("15"),
                    "15 is not present in command list: " + commandList);
        }

        @Test
        void medium() {
            var op = newBaseOperation();

            assertFalse(op.medium(), "medium should be false");

            op = op.medium(true);
            assertTrue(op.medium(), "medium should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-medium"),
                    "-medium is not present in command list: " + commandList);
        }

        @Test
        void nested() {
            var op = newBaseOperation();

            assertTrue(op.nested(), "nested should be true");

            var commandList = op.executeConstructProcessCommandList();

            assertTrue(commandList.contains("-nested:true"),
                    "-nested:true is not present in command list: " + commandList);

            op = op.nested(false);

            assertFalse(op.nested(), "nested should be false");

            commandList = op.executeConstructProcessCommandList();

            assertFalse(commandList.contains("-nested:true"),
                    "-nested is not present in command list: " + commandList);
        }

        @Test
        void noClassOk() {
            var op = newBaseOperation();

            assertFalse(op.noClassOk(), "noClassOk should be false");

            op = op.noClassOk(true);
            assertTrue(op.noClassOk(), "noClassOk should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-noClassOk"),
                    "-noClassOk is not present in command list: " + commandList);
        }

        @Test
        void omitVisitors() {
            var visitor1 = "visitor1";
            var visitor2 = "visitor2";
            var op = newBaseOperation();

            assertTrue(op.omitVisitors().isEmpty(), "omitVisitors is not empty");

            op = op.omitVisitors(visitor1, visitor2);
            assertEquals(2, op.omitVisitors().size(), "size is not 2");
            assertTrue(op.omitVisitors().contains(visitor1), visitor1 + " not found");
            assertTrue(op.omitVisitors().contains(visitor2), visitor2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-omitVisitors"),
                    "-omitVisitors is not present in command list: " + commandList);
            assertTrue(commandList.contains(visitor1 + ',' + visitor2),
                    "visitors are not present in command list: " + commandList);
        }

        @Test
        void omitVisitorsAsCollection() {
            var visitor1 = "visitor1";
            var visitor2 = "visitor2";
            var op = newBaseOperation();

            assertTrue(op.omitVisitors().isEmpty(), "omitVisitors is not empty");

            op = op.omitVisitors(List.of(visitor1, visitor2));
            assertEquals(2, op.omitVisitors().size(), "size is not 2");
            assertTrue(op.omitVisitors().contains(visitor1), visitor1 + " not found");
            assertTrue(op.omitVisitors().contains(visitor2), visitor2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-omitVisitors"),
                    "-omitVisitors is not present in command list: " + commandList);
            assertTrue(commandList.contains(visitor1 + ',' + visitor2),
                    "visitors are not present in command list: " + commandList);
        }

        @Test
        void onlyAnalyze() {
            var p1 = "pattern1";
            var p2 = "pattern2";

            var op = newBaseOperation();
            assertTrue(op.onlyAnalyze().isEmpty(), "onlyAnalyze is not empty");

            op = op.onlyAnalyze(p1, p2);
            assertEquals(2, op.onlyAnalyze().size(), "size is not 2");
            assertTrue(op.onlyAnalyze().contains(p1), p1 + " not found");
            assertTrue(op.onlyAnalyze().contains(p2), p2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-onlyAnalyze"),
                    "-onlyAnalyze is not present in command list: " + commandList);
            assertTrue(commandList.contains(p1 + ',' + p2),
                    "patterns are not present in command list: " + commandList);

        }

        @Test
        void onlyAnalyzeAsList() {
            var p1 = "pattern1";
            var p2 = "pattern2";

            var op = newBaseOperation();
            assertTrue(op.onlyAnalyze().isEmpty(), "onlyAnalyze is not empty");

            op = op.onlyAnalyze(List.of(p1, p2));
            assertEquals(2, op.onlyAnalyze().size(), "size is not 2");
            assertTrue(op.onlyAnalyze().contains(p1), p1 + " not found");
            assertTrue(op.onlyAnalyze().contains(p2), p2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-onlyAnalyze"),
                    "-onlyAnalyze is not present in command list: " + commandList);
            assertTrue(commandList.contains(p1 + ',' + p2),
                    "patterns are not present in command list: " + commandList);

        }

        @Test
        void output() {
            var op = newBaseOperation();

            assertEquals(
                    Path.of("build", "reports", "spotbugs", "main.xml").toAbsolutePath().toFile(),
                    op.output(),
                    "output should be build/reports/spotbugs/main.xml");
        }

        @Test
        void outputWithFile() {
            var bar = new File("bar");
            var op = newBaseOperation();

            op.output(bar);

            assertEquals(bar, op.output(), "output should be bar as File");
        }

        @Test
        void outputWithPath() {
            var baz = Path.of("baz");
            var op = newBaseOperation();

            op.output(baz);

            assertEquals(baz, op.output().toPath(), "output should be baz as path");
        }

        @Test
        void outputWithString() {
            var foo = new File("foo");
            var op = newBaseOperation();

            op.output(foo.getName());

            assertEquals(foo, op.output(), "output should be foo as String");
        }

        @Test
        void pluginList() {
            var plugin1 = "/path/plugin1.jar";
            var plugin2 = "/path/plugin2.jar";
            var op = newBaseOperation();

            assertTrue(op.pluginList().isEmpty(), "pluginList is not empty");

            op.pluginList(plugin1, plugin2);
            assertEquals(2, op.pluginList().size(), "size is not 2");
            assertTrue(op.pluginList().contains(plugin1), plugin1 + " not found");
            assertTrue(op.pluginList().contains(plugin2), plugin2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-pluginList"),
                    "-pluginList is not present in command list: " + commandList);
            assertTrue(commandList.contains(plugin1 + ':' + plugin2),
                    "plugins are not present in command list: " + commandList);
        }

        @Test
        void pluginListAsCollection() {
            var plugin1 = "/path/plugin1.jar";
            var plugin2 = "/path/plugin2.jar";
            var op = newBaseOperation();

            assertTrue(op.pluginList().isEmpty(), "pluginList is not empty");

            op.pluginList(List.of(plugin1, plugin2));
            assertEquals(2, op.pluginList().size(), "size is not 2");
            assertTrue(op.pluginList().contains(plugin1), plugin1 + " not found");
            assertTrue(op.pluginList().contains(plugin2), plugin2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-pluginList"),
                    "-pluginList is not present in command list: " + commandList);
            assertTrue(commandList.contains(plugin1 + ':' + plugin2),
                    "plugins are not present in command list: " + commandList);
        }

        @Test
        void progress() {
            var op = newBaseOperation();

            assertFalse(op.progress(), "progress should be false");

            op = op.progress(true);
            assertTrue(op.progress(), "progress should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-progress"),
                    "-progress is not present in command list: " + commandList);
        }

        @Test
        void projectName() {
            var name = "TestProject";
            var op = newBaseOperation();

            assertNull(op.projectName(), "projectName should be null");

            op = op.projectName(name);
            assertEquals(name, op.projectName(), "projectName should be TestProject");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-projectName"),
                    "-projectName is not present in command list: " + commandList);
            assertTrue(commandList.contains(name),
                    "TestProject is not present in command list: " + commandList);
        }

        @Test
        void relaxed() {
            var op = newBaseOperation();

            assertFalse(op.relaxed(), "relaxed should be false");

            op = op.relaxed(true);
            assertTrue(op.relaxed(), "relaxed should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-relaxed"),
                    "-relaxed is not present in command list: " + commandList);
        }

        @Test
        void release() {
            var release = "1.0.0";
            var op = newBaseOperation();

            assertNull(op.release(), "release should be null");

            op = op.release(release);
            assertEquals(release, op.release(), "release should be 1.0.0");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-release"),
                    "-release is not present in command list: " + commandList);
            assertTrue(commandList.contains(release),
                    "1.0.0 is not present in command list: " + commandList);
        }

        @Test
        void sarif() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.sarif(), "sarif should be null");

            op = op.sarif(foo.getName());

            assertEquals(foo.getName(), op.sarif().toString(), "sarif should be foo");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sarif=" + foo.getAbsolutePath()),
                    "-sarif=foo is not present in command list: " + commandList);

        }

        @Test
        void sarifAsFile() {
            var foo = new File("foo");
            var op = newBaseOperation();

            assertNull(op.sarif(), "sarif should be null");

            op = op.sarif(foo);

            assertEquals(foo, op.sarif(), "sarif file does not match");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sarif=" + foo.getAbsolutePath()),
                    "-sarif=foo is not present in command list: " + commandList);

        }

        @Test
        void sarifAsPath() {
            var foo = Path.of("foo");
            var op = newBaseOperation();

            assertNull(op.sarif(), "sarif should be null");

            op = op.sarif(foo);

            assertEquals(foo.toFile(), op.sarif(), "sarif file does not match");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sarif=" + foo.toAbsolutePath()),
                    "-sarif=foo is not present in command list: " + commandList);

        }

        @Test
        void sortByClass() {
            var op = newBaseOperation();

            assertFalse(op.sortByClass(), "sortByClass should be false");

            op = op.sortByClass(true);
            assertTrue(op.sortByClass(), "sortByClass should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-sortByClass"),
                    "-sortByClass is not present in command list: " + commandList);
        }

        @Test
        void sourcePath() {
            var path1 = "/src/main/java";
            var path2 = "/src/main/resources";
            var op = newBaseOperation();

            op.sourcePath(path1, path2);
            assertEquals(4, op.sourcePath().size(), "size is not 4");
            assertTrue(op.sourcePath().contains(path1), path1 + " not found");
            assertTrue(op.sourcePath().contains(path2), path2 + " not found");
        }

        @Test
        void sourcePathAsFileArray() {
            var path1 = new File("/src/main/java");
            var path2 = new File("/src/main/resources");
            var op = newBaseOperation();

            op = op.sourcePath(path1, path2);
            assertEquals(4, op.sourcePath().size(), "size is not 4");
            assertTrue(op.sourcePath().contains(path1.toString()), path1 + " not found");
            assertTrue(op.sourcePath().contains(path2.toString()), path2 + " not found");
        }

        @Test
        void sourcePathAsFileCollection() {
            var path1 = new File("/src/main/java");
            var path2 = new File("/src/main/resources");
            var op = newBaseOperation();

            op = op.sourcePath(List.of(path1, path2));
            assertEquals(4, op.sourcePath().size(), "size is not 4");
            assertTrue(op.sourcePath().contains(path1.toString()), path1 + " not found");
            assertTrue(op.sourcePath().contains(path2.toString()), path2 + " not found");
        }

        @Test
        void sourcePathAsPathArray() {
            var path1 = Path.of("/src/main/java");
            var path2 = Path.of("/src/main/resources");
            var op = newBaseOperation();

            op = op.sourcePath(path1, path2);
            assertEquals(4, op.sourcePath().size(), "size is not 4");
            assertTrue(op.sourcePath().contains(path1.toString()), path1 + " not found");
            assertTrue(op.sourcePath().contains(path2.toString()), path2 + " not found");
        }

        @Test
        void sourcePathAsPathCollection() {
            var path1 = Path.of("/src/main/java");
            var path2 = Path.of("/src/main/resources");
            var op = newBaseOperation();

            op = op.sourcePath(List.of(path1, path2));
            assertEquals(4, op.sourcePath().size(), "size is not 4");
            assertTrue(op.sourcePath().contains(path1.toString()), path1 + " not found");
            assertTrue(op.sourcePath().contains(path2.toString()), path2 + " not found");
        }

        @Test
        void sourcePathAsStringCollection() {
            var path1 = "/src/main/java";
            var path2 = "/src/main/resources";
            var op = newBaseOperation();

            op = op.sourcePath(List.of(path1, path2));
            assertEquals(4, op.sourcePath().size(), "size is not 4");
            assertTrue(op.sourcePath().contains(path1), path1 + " not found");
            assertTrue(op.sourcePath().contains(path2), path2 + " not found");
        }

        @Test
        void spotBugsJar() {
            var jar = new File("spotbugs.jar");
            var op = newBaseOperation();

            assertNull(op.spotBugsJar(), "spotBugsJar should be null");

            op = op.spotBugsJar(jar);
            assertEquals(jar, op.spotBugsJar(), "spotBugsJar should be spotbugs.jar");
        }

        @Test
        void spotBugsJarAsPath() {
            var jar = Path.of("spotbugs.jar");
            var op = newBaseOperation();

            assertNull(op.spotBugsJar(), "spotBugsJar should be null");

            op = op.spotBugsJar(jar);
            assertEquals(jar.toFile(), op.spotBugsJar(), "spotBugsJar should be spotbugs.jar");
        }

        @Test
        void spotBugsJarAsString() {
            var jar = new File("spotbugs.jar");
            var op = newBaseOperation();

            assertNull(op.spotBugsJar(), "spotBugsJar should be null");

            op.spotBugsJar(jar.getName());
            assertEquals(jar.getName(), op.spotBugsJar().toString(), "spotBugsJar should be spotbugs.jar");
        }

        @Test
        void timestampNow() {
            var op = newBaseOperation();

            assertTrue(op.timestampNow(), "timestampNow should be true");
            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-timestampNow"),
                    "-timestampNow should be present in command list: " + commandList);

            op = op.timestampNow(false);

            assertFalse(op.timestampNow(), "timestampNow should be false");

            commandList = op.executeConstructProcessCommandList();
            assertFalse(commandList.contains("-timestampNow"),
                    "-timestampNow should not be present in command list: " + commandList);
        }

        @Test
        void visitors() {
            var visitor1 = "visitor1";
            var visitor2 = "visitor2";
            var op = newBaseOperation();

            assertTrue(op.visitors().isEmpty(), "visitors is not empty");

            op.visitors(visitor1, visitor2);
            assertEquals(2, op.visitors().size(), "size is not 2");
            assertTrue(op.visitors().contains(visitor1), visitor1 + " not found");
            assertTrue(op.visitors().contains(visitor2), visitor2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-visitors"),
                    "-visitors is not present in command list: " + commandList);
            assertTrue(commandList.contains(visitor1 + ',' + visitor2),
                    "visitors are not present in command list: " + commandList);
        }

        @Test
        void visitorsAsCollection() {
            var visitor1 = "visitor1";
            var visitor2 = "visitor2";
            var op = newBaseOperation();

            assertTrue(op.visitors().isEmpty(), "visitors is not empty");

            op.visitors(List.of(visitor1, visitor2));
            assertEquals(2, op.visitors().size(), "size is not 2");
            assertTrue(op.visitors().contains(visitor1), visitor1 + " not found");
            assertTrue(op.visitors().contains(visitor2), visitor2 + " not found");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-visitors"),
                    "-visitors is not present in command list: " + commandList);
            assertTrue(commandList.contains(visitor1 + ',' + visitor2),
                    "visitors are not present in command list: " + commandList);
        }

        @Test
        void workHard() {
            var op = newBaseOperation();

            assertFalse(op.workHard(), "workHard should be false");

            op = op.workHard(true);
            assertTrue(op.workHard(), "workHard should be true");

            var commandList = op.executeConstructProcessCommandList();
            assertTrue(commandList.contains("-workHard"),
                    "-workHard is not present in command list: " + commandList);
        }
    }

    @Nested
    @DisplayName("parseIntOrDefault Tests")
    class ParseIntOrDefaultTests {
        @Test
        void parseIntOrDefaultWithEmptyString() {
            assertEquals(42, SpotBugsOperation.parseIntOrDefault("", 42));
        }

        @Test
        void parseIntOrDefaultWithInvalidIntegerString() {
            assertEquals(-1, SpotBugsOperation.parseIntOrDefault("abc", -1));
            assertEquals(0, SpotBugsOperation.parseIntOrDefault("12.34", 0));
        }

        @Test
        void parseIntOrDefaultWithNullString() {
            assertEquals(99, SpotBugsOperation.parseIntOrDefault(null, 99));
        }

        @Test
        void parseIntOrDefaultWithValidIntegerString() {
            assertEquals(123, SpotBugsOperation.parseIntOrDefault("123"));
            assertEquals(-456, SpotBugsOperation.parseIntOrDefault("-456"));
        }
    }
}