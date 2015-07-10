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

package org.iguana.parser;


import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.iguana.datadependent.ast.Expression;
import org.iguana.datadependent.ast.Statement;
import org.iguana.datadependent.env.Environment;
import org.iguana.datadependent.env.IEvaluatorContext;
import org.iguana.datadependent.env.persistent.PersistentEvaluatorContext;
import org.iguana.grammar.GrammarGraph;
import org.iguana.grammar.condition.DataDependentCondition;
import org.iguana.grammar.slot.BodyGrammarSlot;
import org.iguana.grammar.slot.DummySlot;
import org.iguana.grammar.slot.EndGrammarSlot;
import org.iguana.grammar.slot.GrammarSlot;
import org.iguana.grammar.slot.NonterminalGrammarSlot;
import org.iguana.grammar.slot.TerminalGrammarSlot;
import org.iguana.grammar.symbol.Nonterminal;
import org.iguana.parser.descriptor.Descriptor;
import org.iguana.parser.gss.GSSEdge;
import org.iguana.parser.gss.GSSNode;
import org.iguana.parser.gss.GSSNodeData;
import org.iguana.parser.gss.NewGSSEdgeImpl;
import org.iguana.sppf.DummyNode;
import org.iguana.sppf.IntermediateNode;
import org.iguana.sppf.NonPackedNode;
import org.iguana.sppf.NonterminalNode;
import org.iguana.sppf.TerminalNode;
import org.iguana.sppf.lookup.SPPFLookup;
import org.iguana.util.BenchmarkUtil;
import org.iguana.util.Configuration;
import org.iguana.util.Input;
import org.iguana.util.ParseStatistics;
import org.iguana.util.logging.LoggerWrapper;

/**
 * 
 * 
 * @author Ali Afroozeh
 * @author Anastasia Izmaylova
 * 
 */
public class GLLParserImpl implements GLLParser {
		
	protected static final LoggerWrapper log = LoggerWrapper.getLogger(GLLParserImpl.class);
	
	protected final SPPFLookup sppfLookup;
	
	protected GSSNode cu;
	
	protected NonPackedNode cn = DummyNode.getInstance();
	
	protected int ci = 0;
	
	protected Input input;
	
	/**
	 * 
	 */
	protected GrammarGraph grammarGraph;
	
	/**
	 * The grammar slot at which a parse error is occurred. 
	 */
	protected GrammarSlot errorSlot;
	
	/**
	 * The last input index at which an error is occurred. 
	 */
	protected int errorIndex;
	
	/**
	 * The current GSS node at which an error is occurred.
	 */
	protected GSSNode errorGSSNode;
	
	protected GSSNode startGSSNode;
	
	protected int descriptorsCount;
	
	protected int nonterminalNodesCount;
	
	protected int countGSSNodes;
	
	protected int countGSSEdges;

	private final Configuration config;
	
	private Deque<Descriptor> descriptorsStack;

	public GLLParserImpl(Configuration config, SPPFLookup sppfLookup) {
		this.config = config;
		this.sppfLookup = sppfLookup;
		this.descriptorsStack = new ArrayDeque<>();
	}
	
