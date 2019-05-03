package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.StringWriter;

import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public class ParameterSpec implements Writable, Named {
	protected final boolean isFinal;
	protected final TypeName type;
	protected final boolean varargs;
	protected final String name;
	
	public ParameterSpec(TypeName type, String name) {
		this(false, type, false, name);
	}
	
	public ParameterSpec(boolean isFinal, TypeName type, String name) {
		this(isFinal, type, false, name);
	}
	
	public ParameterSpec(TypeName type, boolean varargs, String name) {
		this(false, type, varargs, name);
	}
	
	public ParameterSpec(boolean isFinal, TypeName type, boolean varargs, String name) {
		this.isFinal = isFinal;
		this.type = type;
		this.varargs = varargs;
		this.name = name;
	}
	
	public ParameterSpec withName(String name) {
		return new ParameterSpec(this.isFinal, this.type, this.varargs, name);
	}
	
	public TypeName getType() {
		return this.type;
	}
	
	@Override
	public String getName() {
		return this.name;
	}
	
	@Override
	public String toString() {
		try (StringWriter sw = new StringWriter();
				CodeWriter cw = new CodeWriter(sw)){
			this.write(cw);
			return sw.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void write(CodeWriter out) {
		if (this.isFinal)
			out.append("final ");
		
		if (this.varargs)
			out.emit("$T...$N", this.getType(), this);
		else
			out.emit("$T $N", this.getType(), this);
	}
}
