package com.mindlin.nautilus.tools.irgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

public class AnnotationProcessorBase {
	final DeclaredType annotation;
	final ProcessingEnvironment procEnv;
	
	public AnnotationProcessorBase(ProcessingEnvironment procEnv, DeclaredType annotation) {
		this.annotation = annotation;
		this.procEnv = procEnv;
	}
	
	protected Logger getLogger() {
		return new Logger(this.procEnv.getMessager());
	}
	
	protected List<AnnotationMirror> getMirrors(TypeElement target) {
		List<AnnotationMirror> result = new ArrayList<>();
		for (AnnotationMirror mirror : target.getAnnotationMirrors())
			if (Objects.equals(this.annotation, mirror.getAnnotationType()))
				result.add(mirror);
		return result;
	}
	
}
