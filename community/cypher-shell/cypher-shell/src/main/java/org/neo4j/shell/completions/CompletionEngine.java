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
package org.neo4j.shell.completions;

import static java.util.stream.Collectors.toCollection;
import static org.neo4j.shell.util.Versions.version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.ast.factory.neo4j.completion.CodeCompletionCore;
import org.neo4j.cypher.internal.parser.AstRuleCtx;
import org.neo4j.cypher.internal.parser.v25.Cypher25Lexer;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;
import org.neo4j.cypher.internal.parser.v25.ast.factory.Cypher25AstLexer;
import org.neo4j.cypher.internal.preparser.CypherPreparserLexer;
import org.neo4j.cypher.internal.preparser.CypherPreparserParser;
import org.neo4j.cypher.internal.preparser.PreparserCypherLexer;
import org.neo4j.shell.log.Logger;
import org.neo4j.shell.state.BoltStateHandler;
import org.neo4j.shell.util.Versions;

public class CompletionEngine {

    public CypherVersion resolveCypherVersion(CypherVersion parsedVersion) {
        return parsedVersion != null
                ? parsedVersion
                : dbInfo.defaultLanguage != null ? dbInfo.defaultLanguage : CypherVersion.Cypher5;
    }

    public enum ParameterType {
        STRING,
        MAP,
        ANY
    }

    DbInfo dbInfo;
    BoltStateHandler boltStateHandler;

    class VariableCollector implements ParseTreeListener {
        private final List<String> variables = new ArrayList<>();
        TokenStream tokens;

        public VariableCollector(TokenStream tokens) {
            this.tokens = tokens;
        }

        @Override
        public void visitTerminal(TerminalNode node) {}

        @Override
        public void visitErrorNode(ErrorNode node) {}

        @Override
        public void enterEveryRule(ParserRuleContext ctx) {}

        @Override
        public void exitEveryRule(ParserRuleContext ctx) {
            if (ctx.getRuleIndex() == Cypher25Parser.RULE_variable) {
                var c = (Cypher25Parser.VariableContext) ctx;
                // To avoid suggesting the variable that is currently being typed
                // For example RETURN a| <- we don't want to suggest "a" as a variable
                // We check if the variable is in the end of the statement
                var tokenIndex = c.stop.getTokenIndex();
                var nextTokenIsEOF =
                        tokenIndex != -1 && tokens.get(tokenIndex + 1).getType() == Cypher25Lexer.EOF;

                var definesVariable = c.getParent() != null
                        && ParserInfo.rulesDefiningOrUsingVariables.contains(
                                c.getParent().getRuleIndex());
                if (c.symbolicVariableNameString() != null
                        && c.symbolicVariableNameString().getText() != null
                        && !nextTokenIsEOF
                        && definesVariable) {
                    var variable = c.symbolicVariableNameString().getText();
                    this.variables.add(variable);
                }
            } else if (ctx.getRuleIndex() == Cypher25Parser.RULE_procedureResultItem) {
                var c = (Cypher25Parser.ProcedureResultItemContext) ctx;
                if (c.yieldItemName != null && c.yieldItemName.getText() != null) {
                    var variable = c.yieldItemName.getText();
                    this.variables.add(variable);
                }
            }
        }
    }

    class VersionCollector implements ParseTreeListener {
        private CypherVersion version = null;

        @Override
        public void visitTerminal(TerminalNode node) {}

        @Override
        public void visitErrorNode(ErrorNode node) {}

        @Override
        public void enterEveryRule(ParserRuleContext ctx) {}

        @Override
        public void exitEveryRule(ParserRuleContext ctx) {
            if (ctx.getRuleIndex() == CypherPreparserParser.RULE_option) {
                var c = (CypherPreparserParser.OptionContext) ctx;
                if (c.VERSION() != null) {
                    Arrays.stream(CypherVersion.values()).forEach(version -> {
                        if (Objects.equals(version.versionName, c.VERSION().getText())) {
                            this.version = version;
                        }
                    });
                }
            }
        }
    }

    public CompletionEngine(DbInfo dbInfo, BoltStateHandler boltStateHandler) {
        this.dbInfo = dbInfo;
        this.boltStateHandler = boltStateHandler;
    }

    private record CompletionResolution(
            ParserInfo parserInfo,
            PreParserInfo preparserInfo,
            boolean completeWithPreparser,
            boolean completeWithParser) {}