	@Override
	public final ParseResult parse(Input input, GrammarGraph grammarGraph, Nonterminal nonterminal, Map<String, ? extends Object> map, boolean global) {
		this.grammarGraph = grammarGraph;
		this.input = input;
		
		/**
		 * Data-dependent GLL parsing
		 */
		this.ctx = new PersistentEvaluatorContext(input);
		
		if (global)
			map.forEach((k,v) -> ctx.declareGlobalVariable(k, v));
		
		NonterminalGrammarSlot startSymbol = getStartSymbol(nonterminal);
		
		if(startSymbol == null) {
			throw new RuntimeException("No nonterminal named " + nonterminal + " found");
		}
		
		startGSSNode = new GSSNode(startSymbol, 0);
		
		grammarGraph.reset(input);
		resetParser(startSymbol);
	
		log.info("Parsing %s:", input.getURI());

		long start = System.nanoTime();
		long startUserTime = BenchmarkUtil.getUserTime();
		long startSystemTime = BenchmarkUtil.getSystemTime();
		
		NonterminalNode root;
		
		Environment env = null;
		
		if (!global && !map.isEmpty()) {
			Object[] values = new Object[map.size()];
			
			int i = 0;
			for (String parameter : nonterminal.getParameters())
				values[i++] = map.get(parameter);
			
			env = getEmptyEnvironment().declare(nonterminal.getParameters(), values);
		}
		
		parse(startSymbol, env);
		
		root = startGSSNode.getNonterminalNode(input.length() - 1);

		ParseResult parseResult;
		
		long end = System.nanoTime();
		long endUserTime = BenchmarkUtil.getUserTime();
		long endSystemTime = BenchmarkUtil.getSystemTime();
		
		if (root == null) {
			parseResult = new ParseError(errorSlot, input, errorIndex, errorGSSNode);
			log.info("Parse error:\n %s", parseResult);
		} else {
			ParseStatistics parseStatistics = ParseStatistics.builder()
					.setNanoTime(end - start)
					.setUserTime(endUserTime - startUserTime)
					.setSystemTime(endSystemTime - startSystemTime) 
					.setMemoryUsed(BenchmarkUtil.getMemoryUsed())
					.setDescriptorsCount(descriptorsCount) 
					.setGSSNodesCount(countGSSNodes + 1) // + start gss node 
					.setGSSEdgesCount(countGSSEdges) 
					.setNonterminalNodesCount(nonterminalNodesCount)
					.setTerminalNodesCount(sppfLookup.getTerminalNodesCount())
					.setIntermediateNodesCount(sppfLookup.getIntermediateNodesCount()) 
					.setPackedNodesCount(sppfLookup.getPackedNodesCount()) 
					.setAmbiguousNodesCount(sppfLookup.getAmbiguousNodesCount()).build();

			parseResult = new ParseSuccess(root, parseStatistics, input);
			log.info("Parsing finished successfully.");			
			log.info(parseStatistics.toString());
		}
		
		return parseResult;
	}
	
	protected NonterminalGrammarSlot getStartSymbol(Nonterminal nonterminal) {
		return grammarGraph.getHead(nonterminal);
	}
	
	protected void parse(NonterminalGrammarSlot startSymbol, Environment env) {
		
		if(!startSymbol.testPredict(input.charAt(ci))) {
			recordParseError(startSymbol);
			return;
		}
		
		if (env == null)
			startSymbol.getFirstSlots().forEach(s -> scheduleDescriptor(new Descriptor(s, cu, ci, DummyNode.getInstance())));
		else 
			startSymbol.getFirstSlots().forEach(s -> scheduleDescriptor(new org.iguana.datadependent.descriptor.Descriptor(s, cu, ci, DummyNode.getInstance(), env)));
		
		while(!descriptorsStack.isEmpty()) {
			Descriptor descriptor = descriptorsStack.pop();
			ci = descriptor.getInputIndex();
			cu = descriptor.getGSSNode();
			cn = descriptor.getSPPFNode();
			log.trace("Processing %s", descriptor);
			descriptor.execute(this);
		}
	}
	
	@Override
	public final void pop(GSSNode gssNode, int inputIndex, NonterminalNode node) {
		
		if (node == null) return;
		
		log.debug("Pop %s, %d, %s", gssNode, inputIndex, node);
		nonterminalNodesCount++;
		
		for(GSSEdge edge : gssNode.getGSSEdges()) {			
			Descriptor descriptor = edge.addDescriptor(this, gssNode, inputIndex, node);
			if (descriptor != null) {
				scheduleDescriptor(descriptor);
			}
		}			
	}
	
