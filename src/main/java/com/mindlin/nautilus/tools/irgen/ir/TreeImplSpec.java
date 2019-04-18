package com.mindlin.nautilus.tools.irgen.ir;

import static com.mindlin.nautilus.tools.irgen.Utils.invoke;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.mindlin.nautilus.tools.irgen.IRTypes;
import com.mindlin.nautilus.tools.irgen.IndentWriter;
import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.OverrideMethod;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;

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
	public final Set<String> parentIfaces = new HashSet<>();
	public String parent = IRTypes.ABSTRACT_BASE;
	/** Base (implementing) type */
	public String baseType;
	String name;
	String kind;
	// Members
	public final List<FieldSpec> declaredFields = new ArrayList<>();
	public final List<MethodSpec> declaredMethods = new ArrayList<>();
	public final List<CtorSpec> constructors = new ArrayList<>();
	
	// For child resolution
	/** Contains all fields (declared & inherited) by name */
	public final Map<String, FieldSpec> fields = new HashMap<>();
	/** Contains all getters (declared & inherited) by name */
	public final Map<String, GetterSpec> getters = new HashMap<>();
	/** Contains field parameters (not including range stuff) for constructors */
	public final List<ParameterSpec> params = new ArrayList<>();
	
	
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
	protected Optional<String> getSuper() {
		return Optional.of(this.parent);
	}
	
	@Override
	protected Collection<String> getImplementing() {
		return Arrays.asList(this.source.getQualifiedName().toString());
	}
	
	@Override
	protected String getSimpleName() {
		return this.name;
	}
	
	protected String getBaseTreeType() {
		return this.baseType;//TODO
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
		int result = Modifier.PUBLIC;
//			if (!this.extensible)
//				result |= Modifier.FINAL;
		
		return result;
	}
	
	@Override
	protected void addCtBody(CtClass clz) throws NotFoundException, CannotCompileException, IOException {
		super.addCtBody(clz);
		
		for (FieldSpec field : this.declaredFields) {
			//TODO
		}
		
		for (CtorSpec ctor : this.constructors) {
			//TODO
		}
		
		for (MethodSpec method : this.declaredMethods);
			//clz.addMethod(method.toCt(clz));
	}
	
	@Override
	protected void writeBody(IndentWriter writer) throws IOException {
		super.writeBody(writer);
		for (FieldSpec field : this.declaredFields) {
			field.write(writer);
			writer.setEOL();
		}
		
		writer.println();
		
		for (CtorSpec ctor : this.constructors) {
			ctor.write(writer);
			writer.setEOL();
		}
		
		for (MethodSpec method : this.declaredMethods) {
			method.write(writer);
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
		protected void writeBody(IndentWriter out) throws IOException {
			out.write("this(");
			
			out.write("new ");
			out.write(IRTypes.SOURCERANGE);
			out.write("(");
			out.write(START_PARAM.getName());
			out.write(", ");
			out.write(END_PARAM.getName());
			out.write(")");
			
			for (ParameterSpec parameter : this.parameters) {
				out.write(", ");
				out.write(parameter.getName());
			}
			
			out.write(");");
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
		protected Iterable<ParameterSpec> getParameters() {
			List<ParameterSpec> result = new ArrayList<>();
			result.add(RANGE_PARAM);
			result.addAll(this.superParams);
			for (FieldSpec field: TreeImplSpec.this.declaredFields)
				result.add(new ParameterSpec(true, field.type.toString(), field.name));
			return result;
		}
		
		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			// Write call to super
			out.write("super(range");
			for (ParameterSpec superParam : this.superParams) {
				out.format(", ");
				out.write(superParam.getName());
				//TODO: null checks
			}
			out.println(");");
			
			for (FieldSpec localField: TreeImplSpec.this.declaredFields) {
				boolean isPrimitive = IRTypes.isPrimitive(localField.type);
				if (isPrimitive) {
					out.format("this.%1$s = %1$s;\n", localField.name);
					continue;
				}
				
				boolean nnCheck = Utils.isNonNull(localField.type, true);
				if (nnCheck) {
					out.format("%s.requireNonNull(%s);\n", IRTypes.OBJECTS, localField.name);
				}
				
				boolean isCollection = IRTypes.isCollection(localField.type);
				if (isCollection) {
					//TODO: check element nonnull
				}
				
				out.format("this.%1$s = %1$s;\n", localField.name);
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
		public String getReturnType() {
			return "int";
		}

		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			List<String> params = Utils.map(getMethods(MF_HASH), spec -> Utils.invoke("this", spec.getName()));
			out.format("return %s;", Utils.invoke(IRTypes.OBJECTS, "hash", params));
			out.setEOL();
		}
	}
	
	public class EquivalentToMethodSpec extends OverrideMethod {
		public EquivalentToMethodSpec() {
			super("equivalentTo");
		}

		@Override
		public String getReturnType() {
			return "boolean";
		}
		
		@Override
		protected Collection<ParameterSpec> getParameters() {
			return Arrays.asList(new ParameterSpec(true, IRTypes.TREE_CLASS, "other"));
		}

		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			out.format("return (other instanceof %s) && this.equivalentTo((%s) other);", TreeImplSpec.this.getBaseTreeType(), TreeImplSpec.this.getBaseTreeType());
			out.setEOL();
		}
	}
	
	public class EquivalentToSelfMethodSpec extends MethodSpec {

		public EquivalentToSelfMethodSpec() {
			super("equivalentTo");
		}

		@Override
		public String getReturnType() {
			return "boolean";
		}
		
		@Override
		protected Collection<ParameterSpec> getParameters() {
			return Arrays.asList(new ParameterSpec(true, TreeImplSpec.this.getBaseTreeType(), "other"));
		}

		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			out.println("if (this == other)");
			out.indentln("return true;");
			
			out.println("if (other == null || this.getKind() != other.getKind() || this.hashCode() != other.hashCode())");
			out.indentln("return false;");
			
			out.print("return true");// Make '&&' chaining easier
			out.pushIndent(2);
			
			// Primitive props: (a == b)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GPRIMITIVE | MF_EQUIV))
				out.format("\n&& (%s == %s)", invoke("this", spec.getName()), invoke("other", spec.getName()));
			
			// Object props: Objects.equals(a, b)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GOBJECT | MF_EQUIV))
				out.format("\n&& %s", Utils.equals(invoke("this", spec.getName()), invoke("other", spec.getName())));
			
			// Child: Tree.equivalentTo(Tree, Tree)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GCHILD | MF_EQUIV))
				out.format("\n&& %s", invoke(IRTypes.TREE_CLASS, "equivalentTo", invoke("this", spec.getName()), invoke("other", spec.getName())));
			
			// Children: Tree.equivalentTo(Collection<Tree>, Collection<Tree>)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(MF_GCHILDREN | MF_EQUIV))
				out.format("\n&& %s", invoke(IRTypes.TREE_CLASS, "equivalentTo", invoke("this", spec.getName()), invoke("other", spec.getName())));
			
			out.println(";");
			out.popIndent(2);
		}
		
	}
}