    private record ParserInfo(
            Cypher25Parser parser,
            Cypher25Parser.StatementsContext parserCtx,
            VariableCollector variableCollector,
            List<Token> tokens) {
        static Set<Integer> keywords;
        static Set<Integer> preferredRules = Set.of(
                Cypher25Parser.RULE_functionName,
                Cypher25Parser.RULE_procedureName,
                Cypher25Parser.RULE_labelExpression1,
                Cypher25Parser.RULE_symbolicAliasName,
                Cypher25Parser.RULE_parameter,
                Cypher25Parser.RULE_propertyKeyName,
                Cypher25Parser.RULE_variable,
                Cypher25Parser.RULE_leftArrow,
                // this rule is used for usernames and roles.
                Cypher25Parser.RULE_commandNameExpression,
                Cypher25Parser.RULE_symbolicNameString,
                Cypher25Parser.RULE_procedureResultItem);
        static Set<Integer> rulesDefiningVariables = Set.of(
                Cypher25Parser.RULE_returnItem,
                Cypher25Parser.RULE_unwindClause,
                Cypher25Parser.RULE_subqueryInTransactionsReportParameters,
                Cypher25Parser.RULE_procedureResultItem,
                Cypher25Parser.RULE_foreachClause,
                Cypher25Parser.RULE_loadCSVClause,
                Cypher25Parser.RULE_reduceExpression,
                Cypher25Parser.RULE_listItemsPredicate,
                Cypher25Parser.RULE_listComprehension);
        static Set<Integer> rulesDefiningOrUsingVariables;
        static Map<Integer, String> customTokenDisplayNames = Map.of(
                Cypher25Parser.ALL_SHORTEST_PATHS, "allShortestPaths", Cypher25Parser.SHORTEST_PATH, "shortestPath");
        static Vocabulary vocabulary = Cypher25Lexer.VOCABULARY;

        static {
            rulesDefiningOrUsingVariables = new HashSet(rulesDefiningVariables);
            rulesDefiningOrUsingVariables.addAll(List.of(
                    Cypher25Parser.RULE_pattern,
                    Cypher25Parser.RULE_nodePattern,
                    Cypher25Parser.RULE_relationshipPattern,
                    Cypher25Parser.RULE_variable));
            var ignoreFromLexer = Set.of(
                    Cypher25Lexer.DECIMAL_DOUBLE,
                    Cypher25Lexer.UNSIGNED_DECIMAL_INTEGER,
                    Cypher25Lexer.UNSIGNED_HEX_INTEGER,
                    Cypher25Lexer.UNSIGNED_OCTAL_INTEGER,
                    Cypher25Lexer.STRING_LITERAL1,
                    Cypher25Lexer.STRING_LITERAL2,
                    Cypher25Lexer.ErrorChar,
                    Cypher25Lexer.EOF,
                    Cypher25Lexer.SPACE,
                    Cypher25Lexer.IDENTIFIER,
                    Cypher25Lexer.ESCAPED_SYMBOLIC_NAME,
                    Cypher25Lexer.MULTI_LINE_COMMENT,
                    Cypher25Lexer.SINGLE_LINE_COMMENT);
            keywords = new HashSet<>();
            for (int i = 0; i < Cypher25Lexer.VOCABULARY.getMaxTokenType(); ++i) {
                if (vocabulary.getLiteralName(i) == null && !ignoreFromLexer.contains(i)) {
                    keywords.add(i);
                }
            }
        }
    }

    private record PreParserInfo(
            CypherPreparserParser preparser,
            CypherPreparserParser.StrictlyPreparserOptionsContext preparserCtx,
            List<Token> preparserTokens,
            CypherPreparserParser.StatementContext preparserStmt,
            CypherVersion parsedVersion) {
        static Vocabulary vocabulary = PreparserCypherLexer.VOCABULARY;
        static Set<Integer> keywords = new HashSet<>();

        static {
            var ignoreFromPreparserLexer = Set.of(
                    CypherPreparserLexer.VERSION,
                    CypherPreparserLexer.ErrorChar,
                    CypherPreparserLexer.EOF,
                    CypherPreparserLexer.SPACE,
                    CypherPreparserLexer.IDENTIFIER,
                    CypherPreparserLexer.MULTI_LINE_COMMENT,
                    CypherPreparserLexer.SINGLE_LINE_COMMENT);
            for (int i = 0; i < CypherPreparserLexer.VOCABULARY.getMaxTokenType(); ++i) {
                if (vocabulary.getLiteralName(i) == null && !ignoreFromPreparserLexer.contains(i)) {
                    keywords.add(i);
                }
            }
        }

        static Set<Integer> preferredRules = Set.of(CypherPreparserParser.RULE_cypher);
    }

    /*
    Using this new rule:
    preparserOption
      : option* EOF;
    We try to parse with preparserOption
    - If there are no errors, complete with the completePreparser(query) and completeParser("")
    - If there are errors:
      p = parse(cypher statement part of query) <- get cypher statement part by parsing with old rule preparserOptions
      cypher runtime =  -> we want to complete with preparser only
        preparser.statement() is empty
      PROF -> we want to complete with preparser and parser
      PROFILE EXPL
        preparser.statement() is not empty
        p.statement().regularQuery() is empty
      PROFILE MATCH (n) RETURN n C -> we want to complete with parser only
        preparser.statement() is not empty
        p.statement().regularQuery() is non empty
     */

    private PreParserInfo getPreParserInfo(String incompleteQuery) throws IOException {
        PreparserCypherLexer preLexer = PreparserCypherLexer.fromString(incompleteQuery, true);
        CommonTokenStream preparserTokenStream = new CommonTokenStream(preLexer);
        var preparser = new CypherPreparserParser(preparserTokenStream);
        preLexer.removeErrorListeners();
        preparser.removeErrorListeners();
        var versionCollector = new VersionCollector();
        preparser.addParseListener(versionCollector);
        var preparserCtx = preparser.strictlyPreparserOptions();
        preparserTokenStream.seek(0);
        var preparserStmt = preparser.preparserOptions().statement();
        var preparserTokens = preparserTokenStream.getTokens();

        return new PreParserInfo(preparser, preparserCtx, preparserTokens, preparserStmt, versionCollector.version);
    }

