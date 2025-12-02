/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.shell.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.driver.Values;
import org.neo4j.shell.StubDbInfo;
import org.neo4j.shell.TransactionHandler;
import org.neo4j.shell.commands.CommandHelper;
import org.neo4j.shell.completions.CompletionEngine;
import org.neo4j.shell.completions.DbInfo;
import org.neo4j.shell.completions.SuggestionType;
import org.neo4j.shell.parameter.ParameterService;
import org.neo4j.shell.parameter.ParameterService.Parameter;
import org.neo4j.shell.parser.ShellStatementParser;
import org.neo4j.shell.state.BoltStateHandler;

class JlineCompleterTest {
    private record Completion(String completion, String display, String group, String desc) {}

    private ParameterService parameters;
    private JlineCompleter completer;
    private StatementJlineParser parser;
    private StubDbInfo dbInfo;
    private MockBoltStateHandler mockStateHandler;
    private CompletionEngine completionEngine;
    private final LineReader lineReader = mock(LineReader.class);
    private final CommandHelper.CommandFactoryHelper commandHelper = new CommandHelper.CommandFactoryHelper();
    private final List<Completion> allCommands = Stream.of(
                    ":begin",
                    ":commit",
                    ":connect",
                    ":disconnect",
                    ":exit",
                    ":help",
                    ":history",
                    ":param",
                    ":rollback",
                    ":source",
                    ":use",
                    ":impersonate",
                    ":sysinfo",
                    ":access-mode")
            .map(this::command)
            .toList();

    Completion command(String command) {
        var metadata = commandHelper.factoryByName(command).metadata();
        return new Completion(metadata.name(), metadata.name(), SuggestionType.COMMAND.name, metadata.description());
    }

    Completion keyword(String completion) {
        return new Completion(completion, completion, SuggestionType.KEYWORD.name, null);
    }

    Completion identifier(String completion) {
        return new Completion(completion, completion, SuggestionType.IDENTIFIER.name, null);
    }

    Completion parameter(String completion) {
        return new Completion(completion, completion, SuggestionType.PARAMETER.name, null);
    }

    Completion parameter(String completion, String display) {
        return new Completion(completion, display, SuggestionType.PARAMETER.name, null);
    }

    Completion procedureNamespace(String completion) {
        return new Completion(completion, completion, SuggestionType.PROCEDURE.name, "namespace");
    }

    Completion procedureCompletion(String completion, String display) {
        return new Completion(completion, display, SuggestionType.PROCEDURE.name, "procedure");
    }

    Completion procedureCompletion(String completion) {
        return new Completion(completion, completion, SuggestionType.PROCEDURE.name, "procedure");
    }

    Completion functionNamespace(String completion, String display) {
        return new Completion(completion, display, SuggestionType.FUNCTION.name, "namespace");
    }

    Completion functionNamespace(String completion) {
        return new Completion(completion, completion, SuggestionType.FUNCTION.name, "namespace");
    }

    Completion functionCompletion(String completion) {
        return new Completion(completion, completion, SuggestionType.FUNCTION.name, "function");
    }

    Completion labelOrRelType(String completion, String display) {
        return new Completion(completion, display, SuggestionType.LABEL_OR_RELATIONSHIP.name, null);
    }

    Completion labelOrRelType(String completion) {
        return new Completion(completion, completion, SuggestionType.LABEL_OR_RELATIONSHIP.name, null);
    }

    Completion property(String completion, String display) {
        return new Completion(completion, display, SuggestionType.PROPERTY.name, null);
    }

    Completion value(String completion) {
        return new Completion(completion, completion, SuggestionType.VALUE.name, null);
    }

    Completion value(String completion, String display) {
        return new Completion(completion, display, SuggestionType.VALUE.name, null);
    }

    void addDummyProcedure(Map<String, DbInfo.Neo4jProcedure> m, String name) {
        m.put(name, new DbInfo.Neo4jProcedure(List.of()));
    }

