package com.mindlin.nautilus.tools.irgen.ir;

import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public abstract class CtorSpec implements Writable {
	
	public CtorSpec() {
	}
	
	protected abstract int getModifiers();
	
	protected abstract String getName();
	
	protected abstract Iterable<ParameterSpec> getParameters();
	
	protected abstract void writeBody(CodeWriter out);
	
	@Override
	public void write(CodeWriter out) {
		out.emit("$M $N($,n) {", this.getModifiers(), this.getName(), this.getParameters());
		out.setEOL();
		
		out.pushIndent();
		this.writeBody(out);
		out.popIndent();
		
		out.setEOL();
		out.print("}");
		out.setEOL();
	}
}
