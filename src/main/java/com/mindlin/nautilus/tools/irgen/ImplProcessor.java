package com.mindlin.nautilus.tools.irgen;

import static com.mindlin.nautilus.tools.irgen.Utils.invoke;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.mindlin.nautilus.tools.irgen.ir.ClassSpec;
import com.mindlin.nautilus.tools.irgen.ir.CtorSpec;
import com.mindlin.nautilus.tools.irgen.ir.FieldSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.CollectionGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.NarrowGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.OverrideMethod;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.SimpleGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.ParameterSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;

public class ImplProcessor extends AnnotationProcessorBase {
	final Map<String, TreeSpec> niSpecs;
	final Map<String, TreeSpec> iSpecs;
	final Map<String, TreeImplSpec> impls;
	
	public ImplProcessor(ProcessingEnvironment procEnv, DeclaredType annotation, RoundEnvironment roundEnv, Map<String, TreeSpec> niSpecs, Map<String, TreeSpec> iSpecs, Map<String, TreeImplSpec> impls) {
		super(procEnv, annotation, roundEnv);
		this.niSpecs = niSpecs;
		this.iSpecs = iSpecs;
		this.impls = impls;
	}
	
	public String getResolvedSuperclass(Collection<String> parents) {
		for (String parent : parents)
			if (this.impls.containsKey(parent))
				return this.impls.get(parent).getQualifiedName();
		return IRTypes.ABSTRACT_BASE;
	}
	
	protected FieldSpec getterToField(GetterSpec getter) {
		return new FieldSpec(Utils.modifiers(Modifier.PROTECTED, Modifier.FINAL), getter.type, getter.fName);
	}
	
	protected void resolveGetters(List<GetterSpec> getters, Map<String, GetterSpec> gettersMap, TreeSpec spec) {
		//TODO: fix parent traversal order
		for (String parent : spec.parents) {
			TreeSpec parentSpec = this.niSpecs.get(parent);
			if (parentSpec == null)
				continue;
			this.resolveGetters(getters, gettersMap, parentSpec);
		}
		
		for (GetterSpec getter : spec.getters) {
			GetterSpec old = gettersMap.put(getter.name, getter);
			if (old != null) {
				getters.remove(old);
				getLogger().warn("Override getter '%s' on %s", getter.name, spec.getName());
			}
			getters.add(getter);
		}
	}
	
	protected List<GetterSpec> gettersToDeclare(List<GetterSpec> getters, TreeImplSpec parent) {
		if (parent == null)
			return new ArrayList<>(getters);
		
		for (GetterSpec getter : getters) {
			
		}
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
		for (String parent : spec.parents) {
			TreeSpec parentSpec = this.niSpecs.get(parent);
			if (parentSpec == null) {
				continue;
			}
			this.buildFields(fields, parentSpec);
		}
		
		for (GetterSpec getter : spec.getters) {
			FieldSpec field = this.getterToField(getter);
			if (fields.put(field.name, field) != null)
				getLogger().warn("Duplicate field '%s' on %s", field.name, spec.getName());
		}
	}
	
	protected MethodSpec makeGetter(TreeImplSpec impl, GetterSpec getter, TreeImplSpec parent) {
		int flags = 0;
		if (getter.hash)
			flags |= TreeImplSpec.MF_HASH;
		if (getter.compare)
			flags |= TreeImplSpec.MF_EQUIV;
		
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
				flags |= TreeImplSpec.MF_GOBJECT;
				return new CollectionGetterSpec(flags, getter.name, field, true);
			} else {
				if (IRTypes.isPrimitive(getter.type))
					flags |= TreeImplSpec.MF_GPRIMITIVE;
				else
					flags |= TreeImplSpec.MF_GOBJECT;
				
				return new SimpleGetterSpec(flags, getter.name, field);
			}
		} else if (Objects.equals(invName, IRTypes.TREE_CHILD)) {
			flags |= TreeImplSpec.MF_GCHILD;
			return new SimpleGetterSpec(flags, getter.name, field);
		} else if (Objects.equals(invName, IRTypes.TREE_CHILDREN)) {
			flags |= TreeImplSpec.MF_GCHILDREN;
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
		impl.parent = this.getResolvedSuperclass(spec.parents);
		TreeImplSpec parentSpec = this.impls.get(impl.parent);
		impl.parentIfaces.add(spec.getName());
		
		// Resolve getters
		Map<String, GetterSpec> gettersMap = new HashMap<>();
		List<GetterSpec> resolvedGetters = new LinkedList<>();// LinkedList because we might be removing values from the middle
		this.resolveGetters(resolvedGetters, gettersMap, spec);
		
		getLogger().warn("Getters for %s: %s", spec.getName(), resolvedGetters);
		
		// Determine which fields we have to declare
		// We only declare a field if it doesn't exist in our parent
		
		if (parentSpec != null)
			impl.fields.putAll(parentSpec.fields);
		this.buildFields(impl.fields, spec);
		// Drop fields that exist in superclass
		Map<String, FieldSpec> localFields;
		if (parentSpec != null) {
			localFields = new HashMap<>(impl.fields);
			for (String parentField : parentSpec.fields.keySet())
				localFields.remove(parentField);
		} else {
			localFields = impl.fields;
		}
		impl.declaredFields.addAll(localFields.values());
		
		// Generate getter methods
		for (GetterSpec getter : resolvedGetters) { 
			getLogger().warn("Add getter %s to %s", getter.name, spec.getName());
			MethodSpec method = this.makeGetter(impl, getter, parentSpec);
			if (method == null)
				continue;
			impl.declaredMethods.add(method);	
		}
		
		// Generate hash & equivalence methods
		impl.buildMethods();
		
		// Generate constructors
		List<ParameterSpec> superParams = parentSpec == null ? Collections.emptyList() : parentSpec.params;
		impl.constructors.add(impl.new ForwardingCtorSpec(superParams));
//		impl.constructors.add(new TreeImplSpec.RangeMergeCtorSpec(Utils.map(superArgs, arg -> new ParameterSpec()));
		
		return impl;
	}
}