    private ParserInfo getParserInfo(String incompleteQuery, CypherPreparserParser.StatementContext preparserStmt)
            throws IOException {
        Optional<Integer> stmtPos =
                Optional.ofNullable(preparserStmt).map(x -> x.start).map(Token::getStartIndex);
        var cypherStmt = stmtPos.map(incompleteQuery::substring).orElse("");
        var lexer = Cypher25AstLexer.fromString(cypherStmt, true);
        var tokenStream = new CommonTokenStream(lexer);
        var parser = new Cypher25Parser(tokenStream);
        var variableCollector = new VariableCollector(tokenStream);
        parser.addParseListener(variableCollector);

        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        var parserCtx = parser.statements();
        var tokens = tokenStream.getTokens();

        return new ParserInfo(parser, parserCtx, variableCollector, tokens);
    }

    private CompletionResolution resolveCompletionWork(String incompleteQuery) throws IOException {
        var preparserInfo = getPreParserInfo(incompleteQuery);
        var parserInfo = getParserInfo(incompleteQuery, preparserInfo.preparserStmt);

        if (preparserInfo.preparser.getNumberOfSyntaxErrors() == 0) {
            return new CompletionResolution(parserInfo, preparserInfo, true, true);
        } else {
            if (preparserInfo.preparserStmt == null) {
                return new CompletionResolution(parserInfo, preparserInfo, true, false);
            } else if (parserInfo.parserCtx.statement().stream()
                    .anyMatch(statement -> statement.queryWithLocalDefinitions() != null)) {
                return new CompletionResolution(parserInfo, preparserInfo, false, true);
            } else {
                return new CompletionResolution(parserInfo, preparserInfo, true, true);
            }
        }
    }

    public List<Suggestion> completeQuery(String incompleteQuery) throws IOException {
        var completionResolution = resolveCompletionWork(incompleteQuery);
        ArrayList<Suggestion> suggestions = new ArrayList<>();

        if (completionResolution.completeWithParser) {
            var parserCompletions = completeStatement(
                    completionResolution.parserInfo.parser,
                    completionResolution.parserInfo.parserCtx,
                    completionResolution.parserInfo.tokens,
                    completionResolution.parserInfo.variableCollector.variables,
                    completionResolution.preparserInfo.parsedVersion);
            suggestions.addAll(parserCompletions);
        }

        if (completionResolution.completeWithPreparser) {
            var preparserCompletions = completePreparser(
                    completionResolution.preparserInfo.preparser,
                    completionResolution.preparserInfo.preparserCtx,
                    completionResolution.preparserInfo.preparserTokens,
                    completionResolution.preparserInfo.parsedVersion);
            suggestions.addAll(preparserCompletions);
        }

        return suggestions.stream().toList();
    }

    private List<Suggestion> complete(
            Parser parser,
            Set<Integer> ignoredTokens,
            int caretIndex,
            List<String> collectedVariables,
            CypherVersion parsedVersion,
            List<Token> tokens,
            ParserRuleContext stopNode) {

        boolean isPreParserCompletion = parser instanceof CypherPreparserParser;
        Set<Integer> parserPreferredRules =
                isPreParserCompletion ? PreParserInfo.preferredRules : ParserInfo.preferredRules;

        var completionEngine = new CodeCompletionCore(parser, parserPreferredRules, ignoredTokens);
        var candidates = completionEngine.collectCandidates(caretIndex, null);
        var tokenCompletions = getTokenCompletions(candidates, ignoredTokens, isPreParserCompletion);
        List<Suggestion> ruleCompletions = isPreParserCompletion
                ? getPreParserRuleCompletions(candidates)
                : getParserRuleCompletions(candidates, collectedVariables, parsedVersion, tokens, stopNode);
        var result = new ArrayList<Suggestion>();

        result.addAll(tokenCompletions);
        result.addAll(ruleCompletions);
        return result;
    }

    private Set<Integer> getIgnoredTokens(boolean forPreParser) {
        int startToken;
        int endToken;
        Set<Integer> keywords;

        if (forPreParser) {
            startToken = CypherPreparserParser.EOF;
            endToken = PreParserInfo.vocabulary.getMaxTokenType();
            keywords = PreParserInfo.keywords;
        } else {
            startToken = Cypher25Parser.EOF;
            endToken = ParserInfo.vocabulary.getMaxTokenType();
            keywords = ParserInfo.keywords;
        }

        return IntStream.rangeClosed(startToken, endToken)
                .filter(i -> !keywords.contains(i))
                .boxed()
                .collect(Collectors.toSet());
    }

