/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright The original authors
 *
 *  Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.moditect.commands;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import org.moditect.internal.command.LogWriter;
import org.moditect.model.DependencyJar;
import org.moditect.model.Version;
import org.moditect.spi.log.Log;

public class GenerateModuleList {

    private final Path projectJar;
    private final Set<DependencyJar> dependencies;
    private final Version jvmVersion;
    private final Log log;

    private final ToolProvider jdeps;

    public GenerateModuleList(Path projectJar, Set<DependencyJar> dependencies, Version jvmVersion, Log log) {
        this.projectJar = projectJar;
        this.dependencies = dependencies;
        this.jvmVersion = jvmVersion;
        this.log = log;

        this.jdeps = ToolProvider
                .findFirst("jdeps")
                .orElseThrow(() -> new RuntimeException("jdeps tool not found"));
    }

    public void run() {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outStream);
        jdeps.run(out, System.err, "--version");
        out.close();
        int jdepsVersion = Runtime.Version
                .parse(outStream.toString().strip())
                .feature();
        if (jdepsVersion < 12) {
            log.error("The jdeps option this plugin uses to list JDK modules only works flawlessly on JDK 12+, so please use that to run this goal.");
            return;
        }

        List<String> command = new ArrayList<>();
        command.add("--print-module-deps");
        command.add("--ignore-missing-deps");
        command.add("--multi-release");
        command.add(String.valueOf(jvmVersion.feature()));
        command.add("--class-path");
        String classPath = dependencies.stream()
                .map(DependencyJar::jarPath)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        command.add(classPath);
        command.add(projectJar.toAbsolutePath().toString());

        log.debug("Running jdeps " + String.join(" ", command));

        LogWriter logWriter = new LogWriter(log);
        int result = jdeps.run(logWriter, logWriter, command.toArray(new String[0]));
        if (result != 0) {
            throw new IllegalStateException("Invocation of jdeps failed: jdeps " + String.join(" ", command));
        }
    }

}
