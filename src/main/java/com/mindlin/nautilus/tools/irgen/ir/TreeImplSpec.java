package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.mindlin.nautilus.tools.irgen.IRTypes;
import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.OverrideMethod;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;

public class TreeImplSpec extends ClassSpec {
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
	
	public final TypeElement source;
	public final List<Element> sources = new ArrayList<>();
	boolean extensible = false;
	public final Set<TypeName> parentIfaces = new HashSet<>();
	public TypeName parent = IRTypes.ABSTRACT_BASE;
	/** Base (implementing) type */
	public TypeName baseType;
	String name;
	String kind;
	// Members
	public final List<FieldSpec> declaredFields = new ArrayList<>();
	public final List<MethodSpec> declaredMethods = new ArrayList<>();
	public final List<CtorSpec> constructors = new ArrayList<>();
	
	// For child resolution
	/** Contains all fields (declared & inherited) by name */
	public final Map<String, FieldSpec> fields = new LinkedHashMap<>();
	/** Contains all getters (declared & inherited) by name */
	public final Map<String, GetterSpec> getters = new HashMap<>();
	
	
	public TreeImplSpec(TypeElement source, String name) {
		this.source = source;
		this.name = name;
		
		if (source != null)
			this.sources.add(source);
	}
	
	@Override
	protected Element[] getSources() {
//			if (this.source == null)
//				return new Element[0];
//			return new Element[] {this.source};
		return this.sources.toArray(new Element[this.sources.size()]);
	}
	
	@Override
	protected void getImports(Collection<? super ClassName> result) {
		super.getImports(result);
//		result.add(ClassName.get(Objects.class));
//		result.add(ClassName.get(Collections.class));
//		result.add(ClassName.get(List.class));
		result.add(IRTypes.TREE);
		result.add(IRTypes.SOURCEPOSITION);
		result.add(IRTypes.SOURCERANGE);
		
		// Import field types (if possible)
		for (FieldSpec field : this.declaredFields)
			this.addClasses(field.getType(), result);
		
		// Import tree.* and because we use so many things from them
//		result.add(new ClassName(IRTypes.RAW_PACKAGE, null));
		if (this.baseType instanceof ClassName)
			result.add((ClassName) this.baseType);
	}
	
	@Override
	protected Optional<TypeName> getSuper() {
		return Optional.of(this.parent);
	}
	
	@Override
	protected Collection<TypeName> getImplementing() {
		return Arrays.asList(ClassName.get(this.source));
	}
	
	@Override
	protected String getSimpleName() {
		return this.name;
	}
	
	protected TypeName getBaseTreeType() {
		return this.baseType;
	}
	
	public List<ParameterSpec> getFieldParams() {
		List<ParameterSpec> result = new ArrayList<>(this.declaredFields.size());
		for (FieldSpec field : this.declaredFields)
			result.add(new ParameterSpec(true, TypeName.wrap(field.type), field.name));
		return result;
	}
	
	public MethodSpec lookupMethod(String name) {
		//TODO
		return null;
	}
	
	protected void addMethod(MethodSpec spec, int flags) {
		spec.flags = flags;
		this.declaredMethods.add(spec);
	}
	
	public void buildMethods() {
		// Add hash()
		this.addMethod(new HashInnerMethodSpec(), 0);
		// Add equivalentTo(Tree)
		this.addMethod(new EquivalentToMethodSpec(), 0);
		// Add equivalentTo(self)
		this.addMethod(new EquivalentToSelfMethodSpec(), 0);
		// Add equals(Object)
		// Add equals(self)
	}
	
	@Override
	protected String getPackage() {
		return IRTypes.IMPL_PACKAGE;
	}
	
	@Override
	protected Collection<MethodSpec> getMethods() {
		return this.declaredMethods;
	}
	
