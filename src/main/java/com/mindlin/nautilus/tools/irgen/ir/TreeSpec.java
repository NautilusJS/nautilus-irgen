package com.mindlin.nautilus.tools.irgen.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.Logger;
import com.mindlin.nautilus.tools.irgen.Utils;

public class TreeSpec implements Orderable {
	public TypeElement source;
	public Logger logger;
	public List<String> parents = new ArrayList<>();
	public Map<String, Logger> kinds;
	public List<GetterSpec> getters = new ArrayList<>();
	
	public TreeSpec() {
	}
	
	public String getName() {
		return this.source.getQualifiedName().toString();
	}
	
	public void sortGetters() {
		this.getters = Orderable.sorted(this.getters);
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
	public Set<String> getBefore() {
		return new HashSet<>(this.parents);
	}

	@Override
	public Set<String> getAfter() {
		return Collections.emptySet();
	}

	@Override
	public String getOrderName() {
		return this.getName();
	}

	public static class GetterSpec implements Orderable {
		public ExecutableElement target;
		public AnnotationMirror invoker;
		public TypeMirror type;
		public String fName;
		public String name;
		public boolean override;
		public int overrideTarget = -1;
		
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
}
