/*
 * Copyright 2025-2026 the original author or authors.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import rife.bld.BaseProject;
import rife.bld.extension.spotbugs.Effort;
import rife.bld.extension.spotbugs.Priority;
import rife.bld.extension.spotbugs.SpotBugsFlag;
import rife.bld.extension.tools.*;
import rife.bld.operations.AbstractProcessOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run SpotBugs with the specified arguments.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@NullMarked
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Builder pattern intentionally exposes mutable collections"
)
@SuppressWarnings("PMD.GuardLogStatement")
public class SpotBugsOperation extends AbstractProcessOperation<SpotBugsOperation> {

    private static final String ANALYZE = "analyze";
    private static final String INVALID_SPOTBUGS_LOCATION = "Please specify a valid SpotBugs (JAR or home) location.";
    private static final String SARIF = "sarif";
    private static final String SOURCE_PATH = "sourcePath";
    private static final String SPOTBUGS_SARIF = "spotbugs.sarif";
    private static final String SPOTBUGS_XML = "spotbugs.xml";
    private static final Logger logger = Logger.getLogger(SpotBugsOperation.class.getName());
    private final Set<String> adjustPriority_ = new LinkedHashSet<>();
    private final List<File> analyze_ = new ArrayList<>();
    private final Set<String> auxClasspath_ = new LinkedHashSet<>();
    private final Set<String> bugCategories_ = new LinkedHashSet<>();
    private final Set<String> bugReporters_ = new LinkedHashSet<>();
    private final Set<String> choosePlugins_ = new LinkedHashSet<>();
    private final Set<String> chooseVisitors_ = new LinkedHashSet<>();
    private final Set<String> jvmArgs_ = new LinkedHashSet<>();
    private final Set<String> omitVisitors_ = new LinkedHashSet<>();
    private final Set<String> onlyAnalyze_ = new LinkedHashSet<>();
    private final Set<String> pluginList_ = new LinkedHashSet<>();
    private final Set<String> sourcePath_ = new LinkedHashSet<>();
    private final Set<String> visitors_ = new LinkedHashSet<>();
    private boolean adjustExperimental_;
    private boolean applySuppression_;
    private boolean debug_;
    private boolean detailedMessage_;
    private boolean dontCombineWarnings_;
    private @Nullable Effort effort_;
    private @Nullable File emacs_;
    private @Nullable File excludeBugs_;
    private @Nullable File exclude_;
    private boolean experimental_;
    private boolean high_;
    private @Nullable Path home_;
    private @Nullable String htmlXsl_;
    private @Nullable File html_;
    private boolean ignoreFailures_;
    private boolean includeLineNumber_ = true;
    private @Nullable File include_;
    private boolean longBugCodes_;
    private boolean low_;
    private int maxHeap_;
    private int maxRank_;
    private boolean medium_;
    private boolean nested_;
    private boolean noClassOk_;
    private @Nullable File output_;
    private boolean progress_;
    private @Nullable String projectName_;
    private boolean quiet_;
    private boolean relaxed_;
    private @Nullable String release_;
    private @Nullable File sarif_;
    private boolean sortByClass_;
    private @Nullable File sourceInfo_;
    private @Nullable File spotBugsJar_;
    private boolean timestampNow_;
    private @Nullable File userPrefs_;
    private @Nullable File workDirectory_;
    private boolean workHard_;

    /**
     * Performs the operation.
     *
     * @throws ExitStatusException      when the exit status was changed during the operation
     * @throws IOException              when an exception occurred during the execution of the process
     * @throws IllegalArgumentException when the XML bug report output file is not valid
     * @throws InterruptedException     when the operation was interrupted
     * @throws NullPointerException     when the XML bug report output file is {@code null}
     */
    @Override
    public void execute() throws IOException, InterruptedException, ExitStatusException {
        if (!silent() && logger.isLoggable(Level.INFO)) {
            logger.info("Running SpotBugs analysis...");
        }

        super.execute();

        Objects.requireNonNull(output_, "XML bug report output file must not be null");
        if (!output_.exists()) {
            throw new IllegalArgumentException("XML bug report output file not found: " + output_.getAbsolutePath());
        }

        var spotBugs = SpotBugsXmlParser.parse(output_.toPath());

        Map<String, String> bugMap = Collections.emptyMap();
        try {
            bugMap = SpotBugsXmlParser.parseSarif(ObjectTools.requireNonNull(sarif_, SARIF));
        } catch (IOException e) {
            if (!silent() && logger.isLoggable(Level.WARNING)) {
                logger.warning(logFormat("Unable to parse SARIF report: %s", e.getLocalizedMessage()));
            }
        }

        printBugs(spotBugs, bugMap);

        if (!ignoreFailures_ && !spotBugs.isEmpty()) {
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    /**
     * Part of the execute operation constructs the command list to use for building the process.
     *
     * @return a list of strings representing the commands or an empty list if no commands are constructed.
     * @throws IllegalArgumentException if SpotBugs location is not valid
     * @throws IllegalStateException    if the output directory cannot be created
     * @throws UncheckedIOException     if temporary files cannot be created or written
     */
    @Override
    protected List<String> executeConstructProcessCommandList() {
        var loggableInfo = logger.isLoggable(Level.INFO) && !silent();
        var loggableFine = logger.isLoggable(Level.FINE) && !silent();

        var cmd = new ArrayList<String>();

        var jar = findSpotBugsJar();
        if (jar.isEmpty()) {
            throw new IllegalArgumentException(INVALID_SPOTBUGS_LOCATION);
        } else {
            // Resolve defaults once and write back to fields so execute() sees consistent values.
            // This is the single authoritative place defaults are applied; fromProject() may have
            // already set these, in which case the ternaries are no-ops.
            if (output_ == null) {
                output_ = new File(SPOTBUGS_XML);
            }
            if (sarif_ == null) {
                sarif_ = new File(SPOTBUGS_SARIF);
            }

            var outputDir = output_.getParentFile();
            try {
                IOTools.createDirs(outputDir);
            } catch (IOException e) {
                throw new IllegalStateException("Could not create output directory: " + outputDir, e);
            }

            // Java
            cmd.add(javaTool());

            // jvmArgs
            if (!jvmArgs_.isEmpty()) {
                cmd.addAll(jvmArgs_);
            }

            // maxHeapSize
            if (maxHeap_ > 0) {
                cmd.add("-Xmx" + maxHeap_ + "m");
            }

            // debug
            if (debug_) {
                cmd.add("-Dfindbugs.debug=true");
            }

            // SpotBugs
            cmd.add("-jar");
            cmd.add(jar.get());

            // textui
            cmd.add("-textui");

            // quiet
            if (quiet_) {
                cmd.add(SpotBugsFlag.QUIET.flag());
            }

            // timestampNow
            if (timestampNow_) {
                cmd.add(SpotBugsFlag.TIMESTAMP_NOW.flag());
            }

            // projectName
            if (projectName_ != null) {
                cmd.add(SpotBugsFlag.PROJECT_NAME.flag());
                cmd.add(projectName_);
            }

            // effort
            if (effort_ != null) {
                cmd.add(SpotBugsFlag.EFFORT.flag() + ":" + effort_.name().toLowerCase());
            }

            // adjustExperimental
            if (adjustExperimental_) {
                cmd.add(SpotBugsFlag.ADJUST_EXPERIMENTAL.flag());
            }

            // workHard
            if (workHard_) {
                cmd.add(SpotBugsFlag.WORK_HARD.flag());
            }

            // longBugCodes
            if (longBugCodes_) {
                cmd.add(SpotBugsFlag.LONG_BUG_CODES.flag());
            }

            // progress
            if (progress_) {
                cmd.add(SpotBugsFlag.PROGRESS.flag());
            }

            // release
            if (release_ != null) {
                cmd.add(SpotBugsFlag.RELEASE.flag());
                cmd.add(release_);
            }

            // experimental
            if (experimental_) {
                cmd.add(SpotBugsFlag.EXPERIMENTAL.flag());
            }

            // low
            if (low_) {
                cmd.add(SpotBugsFlag.LOW.flag());
            }

            // medium
            if (medium_) {
                cmd.add(SpotBugsFlag.MEDIUM.flag());
            }

            // high
            if (high_) {
                cmd.add(SpotBugsFlag.HIGH.flag());
            }

            // maxRank
            if (maxRank_ > 0) {
                cmd.add(SpotBugsFlag.MAX_RANK.flag());
                cmd.add(String.valueOf(maxRank_));
            }

            // dontCombineWarnings
            if (dontCombineWarnings_) {
                cmd.add(SpotBugsFlag.DONT_COMBINE_WARNINGS.flag());
            }

            // sortByClass
            if (sortByClass_) {
                cmd.add(SpotBugsFlag.SORT_BY_CLASS.flag());
            }

            // relaxed
            if (relaxed_) {
                cmd.add(SpotBugsFlag.RELAXED.flag());
            }

            // sourceInfo
            if (sourceInfo_ != null) {
                cmd.add(SpotBugsFlag.SOURCE_INFO.flag());
                cmd.add(sourceInfo_.getAbsolutePath());
            }

            // nested
            if (nested_) {
                cmd.add(SpotBugsFlag.NESTED.flag() + ":true");
            }

            // html
            if (html_ != null) {
                if (htmlXsl_ != null) {
                    cmd.add(SpotBugsFlag.HTML.flag() + ":" + htmlXsl_ + "=" + html_.getAbsolutePath());
                } else {
                    cmd.add(SpotBugsFlag.HTML.flag() + "=" + html_.getAbsolutePath());
                }
            }

            // sarif — always emitted because we always default sarif_ above; the field is never null here
            cmd.add(SpotBugsFlag.SARIF.flag() + "=" + sarif_.getAbsolutePath());

            // emacs
            if (emacs_ != null) {
                cmd.add(SpotBugsFlag.EMACS.flag() + "=" + emacs_.getAbsolutePath());
            }

            // bugCategories
            if (!bugCategories_.isEmpty()) {
                cmd.add(SpotBugsFlag.BUG_CATEGORIES.flag());
                cmd.add(String.join(",", bugCategories_));
            }

            // onlyAnalyze
            if (!onlyAnalyze_.isEmpty()) {
                cmd.add(SpotBugsFlag.ONLY_ANALYZE.flag());
                cmd.add(String.join(",", onlyAnalyze_));
            }

            // excludeBugs
            if (excludeBugs_ != null) {
                cmd.add(SpotBugsFlag.EXCLUDE_BUGS.flag());
                cmd.add(excludeBugs_.getAbsolutePath());
            }

            // exclude
            if (exclude_ != null) {
                cmd.add(SpotBugsFlag.EXCLUDE.flag());
                cmd.add(exclude_.getAbsolutePath());
            }

            // include
            if (include_ != null) {
                cmd.add(SpotBugsFlag.INCLUDE.flag());
                cmd.add(include_.getAbsolutePath());
            }

            // applySuppression
            if (applySuppression_) {
                cmd.add(SpotBugsFlag.APPLY_SUPPRESSION.flag());
            }

            // visitors
            if (!visitors_.isEmpty()) {
                cmd.add(SpotBugsFlag.VISITORS.flag());
                cmd.add(String.join(",", visitors_));
            }

            // chooseVisitors
            if (!chooseVisitors_.isEmpty()) {
                cmd.add(SpotBugsFlag.CHOOSE_VISITORS.flag());
                cmd.add(String.join(",", chooseVisitors_));
            }

            // omitVisitors
            if (!omitVisitors_.isEmpty()) {
                cmd.add(SpotBugsFlag.OMIT_VISITORS.flag());
                cmd.add(String.join(",", omitVisitors_));
            }

            // choosePlugins
            if (!choosePlugins_.isEmpty()) {
                cmd.add(SpotBugsFlag.CHOOSE_PLUGINS.flag());
                cmd.add(String.join(",", choosePlugins_));
            }

            // adjustPriority
            if (!adjustPriority_.isEmpty()) {
                cmd.add(SpotBugsFlag.ADJUST_PRIORITY.flag());
                cmd.add(String.join(",", adjustPriority_));
            }

            // noClassOk
            if (noClassOk_) {
                cmd.add(SpotBugsFlag.NO_CLASS_OK.flag());
            }

            // bugReporters
            if (!bugReporters_.isEmpty()) {
                cmd.add(SpotBugsFlag.BUG_REPORTERS.flag());
                cmd.add(String.join(",", bugReporters_));
            }

            // pluginList — SpotBugs docs specify ";" as the separator between plugins
            if (!pluginList_.isEmpty()) {
                cmd.add(SpotBugsFlag.PLUGIN_LIST.flag());
                cmd.add(String.join(";", pluginList_));
            }

            // userPrefs
            if (userPrefs_ != null) {
                cmd.add(SpotBugsFlag.USER_PREFS.flag());
                cmd.add(userPrefs_.getAbsolutePath());
            }

            // output
            cmd.add(SpotBugsFlag.XML_WITH_MESSAGES.flag() + "=" + output_.getAbsolutePath());

            // auxClassPathFromFile
            if (!auxClasspath_.isEmpty()) {
                File auxFile = createAuxClasspathFile(auxClasspath_);
                cmd.add(SpotBugsFlag.AUX_CLASSPATH_FROM_FILE.flag());
                cmd.add(auxFile.getAbsolutePath());

                if (loggableInfo) {
                    var relativePaths = auxClasspath_.stream().map(this::projectRelativePath).toList();
                    logger.info(logFormat("auxclasspath" + relativePaths));
                }
            }

            // sourcepath
            if (!sourcePath_.isEmpty()) {
                cmd.add(SpotBugsFlag.SOURCE_PATH.flag());
                cmd.add(String.join(File.pathSeparator, sourcePath_));

                if (loggableInfo) {
                    var relativePaths = sourcePath_.stream().map(this::projectRelativePath).toList();
                    logger.info(logFormat("sourcepath" + relativePaths));
                }
            }

            // analyzeFromFile
            if (!analyze_.isEmpty()) {
                File analyzeFile = createAnalyzeFile(analyze_);
                cmd.add(SpotBugsFlag.ANALYZE_FROM_FILE.flag());
                cmd.add(analyzeFile.getAbsolutePath());

                if (loggableInfo) {
                    var analyzeList = analyze_.stream().map(File::getAbsolutePath).toList();
                    var relativePaths = analyzeList.stream().map(this::projectRelativePath).toList();
                    logger.info(logFormat(ANALYZE + relativePaths));
                }
            }
        }

        if (loggableFine) {
            logger.fine(PathTools.formatCommandLine(cmd));
        }

        return cmd;
    }

    /**
     * Configures the operation from a {@link BaseProject}.
     * <p>
     * Sets the following from the project:
     * <ul>
     *     <li>
     *         {@link #analyze() analyze} to {@link BaseProject#buildMainDirectory() buildMainDirectory}, if not
     *         already set
     *     </li>
     *     <li>
     *         {@link #auxClasspath() auxClasspath} to {@link BaseProject#compileMainClasspath() compileMainClasspath},
     *         if not already set
     *     </li>
     *     <li>
     *         {@link #nested() nested} and {@link #timestampNow() timestampNow} to {@code true}.
     *     </li>
     *     <li>
     *         {@link #output() output} to {@code reports/spotbugs/spotbugs.xml} in the
     *         {@link BaseProject#buildDirectory() buildDirectory}, if not already set.
     *     </li>
     *     <li>
     *         {@link #projectName() projectName} to the {@link BaseProject#name() project name}, if any and not
     *         already set.
     *     </li>
     *     <li>
     *         {@link #sarif() sarif} to {@code reports/spotbugs/spotbugs.sarif} in the
     *         {@link BaseProject#buildDirectory() buildDirectory}, if not already set.
     *     </li>
     *     <li>
     *         {@link #sourcePath() sourcePath} to {@link BaseProject#srcMainJavaDirectory() srcMainJavaDirectory}
     *         and {@link BaseProject#srcMainResourcesDirectory() srcMainResourceDirectory}, if not already set.
     *     </li>
     *     <li>
     *         {@link #workDirectory()} to the project's {@link BaseProject#workDirectory() workDirectory}, if not
     *         already set.
     *     </li>
     * </ul>
     *
     * @param project the project to configure the compile operation from
     * @return this operation instance
     * @throws NullPointerException if {@code project} is {@code null}
     * @see #fromProject(BaseProject, boolean)
     */
    @Override
    public SpotBugsOperation fromProject(BaseProject project) {
        ObjectTools.requireNonNull(project, "fromProject");

        if (workDirectory_ == null) {
            workDirectory_ = project.workDirectory();
        }

        var reportsDir = IOTools.resolveFile(project.buildDirectory(), "reports", "spotbugs");
        if (output_ == null) {
            output_ = new File(reportsDir, SPOTBUGS_XML);
        }
        if (sarif_ == null) {
            sarif_ = new File(reportsDir, SPOTBUGS_SARIF);
        }

        if (analyze_.isEmpty()) {
            analyze_.add(project.buildMainDirectory());
        }
        if (sourcePath_.isEmpty()) {
            sourcePath_.add(project.srcMainJavaDirectory().getAbsolutePath());
            sourcePath_.add(project.srcMainResourcesDirectory().getAbsolutePath());
        }
        if (auxClasspath_.isEmpty()) {
            var mainClasspath = project.compileMainClasspath();
            if (mainClasspath.isEmpty() && logger.isLoggable(Level.FINE)) {
                logger.fine(logFormat(
                        "compileMainClasspath() is empty. Dependency classes will not be available during analysis"));
            }
            auxClasspath_.addAll(mainClasspath);
        }

        if (projectName_ == null) {
            try {
                projectName_ = project.name();
            } catch (IllegalStateException ignored) {
                // do nothing
            }
        }

        nested_ = true;
        timestampNow_ = true;

        return this;
    }

    /**
     * Writes the given lines to a file.
     *
     * @param lines the lines to write
     * @param file  the target file
     * @throws IOException if an I/O error occurs writing to the file
     */
    private static void writeLinesToFile(Iterable<String> lines, File file) throws IOException {
        Files.write(file.toPath(), lines);
    }

    /**
     * Lower priority of experimental Bug Patterns.
     *
     * @param adjustExperimental set to {@code true} to lower priority, {@code false} otherwise
     * @return this operation
     * @see #adjustExperimental()
     */
    public SpotBugsOperation adjustExperimental(boolean adjustExperimental) {
        adjustExperimental_ = adjustExperimental;
        return this;
    }

    /**
     * Returns whether experimental bug patterns will have their priority lowered.
     *
     * @return {@code true} if experimental patterns priorities are adjusted, {@code false} otherwise
     * @see #adjustExperimental(boolean)
     */
    public boolean adjustExperimental() {
        return adjustExperimental_;
    }

    /**
     * Returns the collection of adjusted priorities.
     * <p>
     * Each element is formatted as: {@code name=value}
     *
     * @return a collection containing the adjusted priorities as strings
     * @see #adjustPriority(String, int)
     * @see #adjustPriority(String, Priority)
     */
    public Set<String> adjustPriorities() {
        return adjustPriority_;
    }

    /**
     * Adjust the priority of warnings for a given detector or bug pattern, or suppress it completely.
     * <p>
     * An unsigned integer sets the priority to an absolute value.
     * <p>
     * Note that {@code -1} is equivalent {@link Priority#RAISE}, and semantically increases the priority.
     *
     * @param name     the detector or bug pattern name
     * @param priority the priority level to adjust
     * @return this operation
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     * @see #adjustPriority(String, Priority)
     * @see #adjustPriorities()
     */
    public SpotBugsOperation adjustPriority(String name, int priority) {
        TextTools.requireNotBlank(name, "adjustPriority");
        adjustPriority_.add(name + "=" + priority);
        return this;
    }

    /**
     * Adjust the priority of warnings for a given detector (simple or fully qualified class name) or bug pattern, or
     * suppress it completely.
     *
     * @param name     the detector or bug pattern name
     * @param priority the priority level to adjust
     * @return this operation
     * @throws IllegalArgumentException if {@code name} is blank
     * @throws NullPointerException     if {@code priority} or {@code name} is {@code null}
     * @see #adjustPriority(String, int)
     * @see #adjustPriorities()
     */
    public SpotBugsOperation adjustPriority(String name, Priority priority) {
        TextTools.requireNotBlank(name, "adjustPriority name");
        ObjectTools.requireNonNull(priority, "adjustPriority priority");
        adjustPriority_.add(name + "=" + priority.name().toLowerCase());
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param filePaths array of file paths to analyze
     * @return this operation
     * @throws NullPointerException     if {@code filePaths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code filePaths} is empty, or contains blank elements
     * @see #analyze(File...)
     * @see #analyze(Path...)
     * @see #analyze(Collection)
     * @see #analyzeStrings(Collection)
     */
    public SpotBugsOperation analyze(String... filePaths) {
        TextTools.requireNotBlank(ANALYZE, filePaths);
        analyze_.addAll(CollectionTools.combineStringsToFiles(filePaths));
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param files array of files to analyze
     * @return this operation
     * @throws NullPointerException     if {@code files} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code files} is empty
     * @see #analyze(String...)
     * @see #analyze(Path...)
     * @see #analyze(Collection)
     */
    public SpotBugsOperation analyze(File... files) {
        ObjectTools.requireNotEmpty(files, ANALYZE);
        analyze_.addAll(List.of(files));
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param paths array of file paths to analyze
     * @return this operation
     * @throws NullPointerException     if {@code paths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code paths} is empty
     * @see #analyze(String...)
     * @see #analyze(File...)
     * @see #analyze(Collection)
     * @see #analyzePaths(Collection)
     */
    public SpotBugsOperation analyze(Path... paths) {
        ObjectTools.requireNotEmpty(paths, ANALYZE);
        analyze_.addAll(CollectionTools.combinePathsToFiles(paths));
        return this;
    }

    /**
     * Returns the collection of files configured to be analyzed.
     *
     * @return a collection containing the files to analyze
     * @see #analyze(String...)
     * @see #analyze(File...)
     * @see #analyze(Path...)
     * @see #analyze(Collection)
     */
    public List<File> analyze() {
        return analyze_;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param files collection of files to analyze
     * @return this operation
     * @throws NullPointerException     if {@code files} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code files} is empty
     * @see #analyze(String...)
     * @see #analyze(File...)
     * @see #analyze(Path...)
     */
    public SpotBugsOperation analyze(Collection<File> files) {
        ObjectTools.requireNotEmpty(files, ANALYZE);
        analyze_.addAll(files);
        return this;
    }

    /**
     * Specifies paths to analyze for bugs.
     *
     * @param paths collection of paths to analyze
     * @return this operation
     * @throws NullPointerException     if {@code paths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code paths} is empty
     * @see #analyze(Path...)
     * @see #analyze(Collection)
     */
    public SpotBugsOperation analyzePaths(Collection<Path> paths) {
        ObjectTools.requireNotEmpty(paths, "analyzePaths");
        analyze_.addAll(CollectionTools.combinePathsToFiles(paths));
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param filePaths collection of file paths to analyze
     * @return this operation
     * @throws NullPointerException     if {@code filePaths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code filePaths} is empty, or contains blank elements
     * @see #analyze(String...)
     * @see #analyze(Collection)
     */
    public SpotBugsOperation analyzeStrings(Collection<String> filePaths) {
        TextTools.requireNotBlank(filePaths, "analyzeStrings");
        analyze_.addAll(CollectionTools.combineStringsToFiles(filePaths));
        return this;
    }

    /**
     * Exclude any bugs that match suppression filter loaded from fbp file.
     *
     * @param applySuppression set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #applySuppression()
     */
    public SpotBugsOperation applySuppression(boolean applySuppression) {
        applySuppression_ = applySuppression;
        return this;
    }

    /**
     * Returns whether suppression from filter files is applied.
     *
     * @return {@code true} if suppression is applied, {@code false} otherwise
     * @see #applySuppression(boolean)
     */
    public boolean applySuppression() {
        return applySuppression_;
    }

    /**
     * Set the auxiliary classpath for analysis.
     * <p>
     * This classpath should include all jar files and directories containing classes that are part of the program
     * being analyzed, but you do not want to have analyzed for bugs.
     *
     * @param filePaths the auxiliary file paths to set
     * @return this operation
     * @throws NullPointerException     if {@code filePaths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code filePaths} is empty, or contains blank elements
     * @see #auxClasspath(Collection)
     * @see #auxClasspath()
     */
    public SpotBugsOperation auxClasspath(String... filePaths) {
        TextTools.requireNotBlank("auxClasspath", filePaths);
        auxClasspath_.addAll(List.of(filePaths));
        return this;
    }

    /**
     * Set the auxiliary classpath for analysis.
     * <p>
     * This classpath should include all jar files and directories containing classes that are part of the program
     * being analyzed, but you do not want to have analyzed for bugs.
     *
     * @param filePaths the auxiliary paths to set
     * @return this operation
     * @throws NullPointerException     if {@code filePaths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code filePaths} is empty, or contains blank elements
     * @see #auxClasspath(String...)
     * @see #auxClasspath()
     */
    public SpotBugsOperation auxClasspath(Collection<String> filePaths) {
        TextTools.requireNotBlank(filePaths, "auxClasspath");
        auxClasspath_.addAll(filePaths);
        return this;
    }

    /**
     * Returns the auxiliary classpath used for analysis.
     *
     * @return a collection containing the auxiliary classpath entries
     * @see #auxClasspath(String...)
     * @see #auxClasspath(Collection)
     */
    public Set<String> auxClasspath() {
        return auxClasspath_;
    }

    /**
     * Only report bugs in given categories.
     *
     * @param categories the bug categories
     * @return this operation
     * @throws NullPointerException     if {@code categories} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code categories} is empty, or contains blank elements
     * @see #bugCategories(Collection)
     * @see #bugCategories()
     */
    public SpotBugsOperation bugCategories(String... categories) {
        TextTools.requireNotBlank("bugCategories", categories);
        bugCategories_.addAll(List.of(categories));
        return this;
    }

    /**
     * Only report bugs in given categories.
     *
     * @param categories the bug categories
     * @return this operation
     * @throws NullPointerException     if {@code categories} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code categories} is empty, or contains blank elements
     * @see #bugCategories(String...)
     * @see #bugCategories()
     */
    public SpotBugsOperation bugCategories(Collection<String> categories) {
        TextTools.requireNotBlank(categories, "bugCategories");
        bugCategories_.addAll(categories);
        return this;
    }

    /**
     * Returns the configured bug categories to report.
     *
     * @return a collection containing the bug categories
     * @see #bugCategories(String...)
     * @see #bugCategories(Collection)
     */
    public Set<String> bugCategories() {
        return bugCategories_;
    }

    /**
     * Bug reporter decorators to explicitly enable/disable.
     * <p>
     * Prefix the reporter name with {@code -} to disable.
     *
     * @param reporters the reporters to enable/disable
     * @return this operation
     * @throws NullPointerException     if {@code reporters} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code reporters} is empty, or contains blank elements
     * @see #bugReporters(Collection)
     * @see #bugReporters()
     */
    public SpotBugsOperation bugReporters(String... reporters) {
        TextTools.requireNotBlank("bugReporters", reporters);
        bugReporters_.addAll(List.of(reporters));
        return this;
    }

    /**
     * Bug reporter decorators to explicitly enable/disable.
     * <p>
     * Prefix the reporter name with {@code -} to disable.
     *
     * @param reporters the reporters to enable/disable
     * @return this operation
     * @throws NullPointerException     if {@code reporters} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code reporters} is empty, or contains blank elements
     * @see #bugReporters(String...)
     * @see #bugReporters()
     */
    public SpotBugsOperation bugReporters(Collection<String> reporters) {
        TextTools.requireNotBlank(reporters, "bugReporters");
        bugReporters_.addAll(reporters);
        return this;
    }

    /**
     * Returns the collection of bug reporter decorators that are enabled/disabled.
     *
     * @return a collection containing the bug reporters
     * @see #bugReporters(String...)
     * @see #bugReporters(Collection)
     */
    public Set<String> bugReporters() {
        return bugReporters_;
    }

    /**
     * Selectively enable/disable plugins.
     * <p>
     * Prefix the plugin name with {@code -} to disable.
     *
     * @param plugins the plugins to enable/disable
     * @return this operation
     * @throws NullPointerException     if {@code plugins} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code plugins} is empty, or contains blank elements
     * @see #choosePlugins(Collection)
     * @see #choosePlugins()
     */
    public SpotBugsOperation choosePlugins(String... plugins) {
        TextTools.requireNotBlank("choosePlugins", plugins);
        choosePlugins_.addAll(List.of(plugins));
        return this;
    }

    /**
     * Selectively enable/disable plugins.
     * <p>
     * Prefix the plugin name with {@code -} to disable.
     *
     * @param plugins the plugins to enable/disable
     * @return this operation
     * @throws NullPointerException     if {@code plugins} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code plugins} is empty, or contains blank elements
     * @see #choosePlugins(String...)
     * @see #choosePlugins()
     */
    public SpotBugsOperation choosePlugins(Collection<String> plugins) {
        TextTools.requireNotBlank(plugins, "choosePlugins");
        choosePlugins_.addAll(plugins);
        return this;
    }

    /**
     * Returns the collection of chosen plugin enable/disable decorators.
     *
     * @return a collection containing the chosen plugin strings
     * @see #choosePlugins(String...)
     * @see #choosePlugins(Collection)
     */
    public Set<String> choosePlugins() {
        return choosePlugins_;
    }

    /**
     * Selectively enable/disable detectors.
     * <p>
     * Prefix the detector name with {@code -} to disable.
     *
     * @param visitors the visitors to enable/disable
     * @return this operation
     * @throws NullPointerException     if {@code visitors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code visitors} is empty, or contains blank elements
     * @see #chooseVisitors(Collection)
     * @see #chooseVisitors()
     */
    public SpotBugsOperation chooseVisitors(String... visitors) {
        TextTools.requireNotBlank("chooseVisitors", visitors);
        chooseVisitors_.addAll(List.of(visitors));
        return this;
    }

    /**
     * Selectively enable/disable detectors.
     * <p>
     * Prefix the detector name with {@code -} to disable.
     *
     * @param visitors the visitors to enable/disable
     * @return this operation
     * @throws NullPointerException     if {@code visitors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code visitors} is empty, or contains blank elements
     * @see #chooseVisitors(String...)
     * @see #chooseVisitors()
     */
    public SpotBugsOperation chooseVisitors(Collection<String> visitors) {
        TextTools.requireNotBlank(visitors, "chooseVisitors");
        chooseVisitors_.addAll(visitors);
        return this;
    }

    /**
     * Returns the collection of chosen visitor enable/disable decorators.
     *
     * @return a collection containing the chosen visitor strings
     * @see #chooseVisitors(String...)
     * @see #chooseVisitors(Collection)
     */
    public Set<String> chooseVisitors() {
        return chooseVisitors_;
    }

    /**
     * Sets the debug mode for the SpotBugs operation.
     *
     * @param debug {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #debug()
     */
    public SpotBugsOperation debug(boolean debug) {
        debug_ = debug;
        return this;
    }

    /**
     * Retrieves the debug state.
     *
     * @return {@code true} if debug mode is enabled, {@code false} otherwise.
     * @see #debug(boolean)
     */
    public boolean debug() {
        return debug_;
    }

    /**
     * Sets whether detailed messages should be printed.
     *
     * @param detailedMessage {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #detailedMessage()
     */
    public SpotBugsOperation detailedMessage(boolean detailedMessage) {
        detailedMessage_ = detailedMessage;
        return this;
    }

    /**
     * Returns whether detailed messages are enabled.
     *
     * @return {@code true} if detailed messages will be printed, {@code false} otherwise
     * @see #detailedMessage(boolean)
     */
    public boolean detailedMessage() {
        return detailedMessage_;
    }

    /**
     * Don't combine warnings that differ only in line number.
     *
     * @param dontCombineWarnings set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #dontCombineWarnings()
     */
    public SpotBugsOperation dontCombineWarnings(boolean dontCombineWarnings) {
        dontCombineWarnings_ = dontCombineWarnings;
        return this;
    }

    /**
     * Returns whether warnings that differ only by line number are combined.
     *
     * @return {@code true} if warnings are not combined, {@code false} otherwise
     * @see #dontCombineWarnings(boolean)
     */
    public boolean dontCombineWarnings() {
        return dontCombineWarnings_;
    }

    /**
     * Set the analysis effort level.
     * <p>
     * The {@link Effort#MIN} disables several analyses that increase precision but also increase memory consumption.
     * You may want to try this option if you find that SpotBugs with the {@link Effort#LESS} still runs out of memory,
     * or takes an unusually long time to complete its analysis.
     * <p>
     * The {@link Effort#LESS} disables some analyses that increase precision but also increase memory consumption.
     * You may want to try this option if you find that SpotBugs with the {@link Effort#MORE}/{@link Effort#DEFAULT}
     * runs out of memory, or takes an unusually long time to complete its analysis.
     * <p>
     * The {@link Effort#MORE} runs several analyses to find bugs, this is the {@link Effort#DEFAULT}.
     * <p>
     * The {@link Effort#MAX} enable analyses which increase precision and find more bugs, but which may require more
     * memory and take more time to complete.
     *
     * @param effort the effort level
     * @return this operation
     * @throws NullPointerException if {@code effort} is {@code null}
     * @see Effort
     * @see #effort()
     */
    public SpotBugsOperation effort(Effort effort) {
        ObjectTools.requireNonNull(effort, "effort");
        effort_ = effort;
        return this;
    }

    /**
     * Returns the configured analysis effort level.
     *
     * @return the {@link Effort} configured for this operation, or {@code null} if none
     * @see #effort(Effort)
     */
    @Nullable
    public Effort effort() {
        return effort_;
    }

    /**
     * Produce the bug reports in Emacs format.
     *
     * @param file the output file
     * @return this operation
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #emacs(String)
     * @see #emacs(Path)
     * @see #emacs()
     */
    public SpotBugsOperation emacs(File file) {
        ObjectTools.requireNonNull(file, "emacs");
        emacs_ = file;
        return this;
    }

    /**
     * Produce the bug reports in Emacs format.
     *
     * @param path the output file
     * @return this operation
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #emacs(String)
     * @see #emacs(File)
     * @see #emacs()
     */
    public SpotBugsOperation emacs(Path path) {
        ObjectTools.requireNonNull(path, "emacs");
        emacs_ = path.toFile();
        return this;
    }

    /**
     * Produce the bug reports in Emacs format.
     *
     * @param filePath the output file path
     * @return this operation
     * @throws IllegalArgumentException if {@code filePath} is blank
     * @throws NullPointerException     if {@code filePath} is {@code null}
     * @see #emacs(File)
     * @see #emacs(Path)
     * @see #emacs()
     */
    public SpotBugsOperation emacs(String filePath) {
        TextTools.requireNotBlank(filePath, "emacs");
        emacs_ = new File(filePath);
        return this;
    }

    /**
     * Returns the Emacs bug reports file.
     *
     * @return the bug reports file or {@code null}
     * @see #emacs(File)
     * @see #emacs(String)
     * @see #emacs(Path)
     */
    @Nullable
    public File emacs() {
        return emacs_;
    }

    /**
     * Report all bug instances except those matching the filter specified by the
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param excludeFilter the filter file
     * @return this operation
     * @throws NullPointerException if {@code excludeFilter} is {@code null}
     * @see #exclude(String)
     * @see #exclude(Path)
     * @see #exclude()
     */
    public SpotBugsOperation exclude(File excludeFilter) {
        ObjectTools.requireNonNull(excludeFilter, "exclude");
        exclude_ = excludeFilter;
        return this;
    }

    /**
     * Report all bug instances except those matching the filter specified by the
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param excludeFilter the filter file
     * @return this operation
     * @throws IllegalArgumentException if {@code excludeFilter} is blank
     * @throws NullPointerException     if {@code excludeFilter} is {@code null}
     * @see #exclude(File)
     * @see #exclude(Path)
     * @see #exclude()
     */
    public SpotBugsOperation exclude(String excludeFilter) {
        TextTools.requireNotBlank(excludeFilter, "exclude");
        exclude_ = new File(excludeFilter);
        return this;
    }

    /**
     * Report all bug instances except those matching the filter specified by the
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param excludeFilter the filter file
     * @return this operation
     * @throws NullPointerException if {@code excludeFilter} is {@code null}
     * @see #exclude(File)
     * @see #exclude(String)
     * @see #exclude()
     */
    public SpotBugsOperation exclude(Path excludeFilter) {
        ObjectTools.requireNonNull(excludeFilter, "exclude");
        exclude_ = excludeFilter.toFile();
        return this;
    }

    /**
     * Returns the configured filter file used to exclude bugs.
     *
     * @return the exclude filter {@link File}, or {@code null} if none configured
     * @see #exclude(File)
     * @see #exclude(String)
     * @see #exclude(Path)
     */
    @Nullable
    public File exclude() {
        return exclude_;
    }

    /**
     * Exclude bugs that are also reported in the baseline XML output.
     *
     * @param excludeFile the exclude file
     * @return this operation
     * @throws IllegalArgumentException if {@code excludeFile} is blank
     * @throws NullPointerException     if {@code excludeFile} is {@code null}
     * @see #excludeBugs(File)
     * @see #excludeBugs(Path)
     * @see #excludeBugs()
     */
    public SpotBugsOperation excludeBugs(String excludeFile) {
        TextTools.requireNotBlank(excludeFile, "excludeBugs");
        excludeBugs_ = new File(excludeFile);
        return this;
    }

    /**
     * Exclude bugs that are also reported in the baseline XML output.
     *
     * @param excludeFile the exclude file
     * @return this operation
     * @throws NullPointerException if {@code excludeFile} is {@code null}
     * @see #excludeBugs(String)
     * @see #excludeBugs(Path)
     * @see #excludeBugs()
     */
    public SpotBugsOperation excludeBugs(File excludeFile) {
        ObjectTools.requireNonNull(excludeFile, "excludeBugs");
        excludeBugs_ = excludeFile;
        return this;
    }

    /**
     * Exclude bugs that are also reported in the baseline XML output.
     *
     * @param excludeFile the exclude file
     * @return this operation
     * @throws NullPointerException if {@code excludeFile} is {@code null}
     * @see #excludeBugs(File)
     * @see #excludeBugs(String)
     * @see #excludeBugs()
     */
    public SpotBugsOperation excludeBugs(Path excludeFile) {
        ObjectTools.requireNonNull(excludeFile, "excludeBugs");
        excludeBugs_ = excludeFile.toFile();
        return this;
    }

    /**
     * Returns the exclude file used to exclude bugs.
     *
     * @return the exclude file or {@code null}
     * @see #excludeBugs(String)
     * @see #excludeBugs(File)
     * @see #excludeBugs(Path)
     */
    @Nullable
    public File excludeBugs() {
        return excludeBugs_;
    }

    /**
     * Report of any confidence level including experimental bug patterns.
     *
     * @param experimental set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #experimental()
     */
    public SpotBugsOperation experimental(boolean experimental) {
        experimental_ = experimental;
        return this;
    }

    /**
     * Returns whether experimental bug patterns are included in the report.
     *
     * @return {@code true} if experimental patterns are reported, {@code false} otherwise
     * @see #experimental(boolean)
     */
    public boolean experimental() {
        return experimental_;
    }

    /**
     * Configures a compile operation from a {@link BaseProject}.
     * <p>
     * Sets the following from the project:
     * <ul>
     *     <li>{@link #analyze() analyze} to {@link BaseProject#buildMainDirectory() buildMainDirectory}</li>
     *     <li>
     *         {@link #auxClasspath() auxClasspath} to {@link BaseProject#compileMainClasspath() compileMainClasspath}
     *     </li>
     *     <li>{@link #nested() nested} and {@link #timestampNow() timestampNow} to {@code true}</li>
     *     <li>{@link #output() output} to {@code reports/spotbugs/spotbugs.xml} in the
     *     {@link BaseProject#buildDirectory() buildDirectory}</li>
     *     <li>{@link #projectName() projectName} to the {@link BaseProject#name() project name}</li>
     *     <li>{@link #sarif() sarif} to {@code reports/spotbugs/spotbugs.sarif} in the
     *     {@link BaseProject#buildDirectory() buildDirectory}</li>
     *     <li>{@link #sourcePath() sourcePath} to {@link BaseProject#srcMainJavaDirectory() srcMainJavaDirectory}
     *     and {@link BaseProject#srcMainResourcesDirectory() srcMainResourceDirectory}</li>
     * </ul>
     * <p>
     * If {@code includeTest} is enabled, {@code test} directories are also included.
     * </p>
     *
     * @param project     the project to configure the compile operation from
     * @param includeTest set to {@code true} to include test directories, {@code false} otherwise
     * @return this operation instance
     * @throws NullPointerException if {@code project} is {@code null}
     * @see #fromProject(BaseProject)
     */
    public SpotBugsOperation fromProject(BaseProject project, boolean includeTest) {
        fromProject(project);

        if (includeTest) {
            analyze_.add(project.buildTestDirectory());
            sourcePath_.add(project.srcTestJavaDirectory().getAbsolutePath());
            sourcePath_.add(project.srcTestResourcesDirectory().getAbsolutePath());
            auxClasspath_.addAll(project.compileTestClasspath());
        }

        return this;
    }

    /**
     * Report only high-priority bugs.
     *
     * @param high set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #high()
     */
    public SpotBugsOperation high(boolean high) {
        high_ = high;
        return this;
    }

    /**
     * Returns whether only high-priority bugs will be reported.
     *
     * @return {@code true} if only high-priority bugs are reported, {@code false} otherwise
     * @see #high(boolean)
     */
    public boolean high() {
        return high_;
    }

    /**
     * Specify SpotBugs home directory.
     *
     * @param home the home directory
     * @return this operation
     * @throws IllegalArgumentException if {@code home} is blank
     * @throws NullPointerException     if {@code home} is {@code null}
     * @see #home(File)
     * @see #home(Path)
     * @see #home()
     */
    public SpotBugsOperation home(String home) {
        TextTools.requireNotBlank(home, "home");
        home_ = Path.of(home);
        return this;
    }

    /**
     * Specify SpotBugs home directory.
     *
     * @param home the home directory
     * @return this operation
     * @throws NullPointerException if {@code home} is {@code null}
     * @see #home(String)
     * @see #home(Path)
     * @see #home()
     */
    public SpotBugsOperation home(File home) {
        ObjectTools.requireNonNull(home, "home");
        home_ = home.toPath();
        return this;
    }

    /**
     * Specify SpotBugs home directory.
     *
     * @param home the home directory
     * @return this operation
     * @throws NullPointerException if {@code home} is {@code null}
     * @see #home(String)
     * @see #home(File)
     * @see #home()
     */
    public SpotBugsOperation home(Path home) {
        ObjectTools.requireNonNull(home, "home");
        home_ = home;
        return this;
    }

    /**
     * Returns the SpotBugs home directory.
     *
     * @return the SpotBugs home path, or {@code null} if not set
     * @see #home(String)
     * @see #home(File)
     * @see #home(Path)
     */
    @Nullable
    public Path home() {
        return home_;
    }

    /**
     * Generate HTML output.
     *
     * @param file the output file
     * @return this operation
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #html(String)
     * @see #html(Path)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     * @see #html()
     */
    public SpotBugsOperation html(File file) {
        ObjectTools.requireNonNull(file, "html");
        html_ = file;
        return this;
    }

    /**
     * Generate HTML output.
     *
     * @param path the output file
     * @return this operation
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #html(String)
     * @see #html(File)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     * @see #html()
     */
    public SpotBugsOperation html(Path path) {
        ObjectTools.requireNonNull(path, "html");
        html_ = path.toFile();
        return this;
    }

    /**
     * Generate HTML output.
     *
     * @param filePath the output file path
     * @return this operation
     * @throws IllegalArgumentException if {@code filePath} is blank
     * @throws NullPointerException     if {@code filePath} is {@code null}
     * @see #html(File)
     * @see #html(Path)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     * @see #html()
     */
    public SpotBugsOperation html(String filePath) {
        TextTools.requireNotBlank(filePath, "html");
        html_ = new File(filePath);
        return this;
    }

    /**
     * Returns the HTML output file.
     *
     * @return the HTML output file or {@code null}
     * @see #html(File)
     * @see #html(String)
     * @see #html(Path)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     */
    @Nullable
    public File html() {
        return html_;
    }

    /**
     * Generate HTML output.
     * <p>
     * By default, SpotBugs will use the default.xsl XSLT stylesheet to generate the HTML: you can find this file path
     * in spotbugs.jar, or in the SpotBugs source or binary distributions. Variants of this option include:
     *
     * <ul>
     * <li>{@code plain.xsl}</li>
     * <li>{@code fancy.xsl}</li>
     * <li>{@code fancy-hist.xsl}</li>
     * </ul>
     * <p>
     * {@code plain.xsl} stylesheet does not use JavaScript or DOM, and may work better with older web browsers,
     * or for printing.
     * <p>
     * {@code fancy.xsl} stylesheet uses DOM and JavaScript for navigation and CSS for visual presentation.
     * <p>
     * {@code fancy-hist.xsl} an evolution of fancy.xsl stylesheet. It makes extensive use of DOM and JavaScript
     * for dynamically filtering the lists of bugs.
     * <p>
     * If you want to specify your own XSLT stylesheet to perform the transformation to HTML, specify the option as
     * {@code myStylesheet.xsl}, the filename of the stylesheet you want to use.
     *
     * @param filePath   the output file path
     * @param stylesheet the stylesheet to use
     * @return this operation
     * @throws IllegalArgumentException if {@code filePath} or {@code stylesheet} is blank
     * @throws NullPointerException     if {@code filePath} or {@code stylesheet} is {@code null}
     * @see #html(String)
     * @see #html(Path)
     * @see #html(File)
     * @see #html(File, String)
     * @see #html(Path, String)
     * @see #html()
     */
    public SpotBugsOperation html(String filePath, String stylesheet) {
        TextTools.requireNotBlank(filePath, "html filePath");
        TextTools.requireNotBlank(stylesheet, "html stylesheet");
        html_ = new File(filePath);
        htmlXsl_ = stylesheet;
        return this;
    }

    /**
     * Generate HTML output.
     * <p>
     * By default, SpotBugs will use the default.xsl XSLT stylesheet to generate the HTML: you can find this file in
     * spotbugs.jar, or in the SpotBugs source or binary distributions. Variants of this option include:
     *
     * <ul>
     * <li>{@code plain.xsl}</li>
     * <li>{@code fancy.xsl}</li>
     * <li>{@code fancy-hist.xsl}</li>
     * </ul>
     * <p>
     * {@code plain.xsl} stylesheet does not use JavaScript or DOM, and may work better with older web browsers,
     * or for printing.
     * <p>
     * {@code fancy.xsl} stylesheet uses DOM and JavaScript for navigation and CSS for visual presentation.
     * <p>
     * {@code fancy-hist.xsl} an evolution of fancy.xsl stylesheet. It makes extensive use of DOM and JavaScript
     * for dynamically filtering the lists of bugs.
     * <p>
     * If you want to specify your own XSLT stylesheet to perform the transformation to HTML, specify the option as
     * {@code myStylesheet.xsl}, the filename of the stylesheet you want to use.
     *
     * @param filePath   the output file
     * @param stylesheet the stylesheet to use
     * @return this operation
     * @throws NullPointerException     if {@code filePath} or {@code stylesheet} is {@code null}
     * @throws IllegalArgumentException if {@code filePath} or {@code stylesheet} is blank
     * @see #html(String)
     * @see #html(File)
     * @see #html(Path)
     * @see #html(File, String)
     * @see #html(String, String)
     * @see #html()
     */
    public SpotBugsOperation html(Path filePath, String stylesheet) {
        ObjectTools.requireNonNull(filePath, "html file");
        TextTools.requireNotBlank(stylesheet, "html stylesheet");
        html_ = filePath.toFile();
        htmlXsl_ = stylesheet;
        return this;
    }

    /**
     * Generate HTML output.
     * <p>
     * By default, SpotBugs will use the default.xsl XSLT stylesheet to generate the HTML: you can find this file in
     * spotbugs.jar, or in the SpotBugs source or binary distributions. Variants of this option include:
     *
     * <ul>
     * <li>{@code plain.xsl}</li>
     * <li>{@code fancy.xsl}</li>
     * <li>{@code fancy-hist.xsl}</li>
     * </ul>
     * <p>
     * {@code plain.xsl} stylesheet does not use JavaScript or DOM, and may work better with older web browsers,
     * or for printing.
     * <p>
     * {@code fancy.xsl} stylesheet uses DOM and JavaScript for navigation and CSS for visual presentation.
     * <p>
     * {@code fancy-hist.xsl} an evolution of fancy.xsl stylesheet. It makes extensive use of DOM and JavaScript
     * for dynamically filtering the lists of bugs.
     * <p>
     * If you want to specify your own XSLT stylesheet to perform the transformation to HTML, specify the option as
     * {@code myStylesheet.xsl}, the filename of the stylesheet you want to use.
     *
     * @param file       the output file
     * @param stylesheet the stylesheet to use
     * @return this operation
     * @throws NullPointerException     if {@code filePath} or {@code stylesheet} is {@code null}
     * @throws IllegalArgumentException if {@code stylesheet} is blank
     * @see #html(String)
     * @see #html(Path)
     * @see #html(File)
     * @see #html(String, String)
     * @see #html(Path, String)
     * @see #html()
     */
    public SpotBugsOperation html(File file, String stylesheet) {
        ObjectTools.requireNonNull(file, "html file");
        TextTools.requireNotBlank(stylesheet, "html stylesheet");
        html_ = file;
        htmlXsl_ = stylesheet;
        return this;
    }

    /**
     * Sets the flag to indicate whether failures should be ignored.
     * <p>
     * The default is enabled.
     *
     * @param ignoreFailures {@code true} to enable, {@code false} to disable.
     * @return this operation
     * @see #ignoreFailures()
     */
    public SpotBugsOperation ignoreFailures(boolean ignoreFailures) {
        ignoreFailures_ = ignoreFailures;
        return this;
    }

    /**
     * Return the ignore failures flag.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     * @see #ignoreFailures(boolean)
     */
    public boolean ignoreFailures() {
        return ignoreFailures_;
    }

    /**
     * Only report bug instances that match the filter specified by
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param includeFilter the filter file
     * @return this operation
     * @throws IllegalArgumentException if {@code includeFilter} is blank
     * @throws NullPointerException     if {@code includeFilter} is {@code null}
     * @see #include(File)
     * @see #include(Path)
     * @see #include()
     */
    public SpotBugsOperation include(String includeFilter) {
        TextTools.requireNotBlank(includeFilter, "include");
        include_ = new File(includeFilter);
        return this;
    }

    /**
     * Only report bug instances that match the filter specified by
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param includeFilter the filter file
     * @return this operation
     * @throws NullPointerException if {@code includeFilter} is {@code null}
     * @see #include(String)
     * @see #include(Path)
     * @see #include()
     */
    public SpotBugsOperation include(File includeFilter) {
        ObjectTools.requireNonNull(includeFilter, "include");
        include_ = includeFilter;
        return this;
    }

    /**
     * Only report bug instances that match the filter specified by
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param includeFilter the filter file
     * @return this operation
     * @throws NullPointerException if {@code includeFilter} is {@code null}
     * @see #include(File)
     * @see #include(String)
     * @see #include()
     */
    public SpotBugsOperation include(Path includeFilter) {
        ObjectTools.requireNonNull(includeFilter, "include");
        include_ = includeFilter.toFile();
        return this;
    }

    /**
     * Returns the include filter file.
     *
     * @return the include filter {@link File}, or {@code null} if none configured
     * @see #include(String)
     * @see #include(File)
     * @see #include(Path)
     */
    @Nullable
    public File include() {
        return include_;
    }

    /**
     * Enable or disable line number in source file URIs.
     * <p>
     * While clicking on the URI works in IntelliJ IDEA, Visual Studio Code, etc.; it might not in terminal emulators.
     * <p>
     * Enabled by default.
     *
     * @param includeLineNumber {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #includeLineNumber()
     */
    public SpotBugsOperation includeLineNumber(boolean includeLineNumber) {
        includeLineNumber_ = includeLineNumber;
        return this;
    }

    /**
     * Returns whether the line number should be included in source file URIs.
     *
     * @return {@code true} if the line number should be included, {@code false} otherwise
     * @see #includeLineNumber(boolean)
     */
    public boolean includeLineNumber() {
        return includeLineNumber_;
    }

    /**
     * Specifies arguments to pass to the JVM.
     *
     * @param args the args to pass to JVM
     * @return this operation
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty, or contains blank elements
     * @see #jvmArgs(Collection)
     * @see #jvmArgs()
     */
    public SpotBugsOperation jvmArgs(String... args) {
        TextTools.requireNotBlank("jvmArgs", args);
        jvmArgs_.addAll(List.of(args));
        return this;
    }

    /**
     * Specifies arguments to pass to the JVM.
     *
     * @param args the args to pass to JVM
     * @return this operation
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code args} is empty, or contains blank elements
     * @see #jvmArgs(String...)
     * @see #jvmArgs()
     */
    public SpotBugsOperation jvmArgs(Collection<String> args) {
        TextTools.requireNotBlank(args, "jvmArgs");
        jvmArgs_.addAll(args);
        return this;
    }

    /**
     * Returns the collection of JVM arguments configured for the SpotBugs run.
     *
     * @return a collection containing JVM arguments
     * @see #jvmArgs(String...)
     * @see #jvmArgs(Collection)
     */
    public Set<String> jvmArgs() {
        return jvmArgs_;
    }

    /**
     * Report long bug codes.
     *
     * @param longBugCodes set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #longBugCodes()
     */
    public SpotBugsOperation longBugCodes(boolean longBugCodes) {
        longBugCodes_ = longBugCodes;
        return this;
    }

    /**
     * Returns whether long bug codes are enabled.
     *
     * @return {@code true} if long bug codes will be reported, {@code false} otherwise
     * @see #longBugCodes(boolean)
     */
    public boolean longBugCodes() {
        return longBugCodes_;
    }

    /**
     * Report all bugs.
     *
     * @param low set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #low()
     */
    public SpotBugsOperation low(boolean low) {
        low_ = low;
        return this;
    }

    /**
     * Returns whether all bugs are reported.
     *
     * @return {@code true} if all bugs are reported, {@code false} otherwise
     * @see #low(boolean)
     */
    public boolean low() {
        return low_;
    }

    /**
     * Specifies the maximum Java heap size in megabytes.
     * <p>
     * The default is 256. More memory may be required to analyze very large programs or libraries.
     *
     * @param size the maximum heap size in megabytes
     * @return this operation
     * @throws IllegalArgumentException if {@code size} is not positive
     * @see #maxHeap()
     */
    public SpotBugsOperation maxHeap(int size) {
        maxHeap_ = ObjectTools.requirePositive(size, "maxHeap");
        return this;
    }

    /**
     * Returns the configured maximum Java heap size in megabytes.
     *
     * @return the maximum heap size in megabytes
     * @see #maxHeap(int)
     */
    public int maxHeap() {
        return maxHeap_;
    }

    /**
     * Only report issues with a bug rank at least as scary as that provided.
     *
     * @param rank the maximum rank
     * @return this operation
     * @throws IllegalArgumentException if {@code rank} is not positive
     * @see #maxRank()
     */
    public SpotBugsOperation maxRank(int rank) {
        maxRank_ = ObjectTools.requirePositive(rank, "maxRank");
        return this;
    }

    /**
     * Returns the configured maximum bug rank threshold.
     *
     * @return the maximum rank value
     * @see #maxRank(int)
     */
    public int maxRank() {
        return maxRank_;
    }

    /**
     * Report medium and high-priority bugs.
     * <p>
     * This is the default setting.
     *
     * @param medium set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #medium()
     */
    public SpotBugsOperation medium(boolean medium) {
        medium_ = medium;
        return this;
    }

    /**
     * Returns whether medium-priority bugs are reported.
     *
     * @return {@code true} if medium (and higher) priority bugs are reported, {@code false} otherwise
     * @see #medium(boolean)
     */
    public boolean medium() {
        return medium_;
    }

    /**
     * This option enables or disables scanning of nested jar and zip files found in the list of files and directories
     * to be analyzed.
     * <p>
     * By default, scanning of nested jar/zip files is enabled.
     *
     * @param nested set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #nested()
     */
    public SpotBugsOperation nested(boolean nested) {
        nested_ = nested;
        return this;
    }

    /**
     * Returns whether nested jar/zip scanning is enabled.
     *
     * @return {@code true} if nested scanning is enabled, {@code false} otherwise
     * @see #nested(boolean)
     */
    public boolean nested() {
        return nested_;
    }

    /**
     * Output an empty warning file if no classes are specified.
     *
     * @param noClassOk set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #noClassOk()
     */
    public SpotBugsOperation noClassOk(boolean noClassOk) {
        noClassOk_ = noClassOk;
        return this;
    }

    /**
     * Returns whether an empty warning file is allowed when no classes are specified.
     *
     * @return {@code true} if an empty warning file is allowed, {@code false} otherwise
     * @see #noClassOk(boolean)
     */
    public boolean noClassOk() {
        return noClassOk_;
    }

    /**
     * Omit named visitors.
     *
     * @param visitors the visitors to omit
     * @return this operation
     * @throws NullPointerException     if {@code visitors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code visitors} is empty, or contains blank elements
     * @see #omitVisitors(Collection)
     * @see #omitVisitors()
     */
    public SpotBugsOperation omitVisitors(String... visitors) {
        TextTools.requireNotBlank("omitVisitors", visitors);
        omitVisitors_.addAll(List.of(visitors));
        return this;
    }

    /**
     * Omit named visitors.
     *
     * @param visitors the visitors to omit
     * @return this operation
     * @throws NullPointerException     if {@code visitors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code visitors} is empty, or contains blank elements
     * @see #omitVisitors(String...)
     * @see #omitVisitors()
     */
    public SpotBugsOperation omitVisitors(Collection<String> visitors) {
        TextTools.requireNotBlank(visitors, "omitVisitors");
        omitVisitors_.addAll(visitors);
        return this;
    }

    /**
     * Returns the collection of visitors configured to be omitted.
     *
     * @return a collection containing the visitors to omit
     * @see #omitVisitors(String...)
     * @see #omitVisitors(Collection)
     */
    public Set<String> omitVisitors() {
        return omitVisitors_;
    }

    /**
     * Restrict analysis to find bugs to a list of classes and packages.
     * <p>
     * Unlike filtering, this option avoids running analysis on classes and packages that are not explicitly matched:
     * for large projects, this may greatly reduce the amount of time needed to run the analysis.
     * (However, some detectors may produce inaccurate results if they aren't run on the entire application.)
     * <p>
     * Classes should be specified using their full class names (including package).
     * <p>
     * Packages should be specified in the same way they would in a Java import statement to import all classes in the
     * package (i.e., add {@code .*} to the full name of the package). Replace {@code .*} with {@code .-} to also
     * analyze all subpackages.
     * <p>
     * Items starting with {@code !} are treated as exclusions, removing otherwise-included classes from analysis.
     *
     * @param patterns the patterns to analyze
     * @return this operation
     * @throws NullPointerException     if {@code patterns} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code patterns} is empty, or contains blank elements
     * @see #onlyAnalyze(Collection)
     * @see #onlyAnalyze()
     */
    public SpotBugsOperation onlyAnalyze(String... patterns) {
        TextTools.requireNotBlank("onlyAnalyze", patterns);
        onlyAnalyze_.addAll(List.of(patterns));
        return this;
    }

    /**
     * Restrict analysis to find bugs to a list of classes and packages.
     * <p>
     * Classes should be specified using their full classnames (including package).
     * <p>
     * Packages should be specified in the same way they would in a Java import statement to import all classes in the
     * package (i.e., add {@code .*} to the full name of the package). Replace {@code .*} with {@code .-} to also
     * analyze all subpackages.
     * <p>
     * Items starting with {@code !} are treated as exclusions, removing otherwise-included classes from analysis.
     *
     * @param patterns the patterns to analyze
     * @return this operation
     * @throws NullPointerException     if {@code patterns} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code patterns} is empty, or contains blank elements
     * @see #onlyAnalyze(String...)
     * @see #onlyAnalyze()
     */
    public SpotBugsOperation onlyAnalyze(Collection<String> patterns) {
        TextTools.requireNotBlank(patterns, "onlyAnalyze");
        onlyAnalyze_.addAll(patterns);
        return this;
    }

    /**
     * Returns the collection of analyze-only patterns configured.
     *
     * @return a collection of analyze-only patterns
     * @see #onlyAnalyze(String...)
     * @see #onlyAnalyze(Collection)
     */
    public Set<String> onlyAnalyze() {
        return onlyAnalyze_;
    }

    /**
     * Sets the XML bug report file path.
     * <p>
     * The default is: {@code spotbugs.xml}
     *
     * @param filePath the file path
     * @return this operation
     * @throws NullPointerException     if {@code filePath} is {@code null}
     * @throws IllegalArgumentException if {@code filePath} is blank
     * @see #output(File)
     * @see #output(Path)
     * @see #output()
     */
    public SpotBugsOperation output(String filePath) {
        TextTools.requireNotBlank(filePath, "output");
        output_ = new File(filePath);
        return this;
    }

    /**
     * Sets the XML bug report file path.
     * <p>
     * The default is: {@code spotbugs.xml}
     *
     * @param file the file path
     * @return this operation
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #output(String)
     * @see #output(Path)
     * @see #output()
     */
    public SpotBugsOperation output(File file) {
        ObjectTools.requireNonNull(file, "output");
        output_ = file;
        return this;
    }

    /**
     * Sets the XML bug report file path.
     * <p>
     * The default is: {@code spotbugs.xml}
     *
     * @param path the file path
     * @return this operation
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #output(String)
     * @see #output(File)
     * @see #output()
     */
    public SpotBugsOperation output(Path path) {
        ObjectTools.requireNonNull(path, "output");
        output_ = path.toFile();
        return this;
    }

    /**
     * Returns the configured XML bug report output file.
     *
     * @return the output {@link File}
     * @see #output(String)
     * @see #output(File)
     * @see #output(Path)
     */
    @Nullable
    public File output() {
        return output_;
    }

    /**
     * Specify a list of plugin Jar files to load.
     *
     * @param plugins the plugin list
     * @return this operation
     * @throws NullPointerException     if {@code plugins} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code plugins} is empty, or contains blank elements
     * @see #pluginList(Collection)
     * @see #pluginList()
     */
    public SpotBugsOperation pluginList(String... plugins) {
        TextTools.requireNotBlank("pluginList", plugins);
        pluginList_.addAll(List.of(plugins));
        return this;
    }

    /**
     * Specify a list of plugin Jar files to load.
     *
     * @param plugins the plugin list
     * @return this operation
     * @throws NullPointerException     if {@code plugins} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code plugins} is empty, or contains blank elements
     * @see #pluginList(String...)
     * @see #pluginList()
     */
    public SpotBugsOperation pluginList(Collection<String> plugins) {
        TextTools.requireNotBlank(plugins, "pluginList");
        pluginList_.addAll(plugins);
        return this;
    }

    /**
     * Returns the collection of plugin jar files to load.
     *
     * @return a collection containing the plugin jar files
     * @see #pluginList(String...)
     * @see #pluginList(Collection)
     */
    public Set<String> pluginList() {
        return pluginList_;
    }

    /**
     * Display progress in the terminal window.
     *
     * @param progress set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #progress()
     */
    public SpotBugsOperation progress(boolean progress) {
        progress_ = progress;
        return this;
    }

    /**
     * Retrieves the current progress setting.
     *
     * @return {@code true} if progress display is enabled, {@code false} otherwise
     * @see #progress(boolean)
     */
    public boolean progress() {
        return progress_;
    }

    /**
     * Descriptive name of the project.
     *
     * @param name the project name
     * @return this operation
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     * @see #projectName()
     */
    public SpotBugsOperation projectName(String name) {
        projectName_ = TextTools.requireNotBlank(name, "projectName");

        return this;
    }

    /**
     * Returns the configured descriptive project name.
     *
     * @return the project name, or {@code null} if not configured
     * @see #projectName(String)
     */
    @Nullable
    public String projectName() {
        return projectName_;
    }

    /**
     * Suppress error messages.
     *
     * @param quiet set to {@code true} to suppress error messages, {@code false} otherwise
     * @return this operation
     * @see #quiet()
     */
    public SpotBugsOperation quiet(boolean quiet) {
        quiet_ = quiet;
        return this;
    }

    /**
     * Returns whether error messages are suppressed.
     *
     * @return {@code true} if error messages are suppressed, {@code false} otherwise
     * @see #quiet(boolean)
     */
    public boolean quiet() {
        return quiet_;
    }

    /**
     * Relaxed reporting mode.
     * <p>
     * For many detectors, this option suppresses the heuristics used to avoid reporting false positives.
     *
     * @param relaxed set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #relaxed()
     */
    public SpotBugsOperation relaxed(boolean relaxed) {
        relaxed_ = relaxed;
        return this;
    }

    /**
     * Returns whether relaxed reporting mode is enabled.
     *
     * @return {@code true} if relaxed mode is enabled, {@code false} otherwise
     * @see #relaxed(boolean)
     */
    public boolean relaxed() {
        return relaxed_;
    }

    /**
     * Set the release name of the analyzed application.
     *
     * @param release the release name
     * @return this operation
     * @throws NullPointerException     if {@code release} is {@code null}
     * @throws IllegalArgumentException if {@code release} is blank
     * @see #release()
     */
    public SpotBugsOperation release(String release) {
        release_ = TextTools.requireNotBlank(release, "release");

        return this;
    }

    /**
     * Returns the configured release name for the analyzed application.
     *
     * @return the release name, or {@code null} if not set
     * @see #release(String)
     */
    @Nullable
    public String release() {
        return release_;
    }

    /**
     * Produce the bug reports in SARIF 2.1.0.
     *
     * @param file the output file
     * @return this operation
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #sarif(String)
     * @see #sarif(Path)
     * @see #sarif()
     */
    public SpotBugsOperation sarif(File file) {
        ObjectTools.requireNonNull(file, SARIF);
        sarif_ = file;
        return this;
    }

    /**
     * Produce the bug reports in SARIF 2.1.0.
     *
     * @param filePath the output file path
     * @return this operation
     * @throws NullPointerException     if {@code filePath} is {@code null}
     * @throws IllegalArgumentException if {@code filePath} is blank
     * @see #sarif(File)
     * @see #sarif(Path)
     * @see #sarif()
     */
    public SpotBugsOperation sarif(String filePath) {
        TextTools.requireNotBlank(filePath, SARIF);
        sarif_ = new File(filePath);
        return this;
    }

    /**
     * Produce the bug reports in SARIF 2.1.0.
     *
     * @param path the output file
     * @return this operation
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #sarif(String)
     * @see #sarif(File)
     * @see #sarif()
     */
    public SpotBugsOperation sarif(Path path) {
        ObjectTools.requireNonNull(path, SARIF);
        sarif_ = path.toFile();
        return this;
    }

    /**
     * Returns the SARIF bug reports file.
     *
     * @return the bug reports file
     * @see #sarif(File)
     * @see #sarif(String)
     * @see #sarif(Path)
     */
    @Nullable
    public File sarif() {
        return sarif_;
    }

    /**
     * Returns whether bug instances will be sorted by class name.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     * @see #sortByClass(boolean)
     */
    public boolean sortByClass() {
        return sortByClass_;
    }

    /**
     * Sort reported bug instances by class name.
     *
     * @param sortByClass set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #sortByClass()
     */
    public SpotBugsOperation sortByClass(boolean sortByClass) {
        sortByClass_ = sortByClass;
        return this;
    }

    /**
     * Specify the source info file (line numbers for fields/classes).
     *
     * @param sourceInfo the source info file
     * @return this operation
     * @throws NullPointerException     if {@code sourceInfo} is {@code null}
     * @throws IllegalArgumentException if {@code sourceInfo} is blank
     * @see #sourceInfo(File)
     * @see #sourceInfo(Path)
     * @see #sourceInfo()
     */
    public SpotBugsOperation sourceInfo(String sourceInfo) {
        TextTools.requireNotBlank(sourceInfo, "sourceInfo");
        sourceInfo_ = new File(sourceInfo);
        return this;
    }

    /**
     * Specify the source info file (line numbers for fields/classes).
     *
     * @param file the source info file
     * @return this operation
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #sourceInfo(String)
     * @see #sourceInfo(Path)
     * @see #sourceInfo()
     */
    public SpotBugsOperation sourceInfo(File file) {
        ObjectTools.requireNonNull(file, "sourceInfo");
        sourceInfo_ = file;
        return this;
    }

    /**
     * Specify the source info file (line numbers for fields/classes).
     *
     * @param path the source info file
     * @return this operation
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #sourceInfo(String)
     * @see #sourceInfo(File)
     * @see #sourceInfo()
     */
    public SpotBugsOperation sourceInfo(Path path) {
        ObjectTools.requireNonNull(path, "sourceInfo");
        sourceInfo_ = path.toFile();
        return this;
    }

    /**
     * Returns the source info file containing line numbers for fields/classes.
     *
     * @return the source info file or {@code null}
     * @see #sourceInfo(String)
     * @see #sourceInfo(File)
     * @see #sourceInfo(Path)
     */
    @Nullable
    public File sourceInfo() {
        return sourceInfo_;
    }

    /**
     * Set source path for analyzed classes.
     *
     * @param sourcePaths the source paths
     * @return this operation
     * @throws NullPointerException     if {@code sourcePaths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourcePaths} is empty, or contains blank elements
     * @see #sourcePath(Path...)
     * @see #sourcePath(File...)
     * @see #sourcePath(Collection)
     * @see #sourcePath()
     */
    public SpotBugsOperation sourcePath(String... sourcePaths) {
        TextTools.requireNotBlank(SOURCE_PATH, sourcePaths);
        sourcePath_.addAll(List.of(sourcePaths));
        return this;
    }

    /**
     * Set source path for analyzed classes.
     *
     * @param paths the source paths
     * @return this operation
     * @throws NullPointerException     if {@code paths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code paths} is empty
     * @see #sourcePath(String...)
     * @see #sourcePath(File...)
     * @see #sourcePath(Collection)
     * @see #sourcePath()
     */
    public SpotBugsOperation sourcePath(Path... paths) {
        ObjectTools.requireNotEmpty(paths, SOURCE_PATH);
        sourcePath_.addAll(CollectionTools.combinePathsToStrings(paths));
        return this;
    }

    /**
     * Set source path for analyzed classes.
     *
     * @param files the source file paths
     * @return this operation
     * @throws NullPointerException     if {@code files} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code files} is empty
     * @see #sourcePath(String...)
     * @see #sourcePath(Path...)
     * @see #sourcePath(Collection)
     * @see #sourcePath()
     */
    public SpotBugsOperation sourcePath(File... files) {
        ObjectTools.requireNotEmpty(files, SOURCE_PATH);
        sourcePath_.addAll(CollectionTools.combineFilesToStrings(files));
        return this;
    }

    /**
     * Returns the source path for analyzed classes.
     *
     * @return a collection containing the source paths
     * @see #sourcePath(String...)
     * @see #sourcePath(Path...)
     * @see #sourcePath(File...)
     * @see #sourcePath(Collection)
     */
    public Set<String> sourcePath() {
        return sourcePath_;
    }

    /**
     * Set the source path for analyzed classes.
     *
     * @param sourcePaths the source paths
     * @return this operation
     * @throws NullPointerException     if {@code sourcePaths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code sourcePaths} is empty, or contains blank elements
     * @see #sourcePath(String...)
     * @see #sourcePath(Path...)
     * @see #sourcePath(File...)
     * @see #sourcePath()
     */
    public SpotBugsOperation sourcePath(Collection<String> sourcePaths) {
        TextTools.requireNotBlank(sourcePaths, SOURCE_PATH);
        sourcePath_.addAll(sourcePaths);
        return this;
    }

    /**
     * Set source path for analyzed classes.
     *
     * @param files the source file paths
     * @return this operation
     * @throws NullPointerException     if {@code files} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code files} is empty
     * @see #sourcePath(String...)
     * @see #sourcePath(Path...)
     * @see #sourcePath(File...)
     * @see #sourcePath()
     */
    public SpotBugsOperation sourcePathFiles(Collection<File> files) {
        ObjectTools.requireNotEmpty(files, "sourcePathFiles");
        sourcePath_.addAll(CollectionTools.combineFilesToStrings(files));
        return this;
    }

    /**
     * Set the source paths for analyzed classes.
     *
     * @param paths the source paths
     * @return this operation
     * @throws NullPointerException     if {@code paths} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code paths} is empty
     * @see #sourcePath(String...)
     * @see #sourcePath(Path...)
     * @see #sourcePath(File...)
     * @see #sourcePath()
     */
    public SpotBugsOperation sourcePathPaths(Collection<Path> paths) {
        ObjectTools.requireNotEmpty(paths, "sourcePathPaths");
        sourcePath_.addAll(CollectionTools.combinePathsToStrings(paths));
        return this;
    }

    /**
     * Returns the SpotBugs jar file.
     *
     * @return the SpotBugs jar file  or {@code null}
     * @see #spotBugsJar(String)
     * @see #spotBugsJar(File)
     * @see #spotBugsJar(Path)
     */
    @Nullable
    public File spotBugsJar() {
        return spotBugsJar_;
    }

    /**
     * Sets the SpotBugs jar file.
     *
     * @param jar the SpotBugs jar file
     * @return this operation
     * @throws NullPointerException     if {@code jar} is {@code null}
     * @throws IllegalArgumentException if {@code jar} is blank
     * @see #spotBugsJar(File)
     * @see #spotBugsJar(Path)
     * @see #spotBugsJar()
     */
    public SpotBugsOperation spotBugsJar(String jar) {
        TextTools.requireNotBlank(jar, "spotBugsJar");
        this.spotBugsJar_ = new File(jar);
        return this;
    }

    /**
     * Sets the SpotBugs jar file.
     *
     * @param path the SpotBugs jar file
     * @return this operation
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #spotBugsJar(File)
     * @see #spotBugsJar(String)
     * @see #spotBugsJar()
     */
    public SpotBugsOperation spotBugsJar(Path path) {
        ObjectTools.requireNonNull(path, "spotBugsJar");
        this.spotBugsJar_ = path.toFile();
        return this;
    }

    /**
     * Sets the SpotBugs jar file.
     *
     * @param file the SpotBugs jar file
     * @return this operation
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #spotBugsJar(String)
     * @see #spotBugsJar(Path)
     * @see #spotBugsJar()
     */
    public SpotBugsOperation spotBugsJar(File file) {
        ObjectTools.requireNonNull(file, "spotBugsJar");
        this.spotBugsJar_ = file;
        return this;
    }

    /**
     * Set the timestamp of results to be the current time.
     *
     * @param timeStampNow set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #timestampNow()
     */
    public SpotBugsOperation timestampNow(boolean timeStampNow) {
        timestampNow_ = timeStampNow;
        return this;
    }

    /**
     * Returns whether the timestamp of results will be set to the current time.
     *
     * @return {@code true} if timestampNow is enabled, {@code false} otherwise
     * @see #timestampNow(boolean)
     */
    public boolean timestampNow() {
        return timestampNow_;
    }

    /**
     * User preferences file.
     *
     * @param file the preferences file
     * @return this operation instance
     * @throws NullPointerException if {@code file} is {@code null}
     * @see #userPrefs(String)
     * @see #userPrefs(Path)
     */
    public SpotBugsOperation userPrefs(File file) {
        ObjectTools.requireNonNull(file, "userPrefs");
        userPrefs_ = file;
        return this;
    }

    /**
     * User preferences path.
     *
     * @param path the preferences path
     * @return this operation instance
     * @throws NullPointerException if {@code path} is {@code null}
     * @see #userPrefs(File)
     * @see #userPrefs(String)
     */
    public SpotBugsOperation userPrefs(Path path) {
        ObjectTools.requireNonNull(path, "userPrefs");
        userPrefs_ = path.toFile();
        return this;
    }

    /**
     * User preferences file.
     *
     * @param filePath the preferences file path
     * @return this operation instance
     * @throws NullPointerException     if {@code filePath} is {@code null}
     * @throws IllegalArgumentException if {@code filePath} is blank
     * @see #userPrefs(File)
     * @see #userPrefs(Path)
     */
    public SpotBugsOperation userPrefs(String filePath) {
        TextTools.requireNotBlank(filePath, "userPrefs");
        userPrefs_ = new File(filePath);
        return this;
    }

    /**
     * Returns the user preferences file.
     *
     * @return the user preferences file
     */
    @Nullable
    public File userPrefs() {
        return userPrefs_;
    }

    /**
     * Run only named visitors.
     *
     * @param visitors the visitors to run
     * @return this operation
     * @throws NullPointerException     if {@code visitors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code visitors} is empty, or contains blank elements
     * @see #visitors(Collection)
     * @see #visitors()
     */
    public SpotBugsOperation visitors(String... visitors) {
        TextTools.requireNotBlank("visitors", visitors);
        visitors_.addAll(List.of(visitors));
        return this;
    }

    /**
     * Run only named visitors.
     *
     * @param visitors the visitors to run
     * @return this operation
     * @throws NullPointerException     if {@code visitors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code visitors} is empty, or contains blank elements
     * @see #visitors(String...)
     * @see #visitors()
     */
    public SpotBugsOperation visitors(Collection<String> visitors) {
        TextTools.requireNotBlank(visitors, "visitors");
        visitors_.addAll(visitors);
        return this;
    }

    /**
     * Returns the collection of named visitors to run.
     *
     * @return a collection containing the visitors
     * @see #visitors(String...)
     * @see #visitors(Collection)
     */
    public Set<String> visitors() {
        return visitors_;
    }

    /**
     * Ensure analysis effort is at least {@link Effort#DEFAULT}.
     *
     * @param workHard set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     * @see #workHard()
     */
    public SpotBugsOperation workHard(boolean workHard) {
        workHard_ = workHard;
        return this;
    }

    /**
     * Returns whether analysis effort should be at least {@link Effort#DEFAULT}.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     * @see #workHard(boolean)
     */
    public boolean workHard() {
        return workHard_;
    }

    @SuppressFBWarnings(value = "EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS", justification = "For testing purposes")
    private File createAnalyzeFile(Collection<File> analyze) {
        try {
            File analyzeFile = createTempFile("analyzeFile");
            var analyzeList = analyze.stream().map(File::getAbsolutePath).toList();
            writeLinesToFile(analyzeList, analyzeFile);
            return analyzeFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create or write analyze file", e);
        }
    }

    private File createAuxClasspathFile(Collection<String> auxClasspath) {
        try {
            File auxFile = createTempFile("aux");
            writeLinesToFile(auxClasspath, auxFile);
            return auxFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create or write auxiliary classpath file", e);
        }
    }

    private File createTempFile(String prefix) throws IOException {
        var tempFile = Files.createTempFile("bld-spotbugs-" + prefix + '-', ".tmp").toFile();
        tempFile.deleteOnExit();
        return tempFile;
    }

    private Optional<Path> findExistingSourceFile(String relativePath) {
        return sourcePath_.stream()
                .map(Path::of)
                .map(base -> base.resolve(relativePath))
                .filter(path -> path.toFile().exists())
                .findFirst();
    }

    private Optional<String> findSpotBugsJar() {
        if (IOTools.exists(spotBugsJar_)) {
            return Optional.of(spotBugsJar_.getAbsolutePath());
        }

        if (home_ != null) {
            var jar = home_.resolve("lib").resolve("spotbugs.jar");
            if (Files.exists(jar)) {
                return Optional.of(jar.toAbsolutePath().toString());
            }
        }

        return Optional.empty();
    }

    private String formatLineNumber(int startLine) {
        if (startLine > 0) {
            if (includeLineNumber_) {
                return ":" + startLine;
            } else {
                return " [Line " + startLine + ']';
            }
        } else {
            return "";
        }
    }

    private String logFormat(String message, Object... args) {
        if (args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }

    private void printBugs(Collection<SpotBugsXmlParser.SpotBug> bugs, Map<String, String> bugMap) {
        if (silent() || !logger.isLoggable(Level.WARNING)) {
            return;
        }

        if (ObjectTools.isEmpty(bugs)) {
            logger.info(logFormat("No potential bugs found"));
        } else {
            var loggableFinest = logger.isLoggable(Level.FINEST);

            if (loggableFinest) {
                logger.finest(logFormat(bugs.toString()));
                logger.finest(logFormat(bugMap.toString()));
            }

            var classNames = new HashSet<String>();
            for (var result : bugs) {
                classNames.add(result.className());
                logger.warning(logFormat(
                        "%s%n" +
                                "    %s (%s)%n" +
                                "    %s%sClass: %s, Priority: %s, Rank: %s, Category: %s%n" +
                                "        --> %s",
                        sourcePathToUri(result.sourcePath(), result.startLine()),
                        result.type(),
                        bugMap.getOrDefault(result.type(), "n/a"),
                        result.method().isBlank() ? "" : "Method: " + result.method() + ", ",
                        result.field().isBlank() ? "" : "Field: " + result.field() + ", ",
                        result.className(),
                        result.priority(),
                        result.rank(),
                        result.category(),
                        detailedMessage_ ? result.message() : result.shortMessage()));
            }

            logger.warning(
                    logFormat("Found %d potential bug%s in %d class%s",
                            bugs.size(),
                            bugs.size() == 1 ? "" : "s",
                            classNames.size(),
                            classNames.size() == 1 ? "" : "es")
            );
        }
    }

    private String projectRelativePath(String path) {
        if (workDirectory_ != null) {
            if (path.equals(workDirectory_.getAbsolutePath())) {
                return ".";
            }
            var prefix = workDirectory_.getAbsolutePath() + File.separator;
            if (path.startsWith(workDirectory_.getAbsolutePath())) {
                return path.substring(prefix.length());
            }
        }

        return path;
    }

    private String sourcePathToUri(String path, int startLine) {
        return findExistingSourceFile(path)
                .map(resolvedPath -> resolvedPath.toUri() + formatLineNumber(startLine))
                .orElse(path);
    }
}
