package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

public abstract class ClassSpec {

	protected Element[] getSources() {
		return new Element[0];
	}
	
	protected abstract String getPackage();
	
	protected abstract String getSimpleName();
	
	protected Set<ClassName> getImports() {
		return Collections.emptySet();
	}
	
	public ClassName getClassName() {
		return new ClassName(this.getPackage(), this.getSimpleName());
	}
	
	protected abstract int getModifiers();
	
	protected Optional<TypeName> getSuper() {
		return Optional.empty();
	}
	
	protected Collection<TypeName> getImplementing() {
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
		JavaFileObject file = filer.createSourceFile(getClassName().toString(), this.getSources());
		try (Writer writer = file.openWriter()) {
			this.write(writer);
		}
	}
	
	protected void writeHeritage(CodeWriter writer) throws IOException {
		Optional<TypeName> parent = this.getSuper();
		if (parent.isPresent())
			writer.emit("extends $T ", parent.get());
		
		Collection<TypeName> implementing = this.getImplementing();
		if (!implementing.isEmpty()) {
			writer.emit("implements $,T ", implementing);
			writer.print(" ");
		}
	}
	
	protected void writeBody(@SuppressWarnings("unused") CodeWriter writer) throws IOException {
		
	}
	
	public void write(Writer _writer) throws IOException {
		final CodeWriter writer = new CodeWriter(_writer, "\t");
		// Write package declaration
		if (!this.getPackage().isEmpty()) {
			writer.emitPackageDeclaration(this.getPackage());
			writer.setPackage(this.getPackage());
		}
		
		Set<ClassName> imports = this.getImports();
		for (ClassName ipclz : imports) {
			writer.emitImport(ipclz);
			writer.addImport(ipclz);
		}
		if (!imports.isEmpty())
			writer.println();

		writer.emit("$M class $N ", this.getModifiers(), this.getSimpleName());
		
		this.writeHeritage(writer);
		
		writer.println("{");
		writer.pushIndent();
		
		this.writeBody(writer);
		
		writer.popIndent();
		writer.setEOL();
		writer.print("}");
	}

}
