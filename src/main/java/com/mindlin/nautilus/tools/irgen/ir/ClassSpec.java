package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
public abstract class ClassSpec {
	protected static String getNamespace(String pkg) {
		if (pkg == null)
			return pkg;
		int firstDot = pkg.indexOf('.');
		if (firstDot >= 0)
			return pkg.substring(0, firstDot);
		return pkg;
	}
	
	protected static int getNamespacePriority(String namespace) {
		if (namespace == null)
			return -1;
		switch (namespace) {
			case "java":
				return 0;
			case "javax":
				return 1;
			case "org":
				return 2;
			case "com":
				return 3;
			default:
				return 4;
		}
	}
	
	protected static int comparePackageNames(String pkgA, String pkgB) {
		if (pkgA == null)
			return pkgB == null ? -1 : 0;
		if (pkgB == null)
			return 1;
		
		String namespaceA = getNamespace(pkgA);
		String namespaceB = getNamespace(pkgB);
		
		int result = getNamespacePriority(namespaceA) - getNamespacePriority(namespaceB);
		if (result != 0)
			return result;
		
		result = pkgA.compareTo(pkgB);
		
		return result;
	}
	
	protected static int compareImports(ClassName a, ClassName b) {
		int result = ClassSpec.comparePackageNames(a.getPackageName(), b.getPackageName());
		if (result != 0)
			return result;
		
		// Glob imports come first
		if (a.getSimpleName() == null)
			return b.getSimpleName() == null ? 0 : -1;
		if (b.getSimpleName() == null)
			return 1;
		
		return a.getQualifiedName().compareTo(b.getQualifiedName());
	}

	protected Element[] getSources() {
		return new Element[0];
	}
	
	protected abstract String getPackage();
	
	protected abstract String getSimpleName();
	
	protected void getImports(Collection<? super ClassName> result) {
		result.add(ClassName.get(Generated.class));
		
		for (CtorSpec ctor : this.getConstructors())
			ctor.getImports(result);
		
		for (MethodSpec method : this.getMethods())
			method.getImports(result);
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
		JavaFileObject file;
		synchronized (filer) {
			file = filer.createSourceFile(getClassName().toString(), this.getSources());
		}
		try (Writer writer = new BufferedWriter(file.openWriter())) {
			this.write(writer);
		}
	}
	
	protected void writeImports(CodeWriter writer) {
		List<ClassName> imports = new ArrayList<>(this.getImports());
		if (imports.isEmpty())
			return;
		
		imports.sort(ClassSpec::compareImports);
		
		Set<String> packageImports = new HashSet<>();
		Set<String> names = new HashSet<>();
		String lastNamespace = null;
		for (ClassName ipclz : imports) {
			if (ipclz.getEnclosingClass() == null && packageImports.contains(ipclz.getPackageName()))
				continue;
			if (names.contains(ipclz.getSimpleName()))
				// Another import exists with the same name
				continue;
			
			String namespace = getNamespace(ipclz.getPackageName());
			if (lastNamespace != null && !Objects.equals(namespace, lastNamespace))
				writer.println();
			lastNamespace = namespace;
			
			writer.emitImport(ipclz);
			writer.addImport(ipclz);
			if (ipclz.getSimpleName() == null)
				packageImports.add(ipclz.getPackageName());
			else
				names.add(ipclz.getName());
		}
		
		writer.println();
	}
	
	protected void writeHeritage(CodeWriter writer) throws IOException {
		Optional<TypeName> parent = this.getSuper();
		if (parent.isPresent())
			writer.emit("extends $T ", parent.get());
		
		Collection<TypeName> implementing = this.getImplementing();
		if (!implementing.isEmpty())
			writer.emit("implements $,T ", implementing);
	}
	
	protected void writeBody(@SuppressWarnings("unused") CodeWriter writer) throws IOException {
		
	}
	
	public void write(Writer _writer) throws IOException {
		@SuppressWarnings("resource")
		final CodeWriter writer = new CodeWriter(_writer, "\t");
		// Write package declaration
		if (!this.getPackage().isEmpty()) {
			writer.emitPackageDeclaration(this.getPackage());
			writer.setPackage(this.getPackage());
		}
		
		this.writeImports(writer);
		
		// Emit a SuppressWarnings b/c possible unused imports
		writer.println("@SuppressWarnings(\"unused\")");

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