    public StubDbInfo dbInfoStub() {
        parameters.setParameters(List.of(new Parameter("intParam", Values.value(1L))));
        parameters.setParameters(List.of(new Parameter("otherIntParam", Values.value(2L))));
        parameters.setParameters(List.of(new Parameter("mapParam", Values.value(Map.of("a", 1)))));
        parameters.setParameters(List.of(new Parameter("stringParam", Values.value("some name"))));
        parameters.setParameters(List.of(new Parameter("split param", Values.value("a value"))));

        dbInfo = new StubDbInfo(parameters, true);
        String[] dummyProcedures = {"foo.bar", "dbms.info", "somethingElse", "foo.info", "db.info"};
        dbInfo.procedures.put(CypherVersion.Cypher5, new ConcurrentHashMap<>());
        dbInfo.procedures.put(CypherVersion.Cypher25, new ConcurrentHashMap<>());
        for (String dummyProcedure : dummyProcedures) {
            addDummyProcedure(dbInfo.procedures.get(CypherVersion.Cypher5), dummyProcedure);
            addDummyProcedure(dbInfo.procedures.get(CypherVersion.Cypher25), dummyProcedure);
        }
        dbInfo.procedures
                .get(CypherVersion.Cypher5)
                .put(
                        "dbms.components",
                        new DbInfo.Neo4jProcedure(List.of(
                                new DbInfo.ReturnDescription("name"),
                                new DbInfo.ReturnDescription("versions"),
                                new DbInfo.ReturnDescription("edition"))));
        dbInfo.procedures
                .get(CypherVersion.Cypher5)
                .put("versionedProcedure", new DbInfo.Neo4jProcedure(List.of(new DbInfo.ReturnDescription("name"))));
        dbInfo.procedures
                .get(CypherVersion.Cypher5)
                .put(
                        "versionedReturnsProcedure",
                        new DbInfo.Neo4jProcedure(List.of(new DbInfo.ReturnDescription("name"))));
        dbInfo.procedures
                .get(CypherVersion.Cypher25)
                .put(
                        "dbms.components",
                        new DbInfo.Neo4jProcedure(List.of(
                                new DbInfo.ReturnDescription("name"),
                                new DbInfo.ReturnDescription("versions"),
                                new DbInfo.ReturnDescription("edition"))));
        dbInfo.procedures
                .get(CypherVersion.Cypher25)
                .put(
                        "versionedReturnsProcedure",
                        new DbInfo.Neo4jProcedure(
                                List.of(new DbInfo.ReturnDescription("name"), new DbInfo.ReturnDescription("title"))));
        dbInfo.procedures
                .get(CypherVersion.Cypher25)
                .put("versionedProcedure25", new DbInfo.Neo4jProcedure(List.of(new DbInfo.ReturnDescription("name"))));
        dbInfo.functions.put(CypherVersion.Cypher5, List.of("a.b", "xx.yy.fna", "xx.yy.fnb"));
        dbInfo.functions.put(CypherVersion.Cypher25, List.of("a.b", "xx.yy.fna25", "xx.yy.fnb25"));
        dbInfo.labels =
                List.of("Actor", "_Airport", "Dog", "Gym", "Window", "123Wedding", "Odd1", "Odd_x", "Odd label");
        dbInfo.relationshipTypes =
                List.of("ACTED_IN", "DIRECTED", "9FOLLOWS", "PRODUCED_GLÖGG", "REVIEWED", "ODD1", "ODD RELTYPE");
        dbInfo.propertyKeys = List.of(
                "1born",
                "data",
                "körkort",
                "name",
                "nodes",
                "rating",
                "relationships",
                "rating1",
                "rating_x",
                "rating score");
        dbInfo.databaseNames = List.of("neo4j", "oskar", "system", "Restaurant", "Cafe", "my.neoDB", "neo db");
        dbInfo.aliasNames = List.of("alias2", "scoped.alias", "Bar", "Hotel", "Supermarket", "very cool alias");
        dbInfo.userNames = List.of("oskar", "neo4j", "admin");
        dbInfo.roleNames = List.of("foo", "bar");

        return dbInfo;
    }

    @BeforeEach
    void setup() {
        var transactionHandler = mock(TransactionHandler.class);
        parameters = ParameterService.create(transactionHandler);
        dbInfo = dbInfoStub();
        mockStateHandler = new MockBoltStateHandler("5.25.0");
        completionEngine = new CompletionEngine(dbInfo, mockStateHandler);
        completer = new JlineCompleter(new CommandHelper.CommandFactoryHelper(), completionEngine);
        parser = new StatementJlineParser(new ShellStatementParser());
        parser.setEnableStatementParsing(true);
    }

