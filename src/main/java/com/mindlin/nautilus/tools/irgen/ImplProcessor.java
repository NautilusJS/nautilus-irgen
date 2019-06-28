package com.mindlin.nautilus.tools.irgen;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.mindlin.nautilus.tools.irgen.ir.AbstractTreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.ClassName;
import com.mindlin.nautilus.tools.irgen.ir.FieldSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.CollectionGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.NarrowGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.SimpleGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeImplSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;

public class ImplProcessor extends AnnotationProcessorBase {
	final Map<String, TreeSpec> specs;
	final Map<String, TreeImplSpec> impls;
	
	public ImplProcessor(ProcessingEnvironment procEnv, DeclaredType annotation, Map<String, TreeSpec> specs, Map<String, TreeImplSpec> impls) {
		super(procEnv, annotation);
		this.specs = specs;
		this.impls = impls;
	}
	
	protected TreeImplSpec getSuperclassImpl(Collection<? extends TypeName> parents) {
		return parents.stream()
				.map(Object::toString)
				.map(this.impls::get)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}
	
	@Deprecated
	protected ClassName getResolvedSuperclass(Collection<? extends TypeName> parents) {
		return parents.stream()
				.map(Object::toString)//TODO: fix?
				.map(this.impls::get)
				.filter(Objects::nonNull)
				.map(TreeImplSpec::getClassName)
				.findFirst()
				.orElse(IRTypes.ABSTRACT_BASE);
	}
	
	protected FieldSpec getterToField(GetterSpec getter) {
		return new FieldSpec(Modifier.PROTECTED | Modifier.FINAL, getter.type, getter.fName);
	}
	
	protected @Nullable TypeElement resolveSource(@NonNull TypeName parent) {
		String parentName = parent.toString();//TODO: fix?
		TreeImplSpec parentImpl = this.impls.get(parentName);
		if (parentImpl != null)
			return parentImpl.source;
		TreeSpec parentSpec = this.specs.get(parentName);
		if (parentSpec != null)
			return parentSpec.source;
		return null;
	}
	
	protected List<GetterSpec> resolveGetters(List<TreeSpec> parents, TreeSpec spec) {
		Map<String, GetterSpec> gettersMap = new LinkedHashMap<>();
		Map<String, String> overrideWarnings = new HashMap<>();
		for (TreeSpec parent : parents) {
			for (GetterSpec getter : parent.getters) {
				GetterSpec old = gettersMap.put(getter.name, getter);
				if (old != null) {
					if (getter.override) {
						overrideWarnings.remove(getter.name);
					} else {
						overrideWarnings.put(getter.name, String.format("Override getter '%s' on %s from %s -> %s (%s)", getter.name, spec.getName(), parent.source.getSimpleName(), old.target.getEnclosingElement().getSimpleName(), getter.override));
					}
				}
			}
			getters.add(getter);
		}
		
		for (String warning : overrideWarnings.values())
			getLogger().warn(warning);
		
		return new ArrayList<>(gettersMap.values());
	}
	
	protected List<GetterSpec> gettersToDeclare(List<GetterSpec> getters, TreeImplSpec parent) {
		if (parent == null)
			return new ArrayList<>(getters);
		
		return getters.stream()
				.filter(getter -> {
					GetterSpec parentGetter = parent.getters.get(getter.name);
					if (parentGetter == null)
						return true;// No inheritance
					if (getter == parentGetter)
						return true;// Overridden
					return false;
				})
				.collect(Collectors.toList());
	}
	
	protected void buildFields(Map<String, FieldSpec> fields, TreeSpec spec) {
		// Recurse through parents first, so they get overridden.
		for (TypeName parent : spec.parents) {
			TreeSpec parentSpec = this.specs.get(parent.toString());//TODO: fix?
			if (parentSpec == null) {
				continue;
			}
			this.buildFields(fields, parentSpec);
		}
		
		for (GetterSpec getter : spec.getters) {
			FieldSpec field = this.getterToField(getter);
			if (getter.override)
				fields.put(field.name, field);
			else if (fields.put(field.name, field) != null)
				getLogger().warn("Duplicate field '%s' on %s (no override)", field.name, spec.getName());
		}
	}
	
