package com.mindlin.nautilus.tools.irgen.util;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface Orderable<N> {
	public static <N, T extends Orderable<N>> List<T> sorted(Collection<T> elements) {
		return sorted(elements, Orderable::selectAny);
	}
	
	public static <T> T selectAny(Collection<? extends T> candidates) {
		for (T candidate : candidates)
			return candidate;
		throw new IllegalArgumentException();
	}
	
	public static <N, T extends Orderable<N>> List<T> sorted(Collection<T> elements, Function<Collection<? extends T>, ? extends T> selector) {
		// Put first/last
		T first = null;
		T last = null;
		for (T element : elements) {
			if (element.isFirst())
				first = element;
			if (element.isLast())
				last = element;
		}
		
		Set<T> remaining = new HashSet<>(elements);
		if (first != null)
			remaining.remove(first);
		if (last != null)
			remaining.remove(last);
		
		ConcurrentMap<N, Wrapper<T>> segments = new ConcurrentHashMap<>();
		// Insert all segments
		elements.parallelStream()
			.forEach(elem -> segments.put(elem.getOrderName(), new Wrapper<>(elem)));
		
		// Insert before/after edges
		for (T elem : remaining) {
			Wrapper<T> segment = segments.get(elem.getOrderName());
			
			for (N beforeValue : elem.getBefore()) {
				Wrapper<T> before = segments.get(beforeValue);
				if (before == null)
					continue;
				segment.before.add(before);
				before.after.add(segment);
			}
			
			for (N afterValue : elem.getAfter()) {
				Wrapper<T> after = segments.get(afterValue);
				if (after == null)
					continue;
				segment.after.add(after);
				after.before.add(segment);
			}
		}
		// Insert 'last' edges
		if (last != null) {
			Wrapper<T> lastSegment = segments.get(last.getOrderName());
			remaining.stream()
				.map(Orderable::getOrderName)
				.map(segments::get)
				.forEach(segment -> {
					segment.after.add(lastSegment);
					lastSegment.before.add(segment);
				});
		}
		
		List<T> result = new ArrayList<>(elements.size());
		if (first != null) {
			result.add(first);
			Wrapper<T> segment = segments.remove(first.getOrderName());
			segments.values().forEach(s -> s.before.remove(segment));
		}
		
		while (!segments.isEmpty()) {
			Set<Wrapper<T>> available = segments.values()
				.parallelStream()
				.filter(segment -> segment.before.isEmpty())
				.collect(Collectors.toSet());
			
			Wrapper<T> segment;
			if (available.isEmpty()) {
				throw new IllegalArgumentException("Topo failed (remaining: " + segments.keySet() + ")");
			} else if (available.size() == 1) {
				// Pick only value
				segment = selectAny(available);
			} else {
				Collection<? extends T> candidateProxy = new AbstractCollection<T>() {
					@Override
					public Iterator<T> iterator() {
						Iterator<Wrapper<T>> delegate = available.iterator();
						return new Iterator<T>() {
							@Override
							public boolean hasNext() {
								return delegate.hasNext();
							}

							@Override
							public T next() {
								return delegate.next().value;
							}
						};
					}
					
					@Override
					public int size() {
						return available.size();
					}
				};
				
				T selected = selector.apply(candidateProxy);
				Objects.requireNonNull(selected, "Selected null");
				segment = segments.getOrDefault(selected.getOrderName(), null);
				Objects.requireNonNull(segment, () -> String.format("Unable to find segment for selection %s from %d: %s", selected.getOrderName(), segments.size(), segments.keySet()));
				if (!available.contains(segment))
					throw new IllegalArgumentException();
			}
			
			N name = segment.value.getOrderName();
			segments.remove(name);
			segment.after.parallelStream()
					.forEach(s -> s.before.remove(segment));
			result.add(segment.value);
		}
		
		return result;
	}
	
	public static <N, T extends Orderable<N>> List<T> sorted2(Collection<T> elements, Function<Collection<? extends T>, ? extends T> selector) {
		Map<N, T> nameLookup = new HashMap<>();
		DAG<N, T> graph = new DAG<>();
		
		// Add all nodes to graph
		for (T element : elements) {
			N name = element.getOrderName();
			T prev = nameLookup.put(name, element);
			if (prev != null)
				throw new IllegalArgumentException(String.format("Duplicate name %s", name));
			graph.add(name, element);
		}
		
		// Add all edges to graph
		for (T element : elements) {
			N name = element.getOrderName();
			if (element.isFirst()) {
				
			}
			if (element.isLast()) {
				
			}
			for (N before : element.getBefore())
				graph.connect(before, name);
			for (N after : element.getAfter())
				graph.connect(name, after);
		}
		
		return null;
	}
	
	static class W2<N, T extends Orderable<N>> {
		final N name;
		final T value;
		protected Set<N> before;
		protected Set<N> after;
		
		public W2(T value) {
			this.name = value.getOrderName();
			this.value = value;
			this.before = new HashSet<>(value.getBefore());
			this.after = new HashSet<>(value.getAfter());
		}
	}
	
	static class DAG<T, V> {
		protected Map<T, Node> nodes = new HashMap<>();
		
		public void add(T key, V value) {
			nodes.computeIfAbsent(key, k -> new Node(key, value));
		}
		
		public V remove(T key) {
			Node n = nodes.remove(key);
			for (Node src : n.inEdges)
				src.outEdges.remove(n);
			for (Node dst : n.outEdges)
				dst.inEdges.remove(n);
			return n.value;
		}
		
		public void connect(T source, T sink) {
			Node n1 = nodes.get(source);
			Node n2 = nodes.get(sink);
			n1.outEdges.add(n2);
			n2.inEdges.add(n1);
		}
		
		public void disconnect(T source, T sink) {
			Node n1 = nodes.get(source);
			Node n2 = nodes.get(sink);
			n1.outEdges.add(n2);
			n2.inEdges.add(n1);
		}
		
		class Node {
			final T name;
			final V value;
			final Set<Node> inEdges = new HashSet<>();
			final Set<Node> outEdges = new HashSet<>();
			public Node(T name, V value) {
				this.name = name;
				this.value = value;
			}
		}
	}
	
	static class Wrapper<T extends Orderable<?>> {
		final T value;
		protected Set<Wrapper<T>> before = new HashSet<>();
		protected Set<Wrapper<T>> after = new HashSet<>();
		public Wrapper(T value) {
			this.value = value;
		}
	}
	
	boolean isFirst();
	boolean isLast();
	
	/**
	 * @return Names that should come before
	 */
	Set<N> getBefore();
	/**
	 * @return Names that should come after
	 */
	Set<N> getAfter();
	
	N getOrderName();
}
