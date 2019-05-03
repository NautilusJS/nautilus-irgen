package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;

import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public abstract class CtorSpec implements Writable {
	
	public CtorSpec() {
	}
	
	protected abstract int getModifiers();
	
	protected abstract String getName();
	
	protected abstract Iterable<ParameterSpec> getParameters();
	
	protected abstract void writeBody(CodeWriter out) throws IOException;
	
	@Override
	public void write(CodeWriter out) throws IOException {
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
