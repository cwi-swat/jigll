package org.iguana.parser.datadependent.state;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.iguana.datadependent.ast.AST;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.GrammarGraph;
import org.iguana.grammar.condition.ConditionType;
import org.iguana.grammar.condition.RegularExpressionCondition;
import org.iguana.grammar.symbol.*;
import org.iguana.grammar.symbol.Character;

import static org.iguana.grammar.symbol.LayoutStrategy.*;

import org.iguana.grammar.transformation.DesugarAlignAndOffside;
import org.iguana.grammar.transformation.DesugarPrecedenceAndAssociativity;
import org.iguana.grammar.transformation.DesugarState;
import org.iguana.grammar.transformation.EBNFToBNF;
import org.iguana.grammar.transformation.LayoutWeaver;
import org.iguana.parser.GLLParser;
import org.iguana.parser.ParseResult;
import org.iguana.parser.ParserFactory;
import org.iguana.regex.*;
import org.iguana.util.Configuration;
import org.iguana.util.Input;
import org.iguana.util.Visualization;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Sets;

import static org.iguana.util.CollectionsUtil.*;

@SuppressWarnings("unused")
public class Example2 {

    @Test
    public void test() {
         Grammar grammar =

Grammar.builder()

// A ::= (a) do x = 999; (a) do y = 2; (;)  {UNDEFINED,-1,NON_REC} PREC(1,1) 
.addRule(Rule.withHead(Nonterminal.builder("A").build()).addSymbol(Code.builder(Terminal.builder(Sequence.builder(Character.builder(97).build()).build()).build(),AST.stat(AST.assign("x",AST.integer(999)))).build()).addSymbol(Code.builder(Terminal.builder(Sequence.builder(Character.builder(97).build()).build()).build(),AST.stat(AST.assign("y",AST.integer(2)))).build()).addSymbol(Terminal.builder(Sequence.builder(Character.builder(59).build()).build()).build()).setRecursion(Recursion.NON_REC).setAssociativity(Associativity.UNDEFINED).setPrecedence(-1).setPrecedenceLevel(PrecedenceLevel.from(1,1,-1,false,false,false,false)).build())
// $default$ ::=  {UNDEFINED,-1,NON_REC} PREC(1,1) 
.addRule(Rule.withHead(Nonterminal.builder("$default$").build()).setLayoutStrategy(NO_LAYOUT).setRecursion(Recursion.NON_REC).setAssociativity(Associativity.UNDEFINED).setPrecedence(-1).setPrecedenceLevel(PrecedenceLevel.from(1,1,-1,false,false,false,false)).build())
// D ::= (d)  {UNDEFINED,-1,NON_REC} PREC(1,1) 
.addRule(Rule.withHead(Nonterminal.builder("D").build()).addSymbol(Terminal.builder(Sequence.builder(Character.builder(100).build()).build()).build()).setRecursion(Recursion.NON_REC).setAssociativity(Associativity.UNDEFINED).setPrecedence(-1).setPrecedenceLevel(PrecedenceLevel.from(1,1,-1,false,false,false,false)).build())
// S ::= A do var m = z; C B do var n = x; D when m == 0 && n == 999  {UNDEFINED,-1,NON_REC} PREC(1,1) 
.addRule(Rule.withHead(Nonterminal.builder("S").build()).addSymbol(Code.builder(Nonterminal.builder("A").build(),AST.varDeclStat(AST.varDecl("m",AST.var("z")))).build()).addSymbol(Nonterminal.builder("C").build()).addSymbol(Code.builder(Nonterminal.builder("B").build(),AST.varDeclStat(AST.varDecl("n",AST.var("x")))).build()).addSymbol(Conditional.builder(Nonterminal.builder("D").build(),AST.and(AST.equal(AST.var("m"),AST.integer(0)),AST.equal(AST.var("n"),AST.integer(999)))).build()).setRecursion(Recursion.NON_REC).setAssociativity(Associativity.UNDEFINED).setPrecedence(-1).setPrecedenceLevel(PrecedenceLevel.from(1,1,-1,false,false,false,false)).build())
// C ::= (c)  {UNDEFINED,-1,NON_REC} PREC(1,1) 
.addRule(Rule.withHead(Nonterminal.builder("C").build()).addSymbol(Terminal.builder(Sequence.builder(Character.builder(99).build()).build()).build()).setRecursion(Recursion.NON_REC).setAssociativity(Associativity.UNDEFINED).setPrecedence(-1).setPrecedenceLevel(PrecedenceLevel.from(1,1,-1,false,false,false,false)).build())
// B ::= (b) do z = x; (;)  {UNDEFINED,-1,NON_REC} PREC(1,1) 
.addRule(Rule.withHead(Nonterminal.builder("B").build()).addSymbol(Code.builder(Terminal.builder(Sequence.builder(Character.builder(98).build()).build()).build(),AST.stat(AST.assign("z",AST.var("x")))).build()).addSymbol(Terminal.builder(Sequence.builder(Character.builder(59).build()).build()).build()).setRecursion(Recursion.NON_REC).setAssociativity(Associativity.UNDEFINED).setPrecedence(-1).setPrecedenceLevel(PrecedenceLevel.from(1,1,-1,false,false,false,false)).build())
.build();

         grammar = new EBNFToBNF().transform(grammar);
         // System.out.println(grammar);

         grammar = new DesugarState().transform(grammar);
         System.out.println(grammar.toStringWithOrderByPrecedence());

         Input input = Input.fromString("aa;cb;d");
         GrammarGraph graph = grammar.toGrammarGraph(input, Configuration.DEFAULT);

         Visualization.generateGrammarGraph("/Users/anastasiaizmaylova/git/iguana/test/org/iguana/parser/datadependent/state/", graph);
         
         Map<String, Object> inits = new HashMap<>();
         inits.put("x", 0);
         inits.put("y", 0);
         inits.put("z", 0);
         
         Nonterminal start = null;
         for (Nonterminal nonterminal : grammar.getNonterminals()) {
        	 if (nonterminal.getName().equals("S")) {
        		 start = nonterminal;
        		 break;
        	 }
         }

         GLLParser parser = ParserFactory.getParser(Configuration.DEFAULT, input, grammar);
         ParseResult result = parser.parse(input, graph, start, inits, false);

         Assert.assertTrue(result.isParseSuccess());

         Visualization.generateSPPFGraph("/Users/anastasiaizmaylova/git/iguana/test/org/iguana/parser/datadependent/state/",
                           result.asParseSuccess().getRoot(), input);

         Assert.assertTrue(result.asParseSuccess().getStatistics().getCountAmbiguousNodes() == 0);
    }
}