package com.mindlin.nautilus.tools.irgen.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor8;

public class ClassName extends TypeName implements Named {
	public static ClassName get(Class<?> clazz) {
		String name = clazz.getSimpleName();
		
		if (clazz.getEnclosingClass() == null) {
			int lastDot = clazz.getName().lastIndexOf('.');
			String packageName = (lastDot != -1) ? clazz.getName().substring(0, lastDot) : "";
			return new ClassName(packageName, null, name);
		}
		
		return ClassName.get(clazz.getEnclosingClass()).nestedClass(name);
	}
	
	public static ClassName get(TypeElement element) {
		String name = element.getSimpleName().toString();
		return element.getEnclosingElement().accept(new ElementMapper(), name);
	}
	
	protected final String packageName;
	protected final ClassName enclosingClass;
	protected final String simpleName;
	
	public ClassName(String packageName, String simpleName) {
		this(packageName, null, simpleName);
	}
	
	public ClassName(String packageName, ClassName enclosingClass, String simpleName) {
		this(packageName, enclosingClass, simpleName, Collections.emptyList());
	}
	
	public ClassName(String packageName, ClassName enclosingClass, String simpleName, List<? extends AnnotationSpec> annotations) {
		super(annotations);
		this.packageName = packageName;
		this.enclosingClass = enclosingClass;
		this.simpleName = simpleName;
	}
	
	@Override
	public ClassName withAnnotations(List<? extends AnnotationSpec> annotations) {
		return new ClassName(packageName, enclosingClass, simpleName, annotations);
	}
	
	public String getPackageName() {
		return packageName;
	}
	
	public ClassName getEnclosingClass() {
		return enclosingClass;
	}
	
	public String getSimpleName() {
		return simpleName;
	}
	
	@Override
	public String getName() {
		return this.getSimpleName();
	}
	
	@Override
	public String getReflectionName() {
		if (this.enclosingClass != null)
			return String.format("%s$%s", this.enclosingClass.getReflectionName(), this.simpleName);
		if (!this.packageName.isEmpty())
			return String.format("%s.%s", this.packageName, this.simpleName);
		return this.simpleName;
	}
	
	public String getQualifiedName() {
		if (this.enclosingClass != null)
			return String.format("%s.%s", this.enclosingClass.getQualifiedName(), this.simpleName);
		if (!this.packageName.isEmpty())
			return String.format("%s.%s", this.packageName, this.simpleName);
		return this.simpleName;
	}
	
	public ClassName peerClass(String name) {
		return new ClassName(this.packageName, this.enclosingClass, name);
	}
	
	public ClassName nestedClass(String name) {
		return new ClassName(this.packageName, this, name);
	}
	
	@Override
	public String toString() {
		return this.getQualifiedName();
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(this.packageName, this.enclosingClass, this.simpleName);
	}
	
	protected List<? extends ClassName> enclosingClasses() {
		List<ClassName> result = new ArrayList<>();
		for (ClassName current = this; current != null; current = current.enclosingClass)
			result.add(current);
		Collections.reverse(result);
		return result;
	}
	
	@Override
	public void write(CodeWriter out) {
		if (out.isImported(this)) {
			// Just write simple
		} else if (this.enclosingClass != null) {
			this.enclosingClass.write(out);
			out.print(".");
		} else if (!out.isPackageImported(this.packageName)) {
			out.print(this.packageName);
			out.print(".");
		}
		
		this.writeAnnotations(out);
		out.print(this.getSimpleName());
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof ClassName))
			return false;
		ClassName c2 = (ClassName) obj;
		return Objects.equals(this.getPackageName(), c2.getPackageName())
				&& Objects.equals(this.getEnclosingClass(), c2.getEnclosingClass())
				&& Objects.equals(this.getSimpleName(), c2.getSimpleName());
	}
	
	protected static class ElementMapper extends SimpleElementVisitor8<ClassName, String> {
		@Override
		public ClassName visitPackage(PackageElement packageElement, String simpleName) {
			return new ClassName(packageElement.getQualifiedName().toString(), null, simpleName);
		}
		
		@Override
		public ClassName visitType(TypeElement enclosingClass, String simpleName) {
			String esn = enclosingClass.getSimpleName().toString();
			return enclosingClass.getEnclosingElement()
					.accept(this, esn)
					.nestedClass(simpleName);
		}
		
		@Override
		public ClassName visitUnknown(Element e, String simpleName) {
			return new ClassName("", simpleName);
		}
		
		@Override
		protected ClassName defaultAction(Element e, String simpleName) {
			throw new IllegalArgumentException("Unexpected type");
		}
	}
}
