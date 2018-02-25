package org.iguana.util;

import iguana.utils.input.Input;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.GrammarGraph;
import org.iguana.grammar.symbol.Nonterminal;
import org.iguana.parser.Iguana;
import org.iguana.parser.ParseResult;
import org.iguana.parsetree.DefaultParseTreeBuilder;
import org.iguana.parsetree.ParseTreeNode;
import org.iguana.parsetree.SPPFToParseTree;
import org.iguana.sppf.CyclicGrammarException;
import org.iguana.sppf.NonterminalNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRunner {

    public static void main(String[] args) {
        run("/Users/afroozeh/workspace/iguana");
    }

    public static void run(String rootDir) {
        Iterable<String> tests = getTests(rootDir);
        for (String testDir : tests)
            runTest(testDir);
    }

    public static void runTest(String testPath) {
        Path path = Paths.get(testPath);
        Path testName = path.getFileName();
        System.out.println("Running " + testName);

        String grammarPath = testPath + "/grammar.json";

        Grammar grammar;
        try {
            grammar = Grammar.load(grammarPath, "json");
        } catch (FileNotFoundException e) {
            System.out.println("No grammar.json file is present");
            return;
        }

        File testDir = new File(testPath);
        int size = testDir.list((dir, name) -> name.matches("input\\d*.txt")).length;

        for (int i = 1; i <= size; i++) {
            System.out.print("Input" + i);
            String inputPath = testPath + "/input" + i + ".txt";
            Input input;
            try {
                input = Input.fromPath(inputPath);
            } catch (FileNotFoundException e) {
                System.out.println("Input file not found");
                continue;
            } catch (IOException e) {
                System.out.println("An error occurred while reading from " + inputPath);
                continue;
            }

            Nonterminal start = grammar.getRules().get(0).getHead();
            GrammarGraph grammarGraph = GrammarGraph.from(grammar, input);
            ParseResult result = Iguana.parse(input, grammarGraph, start);
            if (result.isParseError()) {
                error(result.asParseError().toString());
                continue;
            }

            String sppfPath = testPath + "/sppf" + i + ".json";
            NonterminalNode expectedSPPFNode;
            try {
                expectedSPPFNode = SPPFJsonSerializer.deserialize(readFile(sppfPath), grammarGraph);
            } catch (IOException e) {
                error("Cannot deserialize SPPF");
                continue;
            }

            if (!equals(expectedSPPFNode, result.asParseSuccess().getSPPFNode())) {
                error("SPPF nodes do not match");
                continue;
            }

            ParseTreeNode actualParseTree;
            try {
                actualParseTree = SPPFToParseTree.toParseTree(result.asParseSuccess().getSPPFNode(), new DefaultParseTreeBuilder());
            } catch (CyclicGrammarException e) {
                success();
                continue;
            }

            String parseTreePath = testPath + "/parsetree" + i + ".json";
            ParseTreeNode expectedParseTree;
            try {
                expectedParseTree = JsonSerializer.deserialize(readFile(parseTreePath), ParseTreeNode.class);
            } catch (IOException e) {
                error("Cannot deserialize parse trees");
                continue;
            }

            if (!expectedParseTree.equals(actualParseTree)) {
                error("Parse trees do not match");
                continue;
            }
            success();
        }
        System.out.println();
    }

    private static void error(String message) {
        System.out.println("\t\t\tError: " + message);
    }

    private static void success() {
        System.out.println("\t\t\tSuccess");
    }

    private static boolean equals(NonterminalNode expected, NonterminalNode actual) {
        return SPPFJsonSerializer.serialize(expected).equals(SPPFJsonSerializer.serialize(actual));
    }

    private static String readFile(String path) throws IOException {
        byte[] content = Files.readAllBytes(Paths.get(path));
        return new String(content, StandardCharsets.UTF_8);
    }

    private static Iterable<String> getTests(String rootPath) {
        List<String> tests = new ArrayList<>();
        getTests(rootPath, tests);
        return sort(tests);
    }

    private static List<String> sort(List<String> filePaths) {
        Pattern pattern = Pattern.compile("(.*?)(\\d*)");
        filePaths.sort((path1, path2) -> {
            Matcher matcher1 = pattern.matcher(path1);
            Matcher matcher2 = pattern.matcher(path2);
            if (matcher1.matches() && matcher2.matches()) {
                String path1NamePart = matcher1.group(1);
                int path1NumericPart = matcher1.group(2).equals("") ? 0 : Integer.parseInt(matcher1.group(2));

                String path2NamePart = matcher2.group(1);
                int path2NumericPart = matcher2.group(2).equals("") ? 0 : Integer.parseInt(matcher2.group(2));

                int diff = path1NamePart.compareTo(path2NamePart);
                if (diff != 0) return diff;
                return path1NumericPart - path2NumericPart;
            }
            throw new RuntimeException("Should not reach here");
        });
        return filePaths;
    }

    private static void getTests(String currentDirPath, List<String> tests) {
        File currentDir = new File(currentDirPath);
        if (!currentDir.isDirectory()) return;
        if (currentDir.getName().matches("Test.*\\d*")) {
            tests.add(currentDirPath);
        } else {
            for (String childDir: currentDir.list()) {
                getTests(currentDir + "/" + childDir, tests);
            }
        }
    }
}
