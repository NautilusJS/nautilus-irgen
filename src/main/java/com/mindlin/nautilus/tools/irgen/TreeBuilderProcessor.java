package com.mindlin.nautilus.tools.irgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;

public class TreeBuilderProcessor extends AnnotationProcessorBase {
	
	public TreeBuilderProcessor(ProcessingEnvironment procEnv, DeclaredType annotation, RoundEnvironment roundEnv) {
		super(procEnv, annotation, roundEnv);
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
	
	protected Collection<String> getParents(TypeElement target) {
		Collection<String> parents = Utils.stream(target.getInterfaces())
			.map(iface -> (iface instanceof DeclaredType ? (DeclaredType) iface : null))
			.filter(Objects::nonNull)
			.map(Utils::getName)
			.collect(Collectors.toList());
		// Remove Tree because it isn't real
		parents.remove(IRTypes.TREE_CLASS);
		return parents;
	}
	
	public GetterSpec buildGetterSpec(ExecutableElement method) {
		Elements elements = this.procEnv.getElementUtils();
		GetterSpec result = new GetterSpec();
		result.target = method;
		result.name = method.getSimpleName().toString();
		
		
		for (AnnotationMirror mirror : elements.getAllAnnotationMirrors(method)) {
			String name = Utils.getName(mirror.getAnnotationType());
			Map<String, ? extends AnnotationValue> values = Utils.derefValues(mirror.getElementValues());
			switch (name) {
				case IRTypes.OVERRIDE:
					result.override = true;
					continue;
				case IRTypes.ORDERING:
					//TODO: fix
					break;
				case IRTypes.ORDERING_BEFORE:
					try {
						result.before = values.get("value").getValue().toString();
					} catch (NullPointerException e) {
						getLogger().withSite(mirror).error("Missing value: %s", e);
					}
					continue;
				case IRTypes.ORDERING_AFTER:
					try {
						result.after = values.get("value").getValue().toString();
					} catch (NullPointerException e) {
						getLogger().withTarget(method, mirror).error("Missing value: %s", e);
					}
					continue;
				case IRTypes.ORDERING_FIRST:
					result.first = true;
					continue;
				case IRTypes.ORDERING_LAST:
					result.last = true;
					continue;
				case IRTypes.OPTIONAL:
					//TODO: get default value
					result.optional = true;
					continue;
				case IRTypes.TREE_PROPERTY:
				case IRTypes.TREE_CHILD:
				case IRTypes.TREE_CHILDREN: {
					if (result.invoker != null) {
						String message = "Duplicate invocation";
						logger.withSite(result.invoker).error(message);
						logger.error(message);
						return null;
					}
					result.invoker = mirror;
					if (values.containsKey("name")) {
						String fName = values.get("name").getValue().toString();
						if (fName != null && !Objects.equals("__infer__", fName))
							result.fName = fName;
					}
					if (values.containsKey("hash")) {
						boolean hash = Boolean.valueOf(values.get("hash").getValue().toString());
						result.hash = hash;
					}
					if (values.containsKey("compare")) {
						boolean compare = Boolean.valueOf(values.get("compare").getValue().toString());
						result.compare = compare;
					}
					
					continue;
				}
				default:
					break;
			}
			//getLogger().note("Got mirror %s on %s", name, method);
		}
		
		if (result.invoker == null)
			return null;
		
		result.type = method.getReturnType();
		
		if (!method.getParameters().isEmpty()) {
			getLogger().withTarget(method).error("Not a getter (has %d arguments)", method.getParameters().size());
			return null;
		}
		
		//TODO: fix
		result.override = false;
		
		if (result.fName == null) {
			String fName = result.name;
			// Drop get/is
			if (fName.startsWith("get") && fName.length() > 3)
				fName = Character.toLowerCase(fName.charAt(3)) + fName.substring(4);
			else if (fName.startsWith("is") && fName.length() > 2)
				fName = Character.toLowerCase(fName.charAt(2)) + fName.substring(3);
			result.fName = Utils.escapeIdentifier(fName);
		}
		
		return result;
	}
	
	protected List<GetterSpec> buildGetters(TypeElement target) {
		List<GetterSpec> getters = Utils.stream(ElementFilter.methodsIn(target.getEnclosedElements()))
				.map(this::buildGetterSpec)
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
