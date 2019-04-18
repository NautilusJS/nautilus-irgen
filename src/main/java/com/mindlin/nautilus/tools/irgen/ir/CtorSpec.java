package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.Writer;

import com.mindlin.nautilus.tools.irgen.IndentWriter;
import com.mindlin.nautilus.tools.irgen.Utils;

public abstract class CtorSpec {
	
	public CtorSpec() {
	}
	
	protected abstract int getModifiers();
	
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
		Utils.writeModifiers(out, this.getModifiers());
		
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