    @Test
    void completeBlankSanity() {
        assertThat(complete("")).is(emptyStatementMatcher());
    }

    @Test
    void completeCommandSanity() {
        assertThat(complete("")).containsAll(allCommands);
        assertThat(complete(":")).contains(command(":begin"), command(":commit"), command(":disconnect"));
        assertThat(complete(":he")).contains(command(":help"));
    }

    @Test
    void completeIdentifiersSanity() {
        var whereQuery = "match (myFirstNode:SomeLabel)-[rel]->(mySecondNode) where my";
        var returnQuery = "MATCH (myFirstNode)-[rel]-(mySecondNode) RETURN my";
        var cypher = Stream.of("ALL", "ANY", "COLLECT", "COUNT", "EXISTS")
                .map(this::keyword)
                .toList();
        var identifiers = Stream.of("myFirstNode", "rel", "mySecondNode")
                .map(this::identifier)
                .toList();
        var parameters = Stream.concat(
                        Stream.of("$intParam", "$mapParam", "$stringParam", "$otherIntParam")
                                .map(this::parameter),
                        Stream.of(parameter("$`split param`", "$split param")))
                .toList();

        assertThat(complete(whereQuery))
                .containsAll(cypher)
                .containsAll(identifiers)
                .containsAll(identifiers)
                .containsAll(parameters)
                .doesNotContain(identifier("my"));
        assertThat(complete(returnQuery))
                .containsAll(cypher)
                .containsAll(identifiers)
                .containsAll(identifiers)
                .containsAll(parameters)
                .doesNotContain(identifier("my"));
    }

    @Test
    void completeKeywordsSanity() {
        assertThat(complete("match (n) wh")).contains(keyword("WHERE"));
        assertThat(complete("ma")).contains(keyword("MATCH"));
        assertThat(complete("alter ")).contains(keyword("USER"), keyword("DATABASE"));
        assertThat(complete("RETURN ")).contains(keyword("allShortestPaths"), keyword("shortestPath"));
    }

    @Test
    void completeCypherParametersSanity() {
        assertThat(complete("match (n) where n.p = "))
                .contains(
                        parameter("$intParam"),
                        parameter("$otherIntParam"),
                        parameter("$`split param`", "$split param"));
        assertThat(complete("match (n) where n.p = $intP"))
                .contains(parameter("$intParam"), parameter("$`split param`", "$split param"));

        assertThat(complete("match (n) where n.p = $"))
                .contains(
                        parameter("$intParam"),
                        parameter("$otherIntParam"),
                        parameter("$`split param`", "$split param"));

        assertThat(complete("ALTER SERVER \"abc\" SET OPTIONS "))
                .contains(parameter("$mapParam"))
                .doesNotContain(
                        parameter("$intParam"),
                        parameter("$otherIntParam"),
                        parameter("$stringParam"),
                        parameter("$`split param`", "$split param"));
    }

    @Test
    void completeSecondCypherStatementSanity() {
        assertThat(complete("return 1;")).is(emptyStatementMatcher());
        assertThat(complete("return 1;ret")).contains(keyword("RETURN"));
    }

    @Test
    void autocompletesCypherVersions() {
        mockStateHandler.setServerVersion("5.12.0");
        assertThat(complete("CYPH"))
                .doesNotContain(keyword("CYPHER 5"), keyword("CYPHER 25"))
                .contains(keyword("CYPHER"));
        mockStateHandler.setServerVersion("5.21.0");
        assertThat(complete("CYPH"))
                .doesNotContain(keyword("CYPHER 25"))
                .contains(keyword("CYPHER 5"), keyword("CYPHER"));
        mockStateHandler.setServerVersion("5.27.0-2025040");
        // If a new cypher version is added, this test should fail, in which case you need to add another case to
        // getPreParserRuleCompletions() in CompletionEngine.
        // And a new case should be added to the test like setServerVersion(<version supporting new CYPHER X>)
        // -> completion contains CYPHER X, old completions dont
        assertThat(complete("CYPH"))
                .filteredOn(completion -> completion.completion.contains("CYPHER"))
                .containsExactlyInAnyOrder(keyword("CYPHER 25"), keyword("CYPHER 5"), keyword("CYPHER"));
    }

