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

package rife.bld.extension.spotbugs;

/**
 * Command line flags supported by SpotBugs {@code -textui}.
 * <p>
 * Values correspond to flags accepted by {@code edu.umd.cs.findbugs.FindBugs2}
 * as of SpotBugs 4.9.8. See {@code java -jar spotbugs.jar -textui -help}.
 */
public enum SpotBugsFlag {
    /**
     * Lower priority of experimental Bug Patterns
     */
    ADJUST_EXPERIMENTAL("-adjustExperimental"),
    /**
     * Adjust priority of warnings for given detectors or bug patterns
     */
    ADJUST_PRIORITY("-adjustPriority"),
    /**
     * Get the list of class/jar files from a designated file
     */
    ANALYZE_FROM_FILE("-analyzeFromFile"),
    /**
     * Exclude any bugs that match suppression filter loaded from fbp file
     */
    APPLY_SUPPRESSION("-applySuppression"),
    /**
     * Set aux classpath for analysis
     */
    AUX_CLASSPATH("-auxclasspath"),
    /**
     * Read aux classpaths from a designated file
     */
    AUX_CLASSPATH_FROM_FILE("-auxclasspathFromFile"),
    /**
     * Read aux classpath from standard input
     */
    AUX_CLASSPATH_FROM_INPUT("-auxclasspathFromInput"),
    /**
     * Only report bugs in given categories: {@code -bugCategories cat1[,cat2...]}
     */
    BUG_CATEGORIES("-bugCategories"),
    /**
     * Bug reporter decorators to explicitly enable/disable
     */
    BUG_REPORTERS("-bugReporters"),
    /**
     * Selectively enable/disable plugins: {@code -choosePlugins +p1,-p2,...}
     */
    CHOOSE_PLUGINS("-choosePlugins"),
    /**
     * Selectively enable/disable detectors: {@code -chooseVisitors +v1,-v2,...}
     */
    CHOOSE_VISITORS("-chooseVisitors"),
    /**
     * Same as {@code -effort:min}, for backward compatibility
     */
    CONSERVE_SPACE("-conserveSpace"),
    /**
     * Don't combine warnings that differ only in line number
     */
    DONT_COMBINE_WARNINGS("-dontCombineWarnings"),
    /**
     * Set analysis effort level: {@code -effort:[min|less|default|more|max]}
     */
    EFFORT("-effort"),
    /**
     * Use emacs reporting format
     */
    EMACS("-emacs"),
    /**
     * Exclude bugs matching given filter
     */
    EXCLUDE("-exclude"),
    /**
     * Exclude bugs that are also reported in the baseline xml output
     */
    EXCLUDE_BUGS("-excludeBugs"),
    /**
     * Set exit code of process
     */
    EXIT_CODE("-exitcode"),
    /**
     * Report of any confidence level including experimental bug patterns
     */
    EXPERIMENTAL("-experimental"),
    /**
     * Report only high confidence warnings
     */
    HIGH("-high"),
    /**
     * Specify SpotBugs home directory
     */
    HOME("-home"),
    /**
     * Generate HTML output: {@code -html[:stylesheet]}
     */
    HTML("-html"),
    /**
     * Include only bugs matching given filter
     */
    INCLUDE("-include"),
    /**
     * Specify location of JRE
     */
    JAVA_HOME("-javahome"),
    /**
     * Pass args to JVM
     */
    JVM_ARGS("-jvmArgs"),
    /**
     * Report long bug codes
     */
    LONG_BUG_CODES("-longBugCodes"),
    /**
     * Report warnings of any confidence level
     */
    LOW("-low"),
    /**
     * Maximum Java heap size in megabytes
     */
    MAX_HEAP("-maxHeap"),
    /**
     * Only report issues with a bug rank at least as scary as that provided
     */
    MAX_RANK("-maxRank"),
    /**
     * Report only medium and high confidence warnings [default]
     */
    MEDIUM("-medium"),
    /**
     * Analyze nested jar/zip archives: {@code -nested[:true|false]}
     */
    NESTED("-nested"),
    /**
     * Output empty warning file if no classes are specified
     */
    NO_CLASS_OK("-noClassOk"),
    /**
     * Omit named visitors: {@code -omitVisitors v1[,v2...]}
     */
    OMIT_VISITORS("-omitVisitors"),
    /**
     * Only analyze given classes and packages
     */
    ONLY_ANALYZE("-onlyAnalyze"),
    /**
     * Save output in named file
     */
    OUTPUT("-output"),
    /**
     * Specify list of plugin Jar files to load: {@code -pluginList jar1[:jar2...]}
     */
    PLUGIN_LIST("-pluginList"),
    /**
     * Print configuration and exit, without running analysis
     */
    PRINT_CONFIGURATION("-printConfiguration"),
    /**
     * Display progress in terminal window
     */
    PROGRESS("-progress"),
    /**
     * Analyze given project
     */
    PROJECT("-project"),
    /**
     * Descriptive name of project
     */
    PROJECT_NAME("-projectName"),
    /**
     * Suppress error messages
     */
    QUIET("-quiet"),
    /**
     * Redo analysis in provided file
     */
    REANALYZE("-reanalyze"),
    /**
     * Redo analysis using configuration from previous analysis
     */
    REDO_ANALYSIS("-redoAnalysis"),
    /**
     * Relaxed reporting mode (more false positives!)
     */
    RELAXED("-relaxed"),
    /**
     * Set the release name of the analyzed application
     */
    RELEASE("-release"),
    /**
     * SARIF 2.1.0 output
     */
    SARIF("-sarif"),
    /**
     * Show list of available detector plugins
     */
    SHOW_PLUGINS("-showPlugins"),
    /**
     * Sort warnings by class
     */
    SORT_BY_CLASS("-sortByClass"),
    /**
     * Specify source info file (line numbers for fields/classes)
     */
    SOURCE_INFO("-sourceInfo"),
    /**
     * Set source path for analyzed classes
     */
    SOURCE_PATH("-sourcepath"),
    /**
     * Set timestamp of results to be current time
     */
    TIMESTAMP_NOW("-timestampNow"),
    /**
     * Save training data (experimental): {@code -train[:outputDir]}
     */
    TRAIN("-train"),
    /**
     * User preferences file
     */
    USER_PREFS("-userPrefs"),
    /**
     * Use training data (experimental): {@code -useTraining[:inputDir]}
     */
    USE_TRAINING("-useTraining"),
    /**
     * Print version, check for updates and exit, without running analysis
     */
    VERSION("-version"),
    /**
     * Run only named visitors: {@code -visitors v1[,v2...]}
     */
    VISITORS("-visitors"),
    /**
     * Ensure analysis effort is at least 'default'
     */
    WORK_HARD("-workHard"),
    /**
     * Get list of classfiles/jarfiles from standard input rather than command line
     */
    XARGS("-xargs"),
    /**
     * xdoc XML output to use with Apache Maven
     */
    XDOCS("-xdocs"),
    /**
     * XML output: {@code -xml[:withMessages]}
     */
    XML("-xml"),
    /**
     * XML output with messages: {@code -xml:withMessages}
     */
    XML_WITH_MESSAGES("-xml:withMessages");

    private final String flag;

    SpotBugsFlag(String flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return flag;
    }

    /**
     * @return the literal flag, e.g. {@code "-noClassOk"}
     */
    public String flag() {
        return flag;
    }
}