    private int getCaretIndex(List<Token> tokens, boolean forPreParser) {
        var caretIndex = tokens.size() - 1;
        var previousToken = tokens.size() > 1 ? tokens.get(caretIndex - 1) : null;

        if (previousToken != null) {
            boolean previousIsIdentifier;
            boolean previousIsLexerKeyword;
            if (forPreParser) {
                previousIsIdentifier = previousToken.getType() == CypherPreparserLexer.IDENTIFIER;
                previousIsLexerKeyword = PreParserInfo.keywords.contains(previousToken.getType());
            } else {
                previousIsIdentifier = previousToken.getType() == Cypher25Lexer.IDENTIFIER;
                previousIsLexerKeyword = ParserInfo.keywords.contains(previousToken.getType());
            }
            if (previousIsIdentifier || previousIsLexerKeyword) {
                caretIndex--;
            }
        }

        return caretIndex;
    }

    private List<Suggestion> completePreparser(
            CypherPreparserParser preparser,
            CypherPreparserParser.StrictlyPreparserOptionsContext rootCtx,
            List<Token> tokens,
            CypherVersion parsedVersion) {
        var stopNode = findStopNode(rootCtx, rootCtx.EOF());
        // The query is always going to have the EOF
        var caretIndex = getCaretIndex(tokens, true);

        Set<Integer> ignoredTokens = getIgnoredTokens(true);

        return complete(preparser, ignoredTokens, caretIndex, List.of(), parsedVersion, tokens, stopNode);
    }

    private List<Suggestion> completeStatement(
            Cypher25Parser parser,
            Cypher25Parser.StatementsContext rootCtx,
            List<Token> tokens,
            List<String> collectedVariables,
            CypherVersion parsedVersion) {
        var stopNode = findStopNode(rootCtx, rootCtx.EOF());
        // The query is always going to have the EOF
        var caretIndex = getCaretIndex(tokens, false);

        Set<Integer> ignoredTokens = getIgnoredTokens(false);

        return complete(parser, ignoredTokens, caretIndex, collectedVariables, parsedVersion, tokens, stopNode);
    }

