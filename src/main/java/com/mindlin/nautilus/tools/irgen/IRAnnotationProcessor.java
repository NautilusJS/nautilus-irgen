package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.ir.ClassSpec.OutputInfo;
import com.mindlin.nautilus.tools.irgen.util.Orderable;
import com.google.auto.service.AutoService;
import com.mindlin.nautilus.tools.irgen.ir.TreeImplSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;

@AutoService(Processor.class)
@SupportedAnnotationTypes({IRTypes.TREE_NOIMPL, IRTypes.TREE_ADT, IRTypes.TREE_IMPL})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class IRAnnotationProcessor extends AbstractProcessor {
	
	protected Logger getLogger() {
		return new Logger(this.processingEnv.getMessager());
	}
	
	protected Map<String, TreeSpec> processTrees(TypeElement annotation, RoundEnvironment roundEnv) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, annotationType, roundEnv);
		
		Set<? extends Element> targets = roundEnv.getElementsAnnotatedWith(annotation);
		if (targets.isEmpty())
			return Collections.emptyMap();
		
		Map<String, TreeSpec> specMap = new HashMap<>();
		for (Element target : targets) {
			Logger logger = getLogger().withTarget(target);
			
			if (target.getKind() != ElementKind.INTERFACE) {
				logger.error("Illegal @Tree.%s on kind: %s", annotation.getSimpleName(), target.getKind());
				continue;
			}
			
			try {
				TreeSpec spec = processor.buildTreeSpec((TypeElement) target);
				specMap.put(Utils.getName(target), spec);
			} catch (Exception e) {
				logger.error("Error reading @Tree.%s: %s", annotation.getSimpleName(), e.getLocalizedMessage());
				throw e;
			}
		}
		return specMap;
	}
	
	protected Map<String, TreeSpec> processTreesMP(TypeElement annotation, RoundEnvironment roundEnv) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, annotationType, roundEnv);
		
		Set<? extends Element> targets = roundEnv.getElementsAnnotatedWith(annotation);
		if (targets.isEmpty())
			return Collections.emptyMap();
		
		return targets.parallelStream()
			.filter(target -> {
				if (target.getKind() != ElementKind.INTERFACE) {
					getLogger().withTarget(target).error("Illegal @Tree.%s on kind: %s", annotation.getSimpleName(), target.getKind());
					return false;
				}
				return true;
			})
			.collect(Collectors.toMap(Utils::getName, target -> {
				try {
					return processor.buildTreeSpec((TypeElement) target);
				} catch (Exception e) {
					getLogger().withTarget(target).error("Error reading @Tree.%s: %s", annotation.getSimpleName(), e.getLocalizedMessage());
					throw e;
				}
			}));
	}
	
	protected Set<String> getUnprocessed(TypeElement adt, RoundEnvironment roundEnv, Map<String, TreeSpec> niSpecs, Map<String, TreeSpec> iSpecs) {
		Stack<String> queue = new Stack<>();
		Set<String> enqueued = new HashSet<>();
		Set<String> missing = new HashSet<>();
		
		Map<String, String> sources = new HashMap<>();
		Map<String, TreeSpec> specs = new HashMap<>(niSpecs);
		specs.putAll(iSpecs);
		
		queue.addAll(iSpecs.keySet());
		enqueued.addAll(iSpecs.keySet());
		
		while (!queue.isEmpty()) {
			String path = queue.pop();
			TreeSpec spec = specs.get(path);
			if (spec == null) {
				getLogger().note("Missing spec for %s", path);
				missing.add(path);
				continue;
			}
			for (TypeName _parent : spec.parents) {
				//TODO: fix
				String parent = _parent.toString();
				sources.putIfAbsent(parent, path);
				if (!specs.containsKey(parent))
					missing.add(parent);
				if (enqueued.add(parent))
					queue.add(parent);
			}
		}
		
		Set<String> extra = new HashSet<>(specs.keySet());
		extra.removeAll(enqueued);
		if (Utils.isVerbose())
			getLogger().note("Extra specs: %s", extra);
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, (DeclaredType) adt.asType(), roundEnv);
		if (!missing.isEmpty()) {
			String mp = missing.iterator().next();
			String mps = sources.get(mp);
			getLogger().note("%s is missing %s", mps, mp);
			TreeSpec mpsSpec = specs.get(mps);
			for (TypeMirror candidate : this.processingEnv.getTypeUtils().directSupertypes(mpsSpec.source.asType())) {
				String name = Utils.getName((DeclaredType) candidate);
				if (!Objects.equals(name, mp))
					continue;
				getLogger().note("Found type %s (%s)", name, processor.buildTreeSpec((TypeElement) ((DeclaredType) candidate).asElement()));
			}
		}
		
		return missing;
	}
	
	protected void processImplOutputs(TypeElement annotation, RoundEnvironment roundEnv, Map<String, TreeSpec> niSpecs, Map<String, TreeSpec> iSpecs) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		Map<String, TreeImplSpec> impls = new HashMap<>();
		
		ImplProcessor processor = new ImplProcessor(this.processingEnv, annotationType, roundEnv, niSpecs, iSpecs, impls);
		
		
		// Order impl gen
		List<TreeSpec> implOrder = Orderable.sorted(new ArrayList<>(iSpecs.values()));
		if (Utils.isVerbose())
			getLogger().note("Impl order: %s", implOrder.stream().map(TreeSpec::getName).collect(Collectors.toList()));
		
		for (TreeSpec spec : implOrder) {
			Logger logger = getLogger().withTarget(spec.source);
			TreeImplSpec specImpl = processor.buildTreeImpl(spec.source, spec);
			impls.put(spec.getName().toString(), specImpl);
			if (Utils.isVerbose())
				logger.warn("SpecImpl: %s", specImpl);
			
			try {
				specImpl.write(this.processingEnv.getFiler());
			} catch (IOException e) {
				getLogger().error("Error writing impl %s: %s", specImpl.getClassName(), e.getMessage());
				e.printStackTrace();
				continue;
			}
		}
	}
	
	public static void main(String...args) {
		
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		IRAnnotationProcessor.main();
		Instant start = Instant.now();
		
		if (Utils.isVerbose())
			getLogger().note("Hello, world! %s", annotations);
		
		Map<String, TypeElement> annotationLUT = annotations.stream()
				.collect(Collectors.toMap(annotation -> annotation.getQualifiedName().toString(), x -> x));
		
		Map<String, TreeSpec> niSpecs = Collections.emptyMap(), iSpecs = Collections.emptyMap();
		
		TypeElement noImpl = annotationLUT.get(IRTypes.TREE_NOIMPL);
		if (noImpl != null)
			niSpecs = this.processTrees(noImpl, roundEnv);
		TypeElement adt = annotationLUT.get(IRTypes.TREE_ADT);
		if (adt != null)
			;
		TypeElement impl = annotationLUT.get(IRTypes.TREE_IMPL);
		if (impl != null)
			iSpecs = this.processTrees(impl, roundEnv);
		
		if (!iSpecs.isEmpty()) {
//			if (Utils.isVerbose())
//				getLogger().note("niSpecs=%s / iSpecs=%s", niSpecs, iSpecs);
			
			getLogger().note("Missing specs=%s", getUnprocessed(adt, roundEnv, niSpecs, iSpecs));
			
			if (impl != null)
				this.processImplOutputs(impl, roundEnv, niSpecs, iSpecs);
		}
		
		Duration elapsed = Duration.between(start, Instant.now());
		getLogger().note("Ran in %d.%09d", elapsed.getSeconds(), elapsed.getNano());
		
		return true;
	}
}
