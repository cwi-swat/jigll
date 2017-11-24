/*
 * Copyright (c) 2015, Ali Afroozeh and Anastasia Izmaylova, Centrum Wiskunde & Informatica (CWI)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this 
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this 
 *    list of conditions and the following disclaimer in the documentation and/or 
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
 * OF SUCH DAMAGE.
 *
 */

package org.iguana.parser.layout;

import static org.junit.Assert.assertTrue;

import org.iguana.grammar.Grammar;
import org.iguana.grammar.symbol.Nonterminal;
import org.iguana.grammar.symbol.Plus;
import org.iguana.grammar.symbol.Rule;
import org.iguana.grammar.symbol.Terminal;
import org.iguana.grammar.transformation.EBNFToBNF;
import org.iguana.grammar.transformation.LayoutWeaver;
import org.iguana.parser.Iguana;
import org.iguana.parser.ParseResult;
import org.iguana.util.Configuration;
import org.junit.Test;

import iguana.parsetrees.slot.NonterminalNodeType;
import iguana.regex.Character;
import iguana.utils.input.Input;

/**
 * 
 * S ::= A {B ,}+ C
 * A ::= a
 * B ::= b
 * C ::= c
 * 
 * @author Ali Afroozeh
 *
 */
public class LayoutTest4 {

    static Terminal a = Terminal.from(Character.from('a'));
    static Terminal b = Terminal.from(Character.from('b'));
    static Terminal c = Terminal.from(Character.from('c'));
    static Nonterminal S = Nonterminal.withName("S");
    static Nonterminal A = Nonterminal.withName("A");
    static Nonterminal B = Nonterminal.withName("B");
    static Nonterminal C = Nonterminal.withName("C");
    static Terminal space = Terminal.from(Character.from(' '));
    static Terminal comma = Terminal.from(Character.from(','));

    static Nonterminal L = Nonterminal.builder("L").setType(NonterminalNodeType.Layout()).build();

    static Rule r1 = Rule.withHead(S).addSymbols(A, Plus.builder(B).addSeparator(comma).build(), C).setLayout(L).build();
    static Rule r2 = Rule.withHead(A).addSymbol(a).setLayout(L).build();
    static Rule r3 = Rule.withHead(B).addSymbol(b).setLayout(L).build();
    static Rule r4 = Rule.withHead(C).addSymbol(c).setLayout(L).build();

    static Rule layout = Rule.withHead(L).addSymbol(space).build();

	private static Grammar getGrammar() {
        Grammar grammar = Grammar.builder().addRules(r1, r2, r3, r4, layout).build();
        return new LayoutWeaver().transform(new EBNFToBNF().transform(grammar));
    }
	
	@Test
	public void test() {
		Input input = Input.fromString("a b , b , b , b c");
		Grammar grammar = getGrammar();
		ParseResult result = Iguana.parse(input, grammar, Configuration.DEFAULT, Nonterminal.withName("S"));
		assertTrue(result.isParseSuccess());
	}

}
