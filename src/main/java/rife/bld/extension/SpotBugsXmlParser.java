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

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import rife.bld.extension.tools.IOTools;
import rife.bld.extension.tools.TextTools;

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

final class SpotBugsXmlParser {

    private static final String SPOTBUGS_HOST = "spotbugs.readthedocs.io";

    private SpotBugsXmlParser() {
    }

    private static String evaluateXPathWithFallbacks(XPath xpath, Node node, String... expressions)
            throws XPathExpressionException {
        for (var expr : expressions) {
            var result = xpath.evaluate(expr, node);
            if (TextTools.isNotEmpty(result)) {
                return result;
            }
        }
        return "";
    }

    private static String normalizeSpotBugsUri(String helpUri) {
        try {
            var uri = new URI(helpUri);
            if (uri.getHost() != null
                    && SPOTBUGS_HOST.equalsIgnoreCase(uri.getHost())
            ) {
                var frag = uri.getFragment();
                if (frag != null) {
                    return helpUri.replace(frag, frag.toLowerCase(Locale.ROOT).replace('_', '-'));
                }
            }
        } catch (URISyntaxException ignored) {
            // return original
        }
        return helpUri;
    }

    static List<SpotBug> parse(Path xmlPath) throws IOException {
        var list = new ArrayList<SpotBug>();

        if (!IOTools.exists(xmlPath) || xmlPath.toFile().length() == 0) {
            return list;
        }

        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            var db = dbf.newDocumentBuilder();
            var xPathFactory = XPathFactory.newInstance();

            try (var is = Files.newInputStream(xmlPath)) {
                var doc = db.parse(is);
                var xpath = xPathFactory.newXPath();

                var bugInstances = (NodeList) xpath.evaluate("/BugCollection/BugInstance", doc,
                        XPathConstants.NODESET);

                for (int i = 0; i < bugInstances.getLength(); i++) {
                    var bug = bugInstances.item(i);

                    var type = xpath.evaluate("@type", bug);
                    var category = xpath.evaluate("@category", bug);
                    var priority = xpath.evaluate("@priority", bug);
                    var rank = xpath.evaluate("@rank", bug);

                    var shortMessage = xpath.evaluate("ShortMessage/text()", bug);
                    if (shortMessage == null) {
                        shortMessage = "";
                    }

                    var longMessage = xpath.evaluate("LongMessage/text()", bug);
                    if (TextTools.isEmpty(longMessage)) {
                        longMessage = shortMessage;
                    }

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
            }
        } catch (XPathExpressionException | ParserConfigurationException | SAXException e) {
            throw new IOException("Unable to parse XML report", e);
        }

        return list;
    }

    private static int parseIntOrDefault(String s) {
        if (TextTools.isEmpty(s)) {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static Map<String, String> parseSarif(@NonNull File sarifFile) throws IOException {
        Map<String, String> bugMap = new HashMap<>();

        if (!IOTools.exists(sarifFile) || sarifFile.length() == 0) {
            return bugMap;
        }

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

        return bugMap;
    }

    record SpotBug(
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