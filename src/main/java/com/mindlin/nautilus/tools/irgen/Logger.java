package com.mindlin.nautilus.tools.irgen;

import java.util.Objects;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class Logger {
	private final Messager messager;
	private final @Nullable Element target;
	private final @Nullable AnnotationMirror site;
	private final @Nullable AnnotationValue value;
	
	public Logger(Messager messager) {
		this(messager, null, null, null);
	}
	
	public Logger(Messager messager, @Nullable Element target) {
		this(messager, target, null, null);
	}
	
	public Logger(Messager messager, @Nullable Element target, @Nullable AnnotationMirror site) {
		this(messager, target, site, null);
	}
	
	public Logger(Messager messager, @Nullable Element target, @Nullable AnnotationMirror site, @Nullable AnnotationValue value) {
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
	
	public void printStackTrace(Throwable t) {
		try (PrintStream ps = new PrintStream(this.asOutputStream(Kind.ERROR))) {
			t.printStackTrace(ps);
		}
	}
	
	public OutputStream asOutputStream(Kind level) {
		return new FakeOutputStream(level);
	}
	
	class FakeOutputStream extends OutputStream {
		protected final Kind level;
		
		public FakeOutputStream(Kind level) {
			this.level = level;
		}
		
		@Override
		public void write(int b) throws IOException {
			//TODO: buffer?
			Logger.this.log(level, "%c", b);
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException, IndexOutOfBoundsException {
			Objects.requireNonNull(b);
			if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0))
				throw new IndexOutOfBoundsException();
			if (len == 0)
				return;
			Logger.this.log(this.level, new String(b, off, len));
		}
	}
}
