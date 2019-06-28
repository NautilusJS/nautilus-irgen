package com.mindlin.nautilus.tools.irgen.ir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;

import com.mindlin.nautilus.tools.irgen.IRTypes;

public abstract class AbstractTreeSpec extends ClassSpec {

	/** Getter that returns a primitive */
	public static final int MF_GPRIMITIVE = (1 << 0);
	/** Getter that returns an object */
	public static final int MF_GOBJECT = (1 << 1);
	/** Getter that returns a child tree */
	public static final int MF_GCHILD = (1 << 2);
	/** Getter that returns a collection of children */
	public static final int MF_GCHILDREN = (1 << 3);
	/** Method that should be used for hashing */
	public static final int MF_HASH = (1 << 4);
	/** Method that should be used for equivalence */
	public static final int MF_EQUIV = (1 << 5);
	
	public final Set<TypeName> parentIfaces = new HashSet<>();
	public TreeImplSpec resolvedParent;
	public TypeName parent = IRTypes.ABSTRACT_BASE;
	/** Base (implementing) type */
	public TypeName baseType;

	public final List<Element> sources = new ArrayList<>();
	
	// Members
	public final List<FieldSpec> declaredFields = new ArrayList<>();
	public final List<MethodSpec> declaredMethods = new ArrayList<>();
	public final List<CtorSpec> constructors = new ArrayList<>();
	
	protected TypeName getBaseTreeType() {
		return this.baseType;
	}
	
	@Override
	protected Collection<CtorSpec> getConstructors() {
		return this.constructors;
	}
	
	@Override
	protected Collection<MethodSpec> getMethods() {
		return this.declaredMethods;
	}

	@Override
	protected int getModifiers() {
		return 0;
	}
	
	@Override
	protected Element[] getSources() {
		return this.sources.toArray(new Element[this.sources.size()]);
	}
}
