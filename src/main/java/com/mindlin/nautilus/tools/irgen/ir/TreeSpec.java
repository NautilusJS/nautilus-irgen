package com.mindlin.nautilus.tools.irgen.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.Logger;
import com.mindlin.nautilus.tools.irgen.Utils;

public class TreeSpec implements Orderable<TypeName> {
	public TypeElement source;
	public Kind kind;
	public Logger logger;
	public List<TypeName> parents = new ArrayList<>();
	public Map<String, Logger> kinds;
	public List<GetterSpec> getters;
	public Map<String, GetterSpec> resolvedGetters;
	
	public TreeSpec() {
	}
	
	public TypeName getName() {
		return ClassName.get(this.source);
	}
	
	@Override
	public boolean isFirst() {
		return false;
	}

	@Override
	public boolean isLast() {
		return false;
	}

	@Override
	public Set<TypeName> getBefore() {
		return new HashSet<>(this.parents);
	}

	@Override
	public Set<TypeName> getAfter() {
		return Collections.emptySet();
	}

	@Override
	public TypeName getOrderName() {
		return this.getName();
	}
	
	public Logger getLogger() {
		return this.logger;
	}
	
	public List<TreeSpec> getAllParents(Function<TypeName, TreeSpec> lookup) {
		Deque<TreeSpec> queue = new LinkedList<>();
		Set<TreeSpec> visited = new HashSet<>();
		queue.add(this);
		visited.add(this);
		
		while (!queue.isEmpty()) {
			TreeSpec parent = queue.pop();
			for (TypeName gpName : parent.parents) {
				TreeSpec gp = lookup.apply(gpName);
				if (gp == null || !visited.add(gp))
					continue;
				queue.add(gp);
			}
		}
		return Orderable.sorted(visited);
	}

	public static class GetterSpec implements Orderable<String> {
		public ExecutableElement target;
		public AnnotationMirror invoker;
		public TypeMirror type;
		/** Wrapped field name */
		public String fName;
		/** Method name */
		public String name;
		public boolean override;
		public String boundValue = null;
		
		// Usage
		public boolean hash = true;
		public boolean compare = true;
		
		public boolean optional;
		
		// Ordering
		public boolean first;
		public String before;
		public String after;
		public boolean last;
		
		@Override
		public String toString() {
			List<String> props = new ArrayList<>();
			if (this.override)
				props.add("override");
			if (this.optional)
				props.add("optional");
			if (this.first)
				props.add("first");
			if (this.before != null)
				props.add("before=" + this.before);
			if (this.after != null)
				props.add("after=" + this.after);
			if (this.last)
				props.add("last");
			
			return String.format("GetterSpec{target=%s, invoker=%s, type=%s, fName=%s, name=%s, props=%s, type=%s}", this.target, this.invoker, this.type, this.fName, this.name, props, this.type);
		}
		
		public boolean checkReturnNonNull() {
			return Utils.isNonNull(this.type, true);
		}
		
		public boolean returnImmutable() {
			//TODO: finish
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isFirst() {
			return this.first;
		}

		@Override
		public boolean isLast() {
			return this.last;
		}

		@Override
		public Set<String> getBefore() {
			if (this.after == null)
				return Collections.emptySet();
			return new HashSet<>(Arrays.asList(this.after));
		}

		@Override
		public Set<String> getAfter() {
			if (this.before == null)
				return Collections.emptySet();
			return new HashSet<>(Arrays.asList(this.before));
		}

		@Override
		public String getOrderName() {
			return this.fName;
		}
	}
	
	public static enum Kind {
		NO_IMPL,
		ADT,
		IMPL,
	}
}
