package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.IRTypes;
import com.mindlin.nautilus.tools.irgen.IndentWriter;
import com.mindlin.nautilus.tools.irgen.Utils;

public abstract class MethodSpec {
	public int flags;
	protected final String name;
	
	public MethodSpec(String name) {
		this.name = Objects.requireNonNull(name);
	}
	
	protected int getModifiers() {
		return 0;
	}
	
	public abstract String getReturnType();
	
	protected Collection<? extends String> getExceptions() {
		return Collections.emptyList();
	}
	
	public String getName() {
		return this.name;
	}
	
	protected abstract Collection<? extends ParameterSpec> getParameters();
	
	protected abstract void writeBody(IndentWriter out) throws IOException;
	
	protected void writeBefore(IndentWriter out) throws IOException {
	}
	
	public void write(Writer out) throws IOException {
		if (out instanceof IndentWriter)
			write((IndentWriter) out);
		else
			write(new IndentWriter(out));
	}
	
	public void write(IndentWriter out) throws IOException {
		this.writeBefore(out);
		
		Utils.writeModifiers(out, this.getModifiers());
		
		out.print(this.getReturnType());
		out.space();
		out.print(this.getName());
		
		out.print('(');
		Utils.writeAll(out, this.getParameters(), ", ");
		out.println(") {");
		
		out.pushIndent();
		this.writeBody(out);
		out.popIndent();
		
		out.setEOL();
		out.print('}');
	}
	
	public static abstract class OverrideMethod extends MethodSpec {
		public OverrideMethod(String name) {
			super(name);
		}

		@Override
		protected void writeBefore(IndentWriter out) throws IOException {
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
		public String getReturnType() {
			return this.field.type.toString();
		}
		
		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			out.format("return (this.%s);", this.field.name);
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
		public String getReturnType() {
			return this.field.type.toString();
		}
		
		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			String fName = this.field.name;
			String type = this.getReturnType();
			out.format("%s result = %s;\n", type, Utils.getField("this", fName));
			
			if (this.immutable)
				out.format("result = %s;\n", Utils.unmodifiable(type, "result"));
			
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
		public String getReturnType() {
			return this.type.toString();
		}
		
		protected boolean shouldCheckForNull() {
			return Utils.isNonNull(this.type, true);
		}

		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			out.format("%s result = %s;\n", this.type, Utils.invoke("super", this.name));
			
			if (this.shouldCheckForNull())
				out.format("%s.requireNonNull(result);\n", IRTypes.OBJECTS);
			
			out.println("return result;");
		}
	}
}
