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

import com.mindlin.nautilus.tools.irgen.ImplProcessor.TreeImplSpec;
import com.mindlin.nautilus.tools.irgen.ir.Orderable;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;

@SupportedAnnotationTypes({IRTypes.TREE_NOIMPL, IRTypes.TREE_ADT, IRTypes.TREE_IMPL})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class IRGenerator extends AbstractProcessor {
	
	protected Logger getLogger() {
		return new Logger(this.processingEnv.getMessager());
	}
	
	protected Map<String, TreeSpec> processNoImplTrees(TypeElement annotation, RoundEnvironment roundEnv) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, annotationType, roundEnv);
		
		Set<? extends Element> targets = roundEnv.getElementsAnnotatedWith(annotation);
		if (targets.isEmpty())
			return Collections.emptyMap();
		
		Map<String, TreeSpec> specMap = new HashMap<>();
		for (Element target : targets) {
			Logger logger = getLogger().withTarget(target);
			
			if (target.getKind() != ElementKind.INTERFACE) {
				logger.error("Illegal @Tree.NoImpl on kind: %s", target.getKind());
				continue;
			}
			
			TreeSpec spec = processor.buildTreeSpec((TypeElement) target);
			specMap.put(Utils.getName(target), spec);
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
		getLogger().note("Hello, world! %s", annotations);
		TypeElement noImpl = annotations.stream()
				.filter(annotation -> annotation.getQualifiedName().contentEquals(IRTypes.TREE_NOIMPL))
				.findAny()
				.orElseGet(() -> null);
		
		TypeElement impl = annotations.stream()
				.filter(annotation -> annotation.getQualifiedName().contentEquals(IRTypes.TREE_IMPL))
				.findAny()
				.orElseGet(() -> null);
		Map<String, TreeSpec> niSpecs = Collections.emptyMap(), iSpecs = Collections.emptyMap();
		if (noImpl != null)
			niSpecs = this.processNoImplTrees(noImpl, roundEnv);
		if (impl != null)
			iSpecs = this.processImplTrees(impl, roundEnv);
		getLogger().note("niSpecs=%s", niSpecs);
		getLogger().note("iSpecs=%s", iSpecs);
		
		if (impl != null)
			this.processImplOutputs(impl, roundEnv, niSpecs, iSpecs);
		
		return true;
	}
}
