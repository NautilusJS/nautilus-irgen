package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;

import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public class TypeName implements Writable {
	public static TypeName wrap(TypeMirror type) {
		return type.accept(new TypeMapper(), null);
	}
	
	public static ClassName wrap(TypeElement element) {
		String name = element.getSimpleName().toString();
		
		if (element.getEnclosingElement() == null || element.getEnclosingElement().getKind() == ElementKind.PACKAGE) {
			String qName = element.getQualifiedName().toString();
			int lastDot = qName.lastIndexOf('.');
			String packageName = (lastDot != -1) ? qName.substring(0, lastDot) : "";
			return new ClassName(packageName, null, name);
		}
		
		return wrap((TypeElement) element.getEnclosingElement()).nestedClass(name);
	}
	
	public static TypeName wrap(String value) {
		return new TypeName(value);
	}
	
	public static final TypeName VOID = new TypeName("void");
	public static final TypeName BOOLEAN = new TypeName("boolean");
	public static final TypeName CHAR = new TypeName("char");
	public static final TypeName BYTE = new TypeName("byte");
	public static final TypeName SHORT = new TypeName("short");
	public static final TypeName INT = new TypeName("int");
	public static final TypeName LONG = new TypeName("long");
	public static final TypeName FLOAT = new TypeName("float");
	public static final TypeName DOUBLE = new TypeName("double");
	
	private final String keyword;
	private transient String tsCache;
	
	protected TypeName() {
		this(null);
	}
	
	private TypeName(String name) {
		this.keyword = name;
	}

	@Override
	public void write(CodeWriter out) {
		out.print(this.keyword);
	}
	
	@Override
	public String toString() {
		if (this.tsCache != null)
			return this.tsCache;
		try (StringWriter sw = new StringWriter();
				CodeWriter cw = new CodeWriter(sw)) {
			this.write(cw);
			return this.tsCache = sw.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	static class WrappingTypeName extends TypeName {
		private final TypeMirror inner;
		
		public WrappingTypeName(TypeMirror inner) {
			this.inner = inner;
		}
		
		@Override
		public void write(CodeWriter out) {
//			out.print(inner.getClass());
			this.inner.accept(new TypeWriter(), out);
		}
	}
	
	public static class ClassWithTypeArguments extends TypeName {
		private final ClassName name;
		private final List<TypeName> args;
		public ClassWithTypeArguments(ClassName name, List<TypeName> args) {
			this.name = name;
			this.args = args;
		}
		
		public ClassName getName() {
			return this.name;
		}
		
		public List<TypeName> getArgs() {
			return this.args;
		}

		@Override
		public void write(CodeWriter out) {
			out.emit("$T<$,T>", this.name, this.args);
		}
	}
	
	static class TypeMapper implements TypeVisitor<TypeName, Void> {

		@Override
		public TypeName visit(TypeMirror t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visit(TypeMirror t) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitPrimitive(PrimitiveType t, Void p) {
			switch (t.getKind()) {
				case BOOLEAN:
					return TypeName.BOOLEAN;
				case BYTE:
					return TypeName.BYTE;
				case CHAR:
					return TypeName.CHAR;
				case DOUBLE:
					return TypeName.DOUBLE;
				case FLOAT:
					return TypeName.FLOAT;
				case INT:
					return TypeName.INT;
				case LONG:
					return TypeName.LONG;
				case SHORT:
					return TypeName.SHORT;
				case VOID:
					return TypeName.VOID;
				default:
					break;
			}
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitNull(NullType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitArray(ArrayType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitDeclared(DeclaredType t, Void p) {
			ClassName cn = TypeName.wrap((TypeElement) t.asElement());
			if (t.getTypeArguments() != null && !t.getTypeArguments().isEmpty()) {
				List<TypeName> typeArgs = Utils.map(t.getTypeArguments(), arg -> arg.accept(this, null));
				return new ClassWithTypeArguments(cn, typeArgs);
			}
			return cn;
		}

		@Override
		public TypeName visitError(ErrorType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitTypeVariable(TypeVariable t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitWildcard(WildcardType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitExecutable(ExecutableType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitNoType(NoType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitUnknown(TypeMirror t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitUnion(UnionType t, Void p) {
			return new WrappingTypeName(t);
		}

		@Override
		public TypeName visitIntersection(IntersectionType t, Void p) {
			return new WrappingTypeName(t);
		}
	}
	
	static class TypeWriter implements TypeVisitor<Void, CodeWriter> {
		@Override
		public Void visit(TypeMirror t, CodeWriter out) {
			// TODO Auto-generated method stub
			out.print("??");
			return null;
		}

		@Override
		public Void visit(TypeMirror t) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Void visitPrimitive(PrimitiveType t, CodeWriter out) {
			switch (t.getKind()) {
				case BOOLEAN:
					out.print("boolean");
					break;
				case BYTE:
					out.print("byte");
					break;
				case CHAR:
					out.print("char");
					break;
				case DOUBLE:
					out.print("double");
					break;
				case FLOAT:
					out.print("float");
					break;
				case INT:
					out.print("int");
					break;
				case LONG:
					out.print("long");
					break;
				case SHORT:
					out.print("short");
					break;
				case VOID:
					out.print("Void");
					break;
				default:
					out.print("$$ERROR$$");
					break;
			}
			return null;
		}

		@Override
		public Void visitNull(NullType t, CodeWriter out) {
			out.print("$null");
			return null;
		}

		@Override
		public Void visitArray(ArrayType t, CodeWriter out) {
			t.getComponentType().accept(this, out);
			out.print("[]");
			return null;
		}

		@Override
		public Void visitDeclared(DeclaredType t, CodeWriter out) {
			TypeElement elem = (TypeElement) t.asElement();
			ClassName cn = (ClassName) TypeName.wrap(elem);
			out.emitType(cn);
			
			List<? extends TypeMirror> typeArgs = t.getTypeArguments();
			if (typeArgs != null && !typeArgs.isEmpty()) {
				boolean first = true;
				out.print("<");
				for (TypeMirror typeArg : typeArgs) {
					if (first)
						first = false;
					else
						out.print(", ");
					typeArg.accept(this, out);
				}
				out.print(">");
			}
			return null;
		}

		@Override
		public Void visitError(ErrorType t, CodeWriter out) {
			out.print("$error");
			return null;
		}

		@Override
		public Void visitTypeVariable(TypeVariable t, CodeWriter out) {
			out.print("$tv");
			return null;
		}

		@Override
		public Void visitWildcard(WildcardType t, CodeWriter out) {
			out.print("?");
			if (t.getExtendsBound() != null) {
				out.print(" extends ");
				t.getExtendsBound().accept(this, out);
			} else if (t.getSuperBound() != null) {
				out.print(" super ");
				t.getSuperBound().accept(this, out);
			}
			return null;
		}

		@Override
		public Void visitExecutable(ExecutableType t, CodeWriter out) {
			out.print("$ex");
			return null;
		}

		@Override
		public Void visitNoType(NoType t, CodeWriter out) {
			out.print("$nt");
			return null;
		}

		@Override
		public Void visitUnknown(TypeMirror t, CodeWriter out) {
			out.print("$unk");
			return null;
		}

		@Override
		public Void visitUnion(UnionType t, CodeWriter out) {
			out.print("$union");
			return null;
		}

		@Override
		public Void visitIntersection(IntersectionType t, CodeWriter out) {
			out.print("$&");
			return null;
		}
	}
}