    @Test
    void completesStatementsWithPreparserPart() {
        assertThat(complete("CYPHER 5 ")).contains(keyword("EXPLAIN"), keyword("MATCH"));
        assertThat(complete("CYPHER ")).contains(keyword("EXPLAIN"), keyword("MATCH"));
        assertThat(complete("CYPHER runtime=slotted ")).contains(keyword("EXPLAIN"), keyword("MATCH"));
        assertThat(complete("CYPHER runtime=")).doesNotContain(keyword("EXPLAIN"), keyword("MATCH"));
        assertThat(complete("CYPHER 5 CYPHER EXPLAIN PROFILE CYPHER runtime= "))
                .doesNotContain(keyword("EXPLAIN"), keyword("MATCH"));
        // Note the -2 is because antlr4-c3 code seems to be entering into an invalid state in some cases but no token
        // should
        // have a type lower than -1 (EOF), all of the token types are supposed to be greater or equal than 0
        assertThat(complete("PROF"))
                .contains(keyword("PROFILE"), keyword("MATCH"))
                .doesNotContain(keyword("-2"));
        assertThat(complete("EXPLAIN PROF"))
                .contains(keyword("PROFILE"), keyword("MATCH"))
                .doesNotContain(keyword("-2"));
        assertThat(complete("CYPHER 5 runtime = slotted planner = cost operatorEngine = compiled EXPLAIN PROF"))
                .contains(keyword("PROFILE"), keyword("MATCH"))
                .doesNotContain(keyword("-2"));
        assertThat(complete("PROFILE MATCH (n) RETURN n C"))
                .contains(keyword("CALL"))
                .doesNotContain(keyword("CYPHER"));
        assertThat(complete("CYPHER planner = cost operatorEngine = compiled PROFILE MATCH (n) RETURN n C"))
                .contains(keyword("CALL"))
                .doesNotContain(keyword("CYPHER"));
    }

    @Test
    void completesMultiStatementsWithPreparserPart() {
        assertThat(complete("EXPLAIN PROFILE MATCH (n) RETURN n; EXPL")).contains(keyword("EXPLAIN"));
        assertThat(complete("EXPLAIN PROFILE MATCH (n) RETURN n; CYPHER 5 MATCH (n) RET"))
                .contains(keyword("RETURN"));
        assertThat(complete("EXPLAIN PROFILE MATCH (n) RETURN n; CYPHER 5 runtime = slotted engineType = compiled MA"))
                .contains(keyword("MATCH"), keyword("EXPLAIN"));
    }

    @Test
    void completesProcedureReturnNames() {
        assertThat(complete("CALL dbms.components() YIELD "))
                .containsExactlyInAnyOrder(identifier("name"), identifier("versions"), identifier("edition"));

        assertThat(complete("CALL dbms.components() YIELD e"))
                .contains(identifier("name"), identifier("versions"), identifier("edition"));

        assertThat(complete("CALL dbms.components() YIELD name, "))
                .contains(identifier("versions"), identifier("edition"))
                .doesNotContain(identifier("name"));
        assertThat(complete("CALL `dbms.components`() YIELD name, "))
                .doesNotContain(identifier("name"), identifier("versions"), identifier("edition"));
        assertThat(complete("CALL dbms   .    components      () YIELD  "))
                .contains(identifier("versions"), identifier("edition"), identifier("name"));
        assertThat(complete("CALL `dbms`   .    `components`  () YIELD  "))
                .contains(identifier("versions"), identifier("edition"), identifier("name"));

        assertThat(complete("CYPHER 5 CALL versionedReturnsProcedure() YIELD "))
                .containsExactlyInAnyOrder(identifier("name"));
        assertThat(complete("CYPHER 25 CALL versionedReturnsProcedure() YIELD "))
                .containsExactlyInAnyOrder(identifier("name"), identifier("title"));
    }