	protected Collection<MethodSpec> getMethods(int filter) {
		List<MethodSpec> result = new ArrayList<>();
		for (MethodSpec spec : this.declaredMethods)
			if ((spec.flags & filter) == filter)
				result.add(spec);
		return result;
	}

	@Override
	protected int getModifiers() {
		return super.getModifiers() | Modifier.PUBLIC;
	}
	
	@Override
	protected void writeBody(CodeWriter writer) throws IOException {
		super.writeBody(writer);
		for (FieldSpec field : this.declaredFields) {
			field.write(writer);
			writer.setEOL();
		}
		
		if (!this.declaredFields.isEmpty())
			writer.println();
		
		for (CtorSpec ctor : this.constructors) {
			ctor.write(writer);
			writer.println();
		}
		
		if (!this.constructors.isEmpty())
			writer.println();
		
		for (MethodSpec method : this.declaredMethods) {
			method.write(writer);
			writer.println();
			writer.setEOL();
		}
	}
	
	@Override
	public String toString() {
		return String.format("TreeImplSpec{ext=%b,pif=%s,parent=%s,name=%s,kind=%s,fields=%s,methods=%s}",
				this.extensible,
				this.parentIfaces,
				this.parent,
				this.name,
				this.kind,
				this.fields,
				this.declaredMethods);
	}
	
	/**
	 * C'tor that takes start & end and merges them into a SourceRange
	 */
	public class RangeMergeCtorSpec extends CtorSpec {
		private ParameterSpec START_PARAM = new ParameterSpec(true, IRTypes.SOURCEPOSITION, "start");
		private ParameterSpec END_PARAM = new ParameterSpec(true, IRTypes.SOURCEPOSITION, "end");
		private final List<ParameterSpec> parameters;
		public RangeMergeCtorSpec(List<ParameterSpec> parameters) {
			this.parameters = parameters;
		}
		
		@Override
		protected int getModifiers() {
			return Modifier.PUBLIC;
		}

		@Override
		protected String getName() {
			return TreeImplSpec.this.getSimpleName();
		}

		@Override
		protected Iterable<ParameterSpec> getParameters() {
			List<ParameterSpec> result = new ArrayList<>();
			result.add(START_PARAM);
			result.add(END_PARAM);
			result.addAll(this.parameters);
			return result;
		}
		
		@Override
		protected void writeBody(CodeWriter out) {
			out.emit("this(new $T($N, $N), $,N);", IRTypes.SOURCERANGE, START_PARAM, END_PARAM, this.parameters);
			out.setEOL();
		}
	}
	
	public class ForwardingCtorSpec extends CtorSpec {
		private ParameterSpec RANGE_PARAM = new ParameterSpec(true, IRTypes.SOURCERANGE, "range");
		private List<ParameterSpec> superParams;
		public ForwardingCtorSpec(List<ParameterSpec> superParams) {
			this.superParams = superParams;
		}
		
		@Override
		protected int getModifiers() {
			return Modifier.PUBLIC;
		}

		@Override
		protected String getName() {
			return TreeImplSpec.this.getSimpleName();
		}

		@Override
		protected List<ParameterSpec> getParameters() {
			List<ParameterSpec> result = new ArrayList<>();
			result.add(RANGE_PARAM);
			result.addAll(Utils.map(this.superParams, (param, idx) -> param.withName("$s" + idx)));
			result.addAll(TreeImplSpec.this.getFieldParams());
			return result;
		}
		
