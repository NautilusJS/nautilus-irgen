package com.mindlin.nautilus.tools.irgen;

import java.util.Objects;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

public class Logger {
	private final Messager messager;
	private final Element target;
	private final AnnotationMirror site;
	private final AnnotationValue value;
	
	public Logger(Messager messager) {
		this(messager, null, null, null);
	}
	
	public Logger(Messager messager, Element target) {
		this(messager, target, null, null);
	}
	
	public Logger(Messager messager, Element target, AnnotationMirror site) {
		this(messager, target, site, null);
	}
	
	public Logger(Messager messager, Element target, AnnotationMirror site, AnnotationValue value) {
		this.messager = Objects.requireNonNull(messager);
		this.target = target;
		this.site = site;
		this.value = value;
	}
	
	public Logger withTarget(Element target) {
		return new Logger(messager, target, site, value);
	}
	
	public Logger withTarget(Element target, AnnotationMirror site) {
		return new Logger(messager, target, site, value);
	}
	
	public Logger withTarget(Element target, AnnotationMirror site, AnnotationValue value) {
		return new Logger(messager, target, site, value);
	}
	
	public Logger withSite(AnnotationMirror site) {
		return new Logger(messager, target, site, value);
	}
	
	public Logger withSite(AnnotationMirror site, AnnotationValue value) {
		return new Logger(messager, target, site, value);
	}
	
	public Logger withValue(AnnotationValue value) {
		return new Logger(messager, target, site, value);
	}
	
	public void log(Kind kind, String msg) {
		if (value != null)
			messager.printMessage(kind, msg, target, site, value);
		else if (site != null)
			messager.printMessage(kind, msg, target, site);
		else if (target != null)
			messager.printMessage(kind, msg, target);
		else
			messager.printMessage(kind, msg);
	}
	
	public void log(Kind kind, String msg, Object...args) {
		log(kind, String.format(msg, args));
	}
	
	public void note(String msg, Object...args) {
		log(Kind.NOTE, msg, args);
	}

	public void warn(String msg, Object...args) {
		log(Kind.WARNING, msg, args);
	}

	public void error(String msg, Object...args) {
		log(Kind.ERROR, msg, args);
	}
}
