package org.jgll.grammar;

import static org.junit.Assert.assertEquals;

import org.jgll.parser.ParseError;
import org.jgll.sppf.NonterminalSymbolNode;
import org.jgll.util.Input;
import org.junit.Test;

/**
 * 
 * E ::= E z
 *     > x E
 *     > E w
 *     | a
 * 
 * 
 * @author Ali Afroozeh
 *
 */
public class FilterTest4 extends AbstractGrammarTest {

	private Rule rule1;
	private Rule rule2;
	private Rule rule3;
	private Rule rule4;

	@Override
	protected Grammar initGrammar() {
		
		GrammarBuilder builder = new GrammarBuilder("TwoLevelFiltering");
		
		Nonterminal E = new Nonterminal("E");

		// E ::= E z
		rule1 = new Rule(E, list(E, new Character('z')));
		builder.addRule(rule1);
		
		// E ::=  x E
		rule2 = new Rule(E, list(new Character('x'), E));
		builder.addRule(rule2);
		
		// E ::= E w
		rule3 = new Rule(E, list(E, new Character('w')));
		builder.addRule(rule3);
		
		// E ::= a
		rule4 = new Rule(E, list(new Character('a')));
		builder.addRule(rule4);
		
		// (E, .E z, x E) 
		builder.addFilter(E, rule1, 0, rule2);
		
		// (E, x .E, E w)
		builder.addFilter(E, rule2, 1, rule3);
		
		builder.filter();
		return builder.build();
	}

	@Test
	public void testAssociativityAndPriority() throws ParseError {
		NonterminalSymbolNode sppf1 = rdParser.parse(Input.fromString("xawz"), grammar, "E");
		NonterminalSymbolNode sppf2 = levelParser.parse(Input.fromString("xawz"), grammar, "E");
		assertEquals(true, sppf1.equals(sppf2));
	}

}
