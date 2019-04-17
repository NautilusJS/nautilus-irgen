package com.mindlin.nautilus.tools.irgen;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.type.DeclaredType;

/**
 * Generates abstract base classes
 * @author mailmindlin
 */
public class ABCGenerator extends AnnotationProcessorBase {

	public ABCGenerator(ProcessingEnvironment procEnv, DeclaredType annotation, RoundEnvironment roundEnv) {
		super(procEnv, annotation, roundEnv);
	}
	
	public void process() {
		
	}

}
