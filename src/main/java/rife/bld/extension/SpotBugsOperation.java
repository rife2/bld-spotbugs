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

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import rife.bld.BaseProject;
import rife.bld.extension.spotbugs.Effort;
import rife.bld.extension.spotbugs.Priority;
import rife.bld.operations.AbstractProcessOperation;
import rife.bld.operations.exceptions.ExitStatusException;
import rife.tools.exceptions.FileUtilsErrorException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run SpotBugs with the specified arguments.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@SuppressFBWarnings("EXS_EXCEPTION_SOFTENING_NO_CHECKED")
public class SpotBugsOperation extends AbstractProcessOperation<SpotBugsOperation> {
    /**
     * The error message indicating that the provided SpotBugs location is invalid.
     */
    public static final String INVALID_SPOTBUGS_LOCATION = "Please specify a valid SpotBugs (JAR or home) location.";

    private static final Logger LOGGER = Logger.getLogger(SpotBugsOperation.class.getName());
    private static final String LOG_PREFIX = "[spotbugs] ";
    private static final String SPOTBUGS_HOST = "spotbugs.readthedocs.io";
    private static final String SPOTBUGS_SARIF = "spotbugs.sarif";
    private static final String SPOTBUGS_XML = "spotbugs.xml";
    private final List<String> adjustPriority_ = new ArrayList<>();
    private final List<File> analyze_ = new ArrayList<>();
    private final List<String> auxClasspath_ = new ArrayList<>();
    private final List<String> bugCategories_ = new ArrayList<>();
    private final List<String> bugReporters_ = new ArrayList<>();
    private final List<String> choosePlugins_ = new ArrayList<>();
    private final List<String> chooseVisitors_ = new ArrayList<>();
    private final List<String> jvmArgs_ = new ArrayList<>();
    private final List<String> omitVisitors_ = new ArrayList<>();
    private final List<String> onlyAnalyze_ = new ArrayList<>();
    private final List<String> pluginList_ = new ArrayList<>();
    private final List<String> sourcePath_ = new ArrayList<>();
    private final List<String> visitors_ = new ArrayList<>();
    private boolean adjustExperimental_;
    private boolean applySuppression_;
    private boolean debug_;
    private boolean detailedMessage_;
    private boolean dontCombineWarnings_;
    private Effort effort_;
    private File emacs_;
    private File excludeBugs_;
    private File exclude_;
    private boolean experimental_;
    private boolean high_;
    private Path home_;
    private String htmlXsl_;
    private File html_;
    private boolean ignoreFailures_;
    private boolean includeLineNumber_ = true;
    private File include_;
    private boolean longBugCodes_;
    private boolean low_;
    private int maxHeap_;
    private int maxRank_;
    private boolean medium_;
    private boolean nested_;
    private boolean noClassOk_;
    private File output_ = new File(SPOTBUGS_XML);
    private boolean progress_;
    private String projectName_;
    private boolean relaxed_;
    private String release_;
    private File sarif_ = new File(SPOTBUGS_SARIF);
    private boolean sortByClass_;
    private File sourceInfo_;
    private File spotBugsJar_;
    private boolean timestampNow_;
    private File workDirectory_;
    private boolean workHard_;