    @Test
    void completesProcedureNames() {
        assertThat(complete("CALL "))
                .containsExactlyInAnyOrder(
                        procedureNamespace("foo"),
                        procedureNamespace("dbms"),
                        procedureNamespace("db"),
                        procedureCompletion("foo.bar"),
                        procedureCompletion("dbms.info"),
                        procedureCompletion("foo.info"),
                        procedureCompletion("somethingElse"),
                        procedureCompletion("db.info"),
                        procedureCompletion("dbms.components"),
                        procedureCompletion("versionedProcedure"),
                        procedureCompletion("versionedReturnsProcedure"));

        assertThat(complete("CALL db"))
                .contains(
                        procedureNamespace("dbms"),
                        procedureNamespace("db"),
                        procedureCompletion("dbms.info"),
                        procedureCompletion("db.info"),
                        procedureCompletion("dbms.components"));

        assertThat(complete("CALL db.")).contains(procedureCompletion("db.info", "info"));

        assertThat(complete("CYPHER 5 CALL "))
                .contains(procedureCompletion("versionedProcedure"))
                .doesNotContain(procedureCompletion("versionedProcedure25"));
        assertThat(complete("CYPHER 25 CALL "))
                .contains(procedureCompletion("versionedProcedure25"))
                .doesNotContain(procedureCompletion("versionedProcedure"));
    }

    @Test
    void usesParsedCypherVersion() {
        assertThat(complete("CYPHER 25 CALL "))
                .contains(procedureCompletion("versionedProcedure25"))
                .doesNotContain(procedureCompletion("versionedProcedure"));
    }

    @Test
    void completesFunctionNames() {
        assertThat(complete("RETURN "))
                .contains(
                        functionNamespace("a"),
                        functionNamespace("xx"),
                        functionCompletion("a.b"),
                        functionCompletion("xx.yy.fna"),
                        functionCompletion("xx.yy.fnb"));

        assertThat(complete("RETURN xx")).contains(functionCompletion("xx.yy.fna"), functionCompletion("xx.yy.fnb"));

        assertThat(complete("RETURN xx.")).contains(functionNamespace("xx.yy", "yy"));
    }

    @Test
    void completesLabels() {
        assertThat(complete("MATCH (n: "))
                .contains(
                        labelOrRelType("Actor"),
                        labelOrRelType("_Airport"),
                        labelOrRelType("Odd1"),
                        labelOrRelType("Odd_x"),
                        labelOrRelType("`123Wedding`", "123Wedding"),
                        labelOrRelType("`Odd label`", "Odd label"));
        assertThat(complete("MATCH (n:"))
                .contains(
                        labelOrRelType("(n:Actor", "Actor"),
                        labelOrRelType("(n:_Airport", "_Airport"),
                        labelOrRelType("(n:`123Wedding`", "123Wedding"),
                        labelOrRelType("(n:Odd1", "Odd1"),
                        labelOrRelType("(n:Odd_x", "Odd_x"),
                        labelOrRelType("(n:`Odd label`", "Odd label"));

        assertThat(complete("MATCH (n:Ac")).contains(labelOrRelType("(n:Actor", "Actor"));
        assertThat(complete("MATCH (n:Od"))
                .contains(
                        labelOrRelType("(n:Odd1", "Odd1"),
                        labelOrRelType("(n:Odd_x", "Odd_x"),
                        labelOrRelType("(n:`Odd label`", "Odd label"));
    }

    @Test
    void completesRelationshipTypes() {
        assertThat(complete("MATCH (n)-[r:"))
                .contains(
                        labelOrRelType("(n)-[r:ACTED_IN", "ACTED_IN"),
                        labelOrRelType("(n)-[r:DIRECTED", "DIRECTED"),
                        labelOrRelType("(n)-[r:PRODUCED_GLÖGG", "PRODUCED_GLÖGG"),
                        labelOrRelType("(n)-[r:`9FOLLOWS`", "9FOLLOWS"),
                        labelOrRelType("(n)-[r:ODD1", "ODD1"),
                        labelOrRelType("(n)-[r:`ODD RELTYPE`", "ODD RELTYPE"));

        assertThat(complete("MATCH (n)-[r: "))
                .contains(labelOrRelType("ACTED_IN"), labelOrRelType("DIRECTED"), labelOrRelType("ODD1"));

        assertThat(complete("MATCH (n)-[r:A")).contains(labelOrRelType("(n)-[r:ACTED_IN", "ACTED_IN"));
        assertThat(complete("MATCH (n)-[r:OD"))
                .contains(labelOrRelType("(n)-[r:ODD1", "ODD1"), labelOrRelType("(n)-[r:`ODD RELTYPE`", "ODD RELTYPE"));
    }