		@Override
		protected void writeBody(CodeWriter out) {
			List<ParameterSpec> params = this.getParameters();
			
			// Write call to super
			//TODO: null checks
			out.emit("super($,N);", params.subList(0, this.superParams.size() + 1));
			out.println();
			
			int i = this.superParams.size() + 1;
			for (FieldSpec localField: TreeImplSpec.this.declaredFields) {
				ParameterSpec param = params.get(i++);
				boolean isPrimitive = IRTypes.isPrimitive(localField.type);
				if (isPrimitive) {
					out.emit("this.$N = $N;\n", localField, param);
					continue;
				}
				
				boolean nnCheck = Utils.isNonNull(localField.type, true);
				boolean isCollection = IRTypes.isCollection(localField.type);
				if (nnCheck) {
					out.emit("this.$N = $T.requireNonNull($N);", localField, Objects.class, param);
				} else if (isCollection) {
					//TODO: check element nonnull
					out.emit("this.$N = $N;", localField, param);
				} else {
					out.emit("this.$N = $N;", localField, param);
				}
				
				out.setEOL();
			}
		}
	}
	
	/**
	 * <pre>
	 * {@literal @}Override
	 * protected int hash() {
	 * 	return Objects.hash([all properties & children]);
	 * }
	 * </pre>
	 */
	public class HashInnerMethodSpec extends OverrideMethod {
		public HashInnerMethodSpec() {
			super("hash");
		}

		@Override
		protected int getModifiers() {
			return Modifier.PROTECTED;
		}

		@Override
		public TypeName getReturnType() {
			return TypeName.INT;
		}

		@Override
		protected void writeBody(CodeWriter out) {
			List<String> params = Utils.map(getMethods(MF_HASH), spec -> Utils.invoke("this", spec.getName()));
			if (params.isEmpty())
				out.print("return super.hash();");
			else
				out.emit("return $T.hash(super.hash(), $,N);", Objects.class, params);
			out.setEOL();
		}
	}
	
	public class EquivalentToMethodSpec extends OverrideMethod {
		public EquivalentToMethodSpec() {
			super("equivalentTo");
		}

		@Override
		public TypeName getReturnType() {
			return TypeName.BOOLEAN;
		}
		
		@Override
		protected Collection<ParameterSpec> getParameters() {
			return Arrays.asList(new ParameterSpec(true, IRTypes.TREE, "other"));
		}

		@Override
		protected void writeBody(CodeWriter out) {
			TypeName type = TreeImplSpec.this.getBaseTreeType();
			out.emit("return (other instanceof $T) && this.equivalentTo(($T) other);", type, type);
			out.setEOL();
		}
	}
	
	public class EquivalentToSelfMethodSpec extends MethodSpec {

		public EquivalentToSelfMethodSpec() {
			super("equivalentTo");
		}
		
		@Override
		protected int getModifiers() {
			return super.getModifiers() | Modifier.PROTECTED;
		}

		@Override
		public TypeName getReturnType() {
			return TypeName.BOOLEAN;
		}
		
		@Override
		protected Collection<ParameterSpec> getParameters() {
			return Arrays.asList(new ParameterSpec(true, TreeImplSpec.this.getBaseTreeType(), "other"));
		}

		@Override
		protected void writeBody(CodeWriter out) {
			out.println("if (this == other)");
			out.indentln("return true;");
			
			out.print("return super.equivalentTo(other)");// Make '&&' chaining easier
			out.pushIndent(2);
			
			// Primitive props: (a == b)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GPRIMITIVE | MF_EQUIV))
				out.emit("\n&& (this.$N() == other.$N())", spec, spec);
			
			// Object props: Objects.equals(a, b)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GOBJECT | MF_EQUIV))
				out.emit("\n&& $T.equals(this.$N(), other.$N())", Objects.class, spec, spec);
			
			// Child: Tree.equivalentTo(Tree, Tree)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GCHILD | MF_EQUIV))
				out.emit("\n&& $T.equivalentTo(this.$N(), other.$N())", IRTypes.TREE, spec, spec);
			
			// Children: Tree.equivalentTo(Collection<Tree>, Collection<Tree>)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GCHILDREN | MF_EQUIV))
				out.emit("\n&& $T.equivalentTo(this.$N(), other.$N())", IRTypes.TREE, spec, spec);
			
			out.println(";");
			out.popIndent(2);
		}
		
	}
}