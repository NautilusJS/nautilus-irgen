package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.mindlin.nautilus.tools.irgen.ir.TreeImplSpec;
import com.mindlin.nautilus.tools.irgen.ir.Orderable;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;

@SupportedAnnotationTypes({IRTypes.TREE_NOIMPL, IRTypes.TREE_ADT, IRTypes.TREE_IMPL})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class IRGenerator extends AbstractProcessor {
	
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
	
	protected Map<String, TreeSpec> processImplTrees(TypeElement annotation, RoundEnvironment roundEnv) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, annotationType, roundEnv);
		
		Set<? extends Element> targets = roundEnv.getElementsAnnotatedWith(annotation);
		if (targets.isEmpty())
			return Collections.emptyMap();
		
		Map<String, TreeSpec> specMap = new HashMap<>();
		for (Element target : targets) {
			Logger logger = getLogger().withTarget(target);
			
			if (target.getKind() != ElementKind.INTERFACE) {
				logger.error("Illegal @Tree.Impl on kind: %s", target.getKind());
				continue;
			}
			
			TreeSpec spec = processor.buildTreeSpec((TypeElement) target);
			specMap.put(Utils.getName(target), spec);
		}
		return specMap;
	}
	
	protected void processImplOutputs(TypeElement annotation, RoundEnvironment roundEnv, Map<String, TreeSpec> niSpecs, Map<String, TreeSpec> iSpecs) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		Map<String, TreeImplSpec> impls = new HashMap<>();
		
		ImplProcessor processor = new ImplProcessor(this.processingEnv, annotationType, roundEnv, niSpecs, iSpecs, impls);
		
		
		// Order impl gen
		List<TreeSpec> implOrder = Orderable.sorted(new ArrayList<>(iSpecs.values()));
		getLogger().note("Impl order: %s", implOrder.stream().map(TreeSpec::getName).collect(Collectors.toList()));
		
		for (TreeSpec spec : implOrder) {
			Logger logger = getLogger().withTarget(spec.source);
			TreeImplSpec specImpl = processor.buildTreeImpl(spec.source, spec);
			impls.put(spec.getName(), specImpl);
			logger.warn("SpecImpl: %s", specImpl);
			
			try {
				specImpl.write(this.processingEnv.getFiler());
			} catch (IOException e) {
				getLogger().error("Error writing impl %s: %s", specImpl.getQualifiedName(), e.getMessage());
				e.printStackTrace();
				continue;
			}
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
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
			
			getLogger().note("Missing specs=%s", getUnprocessed(niSpecs, iSpecs));
			
			if (impl != null)
				this.processImplOutputs(impl, roundEnv, niSpecs, iSpecs);
		}
		
		Duration elapsed = Duration.between(start, Instant.now());
		getLogger().note("Ran in %d.%09d", elapsed.getSeconds(), elapsed.getNano());
		
		return true;
	}
}