    @Test
    void completePropertyKeys() {
        assertThat(complete("RETURN n."))
                .contains(
                        property("n.data", "data"),
                        property("n.`1born`", "1born"),
                        property("n.körkort", "körkort"),
                        property("n.rating1", "rating1"),
                        property("n.rating_x", "rating_x"),
                        property("n.`rating score`", "rating score"));

        assertThat(complete("RETURN n.d")).contains(property("n.data", "data"));
        assertThat(complete("RETURN n.rat"))
                .contains(
                        property("n.`rating score`", "rating score"),
                        property("n.rating1", "rating1"),
                        property("n.rating_x", "rating_x"));
    }

    @Test
    void completeDatabasesAndAliases() {
        assertThat(complete("ALTER DATABASE "))
                .contains(
                        value("neo4j"),
                        value("oskar"),
                        value("system"),
                        value("my.neoDB"),
                        value("`neo db`", "neo db"),
                        value("alias2"),
                        value("scoped.alias"),
                        value("`very cool alias`", "very cool alias"),
                        parameter("$stringParam"),
                        parameter("$`split param`", "$split param"))
                .doesNotContain(parameter("$mapParam"));

        assertThat(complete("ALTER DATABASE sco")).contains(value("scoped.alias"));

        assertThat(complete("SHOW ALIAS "))
                .contains(
                        value("scoped.alias"),
                        parameter("$stringParam"),
                        value("`very cool alias`", "very cool alias"),
                        parameter("$`split param`", "$split param"))
                .doesNotContain(value("neo4j"), parameter("$mapParam"));
    }

    @Test
    void completeRoleNames() {
        assertThat(complete("GRANT SHOW CONSTRAINT ON DATABASE * TO "))
                .contains(value("foo"), parameter("$stringParam"), value("bar"))
                .doesNotContain(parameter("$mapParam"));
        assertThat(complete("GRANT SHOW CONSTRAINT ON DATABASE * TO f")).contains(value("foo"));
    }

    @Test
    void completeUserNames() {
        assertThat(complete("CREATE USER "))
                .contains(parameter("$stringParam"))
                .doesNotContain(value("oskar"))
                .doesNotContain(value("neo4j"), value("admin"));
        assertThat(complete("SHOW USER "))
                .contains(parameter("$stringParam"), value("oskar"), value("neo4j"), value("admin"))
                .doesNotContain(parameter("$mapParam"));
    }

    @Test
    void completeWithUnicodeSequences() {
        assertThat(complete("M\\u0041TCH (n) ")).contains(keyword("WHERE"), keyword("RETURN"));
    }

    @Test
    void canDisableCompletions() {
        dbInfo.completionsEnabled = false;
        completer = new JlineCompleter(new CommandHelper.CommandFactoryHelper(), completionEngine);
        assertThat(complete("M")).doesNotContain(keyword("MATCH"));
        dbInfo.completionsEnabled = true;
        completer = new JlineCompleter(new CommandHelper.CommandFactoryHelper(), completionEngine);
        assertThat(complete("M")).contains(keyword("MATCH"));
    }

    private List<Completion> complete(String line) {
        var parsed = parser.parse(line, line.length(), Parser.ParseContext.COMPLETE);
        var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsed, candidates);
        var result = candidates.stream()
                .map(c -> new Completion(c.value(), c.displ(), c.group(), c.descr()))
                .toList();
        return result;
    }

    private Condition<List<? extends Completion>> emptyStatementMatcher() {
        var firstKeywords = Stream.of(
                        "CREATE",
                        "MATCH",
                        "DROP",
                        "UNWIND",
                        "RETURN",
                        "WITH",
                        "LOAD CSV",
                        "ALTER",
                        "RENAME",
                        "SHOW",
                        "START DATABASE",
                        "STOP DATABASE")
                .map(this::keyword)
                .toList();
        ;
        var commands = allCommands;
        return new Condition<>(
                items -> items.containsAll(firstKeywords) && items.containsAll(commands), "Empty statement matcher");
    }

    private static class MockBoltStateHandler extends BoltStateHandler {
        private String serverVersion;

        public MockBoltStateHandler(String serverVersion) {
            super(false, null, Optional.empty());
            this.serverVersion = serverVersion;
        }

        @Override
        public String getServerVersion() {
            return serverVersion;
        }

        public void setServerVersion(String serverVersion) {
            this.serverVersion = serverVersion;
        }
    }
}
