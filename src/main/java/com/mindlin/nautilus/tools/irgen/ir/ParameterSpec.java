package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public class ParameterSpec implements Writable {
	protected boolean isFinal;
	protected String type;
	protected boolean varargs;
	protected String name;
	
	public ParameterSpec(String type, String name) {
		this(false, type, false, name);
	}
	
	public ParameterSpec(boolean isFinal, String type, String name) {
		this(isFinal, type, false, name);
	}
	
	public ParameterSpec(String type, boolean varargs, String name) {
		this(false, type, varargs, name);
	}
	
	public ParameterSpec(boolean isFinal, String type, boolean varargs, String name) {
		this.isFinal = isFinal;
		this.type = type;
		this.varargs = varargs;
		this.name = name;
	}
	
	public String getType() {
		return this.type;
	}
	
	public String getName() {
		return this.name;
	}
	
	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		try {
			write(sw);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return sw.toString();
	}
	
	@Override
	public void write(Writer out) throws IOException {
		if (this.isFinal)
			out.append("final ");
		
		out.append(getType());
		
		if (this.varargs)
			out.append("...");
		else
			out.append(" ");
		
		out.append(getName());
	}
}