	@Override
	public GSSNode create(BodyGrammarSlot returnSlot, NonterminalGrammarSlot nonterminal, GSSNode u, int i, NonPackedNode node) {
		GSSNode gssNode = nonterminal.hasGSSNode(i);
		
		if (gssNode == null) {
			gssNode = nonterminal.getGSSNode(i);

			log.trace("GSSNode created: (%s, %d)",  nonterminal, i);
			countGSSNodes++;
			
			createGSSEdge(returnSlot, u, node, gssNode);
			
			final GSSNode __gssNode = gssNode;
			
			List<BodyGrammarSlot> firstSlots = nonterminal.getFirstSlots(input.charAt(i));
			if (firstSlots != null)
				for (BodyGrammarSlot s : firstSlots) {
					if (!s.getConditions().execute(getInput(), __gssNode, i))
						scheduleDescriptor(new Descriptor(s, __gssNode, i, DummyNode.getInstance()));
				}
			
			// nonterminal.getFirstSlots().forEach(s -> scheduleDescriptor(new Descriptor(s, __gssNode, i, DummyNode.getInstance())));
		} else {
			log.trace("GSSNode found: %s",  gssNode);
			createGSSEdge(returnSlot, u, node, gssNode);			
		}
		return gssNode;
	}
		
	private void createGSSEdge(BodyGrammarSlot returnSlot, GSSNode destination, NonPackedNode w, GSSNode source) {
		NewGSSEdgeImpl edge = new NewGSSEdgeImpl(returnSlot, w, destination);
		
		if(source.getGSSEdge(edge)) {
			countGSSEdges++;
			log.trace("GSS Edge created: %s from %s to %s", returnSlot, source, destination);

			for (NonPackedNode z : source.getPoppedElements()) {			
				Descriptor descriptor = edge.addDescriptor(this, source, z.getRightExtent(), z);
				if (descriptor != null) {
					scheduleDescriptor(descriptor);
				}
			}
		}
	}
	
	/**
	 * Replaces the previously reported parse error with the new one if the
	 * inputIndex of the new parse error is greater than the previous one. In
	 * other words, we throw away an error if we find an error which happens at
	 * the next position of input.
	 * 
	 */
	@Override
	public void recordParseError(GrammarSlot slot) {
		if (ci >= this.errorIndex) {
			log.debug("Error recorded at %s %d", slot, ci);
			this.errorIndex = ci;
			this.errorSlot = slot;
			this.errorGSSNode = cu;
		}
	}
	
	public final void scheduleDescriptor(Descriptor descriptor) {
		descriptorsStack.push(descriptor);
		log.trace("Descriptor created: %s", descriptor);
		descriptorsCount++;
	}
		
	public SPPFLookup getSPPFLookup() {
		return sppfLookup;
	}
	
	@Override
	public Input getInput() {
		return input;
	}
	
	private void resetParser(NonterminalGrammarSlot startSymbol) {
		descriptorsStack.clear();
		sppfLookup.reset();
		ci = 0;
		cu = startGSSNode;			
		cn = DummyNode.getInstance();
		errorSlot = null;
		errorIndex = 0;
		errorGSSNode = null;
		
		descriptorsCount = 0;
		nonterminalNodesCount = 0;
		countGSSNodes = 0;
		countGSSEdges = 0;
	}
	
	@Override
	public TerminalNode getEpsilonNode(TerminalGrammarSlot slot, int inputIndex) {
		return sppfLookup.getEpsilonNode(slot, inputIndex);
	}
		
	@Override
	public NonterminalNode getNonterminalNode(EndGrammarSlot slot, NonPackedNode child) {
		return sppfLookup.getNonterminalNode(slot, child);
	}
	
	@Override
	public NonterminalNode hasNonterminalNode(EndGrammarSlot slot, NonPackedNode child) {
		return sppfLookup.hasNonterminalNode(slot, child);
	}
	
	@Override
	public IntermediateNode getIntermediateNode(BodyGrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild) {
		return sppfLookup.getIntermediateNode(slot, leftChild, rightChild);
	}
	
	@Override
	public NonPackedNode getIntermediateNode2(BodyGrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild) {
		return sppfLookup.getIntermediateNode2(slot, leftChild, rightChild);
	}
	
	@Override
	public IntermediateNode hasIntermediateNode(BodyGrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild) {
		return sppfLookup.getIntermediateNode(slot, leftChild, rightChild);
	}
	
	@Override
	public TerminalNode getTerminalNode(TerminalGrammarSlot slot, int leftExtent, int rightExtent) {
		return sppfLookup.getTerminalNode(slot, leftExtent, rightExtent);
	}
	
	@Override 
	public NonPackedNode getNode(GrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild) {
		return sppfLookup.getNode(slot, leftChild, rightChild);
	}
	
