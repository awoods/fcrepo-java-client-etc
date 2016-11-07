/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.client.etc;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;

import java.io.PrintWriter;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author awoods
 * @since 2016-11-16
 */
public class ArgParser {

    private static final Logger LOGGER = getLogger(ArgParser.class);

    private final Options options;

    /**
     * Constructor that creates the command line options
     */
    public ArgParser() {
        // Command Line Options
        options = new Options();

        // Source Resource option
        options.addOption(Option.builder("b")
                .longOpt("baseURL")
                .hasArg(true).numberOfArgs(1).argName("baseURL")
                .desc("Base URL from which the walker will traverse.").build());

        // username option
        final Option userOption = Option.builder("u")
                .longOpt("username")
                .hasArg(true).numberOfArgs(1).argName("username")
                .desc("username for Fedora basic authentication").build();
        options.addOption(userOption);

        // password option
        final Option passOption = Option.builder("p")
                .longOpt("password")
                .hasArg(true).numberOfArgs(1).argName("password")
                .desc("password for Fedora basic authentication").build();
        options.addOption(passOption);
    }

    /**
     * This method creates a configuration from the command-line args
     *
     * @param args from command line
     * @return Config
     */
    public WalkerConfig getConfig(final String[] args) {
        CommandLine cmd = null;
        try {
            cmd = parseArgs(args);
        } catch (ParseException e) {
            printHelp("Error parsing args: " + e.getMessage());
        }

        final String baseUrl = cmd.getOptionValue("b");
        final String username = cmd.getOptionValue("u");
        final String password = cmd.getOptionValue("p");

        if (baseUrl == null) {
            printHelp("Arg 'baseUrl' must not be null");
        }
        return new WalkerConfig(baseUrl, username, password);
    }

    private CommandLine parseArgs(final String[] args) throws ParseException {
        return new DefaultParser().parse(options, args);
    }

    private void printHelp(final String message) {
        final HelpFormatter formatter = new HelpFormatter();
        final PrintWriter writer = new PrintWriter(System.out);

        writer.println("\n-----------------------\n" + message + "\n-----------------------\n");

        writer.println("Running repository Walker Utility from command line arguments");
        formatter.printHelp(writer, 80, "java -jar fcrepo-java-client-etc-driver.jar", "", options, 4, 4, "", true);

        writer.println("\n");
        writer.flush();

        throw new RuntimeException(message);
    }

}
