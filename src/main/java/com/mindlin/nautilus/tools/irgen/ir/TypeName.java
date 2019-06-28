package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;
import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
import com.mindlin.nautilus.tools.irgen.util.Named;

public abstract class TypeName implements Writable {
	public static TypeName wrap(TypeMirror type) {
		return type.accept(new TypeMapper(), null);
	}
	
	private static TypeName wrapAnnotations(AnnotatedConstruct raw, TypeName result) {
		List<? extends AnnotationSpec> annotations = AnnotationSpec.from(raw);
		if (!annotations.isEmpty())
			return result.withAnnotations(annotations);
		return result;
	}
	
	private static TypeName wrapAnnotations(AnnotatedElement raw, TypeName result) {
		//TODO: fix (broken)
		List<AnnotationSpec> annotations = Utils.map(Arrays.asList(raw.getAnnotations()), annotation -> null);
		if (!annotations.isEmpty())
			return result.withAnnotations(annotations);
		return result;
	}
	
	private static TypeName doGet(Class<?> clazz) {
		if (clazz == void.class)
			return TypeName.VOID;
		if (clazz == boolean.class)
			return TypeName.BOOLEAN;
		if (clazz == char.class)
			return TypeName.CHAR;
		if (clazz == byte.class)
			return TypeName.BYTE;
		if (clazz == short.class)
			return TypeName.SHORT;
		if (clazz == int.class)
			return TypeName.INT;
		if (clazz == long.class)
			return TypeName.LONG;
		if (clazz == float.class)
			return TypeName.FLOAT;
		if (clazz == double.class)
			return TypeName.DOUBLE;
		if (clazz.isArray())
			return new ArrayTypeName(TypeName.get(clazz.getComponentType()));
		return ClassName.get(clazz);
	}
	
	public static TypeName get(Class<?> clazz) {
		return wrapAnnotations(clazz, doGet(clazz));
	}
	
	public static TypeName get(ParameterizedType type) {
		//TODO: fallbacks if the casts fail here
		ParameterizedTypeName enclosing = (ParameterizedTypeName) TypeName.get(type.getOwnerType());
		ClassName name = (ClassName) TypeName.get(type.getRawType());
		List<TypeName> args = Utils.map(Arrays.asList(type.getActualTypeArguments()), TypeName::get);
		return new ParameterizedTypeName(enclosing, name, args, Collections.emptyList());
	}
	
	public static TypeName get(GenericArrayType type) {
		TypeName inner = get(type.getGenericComponentType());
		return new ArrayTypeName(inner);
	}

	public static TypeName get(java.lang.reflect.WildcardType type) {
		TypeName superBound = null, extendsBound = null;
		if (type.getUpperBounds().length > 2 || type.getLowerBounds().length > 2)
			throw new IllegalArgumentException();//TODO: fix?
		if (type.getUpperBounds().length == 1)
			superBound = get(type.getUpperBounds()[0]);
		if (type.getLowerBounds().length == 1)
			extendsBound = get(type.getLowerBounds()[0]);
		
		return new WildcardTypeName(superBound, extendsBound, Collections.emptyList());
	}
	
	private static TypeName get(TypeVariable<?> type) {
		String name = type.getName();
		List<TypeName> bounds = Utils.map(Arrays.asList(type.getAnnotatedBounds()), TypeName::get);
		return new TypeVariableName(name, bounds, Collections.emptyList());
	}
	
	public static TypeName get(AnnotatedType type) {
		return wrapAnnotations(type, get(type.getType()));
	}
	
	public static TypeName get(Type type) {
		if (type instanceof Class<?>) {
			return TypeName.get((Class<?>) type);
		} else if (type instanceof ParameterizedType) {
			return TypeName.get((ParameterizedType) type);
		} else if (type instanceof GenericArrayType) {
			return TypeName.get((GenericArrayType) type);
		} else if (type instanceof TypeVariable) {
			return TypeName.get((TypeVariable<?>) type);
		} else if (type instanceof java.lang.reflect.WildcardType) {
			return get((java.lang.reflect.WildcardType) type);
		} else if (type == null) {
			return null;
		} else {
			throw new IllegalArgumentException();
		}
	}
	
	public static Collection<ClassName> getImportable(TypeName name) {
		Collection<ClassName> result = new LinkedHashSet<>();
		getImportable(result, name);
		return result;
	}
	
	private static void getImportable(Collection<? super ClassName> result, TypeName name) {
		if (name == null)
			return;
		for (AnnotationSpec annotation : name.annotations)
			getImportable(result, annotation.type);//TODO: recurse
		if (name instanceof ClassName) {
			result.add((ClassName) name);
		} else if (name instanceof ArrayTypeName) {
			getImportable(result, ((ArrayTypeName) name).component);
		} else if (name instanceof ParameterizedTypeName) {
			result.add(((ParameterizedTypeName) name).getRaw());
			for (TypeName arg : ((ParameterizedTypeName) name).getArgs())
				getImportable(result, arg);
		} else if (name instanceof WildcardTypeName) {
			getImportable(result, ((WildcardTypeName) name).getSuperBound());
			getImportable(result, ((WildcardTypeName) name).getExtendBound());
		} else if (name instanceof TypeVariableName) {
			
		} else if (name instanceof TypeNameWithAnnotations) {
			getImportable(result, ((TypeNameWithAnnotations) name).inner);
		}
	}
	