	@Override
	public NonPackedNode hasNode(GrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild) {
		return sppfLookup.hasNode(slot, leftChild, rightChild);
	}

	@Override
	public Configuration getConfiguration() {
		return config;
	}
	
	@Override
	public Iterable<GSSNode> getGSSNodes() {
		return grammarGraph.getNonterminals().stream().flatMap(s -> StreamSupport.stream(s.getGSSNodes().spliterator(), false)).collect(Collectors.toList());
	}
	
	/**
	 * 
	 * Data-dependent GLL parsing
	 * 
	 */
	private IEvaluatorContext ctx;
	private BodyGrammarSlot currentEndGrammarSlot = DummySlot.getInstance();
	private final Object defaultValue = new Object();
	private Object currentValue = defaultValue;
	
	@Override
	public IEvaluatorContext getEvaluatorContext() {
		return ctx;
	}
	
	@Override
	public BodyGrammarSlot getCurrentEndGrammarSlot() {
		return currentEndGrammarSlot;
	}
	
	@Override
	public Object getCurrentValue() {
		return currentValue;
	}
	
	@Override
	public boolean hasCurrentValue() {
		return currentValue != defaultValue;
	}
	
	@Override
	public void setCurrentEndGrammarSlot(BodyGrammarSlot slot) {
		currentEndGrammarSlot = slot;
	}
	
	@Override
	public void setCurrentValue(Object value) {
		currentValue = value;
	}
	
	@Override
	public void resetCurrentValue() {
		currentValue = defaultValue;
	}
	
	@Override
	public Environment getEnvironment() {
		return ctx.getEnvironment();
	}
	
	@Override
	public void setEnvironment(Environment env) {
		ctx.setEnvironment(env);
	}
	
	@Override
	public Environment getEmptyEnvironment() {
		return ctx.getEmptyEnvironment();
	}
	
	@Override
	public Object evaluate(Statement[] statements, Environment env) {
		assert statements.length > 1;
		
		ctx.setEnvironment(env);
		
		int i = 0;
		while (i < statements.length) {
			statements[i].interpret(ctx);
			i++;
		}
		
		return null;
	}
	
	@Override
	public Object evaluate(DataDependentCondition condition, Environment env) {
		ctx.setEnvironment(env);
		return condition.getExpression().interpret(ctx);
	}
	
	@Override
	public Object evaluate(Expression expression, Environment env) {
		ctx.setEnvironment(env);
		return expression.interpret(ctx);
	}
	
	@Override
	public Object[] evaluate(Expression[] arguments, Environment env) {
		if (arguments == null) return null;
		
		ctx.setEnvironment(env);
		
		Object[] values = new Object[arguments.length];
		
		int i = 0;
		while (i < arguments.length) {
			values[i] = arguments[i].interpret(ctx);
			i++;
		}
		
		return values;
	}
	
