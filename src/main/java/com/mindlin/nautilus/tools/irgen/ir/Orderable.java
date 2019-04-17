package com.mindlin.nautilus.tools.irgen.ir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public interface Orderable {
	public static <T extends Orderable> List<T> sorted(Collection<T> elements) {
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
		
		ConcurrentMap<String, Wrapper<T>> segments = new ConcurrentHashMap<>();
		// Insert all segments
		elements.parallelStream()
			.forEach(elem -> segments.put(elem.getOrderName(), new Wrapper<>(elem)));
		
		// Insert before/after edges
		for (T elem : remaining) {
			Wrapper<T> segment = segments.get(elem.getOrderName());
			
			for (String beforeValue : elem.getBefore()) {
				Wrapper<T> before = segments.get(beforeValue);
				if (before == null)
					continue;
				segment.before.add(before);
				before.after.add(segment);
			}
			
			for (String afterValue : elem.getAfter()) {
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
			Wrapper<T> s = segments.values()
				.parallelStream()
				.filter(segment -> segment.before.isEmpty())
				.findAny()
				.orElse(null);
			if (s == null) {
				throw new IllegalArgumentException("Topo failed (remaining: " + segments.keySet() + ")");
			}
			String name = s.value.getOrderName();
			segments.remove(name);
			s.after.parallelStream()
					.forEach(segment -> segment.before.remove(s));
			result.add(s.value);
		}
		
		return result;
	}
	
	static class Wrapper<T extends Orderable> {
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
	Set<String> getBefore();
	/**
	 * @return Names that should come after
	 */
	Set<String> getAfter();
	
	String getOrderName();
}