	public static final TypeName VOID = new KeywordTypeName("void");
	public static final TypeName BOOLEAN = new KeywordTypeName("boolean");
	public static final TypeName CHAR = new KeywordTypeName("char");
	public static final TypeName BYTE = new KeywordTypeName("byte");
	public static final TypeName SHORT = new KeywordTypeName("short");
	public static final TypeName INT = new KeywordTypeName("int");
	public static final TypeName LONG = new KeywordTypeName("long");
	public static final TypeName FLOAT = new KeywordTypeName("float");
	public static final TypeName DOUBLE = new KeywordTypeName("double");
	public static final ClassName OBJECT = new ClassName("java.lang", "Object");
	
	public final List<? extends AnnotationSpec> annotations;
	private transient String tsCache;
	
	protected TypeName(List<? extends AnnotationSpec> annotations) {
		this.annotations = Objects.requireNonNull(annotations);
	}
	
	@Override
	public abstract void write(CodeWriter out);
	
	public String getReflectionName() {
		return this.toString();
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
	
	protected void writeAnnotations(CodeWriter out) {
		for (AnnotationSpec annotation : this.annotations)
			out.emit("$n ", annotation);
	}
	
	public TypeName withAnnotations(List<? extends AnnotationSpec> annotations) {
		return new TypeNameWithAnnotations(this, annotations);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(this.toString());
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof TypeName))
			return false;
		
