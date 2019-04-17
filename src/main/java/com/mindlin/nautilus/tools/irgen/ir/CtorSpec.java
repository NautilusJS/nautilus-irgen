package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.lang.model.element.Modifier;

import com.mindlin.nautilus.tools.irgen.IndentWriter;
import com.mindlin.nautilus.tools.irgen.Utils;

public abstract class CtorSpec {
	
	public CtorSpec() {
		// TODO Auto-generated constructor stub
	}
	
	protected abstract Set<Modifier> getModifiers();
	
	protected abstract String getName();
	
	protected abstract Iterable<ParameterSpec> getParameters();
	
	protected abstract void writeBody(IndentWriter out) throws IOException;

	public void write(Writer out) throws IOException {
		if (out instanceof IndentWriter)
			write((IndentWriter) out);
		else
			write(new IndentWriter(out));
	}
	
	public void write(IndentWriter out) throws IOException {
		Set<Modifier> modifiers = this.getModifiers();
		Utils.writeModifiers(out, modifiers);
		
		out.print(getName());
		out.print("(");
		Utils.writeAll(out, getParameters(), ", ");
		out.println(") {");
		
		out.pushIndent();
		this.writeBody(out);
		out.popIndent();
		
		out.setEOL();
		out.print("}");
	}
}
