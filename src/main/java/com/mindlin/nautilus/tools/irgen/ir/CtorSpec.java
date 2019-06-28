package com.mindlin.nautilus.tools.irgen.ir;

import java.util.Collection;

import com.mindlin.nautilus.tools.irgen.Utils.Writable;
import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;

public abstract class CtorSpec implements Writable {
	
	public CtorSpec() {
	}
	
	protected void getImports(Collection<? super ClassName> result) {
		for (ParameterSpec parameter : this.getParameters())
			result.addAll(TypeName.getImportable(parameter.getType()));
	}
	
	protected abstract int getModifiers();
	
	protected abstract String getName();
	
	protected abstract Iterable<? extends ParameterSpec> getParameters();
	
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
