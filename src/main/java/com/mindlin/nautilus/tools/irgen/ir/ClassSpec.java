package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import com.mindlin.nautilus.tools.irgen.IndentWriter;
import com.mindlin.nautilus.tools.irgen.Utils;

public abstract class ClassSpec {

	protected Element[] getSources() {
		return new Element[0];
	}
	
	protected abstract String getPackage();
	
	protected abstract String getSimpleName();
	
	public String getQualifiedName() {
		return String.format("%s.%s", this.getPackage(), this.getSimpleName());
	}
	
	protected abstract int getModifiers();
	
	protected Optional<String> getSuper() {
		return Optional.empty();
	}
	
	protected Collection<String> getImplementing() {
		return Collections.emptyList();
	}
	
	protected Collection<FieldSpec> getFields() {
		return Collections.emptyList();
	}
	
	protected Collection<CtorSpec> getConstructors() {
		return Collections.emptyList();
	}
	
	protected Collection<MethodSpec> getMethods() {
		return Collections.emptyList();
	}
	
	public void write(Filer filer) throws IOException {
		JavaFileObject file = filer.createSourceFile(getQualifiedName(), this.getSources());
		try (Writer writer = file.openWriter()) {
			this.write(writer);
		}
	}
	
	protected void writeHeritage(PrintWriter writer) throws IOException {
		Optional<String> parent = this.getSuper();
		if (parent.isPresent()) {
			writer.print("extends ");
			writer.print(parent.get());
			writer.print(" ");
		}
		
		Collection<String> implementing = this.getImplementing();
		if (!implementing.isEmpty()) {
			writer.print("implements ");
			Utils.writeList(writer, implementing, ", ");
			writer.print(" ");
		}
	}
	
	protected void writeBody(IndentWriter writer) throws IOException {
		
	}
	
	public void write(Writer _writer) throws IOException {
		final IndentWriter writer = new IndentWriter(_writer, "\t");
		// Write package declaration
		if (!this.getPackage().isEmpty()) {
			writer.format("package %s;", this.getPackage());
			writer.println();
			writer.println();
		}

		Utils.writeModifiers(writer, getModifiers());
		writer.print("class ");
		writer.print(getSimpleName());
		writer.print(" ");
		
		this.writeHeritage(writer);
		
		writer.println("{");
		writer.pushIndent();
		
		this.writeBody(writer);
		
		writer.popIndent();
		writer.setEOL();
		writer.print("}");
	}

}
