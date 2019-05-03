package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public abstract class MethodSpec implements Writable, Named {
	public int flags;
	protected final String name;
	
	public MethodSpec(String name) {
		this.name = Objects.requireNonNull(name);
	}
	
	protected int getModifiers() {
		return 0;
	}
	
	public abstract TypeName getReturnType();
	
	@Override
	public String getName() {
		return this.name;
	}
	
	protected void writeBefore(@SuppressWarnings("unused") CodeWriter out) throws IOException {
		
	}
	
	protected abstract Collection<? extends ParameterSpec> getParameters();
	
	protected abstract void writeBody(CodeWriter out) throws IOException;
	
	@Override
	public void write(CodeWriter out) throws IOException {
		this.writeBefore(out);
		
		out.emit("$M $T $N($,n) {", this.getModifiers(), this.getReturnType(), this.getName(), this.getParameters());
		out.println();
		
		out.pushIndent();
		this.writeBody(out);
		out.popIndent();
		
		out.setEOL();
		out.print('}');
		out.setEOL();
	}
	
	public static abstract class OverrideMethod extends MethodSpec {
		public OverrideMethod(String name) {
			super(name);
		}

		@Override
		protected void writeBefore(CodeWriter out) throws IOException {
			out.println("@Override");
			super.writeBefore(out);
		}
		
		@Override
		protected Collection<? extends ParameterSpec> getParameters() {
			return Collections.emptyList();
		}
		
		@Override
		protected int getModifiers() {
			return super.getModifiers() | Modifier.PUBLIC;
		}
	}
	
	public static class SimpleGetterSpec extends OverrideMethod {
		final FieldSpec field;
		public SimpleGetterSpec(int flags, String name, FieldSpec field) {
			super(name);
			this.flags = flags;
			this.field = field;
		}
		
		@Override
		protected int getModifiers() {
			return Modifier.PUBLIC;
		}

		@Override
		public TypeName getReturnType() {
			return TypeName.wrap(this.field.type);
		}
		
		@Override
		protected void writeBody(CodeWriter out) throws IOException {
			out.emit("return this.$N;", this.field);
			out.println();
		}
	}
	
	public static class CollectionGetterSpec extends OverrideMethod {
		final FieldSpec field;
		final boolean immutable;
		public CollectionGetterSpec(int flags, String name, FieldSpec field, boolean immutable) {
			super(name);
			this.flags = flags;
			this.field = Objects.requireNonNull(field);
			this.immutable = immutable;
		}
		
		@Override
		public TypeName getReturnType() {
			return TypeName.wrap(this.field.type);
		}
		
		@Override
		protected void writeBody(CodeWriter out) throws IOException {
			TypeName type = this.getReturnType();
			out.emit("$T result = this.$N;", type, this.field);
			out.println();
			
			if (this.immutable) {
				out.emit("result = $T.$N($N);", Collections.class, Utils.immutableMethod(type), "result");
				out.println();
			}
			
			out.println("return result;");
		}
	}
	
	public static class NarrowGetterSpec extends OverrideMethod {
		TypeMirror type;
		
		public NarrowGetterSpec(int flags, String name, TypeMirror type) {
			super(name);
			this.flags = flags;
			this.type = type;
		}

		@Override
		public TypeName getReturnType() {
			return TypeName.wrap(this.type);
		}
		
		protected boolean shouldCheckForNull() {
			return Utils.isNonNull(this.type, true);
		}

		@Override
		protected void writeBody(CodeWriter out) throws IOException {
			out.emit("$T result = super.$N();", this.type, this.name);
			out.println();
			
			if (this.shouldCheckForNull()) {
				out.emit("$T.requireNonNull(result);", Objects.class);
				out.println();
			}
			
			out.println("return result;");
		}
	}
}