	protected MethodSpec makeGetter(TreeImplSpec impl, GetterSpec getter, TreeImplSpec parent) {
		int flags = 0;
		if (getter.hash)
			flags |= AbstractTreeSpec.MF_HASH;
		if (getter.compare)
			flags |= AbstractTreeSpec.MF_EQUIV;
		
		if (getter.optional) {
			//TODO: fix
		}
		
		if (parent != null && parent.getters.containsKey(getter.name))
			return this.makeOverrideGetter(flags, getter, parent.getters.get(getter.name));
		
		// Generate field getter
		FieldSpec field = impl.fields.get(getter.fName);
		
		String invName = Utils.getName(getter.invoker.getAnnotationType());
		if (Objects.equals(invName, IRTypes.TREE_PROPERTY)) {
			if (IRTypes.isCollection(getter.type)) {
				flags |= AbstractTreeSpec.MF_GOBJECT;
				return new CollectionGetterSpec(flags, getter.name, field, true);
			} else {
				if (IRTypes.isPrimitive(getter.type))
					flags |= AbstractTreeSpec.MF_GPRIMITIVE;
				else
					flags |= AbstractTreeSpec.MF_GOBJECT;
				
				return new SimpleGetterSpec(flags, getter.name, field);
			}
		} else if (Objects.equals(invName, IRTypes.TREE_CHILD)) {
			flags |= AbstractTreeSpec.MF_GCHILD;
			return new SimpleGetterSpec(flags, getter.name, field);
		} else if (Objects.equals(invName, IRTypes.TREE_CHILDREN)) {
			flags |= AbstractTreeSpec.MF_GCHILDREN;
			return new CollectionGetterSpec(flags, getter.name, field, true);
		} else {
			throw new IllegalArgumentException("Unknown invName: " + invName);
		}
	}
	
	protected MethodSpec makeOverrideGetter(int flags, GetterSpec local, GetterSpec parent) {
		//TODO: add null check in getter?
		if (local == parent)
			return null;
		return new NarrowGetterSpec(flags, local.name, local.type);
	}
	
	public TreeImplSpec buildTreeImpl(TypeElement element, TreeSpec spec) {
		String implName = element.getSimpleName() + "Impl";
		TreeImplSpec impl = new TreeImplSpec(element, implName);
		
		// Resolve heritage
		impl.baseType = spec.getName();
		TreeImplSpec resolvedParent = impl.resolvedParent = getSuperclassImpl(spec.parents);
		impl.parent = impl.resolvedParent == null ? IRTypes.ABSTRACT_BASE : resolvedParent.getClassName();
		impl.parentIfaces.add(spec.getName());
		
		// Add sources for Filer dependency stuff
		spec.parents.stream()
				.map(this::resolveSource)
				.filter(Objects::nonNull)
				.forEach(impl.sources::add);
		
		// Resolve getters
		List<TreeSpec> parents = spec.getAllParents(tn -> this.specs.get(tn.toString()));
		getLogger().warn("Ordered parents for %s: %s", spec.getName(), Utils.map(parents, parent -> parent.getName()));
		Map<String, GetterSpec> gettersMap = new HashMap<>();
		List<GetterSpec> resolvedGetters = new LinkedList<>();// LinkedList because we might be removing values from the middle
		this.resolveGetters(resolvedGetters, gettersMap, parents, spec);
		
		if (Utils.isVerbose())
			getLogger().warn("Getters for %s: %s", spec.getName(), resolvedGetters);
		
		// Determine which fields we have to declare
		// We only declare a field if it doesn't exist in our parent
		
		if (parentSpec != null)
			impl.fields.putAll(parentSpec.fields);
		this.buildFields(impl.fields, spec);
		// Drop fields that exist in superclass
		Map<String, FieldSpec> localFields;
		if (parentSpec != null) {
			localFields = new LinkedHashMap<>(impl.fields);
			for (String parentField : parentSpec.fields.keySet())
				localFields.remove(parentField);
		} else {
			localFields = impl.fields;
		}
		impl.declaredFields.addAll(localFields.values());
		
		// Generate getter methods
		for (GetterSpec getter : resolvedGetters) {
			if (Utils.isVerbose())
				getLogger().warn("Add getter %s to %s", getter.name, spec.getName());
			MethodSpec method = this.makeGetter(impl, getter);
			if (method == null) {
				getterLogger.warn("Failed to generate method from spec on %s", implName);
				continue;
			}
			impl.declaredMethods.add(method);	
		}
		
		// Generate hash & equivalence methods
		impl.buildMethods();
		
		// Generate constructors
		impl.constructors.add(impl.new ForwardingCtorSpec());
		impl.constructors.add(impl.new RangeMergeCtorSpec());
		
		return impl;
	}
}