    private static String backtickIfNeeded(String e) {
        if (e == null || e.isEmpty()) {
            return e;
        }
        Pattern invalidStartPattern = Pattern.compile("^[^\\p{L}_]", Pattern.UNICODE_CHARACTER_CLASS);
        Pattern invalidAnywherePattern = Pattern.compile("[^\\p{L}\\p{N}_]", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher m1 = invalidStartPattern.matcher(String.valueOf(e.charAt(0)));
        Matcher m2 = invalidAnywherePattern.matcher(e);
        if (m1.find() || m2.find()) {
            return "`" + e + "`";
        } else {
            return e;
        }
    }

    private static String backtickDbNameIfNeeded(String e) {
        if (e == null || e.isEmpty()) {
            return e;
        }
        Pattern invalidStartPattern = Pattern.compile("^[^\\p{L}_]", Pattern.UNICODE_CHARACTER_CLASS);
        Pattern invalidAnywherePattern = Pattern.compile("[^\\p{L}\\p{N}_.]", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher m1 = invalidStartPattern.matcher(String.valueOf(e.charAt(0)));
        Matcher m2 = invalidAnywherePattern.matcher(e);
        if (m1.find() || m2.find()) {
            return "`" + e + "`";
        } else {
            return e;
        }
    }

    private Stream<Suggestion> labelCompletions() {
        return this.dbInfo.labels.stream().map(label -> Suggestion.labelOrRelType(backtickIfNeeded(label), label));
    }

    private Stream<Suggestion> relTypeCompletions() {
        return this.dbInfo.relationshipTypes.stream()
                .map(relType -> Suggestion.labelOrRelType(backtickIfNeeded(relType), relType));
    }

    private Stream<Suggestion> propertyKeyCompletions() {
        return this.dbInfo.propertyKeys.stream()
                .map(property -> Suggestion.property(backtickIfNeeded(property), property));
    }

    private ParserRuleContext findStopNode(ParserRuleContext root, TerminalNode endToken) {
        var children = root.children;
        ParserRuleContext current = root;

        while (children != null && !children.isEmpty()) {
            var index = children.size() - 1;
            var child = children.get(index);

            while (index > 0
                    && (child == endToken
                            || child.getText().isEmpty()
                            || child.getText().startsWith("<missing"))) {
                index--;
                child = children.get(index);
            }
            if (child instanceof ParserRuleContext) {
                current = (ParserRuleContext) child;
                children = current.children;
            } else {
                children = null;
            }
        }

        return current;
    }

    private Optional<ParserRuleContext> getParent(ParserRuleContext ctx, ParserRuleContextFunction condition) {
        var parentCtx = ctx;
        while (!condition.test(parentCtx) && parentCtx != null) {
            parentCtx = parentCtx.getParent();
        }
        return Optional.ofNullable(parentCtx);
    }

    interface ParserRuleContextFunction {
        boolean test(ParserRuleContext ctx);
    }

    private String getMethodName(Cypher25Parser.ProcedureNameContext nameCtx) {
        var namespaces = nameCtx.namespace().symbolicNameString();
        var methodName = nameCtx.symbolicNameString();
        var nameChunks = new ArrayList<>(namespaces);
        nameChunks.add(methodName);
        var normalizedName = nameChunks.stream().map(this::getNamespaceString).collect(Collectors.joining("."));

        return normalizedName;
    }

    private String getNamespaceString(Cypher25Parser.SymbolicNameStringContext nameCtx) {
        var text = nameCtx.getText();
        var isEscaped = nameCtx.escapedSymbolicNameString() != null;
        var hasDot = text.contains(".");

        if (isEscaped && !hasDot) {
            return text.substring(1, text.length() - 1);
        }

        return text;
    }

    private List<Suggestion> getPreParserRuleCompletions(CodeCompletionCore.CandidatesCollection candidates) {
        return candidates.rules.entrySet().stream()
                .flatMap(entry -> {
                    var ruleNumber = entry.getKey();
                    if (ruleNumber == CypherPreparserParser.RULE_cypher) {
                        boolean supportsCypher25 = false;
                        boolean supportsVersions = false;
                        if (boltStateHandler != null) {
                            try {
                                var serverVersion = version(boltStateHandler.getServerVersion());
                                supportsCypher25 = serverVersion.compareTo(version("5.27.0-2025040")) >= 0;
                                supportsVersions = serverVersion.compareTo(version("5.21.0")) >= 0;
                            } catch (Versions.FailedToParseException e) {
                                Logger log = Logger.create();
                                log.warn("Failed to parse server version", e);
                            }
                        }

                        Stream<String> validCypherVersions = supportsCypher25
                                ? Arrays.stream(CypherVersion.values()).map(v -> v.description)
                                : supportsVersions ? Stream.of(CypherVersion.Cypher5.description) : Stream.of();
                        return validCypherVersions.map(versionDescription ->
                                new Suggestion(versionDescription, SuggestionType.KEYWORD, null, false));
                    }

                    return Stream.empty();
                })
                .collect(Collectors.toList());
    }

    private List<Suggestion> getParserRuleCompletions(
            CodeCompletionCore.CandidatesCollection candidates,
            List<String> collectedVariables,
            CypherVersion parsedVersion,
            List<Token> tokens,
            ParserRuleContext stopNode) {
        return candidates.rules.entrySet().stream()
                .flatMap(entry -> {
                    var ruleNumber = entry.getKey();
                    var candidateRule = entry.getValue();
                    var startTokenIndex = candidateRule.startTokenIndex();
                    var ruleList = candidateRule.ruleList();
                    if (ruleNumber == Cypher25Parser.RULE_procedureResultItem) {
                        var callClause = getParent(stopNode, (x) -> x instanceof Cypher25Parser.CallClauseContext);
                        if (callClause.isPresent()) {
                            var call = (Cypher25Parser.CallClauseContext) callClause.get();
                            var procName = getMethodName(call.procedureName());
                            var existingItemNames = call.procedureResultItem().stream()
                                    .map(AstRuleCtx::getText)
                                    .collect(toCollection(HashSet::new));
                            return procedureReturnCompletions(procName, resolveCypherVersion(parsedVersion))
                                    .filter(a -> !existingItemNames.contains(a.value()));
                        }
                    } else if (ruleNumber == Cypher25Parser.RULE_functionName) {
                        return functionNameCompletions(startTokenIndex, tokens, resolveCypherVersion(parsedVersion));
                    } else if (ruleNumber == Cypher25Parser.RULE_procedureName) {
                        return procedureNameCompletions(startTokenIndex, tokens, resolveCypherVersion(parsedVersion));
                    } else if (ruleNumber == Cypher25Parser.RULE_parameter) {
                        return parameterCompletions(inferExpectedParameterTypeFromContext(candidateRule));
                    } else if (ruleNumber == Cypher25Parser.RULE_propertyKeyName) {
                        var parentRule = ruleList.get(ruleList.size() - 1);
                        var grandParentRule = ruleList.get(ruleList.size() - 2);
                        if (parentRule == Cypher25Parser.RULE_map && grandParentRule == Cypher25Parser.RULE_literal) {
                            return Stream.empty();
                        }

                        var greatGrandParentRule = ruleList.get(ruleList.size() - 3);
                        // When propertyKey is used as postfix to an expr there are many false positives
                        // because expression are very flexible. For this case we only suggest property
                        // keys if the expr is a simple variable that is defined.
                        // We still don't know the type of the variable we're completing without a symbol table
                        // but it is likely to be a node/relationship
                        if (parentRule == Cypher25Parser.RULE_property
                                && grandParentRule == Cypher25Parser.RULE_postFix
                                && greatGrandParentRule == Cypher25Parser.RULE_expression2) {
                            var expr2 = stopNode.getParent().getParent().getParent();
                            if (expr2 instanceof Cypher25Parser.Expression2Context) {
                                var variableName = ((Cypher25Parser.Expression2Context) expr2)
                                        .expression1()
                                        .variable()
                                        .getText();
                                if (variableName == null || collectedVariables.contains(variableName)) {
                                    return Stream.empty();
                                }
                            }
                        }

                        return propertyKeyCompletions();
                    } else if (ruleNumber == Cypher25Parser.RULE_variable) {
                        if (!ruleList.isEmpty()) {
                            var parentRule = ruleList.get(ruleList.size() - 1);

                            if (!ParserInfo.rulesDefiningVariables.contains(parentRule)) {
                                return collectedVariables.stream().map(Suggestion::identifier);
                            }
                        }
                    } else if (ruleNumber == Cypher25Parser.RULE_labelExpression1) {
                        var topExprIndex = ruleList.indexOf(Cypher25Parser.RULE_labelExpression);

                        if (topExprIndex > 0) {
                            var topExprParent = ruleList.get(topExprIndex - 1);
                            if (topExprParent == Cypher25Parser.RULE_nodePattern) {
                                return labelCompletions();
                            }

                            if (topExprParent == Cypher25Parser.RULE_relationshipPattern) {
                                return relTypeCompletions();
                            }

                            return Stream.concat(labelCompletions(), relTypeCompletions());
                        }
                    } else if (ruleNumber == Cypher25Parser.RULE_symbolicAliasName) {
                        return completeAliasName(tokens, candidateRule, startTokenIndex);
                    } else if (ruleNumber == Cypher25Parser.RULE_commandNameExpression) {
                        return completeSymbolicName(candidateRule, tokens, startTokenIndex);
                    }

                    return Stream.empty();
                })
                .collect(Collectors.toList());
    }

    private ParameterType inferExpectedParameterTypeFromContext(CodeCompletionCore.CandidateRule candidateRule) {
        var ruleList = candidateRule.ruleList();
        var parentRule = ruleList.get(ruleList.size() - 1);

        if (Set.of(
                        Cypher25Parser.RULE_stringOrParameter,
                        Cypher25Parser.RULE_commandNameExpression,
                        Cypher25Parser.RULE_symbolicNameOrStringParameterList,
                        Cypher25Parser.RULE_symbolicAliasNameOrParameter,
                        Cypher25Parser.RULE_passwordExpression,
                        Cypher25Parser.RULE_createUser,
                        Cypher25Parser.RULE_dropUser,
                        Cypher25Parser.RULE_alterUser,
                        Cypher25Parser.RULE_renameUser,
                        Cypher25Parser.RULE_createRole,
                        Cypher25Parser.RULE_dropRole,
                        Cypher25Parser.RULE_userNames,
                        Cypher25Parser.RULE_roleNames,
                        Cypher25Parser.RULE_renameRole)
                .contains(parentRule)) {
            return ParameterType.STRING;
        } else if (Set.of(Cypher25Parser.RULE_properties, Cypher25Parser.RULE_mapOrParameter)
                .contains(parentRule)) {
            return ParameterType.MAP;
        } else {
            return ParameterType.ANY;
        }
    }

    private Optional<Token> findPreviousNonSpace(List<Token> tokens, int index) {
        var i = index;
        while (i > 0) {
            var token = tokens.get(--i);

            if (token.getType() != Cypher25Parser.SPACE) {
                return Optional.of(token);
            }
        }

        return Optional.empty();
    }

    private Stream<Suggestion> completeSymbolicName(
            CodeCompletionCore.CandidateRule candidateRule, List<Token> tokens, int ruleStartTokenIndex) {
        // parameters are valid values in all cases of symbolic name
        var parameterSuggestions = parameterCompletions(inferExpectedParameterTypeFromContext(candidateRule));
        var ruleList = candidateRule.ruleList();

        var rulesCreatingNewUserOrRole = List.of(Cypher25Parser.RULE_createUser, Cypher25Parser.RULE_createRole);

        var previousToken = findPreviousNonSpace(tokens, ruleStartTokenIndex);
        var afterToToken = previousToken.stream().anyMatch(t -> t.getType() == Cypher25Parser.TO);

        // avoid suggesting existing user names or role names when creating a new one
        if (rulesCreatingNewUserOrRole.stream().anyMatch(ruleList::contains)
                ||
                // We are suggesting an user as target for the renaming
                //      RENAME USER existing TO target
                // so target should be non-existent
                (candidateRule.ruleList().contains(Cypher25Parser.RULE_renameUser) && afterToToken)) {
            return parameterSuggestions;
        }

        var rulesThatAcceptExistingUsers = List.of(
                Cypher25Parser.RULE_dropUser,
                Cypher25Parser.RULE_renameUser,
                Cypher25Parser.RULE_alterUser,
                Cypher25Parser.RULE_userNames);

        if (rulesThatAcceptExistingUsers.stream().anyMatch(ruleList::contains)) {
            return Stream.concat(parameterSuggestions, dbInfo.userNames.stream().map(Suggestion::value));
        }

        var rulesThatAcceptExistingRoles =
                List.of(Cypher25Parser.RULE_roleNames, Cypher25Parser.RULE_dropRole, Cypher25Parser.RULE_renameRole);

        if (rulesThatAcceptExistingRoles.stream().anyMatch(ruleList::contains)) {
            return Stream.concat(parameterSuggestions, dbInfo.roleNames.stream().map(Suggestion::value));
        }

        return Stream.empty();
    }

    private Stream<Suggestion> completeAliasName(
            List<Token> tokens, CodeCompletionCore.CandidateRule candidateRule, int ruleStartTokenIndex) {
        var ruleList = candidateRule.ruleList();
        // The rule for RULE_symbolicAliasName technically allows for spaces given that a dot is included in the name
        // so ALTER ALIAS a . b  FOR DATABASE neo4j is accepted by neo4j. It does however only drop the spaces for the
        // alias
        // it becomes just a.b

        // The issue for us is that when we complete "ALTER ALIAS a " <- according to the grammar points say we could
        // still be building a name
        // To handle this we check if the token after the first identifier in the rule is a space (as opposed to a dot)
        // if so we have a false positive and we return null to ignore the rule
        // symbolicAliasName: (symbolicNameString (DOT symbolicNameString)* | parameter);
        if (ruleStartTokenIndex + 1 < tokens.size()
                && tokens.get(ruleStartTokenIndex + 1).getType() == Cypher25Lexer.SPACE) {
            return Stream.empty();
        }

        // parameters are valid values in all cases of symbolicAliasName
        var parameterSuggestions = parameterCompletions(ParameterType.STRING);
        var rulesCreatingNewDb =
                List.of(Cypher25Parser.RULE_createDatabase, Cypher25Parser.RULE_createCompositeDatabase);

        // avoid suggesting existing database names when creating a new database
        if (rulesCreatingNewDb.stream().anyMatch(ruleList::contains)) {
            return parameterSuggestions;
        }

        // For `CREATE ALIAS aliasName FOR DATABASE databaseName`
        // Should not suggest existing aliases for aliasName but should suggest existing databases for databaseName
        // so we return base suggestions if we're at the `aliasName` rule
        if (ruleList.contains(Cypher25Parser.RULE_createAlias) && ruleList.contains(Cypher25Parser.RULE_aliasName)) {
            return parameterSuggestions;
        }

        var rulesThatOnlyAcceptAlias =
                List.of(Cypher25Parser.RULE_dropAlias, Cypher25Parser.RULE_alterAlias, Cypher25Parser.RULE_showAliases);

        if (rulesThatOnlyAcceptAlias.stream().anyMatch(ruleList::contains)) {
            return Stream.concat(
                    parameterSuggestions,
                    dbInfo.aliasNames.stream().map(name -> Suggestion.value(backtickDbNameIfNeeded(name), name)));
        }

        return Stream.concat(
                Stream.concat(
                        parameterSuggestions,
                        dbInfo.databaseNames.stream()
                                .map(name -> Suggestion.value(backtickDbNameIfNeeded(name), name))),
                dbInfo.aliasNames.stream().map(name -> Suggestion.value(backtickDbNameIfNeeded(name), name)));
    }

    private String calculateNamespacePrefix(int startTokenIndex, List<Token> tokens) {
        var ruleTokens = tokens.subList(startTokenIndex, tokens.size() - 1);
        var lastNonEOFToken = ruleTokens.size() >= 2 ? ruleTokens.get(ruleTokens.size() - 2) : null;

        var nonSpaceTokens = new ArrayList<>(ruleTokens.stream()
                .filter((token) -> token.getType() != Cypher25Lexer.SPACE && token.getType() != Cypher25Lexer.EOF)
                .toList());

        var lastNonSpaceIsDot = !nonSpaceTokens.isEmpty()
                && nonSpaceTokens.get(nonSpaceTokens.size() - 1).getType() == Cypher25Lexer.DOT;

        // `gds version` is invalid but `gds .version` and `gds. version` are valid
        // so if the last token is a space and the last non-space token
        // is anything but a dot return empty completions to avoid
        // creating invalid suggestions (db ping)
        if (lastNonEOFToken != null && lastNonEOFToken.getType() == Cypher25Lexer.SPACE && !lastNonSpaceIsDot) {
            return null;
        }

        // calculate the current namespace prefix
        // only keep finished namespaces both second level `gds.ver` => `gds.`
        // and first level make `gds` => ''
        if (!lastNonSpaceIsDot && !nonSpaceTokens.isEmpty()) {
            nonSpaceTokens.remove(nonSpaceTokens.size() - 1);
        }

        var namespacePrefix = nonSpaceTokens.stream().map(Token::getText).collect(Collectors.joining(""));
        return namespacePrefix;
    }

    private Stream<Suggestion> functionNameCompletions(
            int ruleStartTokenIndex, List<Token> tokens, CypherVersion cypherVersion) {
        return namespacedCompletion(
                ruleStartTokenIndex, tokens, dbInfo.functions.get(cypherVersion), SuggestionType.FUNCTION);
    }

    private Stream<Suggestion> procedureNameCompletions(
            int ruleStartTokenIndex, List<Token> tokens, CypherVersion cypherVersion) {
        return namespacedCompletion(
                ruleStartTokenIndex,
                tokens,
                dbInfo.procedures.get(cypherVersion).keySet().stream().toList(),
                SuggestionType.PROCEDURE);
    }

    private Stream<Suggestion> procedureReturnCompletions(String procedureName, CypherVersion cypherVersion) {
        var procedure = dbInfo.procedures.get(cypherVersion).get(procedureName);
        if (procedure != null) {
            var procedureReturns = procedure.returnDescription().stream().map(DbInfo.ReturnDescription::name);
            return procedureReturns.map(Suggestion::identifier);
        }
        return Stream.of();
    }

    private Stream<Suggestion> getNamespaceSuggestions(Stream<String> namespaces, SuggestionType suggestionType) {
        return namespaces
                .map((completion) -> {
                    if (suggestionType == SuggestionType.FUNCTION) {
                        return Suggestion.functionNamespace(completion);
                    } else {
                        return Suggestion.procedureNamespace(completion);
                    }
                })
                .collect(Collectors.toSet())
                .stream();
    }

    private Stream<Suggestion> getFullNameSuggestions(Stream<String> fullNames, SuggestionType suggestionType) {
        return fullNames.map((completion) -> {
            if (suggestionType == SuggestionType.FUNCTION) {
                return Suggestion.function(completion);
            } else {
                return Suggestion.procedure(completion);
            }
        });
    }

    private Stream<Suggestion> namespacedCompletion(
            int ruleStartTokenIndex, List<Token> tokens, List<String> signatures, SuggestionType suggestionType) {
        var fullNames = new HashSet<>(signatures);
        var namespacePrefix = calculateNamespacePrefix(ruleStartTokenIndex, tokens);
        if (namespacePrefix == null) {
            return Stream.empty();
        }

        if (namespacePrefix.isEmpty()) {
            // If we don't have any prefix show full functions and top level namespaces
            var topLevelPrefixes =
                    fullNames.stream().filter((fn) -> fn.contains(".")).map((fnName) -> fnName.split("\\.")[0]);
            var namespaceCompletions = getNamespaceSuggestions(topLevelPrefixes, suggestionType);
            var fullNameCompletions = getFullNameSuggestions(fullNames.stream(), suggestionType);
            return Stream.concat(namespaceCompletions, fullNameCompletions);
        } else {
            // if we have a namespace prefix, complete on the namespace level:
            // apoc. => show `util` | `load` | `date` etc.
            var fullNameOptions = new HashSet<String>();
            var namespaceOptions = new HashSet<String>();

            for (String name : fullNames) {
                if (name.startsWith(namespacePrefix)) {
                    // given prefix `apoc.` turn `apoc.util.sleep` => `util`
                    var splitByDot = name.substring(namespacePrefix.length()).split("\\.");
                    var option = splitByDot[0];
                    var isFunctionName = splitByDot.length == 1;

                    // handle prefix `time.truncate.` turning `time.truncate` => ``
                    if (!option.isEmpty()) {
                        if (isFunctionName) {
                            fullNameOptions.add(option);
                        } else {
                            namespaceOptions.add(option);
                        }
                    }
                }
            }
            var namespaceCompletions = getNamespaceSuggestions(namespaceOptions.stream(), suggestionType);
            var fullNameCompletions = getFullNameSuggestions(fullNameOptions.stream(), suggestionType);
            return Stream.concat(namespaceCompletions, fullNameCompletions);
        }
    }
    ;

    private Stream<Suggestion> parameterCompletions(ParameterType expectedType) {
        var result = this.dbInfo.parameters().entrySet().stream()
                .filter(entry -> expectedType == ParameterType.ANY || entry.getValue() == expectedType)
                .map((parameter) ->
                        Suggestion.parameter("$" + backtickIfNeeded(parameter.getKey()), "$" + parameter.getKey()));
        return result;
    }

    private String getTokenName(int token, boolean usePreparserTokens) {
        if (usePreparserTokens) {
            return PreParserInfo.vocabulary.getDisplayName(token);
        }
        if (ParserInfo.customTokenDisplayNames.containsKey(token)) {
            return ParserInfo.customTokenDisplayNames.get(token);
        } else {
            return ParserInfo.vocabulary.getDisplayName(token);
        }
    }

    private List<Suggestion> getTokenCompletions(
            CodeCompletionCore.CandidatesCollection candidates,
            Set<Integer> ignoredTokens,
            boolean usePreparserTokens) {
        var tokenEntries = candidates.tokens.entrySet();
        Stream<String> completions = tokenEntries.stream().flatMap((value) -> {
            var tokenNumber = value.getKey();
            var followUpList = value.getValue();
            // Note this is because antlr4-c3 code seems to be entering into an invalid state in some cases but no token
            // should
            // have a type lower than -1 (EOF), all of the token types are supposed to be greater or equal than 0
            if (!ignoredTokens.contains(tokenNumber) && tokenNumber >= -1) {
                var firstToken = getTokenName(tokenNumber, usePreparserTokens);
                var lastIndexToSlice = followUpList.size();

                for (int i = 0; i < followUpList.size() && lastIndexToSlice == followUpList.size(); ++i) {
                    if (ignoredTokens.contains(followUpList.get(i))) {
                        lastIndexToSlice = i;
                    }
                }

                var followUpTokens = followUpList.subList(0, lastIndexToSlice);
                var followUpString = followUpTokens.stream()
                        .map(token -> this.getTokenName(token, usePreparserTokens))
                        .collect(Collectors.joining("  "));

                if (!followUpString.isEmpty()) {
                    return Stream.of(firstToken + " " + followUpString);
                } else {
                    return Stream.of(firstToken);
                }
            }

            return Stream.of();
        });
        var result = completions.map(Suggestion::keyword).collect(Collectors.toList());

        return result;
    }

    public boolean completionsEnabled() {
        return dbInfo.completionsEnabled();
    }
}
