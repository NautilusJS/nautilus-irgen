package com.mindlin.nautilus.tools.irgen.ir;

import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import com.mindlin.nautilus.tools.irgen.Utils.Writable;
import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
import com.mindlin.nautilus.tools.irgen.util.Named;

public class FieldSpec implements Writable, Named {
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
	public void write(CodeWriter out) {
		if (this.defaultValue == null) {
			out.emit("$M $T $N;", this.modifiers, this.type, this.name);
		} else {
			out.emit("$M $T $N = %s;", this.modifiers, this.type, this.name, this.defaultValue);
		}
	}
	
	public TypeName getType() {
		return TypeName.wrap(type);
	}
	
	@Override
	public String toString() {
		return String.format("FieldSpec{mods=%s,type=%s,name=%s}", this.modifiers, this.type, this.name);
	}

	@Override
	public String getName() {
		return this.name;
	}
}