	@Override
	public GSSNode create(BodyGrammarSlot returnSlot, NonterminalGrammarSlot nonterminal, GSSNode u, int i, NonPackedNode node, Expression[] arguments, Environment env) {
		assert !(env.isEmpty() && arguments == null);
		
		if (arguments == null) {
			
			GSSNode gssNode = nonterminal.hasGSSNode(i);
			if (gssNode == null) {
				
				gssNode = nonterminal.getGSSNode(i);
				
				countGSSNodes++;
				log.trace("GSSNode created: %s", gssNode);
				
				createGSSEdge(returnSlot, u, node, gssNode, env); // Record environment on the edge
				
				final GSSNode __gssNode = gssNode;
				
				List<BodyGrammarSlot> firstSlots = nonterminal.getFirstSlots(input.charAt(i));
				if (firstSlots != null)
					for (BodyGrammarSlot s : firstSlots) {
						if (!s.getConditions().execute(getInput(), __gssNode, i))
							scheduleDescriptor(new Descriptor(s, __gssNode, i, DummyNode.getInstance()));
					}
				
				// nonterminal.getFirstSlots().forEach(s -> scheduleDescriptor(new Descriptor(s, __gssNode, i, DummyNode.getInstance())));
				
			} else {
				log.trace("GSSNode found: %s",  gssNode);
				createGSSEdge(returnSlot, u, node, gssNode, env); // Record environment on the edge
			}
			return gssNode;
		}
		
		GSSNodeData<Object> data = new GSSNodeData<>(evaluate(arguments, env));
		
		GSSNode gssNode = nonterminal.hasGSSNode(i, data);
		if (gssNode == null) {
			
			gssNode = nonterminal.getGSSNode(i, data);
			log.trace("GSSNode created: %s(%s)",  gssNode, data);
			
			if (env.isEmpty()) createGSSEdge(returnSlot, u, node, gssNode);
			else createGSSEdge(returnSlot, u, node, gssNode, env);
			
			Environment newEnv = getEmptyEnvironment().declare(nonterminal.getParameters(), data.getValues());
			
			final GSSNode __gssNode = gssNode;
			
			for (BodyGrammarSlot s : nonterminal.getFirstSlots()) {
				
				setEnvironment(newEnv);
				
				if (s.getLabel() != null)
					this.getEvaluatorContext().declareVariable(String.format(Expression.LeftExtent.format, s.getLabel()), i);
				
				if (!s.getConditions().execute(getInput(), __gssNode, i, getEvaluatorContext()))
					scheduleDescriptor(new org.iguana.datadependent.descriptor.Descriptor(s, __gssNode, i, DummyNode.getInstance(), getEnvironment()));
			}
			
			// nonterminal.getFirstSlots().forEach(s -> scheduleDescriptor(new org.jgll.datadependent.descriptor.Descriptor(s, __gssNode, i, DummyNode.getInstance(), newEnv)));
			
		} else {
			log.trace("GSSNode found: %s",  gssNode);
			if (env.isEmpty()) createGSSEdge(returnSlot, u, node, gssNode);
			else createGSSEdge(returnSlot, u, node, gssNode, env);		
		}
		return gssNode;
	}
	
	private void createGSSEdge(BodyGrammarSlot returnSlot, GSSNode destination, NonPackedNode w, GSSNode source, Environment env) {
		NewGSSEdgeImpl edge = new org.iguana.datadependent.gss.NewGSSEdgeImpl(returnSlot, w, destination, env);
		
		if (source.getGSSEdge(edge)) {
			countGSSEdges++;
			log.trace("GSS Edge created: %s from %s to %s with %s", returnSlot, source, destination, env);

			for (NonPackedNode z : source.getPoppedElements()) {
				Descriptor descriptor = edge.addDescriptor(this, source, z.getRightExtent(), z);
				if (descriptor != null) {
					scheduleDescriptor(descriptor);
				}				
			}
		}
	}
	
	@Override
	public <T> NonterminalNode getNonterminalNode(EndGrammarSlot slot, NonPackedNode child, GSSNodeData<T> data, Object value) {
		return sppfLookup.getNonterminalNode(slot, child, data, value);
	}
	
	@Override
	public <T> NonterminalNode hasNonterminalNode(EndGrammarSlot slot, NonPackedNode child, GSSNodeData<T> data, Object value) {
		return sppfLookup.hasNonterminalNode(slot, child, data, value);
	}
	
	public IntermediateNode getIntermediateNode(BodyGrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild, Environment env) {
		return sppfLookup.getIntermediateNode(slot, leftChild, rightChild, env);
	}
	
	@Override
	public NonPackedNode getIntermediateNode2(BodyGrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild, Environment env) {
		return sppfLookup.getIntermediateNode2(slot, leftChild, rightChild, env);
	}
	
	@Override
	public IntermediateNode hasIntermediateNode(BodyGrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild, Environment env) {
		return sppfLookup.hasIntermediateNode(slot, leftChild, rightChild, env);
	}
	
	public <T> NonPackedNode getNode(GrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild, Environment env) {
		return sppfLookup.getNode(slot, leftChild, rightChild, env);
	}
	
	@Override
	public <T> NonPackedNode hasNode(GrammarSlot slot, NonPackedNode leftChild, NonPackedNode rightChild, Environment env) {
		return sppfLookup.hasNode(slot, leftChild, rightChild, env);
	}

	@Override
	public GrammarGraph getGrammarGraph() {
		return grammarGraph;
	}
}