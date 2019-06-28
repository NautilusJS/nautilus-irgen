package com.mindlin.nautilus.tools.irgen;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import com.mindlin.nautilus.tools.irgen.ir.Orderable;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;

public class TreeBuilderProcessor extends AnnotationProcessorBase {
	protected static TreeSpec.@Nullable Kind getKind(@Nullable DeclaredType annotation) {
		if (annotation == null)
			return null;
		try {
			switch (Utils.getName(annotation)) {
				case IRTypes.TREE_IMPL:
					return TreeSpec.Kind.IMPL;
				case IRTypes.TREE_NOIMPL:
					return TreeSpec.Kind.NO_IMPL;
				case IRTypes.TREE_ADT:
					return TreeSpec.Kind.ADT;
				default:
					return null;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	private final TreeSpec.@Nullable Kind kind;
	
	public TreeBuilderProcessor(ProcessingEnvironment procEnv, DeclaredType annotation) {
		this(procEnv, annotation, getKind(annotation));
	}
	
	public TreeBuilderProcessor(ProcessingEnvironment procEnv, TreeSpec.Kind kind) {
		this(procEnv, null, kind);
	}
	
	public TreeBuilderProcessor(ProcessingEnvironment procEnv, DeclaredType annotation, TreeSpec.Kind kind) {
		super(procEnv, annotation);
		this.kind = kind;
	}
	
	protected TreeSpec.Kind getKind() {
		return this.kind;
	}
	
	@Override
	protected boolean matchAnnotation(DeclaredType annotation) {
		if (this.annotation != null)
			return super.matchAnnotation(annotation);
		// Fallback if we just have the TreeSpec.Kind
		TreeSpec.Kind kind = getKind(annotation);
		return this.kind == kind;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Logger> getValues(Logger base, AnnotationMirror mirror) {
		Logger logger = base.withSite(mirror);
		
		Map<String, Logger> result = new HashMap<>();
		AnnotationValue value = Utils.derefValues(mirror.getElementValues()).get("value");
		if (value == null)
			return result;
		
		List<? extends AnnotationValue> values;
		try {
			values = (List<? extends AnnotationValue>) value.getValue();
		} catch (ClassCastException e) {
			logger.withValue(value).error("Unexpected type for Impl#value(): %s", e);
			return result;
		}
		
		for (AnnotationValue impl : values) {
			VariableElement elem;
			Logger local = logger.withValue(impl);
			try {
				elem = (VariableElement) impl.getValue();
			} catch (ClassCastException e) {
				local.error("Unexpected value for Impl#value()");
				continue;
			}
			result.put(elem.getSimpleName().toString(), local);
		}
		return result;
	}
	
	protected Map<String, Logger> getValues(Logger base, List<AnnotationMirror> mirrors) {
		Map<String, Logger> result = new HashMap<>();
		for (AnnotationMirror mirror : mirrors)
			result.putAll(this.getValues(base, mirror));
		return result;
	}
	
	protected Collection<TypeName> getParents(TypeElement target) {
		Collection<TypeName> parents = Utils.stream(target.getInterfaces())
			.map(iface -> (iface instanceof DeclaredType ? (DeclaredType) iface : null))
			.filter(Objects::nonNull)
			.map(TypeName::wrap)
			.collect(Collectors.toList());
		// Remove Tree because it isn't real
		parents.remove(IRTypes.TREE);
		return parents;
	}
	
	protected List<GetterSpec> buildGetters(@NonNull TypeElement target) {
		GetterSpecFactory getterFactory = new GetterSpecFactory(this.procEnv.getElementUtils(), getLogger());
		List<GetterSpec> getters = Utils.stream(ElementFilter.methodsIn(target.getEnclosedElements()))
				.map(getterFactory)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		
		// Toposort getters
		try {
			getters = Orderable.sorted(getters);
		} catch (IllegalArgumentException e) {
			// Toposort failed
			getLogger().withTarget(target).error("Error sorting getters: " + e.getMessage());
		}
		
		return getters;
	}

	public TreeSpec buildTreeSpec(TypeElement target) {
		Logger logger = getLogger().withTarget(target);
		
		if (!target.getTypeParameters().isEmpty())
			logger.warn("Type parameters not supported");
		
		List<AnnotationMirror> mirrors = this.getMirrors(target);
		
		TreeSpec spec = new TreeSpec();
		spec.source = target;
		spec.kind = this.getKind();
		spec.kinds = this.getValues(logger, mirrors);
		
		spec.parents.addAll(this.getParents(target));
		if (Utils.isVerbose())
			getLogger().note("Parent of %s: %s", target.getQualifiedName(), spec.parents);
		
		spec.getters = this.buildGetters(target);
		
		if (Utils.isVerbose()) {
			int i = 0;
			for (GetterSpec getter : spec.getters)
				getLogger().withTarget(getter.target).warn("Getter %d: %s", i++, getter);
		}
		
		return spec;
	}
}