		return Objects.equals(this.toString(), obj.toString());
	}
	
	private static class TypeNameWithAnnotations extends TypeName {
		protected final TypeName inner;
		protected TypeNameWithAnnotations(TypeName inner, List<? extends AnnotationSpec> annotations) {
			super(annotations);
			this.inner = inner;
		}

		@Override
		public void write(CodeWriter out) {
			this.writeAnnotations(out);
			inner.write(out);
		}

		@Override
		public TypeName withAnnotations(List<? extends AnnotationSpec> annotations) {
			return new TypeNameWithAnnotations(inner, annotations);
		}
		
	}
	
	private static class KeywordTypeName extends TypeName {
		private final String keyword;
		public KeywordTypeName(String keyword) {
			this(keyword, Collections.emptyList());
		}
		
		protected KeywordTypeName(String keyword, List<? extends AnnotationSpec> annotations) {
			super(annotations);
			this.keyword = keyword;
		}
		
		@Override
		public TypeName withAnnotations(List<? extends AnnotationSpec> annotations) {
			return new KeywordTypeName(keyword, annotations);
		}
		
		@Override
		public void write(CodeWriter out) {
			this.writeAnnotations(out);
			out.print(this.keyword);
		}
	}
	
	public static class ParameterizedTypeName extends TypeName implements Named {
		private final ParameterizedTypeName enclosing;
		private final ClassName raw;
		private final List<TypeName> args;
		
		public ParameterizedTypeName(ParameterizedTypeName enclosing, ClassName name, List<TypeName> args, List<? extends AnnotationSpec> annotations) {
			super(annotations);
			this.enclosing = enclosing;
			this.raw = Objects.requireNonNull(name);
			this.args = args;
		}
		
		public ParameterizedTypeName getEnclosing() {
			return this.enclosing;
		}
		
		public ClassName getRaw() {
			return this.raw;
		}
		
		@Override
		public String getName() {
			return this.raw.toString();
		}
		
		public List<TypeName> getArgs() {
			return this.args;
		}
		
		public ParameterizedTypeName nestedClass(String name, List<TypeName> typeArguments, List<? extends AnnotationSpec> annotations) {
			Objects.requireNonNull(name);
			return new ParameterizedTypeName(this, raw.nestedClass(name), typeArguments, annotations);
		}
		
		@Override
		public ParameterizedTypeName withAnnotations(List<? extends AnnotationSpec> annotations) {
			return new ParameterizedTypeName(enclosing, raw, args, annotations);
		}
		
		@Override
		public void write(CodeWriter out) {
			this.writeAnnotations(out);
			if (this.enclosing == null) {
				out.emit("$T", this.raw);
			} else {
				out.emit("$T.$N", this.enclosing, this.raw);
			}
			if (!this.args.isEmpty())
				out.emit("<$,T>", this.args);
		}
	}
	
	public static class TypeVariableName extends TypeName {
		private String name;
		private final List<TypeName> bounds;
		
		public TypeVariableName(String name, List<TypeName> bounds, List<? extends AnnotationSpec> annotations) {
			super(annotations);
			this.name = name;
			this.bounds = bounds;
		}
		
		@Override
		public TypeVariableName withAnnotations(List<? extends AnnotationSpec> annotations) {
			return new TypeVariableName(name, bounds, annotations);
		}
		
		@Override
		public void write(CodeWriter out) {
			this.writeAnnotations(out);
			out.print(this.name);
		}
	}
	
	public static class ArrayTypeName extends TypeName {
		private final TypeName component;

		public ArrayTypeName(TypeName component, List<? extends AnnotationSpec> annotations) {
			super(annotations);
			this.component = component;
		}
		
		@Override
		public ArrayTypeName withAnnotations(List<? extends AnnotationSpec> annotations) {
			return new ArrayTypeName(component, annotations);
		}
		
		public ArrayTypeName(TypeName component) {
			this(component, Collections.emptyList());
		}
		
		@Override
		public void write(CodeWriter out) {
			this.component.write(out);
			out.print("[]");
		}
	}
	
	public static class WildcardTypeName extends TypeName {
		private final TypeName superBound;
		private final TypeName extendBound;

		public WildcardTypeName(TypeName superBound, TypeName extendBound, List<? extends AnnotationSpec> annotations) {
			super(annotations);
			this.superBound = superBound;
			this.extendBound = extendBound;
		}
		
		@Override
		public WildcardTypeName withAnnotations(List<? extends AnnotationSpec> annotations) {
			return new WildcardTypeName(superBound, extendBound, annotations);
		}
		
		public TypeName getSuperBound() {
			return superBound;
		}
		
		public TypeName getExtendBound() {
			return extendBound;
		}
		
		@Override
		public void write(CodeWriter out) {
			this.writeAnnotations(out);
			if (this.superBound != null)
				out.emit("? super $T", this.superBound);
			else if (this.extendBound != null)
				out.emit("? extends $T", this.extendBound);
			else
				out.print("?");
		}
	}
	
	static class TypeMapper extends SimpleTypeVisitor8<TypeName, Void> {
		@Override
		protected TypeName defaultAction(TypeMirror t, Void p) {
			throw new IllegalArgumentException();
		}
		
		@Override
		public TypeName visitPrimitive(PrimitiveType t, Void p) {
			
			TypeName result;
			switch (t.getKind()) {
				case BOOLEAN:
					result = TypeName.BOOLEAN;
					break;
				case BYTE:
					result = TypeName.BYTE;
					break;
				case CHAR:
					result = TypeName.CHAR;
					break;
				case DOUBLE:
					result = TypeName.DOUBLE;
					break;
				case FLOAT:
					result = TypeName.FLOAT;
					break;
				case INT:
					result = TypeName.INT;
					break;
				case LONG:
					result = TypeName.LONG;
					break;
				case SHORT:
					result = TypeName.SHORT;
					break;
				case VOID:
					result = TypeName.VOID;
					break;
				default:
					return this.defaultAction(t, p);
			}
			
			return wrapAnnotations(t, result);
		}

		@Override
		public TypeName visitArray(ArrayType t, Void p) {
			TypeName component = t.getComponentType().accept(this, p);
			return new ArrayTypeName(component, AnnotationSpec.from(t));
		}

		@Override
		public TypeName visitDeclared(DeclaredType t, Void p) {
			ClassName rawType = ClassName.get((TypeElement) t.asElement());
			
			TypeMirror enclosingType = t.getEnclosingType();
			TypeName enclosing = null;
			if (enclosingType.getKind() != TypeKind.NONE && !t.asElement().getModifiers().contains(Modifier.STATIC))
				enclosing = enclosingType.accept(this, p);
			
			if (t.getTypeArguments().isEmpty() && !(enclosing instanceof ParameterizedTypeName))
				return wrapAnnotations(t, rawType);
			
			List<TypeName> typeArgs = Utils.map(t.getTypeArguments(), arg -> arg.accept(this, null));
			if (enclosing instanceof ParameterizedTypeName)
				return ((ParameterizedTypeName) enclosing).nestedClass(rawType.getSimpleName(), typeArgs, AnnotationSpec.from(t));
			return new ParameterizedTypeName(null, rawType, typeArgs, AnnotationSpec.from(t));
		}

		@Override
		public TypeName visitError(ErrorType t, Void p) {
			return this.visitDeclared(t, p);
		}

		@Override
		public TypeName visitWildcard(WildcardType t, Void p) {
			TypeName superBound = t.getSuperBound() == null ? null : t.getSuperBound().accept(this, p);
			TypeName extendsBound = t.getExtendsBound() == null ? null : t.getExtendsBound().accept(this, p);
			return new WildcardTypeName(superBound, extendsBound, AnnotationSpec.from(t));
		}

		@Override
		public TypeName visitNoType(NoType t, Void p) {
			if (t.getKind() == TypeKind.VOID)
				return TypeName.VOID;
			return super.visitUnknown(t, p);
		}
	}
}