    /**
     * Performs the operation.
     *
     * @throws InterruptedException    when the operation was interrupted
     * @throws IOException             when an exception occurred during the execution of the process
     * @throws FileUtilsErrorException when an exception occurred during the retrieval of the operation output
     * @throws ExitStatusException     when the exit status was changed during the operation
     */
    @Override
    @SuppressFBWarnings("BED_HIERARCHICAL_EXCEPTION_DECLARATION")
    public void execute() throws IOException, FileUtilsErrorException, InterruptedException, ExitStatusException {
        super.execute();

        var spotBugs = parseSpotBugsXml(output_.toPath());
        printBugs(spotBugs);

        if (!ignoreFailures_ && !spotBugs.isEmpty()) {
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    /**
     * Part of the execute operation constructs the command list to use for building the process.
     *
     * @return a list of strings representing the commands or an empty list if no commands are constructed.
     */
    @Override
    @SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
    @SuppressFBWarnings({"CC_CYCLOMATIC_COMPLEXITY", "PSC_PRESIZE_COLLECTIONS"})
    protected List<String> executeConstructProcessCommandList() {
        var loggableInfo = LOGGER.isLoggable(Level.INFO) && !silent();
        var loggableFine = LOGGER.isLoggable(Level.FINE) && !silent();

        List<String> cmd = new ArrayList<>();

        var jar = findSpotBugsJar();
        if (jar.isEmpty()) {
            throw new IllegalArgumentException(INVALID_SPOTBUGS_LOCATION);
        } else {
            if (!output_.getParentFile().exists() && !output_.getParentFile().mkdirs()) {
                throw new RuntimeException("Could not create output directory: " + output_.getParentFile());
            }

            File auxFile;
            try {
                auxFile = createTempFile("aux");
            } catch (IOException e) {
                throw new RuntimeException("Could not create auxiliary classpath file", e);
            }

            File analyzeFile;
            try {
                analyzeFile = createTempFile("analyzeFile");
            } catch (IOException e) {
                throw new RuntimeException("Could not create analyzeFile file", e);
            }

            // Java
            cmd.add(javaTool());

            // jvmArgs
            cmd.addAll(jvmArgs_);

            // maxHeapSize
            if (maxHeap_ > 0) {
                cmd.add("-Xmx");
                cmd.add(maxHeap_ + "m");
            }

            // debug
            if (debug_) {
                cmd.add("-Dfindbugs.debug=true");
            }

            // SpotBugs
            cmd.add("-jar");
            cmd.add(jar);

            // textui
            cmd.add("-textui");

            // quiet
            if (silent()) {
                cmd.add("-quiet");
            }

            // timestampNow
            if (timestampNow_) {
                cmd.add("-timestampNow");
            }

            // projectName
            if (projectName_ != null) {
                cmd.add("-projectName");
                cmd.add(projectName_);
            }

            // effort
            if (effort_ != null) {
                cmd.add("-effort:" + effort_.name().toLowerCase());
            }

            // adjustExperimental
            if (adjustExperimental_) {
                cmd.add("-adjustExperimental");
            }

            // workHard
            if (workHard_) {
                cmd.add("-workHard");
            }

            // longBugCodes
            if (longBugCodes_) {
                cmd.add("-longBugCodes");
            }

            // progress
            if (progress_) {
                cmd.add("-progress");
            }

            // release
            if (release_ != null) {
                cmd.add("-release");
                cmd.add(release_);
            }

            // experimental
            if (experimental_) {
                cmd.add("-experimental");
            }

            // low
            if (low_) {
                cmd.add("-low");
            }

            // medium
            if (medium_) {
                cmd.add("-medium");
            }

            // high
            if (high_) {
                cmd.add("-high");
            }

            // maxRank
            if (maxRank_ > 0) {
                cmd.add("-maxRank");
                cmd.add(String.valueOf(maxRank_));
            }

            // dontCombineWarnings
            if (dontCombineWarnings_) {
                cmd.add("-dontCombineWarnings");
            }

            // sortByClass
            if (sortByClass_) {
                cmd.add("-sortByClass");
            }

            // relaxed
            if (relaxed_) {
                cmd.add("-relaxed");
            }

            // sourceInfo
            if (sourceInfo_ != null) {
                cmd.add("-sourceInfo");
                cmd.add(sourceInfo_.getAbsolutePath());
            }

            // projectName
            if (projectName_ != null) {
                cmd.add("-projectName");
                cmd.add(projectName_);
            }

            // nested
            if (nested_) {
                cmd.add("-nested:true");
            }

            // html
            if (html_ != null) {
                if (htmlXsl_ != null) {
                    cmd.add("-html:" + htmlXsl_ + "=" + html_.getAbsolutePath());
                } else {
                    cmd.add("-html=" + html_.getAbsolutePath());
                }
            }

            // sarif
            if (sarif_ != null) {
                cmd.add("-sarif=" + sarif_.getAbsolutePath());
            }

            // emacs
            if (emacs_ != null) {
                cmd.add("-emacs=" + emacs_.getAbsolutePath());
            }

            // bugCategories
            if (!bugCategories_.isEmpty()) {
                cmd.add("-bugCategories");
                cmd.add(String.join(",", bugCategories_));
            }

            // onlyAnalyze
            if (!onlyAnalyze_.isEmpty()) {
                cmd.add("-onlyAnalyze");
                cmd.add(String.join(",", onlyAnalyze_));
            }

            // excludeBugs
            if (excludeBugs_ != null) {
                cmd.add("-excludeBugs");
                cmd.add(excludeBugs_.getAbsolutePath());
            }

            // exclude
            if (exclude_ != null) {
                cmd.add("-exclude");
                cmd.add(exclude_.getAbsolutePath());
            }

            // include
            if (include_ != null) {
                cmd.add("-include");
                cmd.add(include_.getAbsolutePath());
            }

            // applySuppression
            if (applySuppression_) {
                cmd.add("-applySuppression");
            }

            // visitors
            if (!visitors_.isEmpty()) {
                cmd.add("-visitors");
                cmd.add(String.join(",", visitors_));
            }

            // chooseVisitors
            if (!chooseVisitors_.isEmpty()) {
                cmd.add("-chooseVisitors");
                cmd.add(String.join(",", chooseVisitors_));
            }

            // omitVisitors
            if (!omitVisitors_.isEmpty()) {
                cmd.add("-omitVisitors");
                cmd.add(String.join(",", omitVisitors_));
            }

            // choosePlugins
            if (!choosePlugins_.isEmpty()) {
                cmd.add("-choosePlugins");
                cmd.add(String.join(",", choosePlugins_));
            }

            // adjustPriority
            if (!adjustPriority_.isEmpty()) {
                cmd.add("-adjustPriority");
                cmd.add(String.join(",", adjustPriority_));
            }

            // noClassOk
            if (noClassOk_) {
                cmd.add("-noClassOk");
            }

            // bugReporters
            if (!bugReporters_.isEmpty()) {
                cmd.add("-bugReporters");
                cmd.add(String.join(",", bugReporters_));
            }

            // pluginList
            if (!pluginList_.isEmpty()) {
                cmd.add("-pluginList");
                cmd.add(String.join(":", pluginList_));
            }

            // output
            cmd.add("-xml:withMessages=" + output_.getAbsolutePath());

            // auxClassPathFromFile
            if (!auxClasspath_.isEmpty()) {
                try {
                    writeLinesToFile(auxClasspath_, auxFile);
                } catch (IOException e) {
                    throw new RuntimeException("Could not write auxiliary classpath file", e);
                }
                cmd.add("-auxclasspathFromFile");
                cmd.add(auxFile.getAbsolutePath());

                if (loggableInfo) {
                    LOGGER.info(logFormat("auxclasspath" +
                            auxClasspath_.stream().map(this::projectRelativePath).toList()));
                }
            }

            // sourcepath
            if (!sourcePath_.isEmpty()) {
                cmd.add("-sourcepath");
                cmd.add(String.join(File.pathSeparator, sourcePath_));

                if (loggableInfo) {
                    LOGGER.info(logFormat("sourcepath" +
                            sourcePath_.stream().map(this::projectRelativePath).toList()));
                }
            }

            // analyzeFromFile
            if (!analyze_.isEmpty()) {
                var analyzeList = analyze_.stream().map(File::getAbsolutePath).toList();
                try {
                    writeLinesToFile(analyzeList, analyzeFile);
                } catch (IOException e) {
                    throw new RuntimeException("Could not write analyze file", e);
                }
                cmd.add("-analyzeFromFile");
                cmd.add(analyzeFile.getAbsolutePath());

                if (loggableInfo) {
                    LOGGER.info(logFormat("analyze" +
                            analyzeList.stream().map(this::projectRelativePath).toList()));
                }
            }
        }

        if (loggableFine) {
            LOGGER.fine(logFormat(String.join(" ", cmd)));
        }

        return cmd;
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
     *     <li>{@link #sarif() sarif} to {@code reports/spotbugs/spotbugs.sarif) in the</li>
     *     {@link BaseProject#buildDirectory() buildDirectory}</li>
     *     <li>{@link #sourcePath() sourcePath} to {@link BaseProject#srcMainJavaDirectory() srcMainJavaDirectory}
     *     and {@link BaseProject#srcMainResourcesDirectory() srcMainResourceDirectory}</li>
     * </ul>
     *
     * @param project the project to configure the compile operation from
     * @return this operation instance
     * @see #fromProject(BaseProject, boolean)
     */
    @Override
    public SpotBugsOperation fromProject(BaseProject project) {
        workDirectory_ = project.workDirectory();

        var reportsDir = Path.of(project.buildDirectory().getAbsolutePath(), "reports", "spotbugs").toFile();
        output_ = new File(reportsDir, SPOTBUGS_XML);
        sarif_ = new File(reportsDir, SPOTBUGS_SARIF);

        analyze_.add(project.buildMainDirectory());
        sourcePath_.add(project.srcMainResourcesDirectory().getAbsolutePath());
        sourcePath_.add(project.srcMainJavaDirectory().getAbsolutePath());
        auxClasspath_.addAll(project.compileMainClasspath());

        try {
            projectName_ = project.name();
        } catch (IllegalStateException ignored) {
            // do nothing
        }
        nested_ = true;
        timestampNow_ = true;

        return this;
    }

    private String findSpotBugsJar() {
        if (spotBugsJar_ != null && spotBugsJar_.exists()) {
            return spotBugsJar_.getAbsolutePath();
        }

        if (home_ != null) {
            var jar = home_.resolve("lib").resolve("spotbugs.jar");
            if (Files.exists(jar)) {
                return jar.toAbsolutePath().toString();
            }
        }

        return "";
    }

    private File createTempFile(String prefix) throws IOException {
        var tempFile = Files.createTempFile("bld-spotbugs-" + prefix + '-', ".tmp").toFile();
        tempFile.deleteOnExit();
        return tempFile;
    }

    private static void writeLinesToFile(Collection<String> lines, File file) throws IOException {
        Files.write(file.toPath(), lines);
    }

    private String projectRelativePath(String path) {
        if (path == null || workDirectory_ == null) {
            return path;
        }

        var prefix = workDirectory_.getAbsolutePath() + File.separator;
        if (path.startsWith(workDirectory_.getAbsolutePath())) {
            return path.substring(prefix.length());
        }

        return path;
    }

    private static List<SpotBug> parseSpotBugsXml(Path xmlPath) throws IOException {
        List<SpotBug> list = new ArrayList<>();
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);

        try {
            var db = dbf.newDocumentBuilder();
            var doc = db.parse(Files.newInputStream(xmlPath));
            var xpf = XPathFactory.newInstance();
            var xpath = xpf.newXPath();

            var bugInstances = (NodeList) xpath.evaluate("/BugCollection/BugInstance", doc,
                    XPathConstants.NODESET);

            for (int i = 0; i < bugInstances.getLength(); i++) {
                var bug = bugInstances.item(i);

                // Extract bug attributes
                var type = xpath.evaluate("@type", bug);
                var category = xpath.evaluate("@category", bug);
                var priority = xpath.evaluate("@priority", bug);
                var rank = xpath.evaluate("@rank", bug);

                // Extract messages with fallback
                var shortMessage = xpath.evaluate("ShortMessage/text()", bug);
                if (shortMessage == null) {
                    shortMessage = "";
                }

                var longMessage = xpath.evaluate("LongMessage/text()", bug);
                if (longMessage == null || longMessage.isEmpty()) {
                    longMessage = shortMessage;
                }

                // Extract class and method information
                var className = xpath.evaluate("Class/@classname", bug);
                var method = xpath.evaluate("Method/@name", bug);
                var field = xpath.evaluate("Field/@name", bug);


                var sourcePath = evaluateXPathWithFallbacks(xpath, bug,
                        "SourceLine/@sourcepath",
                        "Class/SourceLine/@sourcepath",
                        "Class/Method/SourceLine/@sourcepath");

                var startStr = evaluateXPathWithFallbacks(xpath, bug,
                        "SourceLine/@start",
                        "Class/SourceLine/@start",
                        "Class/Method/SourceLine/@start");

                var endStr = evaluateXPathWithFallbacks(xpath, bug,
                        "SourceLine/@end",
                        "Class/SourceLine/@end",
                        "Class/Method/SourceLine/@end");

                int startLine = parseIntOrDefault(startStr);
                int endLine = parseIntOrDefault(endStr);

                list.add(new SpotBug(
                        type,
                        category,
                        shortMessage.trim(),
                        longMessage.trim(),
                        priority,
                        rank,
                        className,
                        field,
                        method,
                        sourcePath,
                        startLine,
                        endLine));
            }

            return list;
        } catch (XPathExpressionException | ParserConfigurationException | SAXException e) {
            throw new IOException("Unable to parse XML report", e);
        }
    }

    private String logFormat(String message, Object... args) {
        if (args.length == 0) {
            return LOG_PREFIX + message;
        }
        return String.format(LOG_PREFIX + message, args);
    }

    /**
     * Parses the given string into an integer.
     * <p>
     * If the string is null, empty, or cannot be parsed as an integer, it returns the specified default value.
     *
     * @param s            the string to parse as an integer
     * @param defaultValue the value to return if parsing fails
     * @return the parsed integer value, or the default value if parsing fails
     */
    public static int parseIntOrDefault(String s, int defaultValue) {
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String evaluateXPathWithFallbacks(XPath xpath, Node node, String... expressions)
            throws XPathExpressionException {
        for (var expr : expressions) {
            var result = xpath.evaluate(expr, node);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return "";
    }

    /**
     * Parses the given string into an integer.
     * <p>
     * If the string cannot be parsed, returns a default value of {@code -1}.
     *
     * @param s the string to be parsed into an integer
     * @return the parsed integer value, or {@code -1} if parsing fails
     */
    public static int parseIntOrDefault(String s) {
        return parseIntOrDefault(s, -1);
    }

    private static Map<String, String> parseSpotBugsSarif(File sarifFile) throws IOException {
        Map<String, String> bugMap = new ConcurrentHashMap<>();

        if (sarifFile != null && sarifFile.exists()) {
            var mapper = new ObjectMapper();
            var root = mapper.readTree(sarifFile);

            var runs = root.get("runs");
            if (runs != null && runs.isArray()) {
                for (var run : runs) {
                    var tool = run.get("tool");
                    if (tool != null) {
                        var driver = tool.get("driver");
                        if (driver != null) {
                            var rules = driver.get("rules");
                            if (rules != null && rules.isArray()) {
                                for (var rule : rules) {
                                    var id = rule.has("id") ? rule.get("id").asText() : null;
                                    var helpUri = rule.has("helpUri") ? rule.get("helpUri").asText() : null;

                                    if (helpUri != null) {
                                        helpUri = normalizeSpotBugsUri(helpUri);
                                    }
                                    if (id != null) {
                                        bugMap.put(id, helpUri);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return bugMap;
    }

    private String sourcePathToUri(String path, int startLine) {
        return findExistingSourceFile(path)
                .map(resolvedPath -> resolvedPath.toUri() + formatLineNumber(startLine))
                .orElse(path);
    }

    private static String normalizeSpotBugsUri(String helpUri) {
        try {
            var uri = new URI(helpUri);
            if (uri.getHost() != null && SPOTBUGS_HOST.equalsIgnoreCase(uri.getHost())) {
                var frag = uri.getFragment();
                if (frag != null) {
                    return helpUri.replace(frag, frag.toLowerCase().replace('_', '-'));
                }
            }
        } catch (URISyntaxException ignored) {
            // return original
        }
        return helpUri;
    }

    private Optional<Path> findExistingSourceFile(String relativePath) {
        return sourcePath_.stream()
                .map(sourcePath -> Path.of(sourcePath, relativePath))
                .filter(path -> path.toFile().exists())
                .findFirst();
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

    /**
     * Lower priority of experimental Bug Patterns.
     *
     * @param adjustExperimental set to {@code true} to lower priority, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation adjustExperimental(boolean adjustExperimental) {
        adjustExperimental_ = adjustExperimental;
        return this;
    }

    /**
     * Returns whether experimental bug patterns will have their priority lowered.
     *
     * @return {@code true} if experimental patterns priorities are adjusted, {@code false} otherwise
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
     */
    public List<String> adjustPriorities() {
        return adjustPriority_;
    }

    /**
     * Adjust the priority of warnings for a given detector (simple or fully qualified class name) or bug pattern, or
     * suppress it completely.
     * <p>
     * An unsigned integer sets the priority to an absolute value.
     * <p>
     * Note that {@code -1} is equivalent {@link Priority#RAISE}, and semantically increases the priority.
     *
     * @param name     the detector or bug pattern name
     * @param priority the priority level to adjust
     * @see #adjustPriority(String, Priority)
     */
    public SpotBugsOperation adjustPriority(String name, int priority) {
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
     * @see #adjustPriority(String, int)
     */
    public SpotBugsOperation adjustPriority(String name, Priority priority) {
        adjustPriority_.add(name + "=" + priority.name().toLowerCase());
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param files array of file paths to analyze
     * @return this operation
     * @see #analyze(File...)
     * @see #analyze(Collection)
     */
    public SpotBugsOperation analyze(String... files) {
        analyze_.addAll(Arrays.stream(files).map(File::new).toList());
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param files array of files to analyze
     * @return this operation
     * @see #analyze(String...)
     * @see #analyze(Collection)
     */
    public SpotBugsOperation analyze(File... files) {
        analyze_.addAll(List.of(files));
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param files array of files to analyze
     * @return this operation
     * @see #analyze(String...)
     * @see #analyze(Collection)
     */
    public SpotBugsOperation analyze(Path... files) {
        analyze_.addAll(Arrays.stream(files).map(Path::toFile).toList());
        return this;
    }

    /**
     * Specifies files to analyze for bugs.
     *
     * @param files collection of files to analyze
     * @return this operation
     * @see #analyze(String...)
     * @see #analyze(File...)
     */
    public SpotBugsOperation analyze(Collection<?> files) {
        files.stream()
                .filter(Objects::nonNull)
                .map(this::toFile)
                .forEach(analyze_::add);
        return this;
    }

    @SuppressFBWarnings("ITC_INHERITANCE_TYPE_CHECKING")
    private File toFile(Object item) {
        if (item instanceof File) {
            return (File) item;
        } else if (item instanceof Path) {
            return ((Path) item).toFile();
        } else {
            return new File(item.toString());
        }
    }

    /**
     * Returns the collection of files configured to be analyzed.
     *
     * @return a collection containing the files to analyze
     */
    public List<File> analyze() {
        return analyze_;
    }

    /**
     * Exclude any bugs that match suppression filter loaded from fbp file.
     *
     * @param applySuppression set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation applySuppression(boolean applySuppression) {
        applySuppression_ = applySuppression;
        return this;
    }

    /**
     * Returns whether suppression from filter files is applied.
     *
     * @return {@code true} if suppression is applied, {@code false} otherwise
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
     * @param paths the auxiliary paths to set
     * @return this operation
     * @see #auxClasspath(Collection)
     */
    public SpotBugsOperation auxClasspath(String... paths) {
        auxClasspath_.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * Set the auxiliary classpath for analysis.
     * <p>
     * This classpath should include all jar files and directories containing classes that are part of the program
     * being analyzed, but you do not want to have analyzed for bugs.
     *
     * @param paths the auxiliary paths to set
     * @return this operation
     * @see #auxClasspath(String...)
     */
    public SpotBugsOperation auxClasspath(Collection<String> paths) {
        auxClasspath_.addAll(paths);
        return this;
    }

    /**
     * Returns the auxiliary classpath used for analysis.
     *
     * @return a collection containing the auxiliary classpath entries
     */
    public List<String> auxClasspath() {
        return auxClasspath_;
    }

    /**
     * 0nly report bugs in given categories.
     *
     * @param categories the bug categories
     * @return this operation
     * @see #bugCategories(Collection)
     */
    public SpotBugsOperation bugCategories(String... categories) {
        bugCategories_.addAll(Arrays.asList(categories));
        return this;
    }

    /**
     * 0nly report bugs in given categories.
     *
     * @param categories the bug categories
     * @return this operation
     * @see #bugCategories(String...)
     */
    public SpotBugsOperation bugCategories(Collection<String> categories) {
        bugCategories_.addAll(categories);
        return this;
    }

    /**
     * Returns the configured bug categories to report.
     *
     * @return a collection containing the bug categories
     */
    public List<String> bugCategories() {
        return bugCategories_;
    }

    /**
     * Bug reporter decorators to explicitly enable/disable.
     * <p>
     * Prefix the reporter name with {@code -} to disable.
     *
     * @param reporters the reporters to enable/disable
     * @return this operation
     * @see #bugReporters(Collection)
     */
    public SpotBugsOperation bugReporters(String... reporters) {
        bugReporters_.addAll(Arrays.asList(reporters));
        return this;
    }

    /**
     * Bug reporter decorators to explicitly enable/disable.
     * <p>
     * Prefix the reporter name with {@code -} to disable.
     *
     * @param reporters the reporters to enable/disable
     * @return this operation
     * @see #bugReporters(String...)
     */
    public SpotBugsOperation bugReporters(Collection<String> reporters) {
        bugReporters_.addAll(reporters);
        return this;
    }

    /**
     * Returns the collection of bug reporter decorators that are enabled/disabled.
     *
     * @return a collection containing the bug reporters
     */
    public List<String> bugReporters() {
        return bugReporters_;
    }

    /**
     * Selectively enable/disable plugins.
     * <p>
     * Prefix the plugin name with {@code -} to disable.
     *
     * @param plugins the plugins to enable/disable
     * @return this operation
     * @see #choosePlugins(Collection)
     */
    public SpotBugsOperation choosePlugins(String... plugins) {
        choosePlugins_.addAll(Arrays.asList(plugins));
        return this;
    }

    /**
     * Selectively enable/disable plugins.
     * <p>
     * Prefix the plugin name with {@code -} to disable.
     *
     * @param plugins the plugins to enable/disable
     * @return this operation
     * @see #choosePlugins(String...)
     */
    public SpotBugsOperation choosePlugins(Collection<String> plugins) {
        choosePlugins_.addAll(plugins);
        return this;
    }

    /**
     * Returns the collection of chosen plugin enable/disable decorators.
     *
     * @return a collection containing the chosen plugin strings
     */
    public List<String> choosePlugins() {
        return choosePlugins_;
    }

    /**
     * Selectively enable/disable detectors.
     * <p>
     * Prefix the detector name with {@code -} to disable.
     *
     * @param visitors the visitors to enable/disable
     * @return this operation
     * @see #chooseVisitors(Collection)
     */
    public SpotBugsOperation chooseVisitors(String... visitors) {
        chooseVisitors_.addAll(Arrays.asList(visitors));
        return this;
    }

    /**
     * Selectively enable/disable detectors.
     * <p>
     * Prefix the detector name with {@code -} to disable.
     *
     * @param visitors the visitors to enable/disable
     * @return this operation
     * @see #chooseVisitors(String...)
     */
    public SpotBugsOperation chooseVisitors(Collection<String> visitors) {
        chooseVisitors_.addAll(visitors);
        return this;
    }

    /**
     * Returns the collection of chosen visitor enable/disable decorators.
     *
     * @return a collection containing the chosen visitor strings
     */
    public List<String> chooseVisitors() {
        return chooseVisitors_;
    }

    /**
     * Sets the debug mode for the SpotBugs operation.
     *
     * @param debug {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation debug(boolean debug) {
        debug_ = debug;
        return this;
    }

    /**
     * Retrieves the debug state.
     *
     * @return {@code true} if debug mode is enabled, {@code false} otherwise.
     */
    public boolean debug() {
        return debug_;
    }

    /**
     * Sets whether detailed messages should be printed.
     *
     * @param detailedMessage {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation detailedMessage(boolean detailedMessage) {
        this.detailedMessage_ = detailedMessage;
        return this;
    }

    /**
     * Returns whether detailed messages are enabled.
     *
     * @return {@code true} if detailed messages will be printed, {@code false} otherwise
     */
    public boolean detailedMessage() {
        return detailedMessage_;
    }

    /**
     * Don't combine warnings that differ only in line number.
     *
     * @param dontCombineWarnings set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation dontCombineWarnings(boolean dontCombineWarnings) {
        dontCombineWarnings_ = dontCombineWarnings;
        return this;
    }

    /**
     * Returns whether warnings that differ only by line number are combined.
     *
     * @return {@code true} if warnings are not combined, {@code false} otherwise
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
     * @see Effort
     */
    public SpotBugsOperation effort(Effort effort) {
        effort_ = effort;
        return this;
    }

    /**
     * Returns the configured analysis effort level.
     *
     * @return the {@link Effort} configured for this operation, or {@code null} if none
     */
    public Effort effort() {
        return effort_;
    }

    /**
     * Produce the bug reports in Emacs format.
     *
     * @param file the output file
     * @return this operation
     * @see #emacs(String)
     * @see #emacs(Path)
     */
    public SpotBugsOperation emacs(File file) {
        emacs_ = file;
        return this;
    }

    /**
     * Produce the bug reports in Emacs format.
     *
     * @param file the output file
     * @return this operation
     * @see #emacs(String)
     * @see #emacs(File)
     */
    public SpotBugsOperation emacs(Path file) {
        emacs_ = file.toFile();
        return this;
    }

    /**
     * Produce the bug reports in Emacs format.
     *
     * @param file the output file
     * @return this operation
     * @see #emacs(File)
     * @see #emacs(Path)
     */
    public SpotBugsOperation emacs(String file) {
        emacs_ = new File(file);
        return this;
    }

    /**
     * Returns the Emacs bug reports file.
     *
     * @return the bug reports file
     */
    public File emacs() {
        return emacs_;
    }

    /**
     * Report all bug instances except those matching the filter specified by the
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param excludeFilter the filter file
     * @return this operation
     * @see #exclude(String)
     * @see #exclude(Path)
     */
    public SpotBugsOperation exclude(File excludeFilter) {
        exclude_ = excludeFilter;
        return this;
    }

    /**
     * Report all bug instances except those matching the filter specified by the
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param excludeFilter the filter file
     * @return this operation
     * @see #exclude(File)
     * @see #exclude(Path)
     */
    public SpotBugsOperation exclude(String excludeFilter) {
        exclude_ = new File(excludeFilter);
        return this;
    }

    /**
     * Report all bug instances except those matching the filter specified by the
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param excludeFilter the filter file
     * @return this operation
     * @see #exclude(File)
     * @see #exclude(String)
     */
    public SpotBugsOperation exclude(Path excludeFilter) {
        exclude_ = excludeFilter.toFile();
        return this;
    }

    /**
     * Returns the configured filter file used to exclude bugs.
     *
     * @return the exclude filter {@link File}, or {@code null} if none configured
     */
    public File exclude() {
        return exclude_;
    }

    /**
     * Exclude bugs that are also reported in the baseline XML output.
     *
     * @param excludeFile the exclude file
     * @return this operation
     * @see #excludeBugs(File)
     * @see #excludeBugs(Path)
     */
    public SpotBugsOperation excludeBugs(String excludeFile) {
        excludeBugs_ = new File(excludeFile);
        return this;
    }

    /**
     * Exclude bugs that are also reported in the baseline XML output.
     *
     * @param excludeFile the exclude file
     * @return this operation
     * @see #excludeBugs(String)
     * @see #excludeBugs(Path)
     */
    public SpotBugsOperation excludeBugs(File excludeFile) {
        excludeBugs_ = excludeFile;
        return this;
    }

    /**
     * Exclude bugs that are also reported in the baseline XML output.
     *
     * @param excludeFile the exclude file
     * @return this operation
     * @see #excludeBugs(File)
     * @see #excludeBugs(String)
     */
    public SpotBugsOperation excludeBugs(Path excludeFile) {
        excludeBugs_ = excludeFile.toFile();
        return this;
    }

    /**
     * Returns the exclude file used to exclude bugs.
     *
     * @return the exclude file
     */
    public File excludeBugs() {
        return excludeBugs_;
    }

    /**
     * Report of any confidence level including experimental bug patterns.
     *
     * @param experimental set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation experimental(boolean experimental) {
        experimental_ = experimental;
        return this;
    }

    /**
     * Returns whether experimental bug patterns are included in the report.
     *
     * @return {@code true} if experimental patterns are reported, {@code false} otherwise
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
     *     <li>{@link #output() output} to {@code reports/spotbugs/spotbugs.xml} in the
     *     {@link BaseProject#buildDirectory() buildDirectory}</li>
     *     <li>{@link #projectName() projectName} to the {@link BaseProject#name() project name}</li>
     *     <li>{@link #sarif() sarif} to {@code reports/spotbugs/spotbugs.sarif) in the</li>
     *     {@link BaseProject#buildDirectory() buildDirectory}</li>     *     <li>{@link #sourcePath() sourcePath} to {@link BaseProject#srcMainJavaDirectory() srcMainJavaDirectory}
     *     and {@link BaseProject#srcMainResourcesDirectory() srcMainResourceDirectory}</li>
     *     <li>{@link #timestampNow() timestampNow} to {@code true}</li>
     * </ul>
     * <p>
     * If {@code includeTest} is enabled, the {@code test} directories are also included.
     * </p>
     *
     * @param project     the project to configure the compile operation from
     * @param includeTest set to {@code true} to include test directories, {@code false} otherwise
     * @return this operation instance
     * @see #fromProject(BaseProject)
     */
    public SpotBugsOperation fromProject(BaseProject project, boolean includeTest) {
        fromProject(project);

        if (includeTest) {
            var reportsDir =
                    Path.of(project.buildDirectory().getAbsolutePath(), "reports", "spotbugs").toFile();
            output_ = new File(reportsDir, SPOTBUGS_XML);
            sarif_ = new File(reportsDir, SPOTBUGS_SARIF);

            analyze_.add(project.buildTestDirectory());
            sourcePath_.add(project.srcTestResourcesDirectory().getAbsolutePath());
            sourcePath_.add(project.srcTestJavaDirectory().getAbsolutePath());
            auxClasspath_.clear();
            auxClasspath_.addAll(project.compileTestClasspath());
        }

        return this;
    }

    /**
     * Report only high-priority bugs.
     *
     * @param high set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation high(boolean high) {
        high_ = high;
        return this;
    }

    /**
     * Returns whether only high-priority bugs will be reported.
     *
     * @return {@code true} if only high-priority bugs are reported, {@code false} otherwise
     */
    public boolean high() {
        return high_;
    }

    /**
     * Specify SpotBugs home directory.
     *
     * @param home the home directory
     * @return this operation
     * @see #home(File)
     * @see #home(Path)
     */
    public SpotBugsOperation home(String home) {
        home_ = Path.of(home);
        return this;
    }

    /**
     * Specify SpotBugs home directory.
     *
     * @param home the home directory
     * @return this operation
     * @see #home(String)
     * @see #home(Path)
     */
    public SpotBugsOperation home(File home) {
        home_ = home.toPath();
        return this;
    }

    /**
     * Specify SpotBugs home directory.
     *
     * @param home the home directory
     * @return this operation
     * @see #home(String)
     * @see #home(File)
     */
    public SpotBugsOperation home(Path home) {
        home_ = home;
        return this;
    }

    /**
     * Returns the SpotBugs home directory.
     *
     * @return the SpotBugs home path, or {@code null} if not set
     */
    public Path home() {
        return home_;
    }

    /**
     * Generate HTML output.
     *
     * @param file the output file
     * @return this operation
     * @see #html(String)
     * @see #html(Path)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     */
    public SpotBugsOperation html(File file) {
        html_ = file;
        return this;
    }

    /**
     * Generate HTML output.
     *
     * @param filePath the output file
     * @return this operation
     * @see #html(String)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     */
    public SpotBugsOperation html(Path filePath) {
        html_ = filePath.toFile();
        return this;
    }

    /**
     * Generate HTML output.
     *
     * @param file the output file
     * @return this operation
     * @see #html(File)
     * @see #html(Path)
     * @see #html(String, String)
     * @see #html(File, String)
     * @see #html(Path, String)
     */
    public SpotBugsOperation html(String file) {
        html_ = new File(file);
        return this;
    }

    /**
     * Returns the HTML output file.
     *
     * @return the HTML output file
     */
    public File html() {
        return html_;
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
     * The {@code plain.xsl} stylesheet does not use JavaScript or DOM, and may work better with older web browsers, or
     * for printing.
     * <p>
     * The {@code fancy.xsl} stylesheet uses DOM and JavaScript for navigation and CSS for visual presentation.
     * <p>
     * The {@code fancy-hist.xsl} an evolution of fancy.xsl stylesheet. It makes extensive use of DOM and JavaScript
     * for dynamically filtering the lists of bugs.
     * <p>
     * If you want to specify your own XSLT stylesheet to perform the transformation to HTML, specify the option as
     * {@code myStylesheet.xsl}, the filename of the stylesheet you want to use.
     *
     * @param file       the output file
     * @param stylesheet the stylesheet to use
     * @return this operation
     * @see #html(String)
     * @see #html(Path)
     * @see #html(File)
     * @see #html(File, String)
     * @see #html(Path, String)
     */
    public SpotBugsOperation html(String file, String stylesheet) {
        html_ = new File(file);
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
     * The {@code plain.xsl} stylesheet does not use JavaScript or DOM, and may work better with older web browsers, or
     * for printing.
     * <p>
     * The {@code fancy.xsl} stylesheet uses DOM and JavaScript for navigation and CSS for visual presentation.
     * <p>
     * The {@code fancy-hist.xsl} an evolution of fancy.xsl stylesheet. It makes extensive use of DOM and JavaScript
     * for dynamically filtering the lists of bugs.
     * <p>
     * If you want to specify your own XSLT stylesheet to perform the transformation to HTML, specify the option as
     * {@code myStylesheet.xsl}, the filename of the stylesheet you want to use.
     *
     * @param filePath   the output file
     * @param stylesheet the stylesheet to use
     * @return this operation
     * @see #html(String)
     * @see #html(File)
     * @see #html(Path)
     * @see #html(File, String)
     */
    public SpotBugsOperation html(Path filePath, String stylesheet) {
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
     * The {@code plain.xsl} stylesheet does not use JavaScript or DOM, and may work better with older web browsers,
     * or for printing.
     * <p>
     * The {@code fancy.xsl} stylesheet uses DOM and JavaScript for navigation and CSS for visual presentation.
     * <p>
     * The {@code fancy-hist.xsl} an evolution of fancy.xsl stylesheet. It makes extensive use of DOM and JavaScript
     * for dynamically filtering the lists of bugs.
     * <p>
     * If you want to specify your own XSLT stylesheet to perform the transformation to HTML, specify the option as
     * {@code myStylesheet.xsl}, the filename of the stylesheet you want to use.
     *
     * @param file       the output file
     * @param stylesheet the stylesheet to use
     * @return this operation
     * @see #html(String)
     * @see #html(Path)
     * @see #html(File)
     * @see #html(String, String)
     * @see #html(Path, String)
     */
    public SpotBugsOperation html(File file, String stylesheet) {
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
     */
    public SpotBugsOperation ignoreFailure(boolean ignoreFailures) {
        ignoreFailures_ = ignoreFailures;
        return this;
    }

    /**
     * Return the ignore failures flag.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public boolean ignoreFailures() {
        return ignoreFailures_;
    }

    public SpotBugsOperation include(String includeFilter) {
        include_ = new File(includeFilter);
        return this;
    }

    /**
     * Only report bug instances that match the filter specified by
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param includeFilter the filter file
     * @return this operation
     * @see #include(File)
     * @see #include(Path)
     */
    public SpotBugsOperation include(File includeFilter) {
        include_ = includeFilter;
        return this;
    }

    /**
     * Only report bug instances that match the filter specified by
     * <a href="https://spotbugs.readthedocs.io/en/latest/filter.html">filter file</a>.
     *
     * @param includeFilter the filter file
     * @return this operation
     * @see #include(File)
     * @see #include(String)
     */
    public SpotBugsOperation include(Path includeFilter) {
        include_ = includeFilter.toFile();
        return this;
    }

    /**
     * Returns the include filter file.
     *
     * @return the include filter {@link File}, or {@code null} if none configured
     */
    public File include() {
        return include_;
    }

    /**
     * Enable or disable line number in source file URIs.
     * <p>
     * While clicking on the URI works in IntelliJ IDEA, Visual Studio Code, etc.; it might not in terminal emulators.
     * <p><
     * Enabled by default.
     *
     * @param includeLineNumber {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation includeLineNumber(boolean includeLineNumber) {
        includeLineNumber_ = includeLineNumber;
        return this;
    }

    /**
     * Returns whether the line number should be included in source file URIs.
     *
     * @return {@code true} if the line number should be included, {@code false} otherwise
     */
    public boolean includeLineNumber() {
        return includeLineNumber_;
    }

    /**
     * Specifies arguments to pass to the JVM.
     *
     * @param args the args to pass to JVM
     * @return this operation
     * @see #jvmArgs(Collection)
     */
    public SpotBugsOperation jvmArgs(String... args) {
        jvmArgs_.addAll(Arrays.asList(args));
        return this;
    }

    /**
     * Specifies arguments to pass to the JVM.
     *
     * @param args the args to pass to JVM
     * @return this operation
     * @see #jvmArgs(String...)
     */
    public SpotBugsOperation jvmArgs(Collection<String> args) {
        jvmArgs_.addAll(args);
        return this;
    }

    /**
     * Returns the collection of JVM arguments configured for the SpotBugs run.
     *
     * @return a collection containing JVM arguments
     */
    public List<String> jvmArgs() {
        return jvmArgs_;
    }

    /**
     * Report long bug codes.
     *
     * @param longBugCodes set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation longBugCodes(boolean longBugCodes) {
        longBugCodes_ = longBugCodes;
        return this;
    }

    /**
     * Returns whether long bug codes are enabled.
     *
     * @return {@code true} if long bug codes will be reported, {@code false} otherwise
     */
    public boolean longBugCodes() {
        return longBugCodes_;
    }

    /**
     * Report all bugs.
     *
     * @param low set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation low(boolean low) {
        low_ = low;
        return this;
    }

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
     */
    public SpotBugsOperation maxHeap(int size) {
        maxHeap_ = size;
        return this;
    }

    /**
     * Returns the configured maximum Java heap size in megabytes.
     *
     * @return the maximum heap size in megabytes
     */
    public int maxHeap() {
        return maxHeap_;
    }

    /**
     * Only report issues with a bug rank at least as scary as that provided.
     *
     * @param maxRank the maximum rank
     * @return this operation
     */
    public SpotBugsOperation maxRank(int maxRank) {
        maxRank_ = maxRank;
        return this;
    }

    /**
     * Returns the configured maximum bug rank threshold.
     *
     * @return the maximum rank value
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
     */
    public SpotBugsOperation medium(boolean medium) {
        medium_ = medium;
        return this;
    }

    /**
     * Returns whether medium-priority bugs are reported.
     *
     * @return {@code true} if medium (and higher) priority bugs are reported, {@code false} otherwise
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
     */
    public SpotBugsOperation nested(boolean nested) {
        nested_ = nested;
        return this;
    }

    /**
     * Returns whether nested jar/zip scanning is enabled.
     *
     * @return {@code true} if nested scanning is enabled, {@code false} otherwise
     */
    public boolean nested() {
        return nested_;
    }

    /**
     * Output an empty warning file if no classes are specified.
     *
     * @param noClassOk set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation noClassOk(boolean noClassOk) {
        noClassOk_ = noClassOk;
        return this;
    }

    /**
     * Returns whether an empty warning file is allowed when no classes are specified.
     *
     * @return {@code true} if an empty warning file is allowed, {@code false} otherwise
     */
    public boolean noClassOk() {
        return noClassOk_;
    }

    /**
     * Omit named visitors.
     *
     * @param visitors the visitors to omit
     * @return this operation
     * @see #omitVisitors(Collection)
     */
    public SpotBugsOperation omitVisitors(String... visitors) {
        omitVisitors_.addAll(Arrays.asList(visitors));
        return this;
    }

    /**
     * Omit named visitors.
     *
     * @param visitors the visitors to omit
     * @return this operation
     * @see #omitVisitors(String...)
     */
    public SpotBugsOperation omitVisitors(Collection<String> visitors) {
        omitVisitors_.addAll(visitors);
        return this;
    }

    /**
     * Returns the collection of visitors configured to be omitted.
     *
     * @return a collection containing the visitors to omit
     */
    public List<String> omitVisitors() {
        return omitVisitors_;
    }

    /**
     * Restrict analysis to find bugs to ist of classes and packages.
     * <p>
     * Unlike filtering, this option avoids running analysis on classes and packages that are not explicitly matched:
     * for large projects, this may greatly reduce the amount of time needed to run the analysis.
     * (However, some detectors may produce inaccurate results if they aren’t run on the entire application.)
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
     * @see #onlyAnalyze(Collection)
     */
    public SpotBugsOperation onlyAnalyze(String... patterns) {
        onlyAnalyze_.addAll(Arrays.asList(patterns));
        return this;
    }

    /**
     * Restrict analysis to find bugs to ist of classes and packages.
     * <p>
     * Unlike filtering, this option avoids running analysis on classes and packages that are not explicitly matched:
     * for large projects, this may greatly reduce the amount of time needed to run the analysis.
     * (However, some detectors may produce inaccurate results if they aren’t run on the entire application.)
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
     * @see #onlyAnalyze(String...)
     */
    public SpotBugsOperation onlyAnalyze(Collection<String> patterns) {
        onlyAnalyze_.addAll(patterns);
        return this;
    }

    /**
     * Returns the collection of analyze-only patterns configured.
     *
     * @return a collection of analyze-only patterns
     */
    public List<String> onlyAnalyze() {
        return onlyAnalyze_;
    }

    /**
     * Sets the XML bug report file path.
     * <p>
     * The default is: {@code spotbugs.xml}
     *
     * @param file the file path
     * @return this operation
     * @see #output(File)
     * @see #output(Path)
     */
    public SpotBugsOperation output(String file) {
        output_ = new File(file);
        return this;
    }

    /**
     * Sets the XML bug report file path.
     * <p>
     * The default is: {@code spotbugs.xml}
     *
     * @param file the file path
     * @return this operation
     * @see #output(String)
     * @see #output(Path)
     */
    public SpotBugsOperation output(File file) {
        output_ = file;
        return this;
    }

    /**
     * Sets the XML bug report file path.
     * <p>
     * The default is: {@code spotbugs.xml}
     *
     * @param filePath the file path
     * @return this operation
     * @see #output(String)
     * @see #output(File)
     */
    public SpotBugsOperation output(Path filePath) {
        output_ = filePath.toFile();
        return this;
    }

    /**
     * Returns the configured XML bug report output file.
     *
     * @return the output {@link File}
     */
    public File output() {
        return output_;
    }

    /**
     * Specify a list of plugin Jar files to load.
     *
     * @param plugins the plugin list
     * @return this operation
     * @see #pluginList(Collection)
     */
    public SpotBugsOperation pluginList(String... plugins) {
        pluginList_.addAll(Arrays.asList(plugins));
        return this;
    }

    /**
     * Specify a list of plugin Jar files to load.
     *
     * @param plugins the plugin list
     * @return this operation
     * @see #pluginList(String...)
     */
    public SpotBugsOperation pluginList(Collection<String> plugins) {
        pluginList_.addAll(plugins);
        return this;
    }

    /**
     * Returns the collection of plugin jar files to load.
     *
     * @return a collection containing the plugin jar files
     */
    public List<String> pluginList() {
        return pluginList_;
    }

    /**
     * Display progress in the terminal window.
     *
     * @param progress set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation progress(boolean progress) {
        progress_ = progress;
        return this;
    }

    /**
     * Retrieves the current progress setting.
     *
     * @return {@code true} if progress display is enabled, {@code false} otherwise
     */
    public boolean progress() {
        return progress_;
    }

    /**
     * Descriptive name of the project.
     *
     * @param name the project name
     * @return this operation
     */
    public SpotBugsOperation projectName(String name) {
        projectName_ = name;
        return this;
    }

    /**
     * Returns the configured descriptive project name.
     *
     * @return the project name, or {@code null} if not configured
     */
    public String projectName() {
        return projectName_;
    }

    /**
     * Relaxed reporting mode.
     * <p>
     * For many detectors, this option suppresses the heuristics used to avoid reporting false positives.
     *
     * @param relaxed set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation relaxed(boolean relaxed) {
        relaxed_ = relaxed;
        return this;
    }

    /**
     * Returns whether relaxed reporting mode is enabled.
     *
     * @return {@code true} if relaxed mode is enabled, {@code false} otherwise
     */
    public boolean relaxed() {
        return relaxed_;
    }

    /**
     * Set the release name of the analyzed application.
     *
     * @param release the release name
     * @return this operation
     */
    public SpotBugsOperation release(String release) {
        release_ = release;
        return this;
    }

    /**
     * Returns the configured release name for the analyzed application.
     *
     * @return the release name, or {@code null} if not set
     */
    public String release() {
        return release_;
    }

    /**
     * Produce the bug reports in SARIF 2.1.0.
     *
     * @param file the output file
     * @return this operation
     * @see #sarif(String)
     * @see #sarif(Path)
     */
    public SpotBugsOperation sarif(File file) {
        sarif_ = file;
        return this;
    }

    /**
     * Produce the bug reports in SARIF 2.1.0.
     *
     * @param file the output file
     * @return this operation
     * @see #sarif(File)
     * @see #sarif(Path)
     */
    public SpotBugsOperation sarif(String file) {
        sarif_ = new File(file);
        return this;
    }

    /**
     * Produce the bug reports in SARIF 2.1.0.
     *
     * @param filePath the output file
     * @return this operation
     * @see #sarif(String)
     * @see #sarif(File)
     */
    public SpotBugsOperation sarif(Path filePath) {
        sarif_ = filePath.toFile();
        return this;
    }

    /**
     * Returns the SARIF bug reports file.
     *
     * @return the bug reports file
     */
    public File sarif() {
        return sarif_;
    }

    /**
     * Returns whether bug instances will be sorted by class name.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public boolean sortByClass() {
        return sortByClass_;
    }

    /**
     * Sort reported bug instances by class name.
     *
     * @param sortByClass set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation sortByClass(boolean sortByClass) {
        sortByClass_ = sortByClass;
        return this;
    }

    /**
     * Specify the source info file (line numbers for fields/classes)
     *
     * @param sourceInfo the source info file
     * @return this operation
     * @see #sourceInfo(File)
     * @see #sourceInfo(Path)
     */
    public SpotBugsOperation sourceInfo(String sourceInfo) {
        sourceInfo_ = new File(sourceInfo);
        return this;
    }

    /**
     * Specify the source info file (line numbers for fields/classes)
     *
     * @param sourceInfo the source info file
     * @return this operation
     * @see #sourceInfo(String)
     * @see #sourceInfo(Path)
     */
    public SpotBugsOperation sourceInfo(File sourceInfo) {
        sourceInfo_ = sourceInfo;
        return this;
    }

    /**
     * Specify the source info file (line numbers for fields/classes)
     *
     * @param sourceInfo the source info file
     * @return this operation
     * @see #sourceInfo(String)
     * @see #sourceInfo(File)
     */
    public SpotBugsOperation sourceInfo(Path sourceInfo) {
        sourceInfo_ = sourceInfo.toFile();
        return this;
    }

    /**
     * Returns the source info file containing line numbers for fields/classes.
     *
     * @return the source info file
     */
    public File sourceInfo() {
        return sourceInfo_;
    }

    /**
     * Set the source path for analyzed classes.
     *
     * @param path the source path
     * @return this operation
     * @see #sourcePath(Path... path)
     * @see #sourcePath(File... path)
     * @see #sourcePath(Collection)
     */
    public SpotBugsOperation sourcePath(String... path) {
        sourcePath_.addAll(Arrays.asList(path));
        return this;
    }

    /**
     * Set the source path for analyzed classes.
     * * @see #sourcePathPaths(Collection)
     *
     * @param path the source path
     * @return this operation
     * @see #sourcePath(String... path)
     * @see #sourcePath(File... path)
     * @see #sourcePath(Collection)
     */
    public SpotBugsOperation sourcePath(Path... path) {
        sourcePath_.addAll(Arrays.stream(path).map(Path::toString).toList());
        return this;
    }

    /**
     * Set the source path for analyzed classes.
     *
     * @param path the source path
     * @return this operation
     * @see #sourcePath(String... path)
     * @see #sourcePath(Path... path)
     * @see #sourcePath(Collection)
     */
    public SpotBugsOperation sourcePath(File... path) {
        sourcePath_.addAll(Arrays.stream(path).map(File::getAbsolutePath).toList());
        return this;
    }

    /**
     * Returns the source path for analyzed classes.
     *
     * @return a collection containing the source paths
     */
    public List<String> sourcePath() {
        return sourcePath_;
    }

    /**
     * Set the source path for analyzed classes.
     *
     * @param path the source path
     * @return this operation
     * @see #sourcePath(String... path)
     * @see #sourcePath(Path... path)
     * @see #sourcePath(File... path)
     * @see #sourcePath(Collection)
     */
    public SpotBugsOperation sourcePath(Collection<?> path) {
        path.stream()
                .filter(Objects::nonNull)
                .map(this::toAbsolutePath)
                .forEach(sourcePath_::add);
        return this;
    }

    private String toAbsolutePath(Object item) {
        if (item instanceof File) {
            return ((File) item).getAbsolutePath();
        } else if (item instanceof Path) {
            return ((Path) item).toAbsolutePath().toString();
        } else {
            return item.toString();
        }
    }

    /**
     * Returns the SpotBugs jar file.
     *
     * @return the SpotBugs jar file
     */
    public File spotBugsJar() {
        return spotBugsJar_;
    }

    /**
     * Sets the SpotBugs jar file.
     *
     * @param jar the SpotBugs jar file
     * @return this operation
     * @see #spotBugsJar(File)
     * @see #spotBugsJar(Path)
     */
    public SpotBugsOperation spotBugsJar(String jar) {
        this.spotBugsJar_ = new File(jar);
        return this;
    }

    /**
     * Sets the SpotBugs jar file.
     *
     * @param jarPath the SpotBugs jar file
     * @return this operation
     * @see #spotBugsJar(File)
     * @see #spotBugsJar(String)
     */
    public SpotBugsOperation spotBugsJar(Path jarPath) {
        this.spotBugsJar_ = jarPath.toFile();
        return this;
    }

    /**
     * Sets the SpotBugs jar file.
     *
     * @param jar the SpotBugs jar file
     * @return this operation
     * @see #spotBugsJar(String)
     * @see #spotBugsJar(File)
     */
    public SpotBugsOperation spotBugsJar(File jar) {
        this.spotBugsJar_ = jar;
        return this;
    }

    /**
     * Set the timestamp of results to be the current time.
     *
     * @param timeStampNow set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation timestampNow(boolean timeStampNow) {
        timestampNow_ = timeStampNow;
        return this;
    }

    /**
     * Returns whether the timestamp of results will be set to the current time.
     *
     * @return {@code true} if timestampNow is enabled, {@code false} otherwise
     */
    public boolean timestampNow() {
        return timestampNow_;
    }

    /**
     * Run only named visitors.
     *
     * @param visitors the visitors to run
     * @return this operation
     * @see #visitors(Collection)
     */
    public SpotBugsOperation visitors(String... visitors) {
        visitors_.addAll(Arrays.asList(visitors));
        return this;
    }

    /**
     * Run only named visitors.
     *
     * @param visitors the visitors to run
     * @return this operation
     * @see #visitors(String...)
     */
    public SpotBugsOperation visitors(Collection<String> visitors) {
        visitors_.addAll(visitors);
        return this;
    }

    /**
     * Returns the collection of named visitors to run.
     *
     * @return a collection containing the visitors
     */
    public List<String> visitors() {
        return visitors_;
    }

    /**
     * Ensure analysis effort is at least {@link Effort#DEFAULT}.
     *
     * @param workHard set to {@code true} to enable, {@code false} otherwise
     * @return this operation
     */
    public SpotBugsOperation workHard(boolean workHard) {
        workHard_ = workHard;
        return this;
    }

    /**
     * Returns whether analysis effort should be at least {@link Effort#DEFAULT}.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public boolean workHard() {
        return workHard_;
    }

    private void printBugs(Collection<SpotBug> bugs) {
        if (silent()) {
            return;
        }

        var loggableWarning = LOGGER.isLoggable(Level.WARNING);
        var loggableFinest = LOGGER.isLoggable(Level.FINEST);

        if (bugs.isEmpty()) {
            if (loggableWarning) {
                LOGGER.info(logFormat("No potential bugs found"));
            }
        } else {
            if (loggableFinest) {
                LOGGER.finest(logFormat(bugs.toString()));
            }
            if (loggableWarning) {
                LOGGER.warning(
                        logFormat("Found %d potential bug%s", bugs.size(), bugs.size() == 1 ? "" : "s")
                );
            }

            var bugsMaps = new HashMap<String, String>();
            try {
                bugsMaps.putAll(parseSpotBugsSarif(sarif_));
                if (loggableFinest) {
                    LOGGER.finest(logFormat(bugsMaps.toString()));
                }
            } catch (IOException e) {
                if (loggableWarning) {
                    LOGGER.warning(logFormat(("Unable to parse SARIF report: %s"), e.getMessage()));
                }
            }

            if (loggableWarning) {
                for (var result : bugs) {
                    LOGGER.warning(logFormat(
                            "%s%n" +
                                    "    %s (%s)%n" +
                                    "    %s%sClass: %s, Priority: %s, Rank: %s, Category: %s%n" +
                                    "        --> %s",
                            sourcePathToUri(result.sourcePath(), result.startLine),
                            result.type,
                            bugsMaps.getOrDefault(result.type, "n/a"),
                            result.method.isBlank() ? "" : "Method: " + result.method + ", ",
                            result.field.isBlank() ? "" : "Field: " + result.field + ", ",
                            result.className,
                            result.priority,
                            result.rank,
                            result.category,
                            detailedMessage_ ? result.message : result.shortMessage));
                }
            }
        }
    }

    private record SpotBug(
            String type,
            String category,
            String shortMessage,
            String message,
            String priority,
            String rank,
            String className,
            String field,
            String method,
            String sourcePath,
            int startLine,
            int endLine) {
    }
}