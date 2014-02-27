package org.jgll.grammar;

import static org.jgll.util.CollectionsUtil.*;
import static org.junit.Assert.*;

import org.jgll.grammar.slot.factory.FirstFollowSetGrammarSlotFactory;
import org.jgll.grammar.slot.factory.GrammarSlotFactory;
import org.jgll.grammar.symbol.Character;
import org.jgll.grammar.symbol.Nonterminal;
import org.jgll.grammar.symbol.Rule;
import org.jgll.parser.GLLParser;
import org.jgll.parser.ParseError;
import org.jgll.parser.ParserFactory;
import org.jgll.recognizer.GLLRecognizer;
import org.jgll.recognizer.RecognizerFactory;
import org.jgll.sppf.NonterminalSymbolNode;
import org.jgll.sppf.SPPFNode;
import org.jgll.sppf.TokenSymbolNode;
import org.jgll.util.Input;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * A ::= 'a'
 */
public class Test2 {

	private Grammar grammar;
	private GLLRecognizer recognizer;
	
	@Before
	public void init() {
		Rule r1 = new Rule(new Nonterminal("A"), list(new Character('a')));
		
		GrammarSlotFactory factory = new FirstFollowSetGrammarSlotFactory();
		grammar = new GrammarBuilder("a", factory).addRule(r1).build();
		
		recognizer = RecognizerFactory.contextFreeRecognizer(grammar);
	}
	
	@Test
	public void testNullable() {
		assertFalse(grammar.getNonterminalByName("A").isNullable());
	}
	
	@Test
	public void testParser() throws ParseError {
		Input input = Input.fromString("a");
		GLLParser parser = ParserFactory.newParser(grammar, input);
		NonterminalSymbolNode sppf = parser.parse(input, grammar, "A");
		assertEquals(true, sppf.deepEquals(expectedSPPF()));
	}
	
	@Test
	public void testRecognizerSuccess() {
		boolean result = recognizer.recognize(Input.fromString("a"), grammar, "A");
		assertEquals(true, result);
	}
	
	public void testRecognizerFail1() {
		boolean result = recognizer.recognize(Input.fromString("b"), grammar, "A");
		assertEquals(false, result);
	}
	
	public void testRecognizerFail2() {
		boolean result = recognizer.recognize(Input.fromString("aa"), grammar, "A");
		assertEquals(false, result);
	}
	
	private SPPFNode expectedSPPF() {
		TokenSymbolNode node0 = new TokenSymbolNode(2, 0, 1);
		NonterminalSymbolNode node1 = new NonterminalSymbolNode(grammar.getNonterminal(0), 0, 1);
		node1.addChild(node0);
		return node1;
	}

}