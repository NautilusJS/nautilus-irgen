package com.mindlin.nautilus.tools.irgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

public class ElementProcessorBase extends AnnotationProcessorBase {
	final TypeElement target;
	
	public ElementProcessorBase(ProcessingEnvironment procEnv, DeclaredType annotation, RoundEnvironment roundEnv, TypeElement target) {
		super(procEnv, annotation);
		this.target = target;
	}
	
	@Override
	protected Logger getLogger() {
		return new Logger(procEnv.getMessager(), target);
	}
	
	protected List<AnnotationMirror> getMirrors() {
		List<AnnotationMirror> result = new ArrayList<>();
		for (AnnotationMirror mirror : target.getAnnotationMirrors())
			if (Objects.equals(annotation, mirror.getAnnotationType()))
				result.add(mirror);
		return result;
	}

}
