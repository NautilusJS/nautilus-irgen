package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;

public class FieldSpec implements Writable {
	public final int modifiers;
	public final TypeMirror type;
	public final String name;
	public final String defaultValue;
	
	public FieldSpec(int modifiers, TypeMirror type, String name) {
		this(modifiers, type, name, null);
	}
	
	public FieldSpec(int modifiers, TypeMirror type, String name, String defaultValue) {
		this.modifiers = modifiers;
		this.type = Objects.requireNonNull(type);
		this.name = Objects.requireNonNull(name);
		this.defaultValue = defaultValue;
	}

	@Override
	public void write(Writer out) throws IOException {
		Utils.writeModifiers(out, this.modifiers);
		out.append(this.type.toString());
		out.append(" ");
		out.append(this.name);
		if (this.defaultValue != null) {
			out.append(" = ");
			out.append(this.defaultValue);
		}
		out.append(";");
	}
	
	@Override
	public String toString() {
		return String.format("FieldSpec{mods=%s,type=%s,name=%s}", this.modifiers, this.type, this.name);
	}
}
