package com.mindlin.nautilus.tools.irgen.ir;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.lang.model.element.TypeElement;

import com.mindlin.nautilus.tools.irgen.IRTypes;
import com.mindlin.nautilus.tools.irgen.NameHelper;
import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.OverrideMethod;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.TypeName.ParameterizedTypeName;

public class TreeImplSpec extends AbstractTreeSpec {
	public final TypeElement source;
	String name;
	
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
	
	protected void addClasses(TypeName base, Collection<? super ClassName> result) {
		if (base == null)
			return;
		
		if (base instanceof ClassName) {
			result.add((ClassName) base);
		} else if (base instanceof TypeName.ParameterizedTypeName) {
			TypeName.ParameterizedTypeName pft = (ParameterizedTypeName) base;
			result.add(pft.getRaw());
			if (pft.getArgs() != null) {
				for (TypeName name : pft.getArgs())
					addClasses(name, result);
			}
		} else if (base instanceof TypeName.WildcardTypeName) {
			TypeName.WildcardTypeName wft = (TypeName.WildcardTypeName) base;
			addClasses(wft.getSuperBound(), result);
			addClasses(wft.getExtendBound(), result);
		}
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
	
	protected void addLocalFieldParams(NameHelper names, List<? super ForwardingParameterSpec> params) {
		for (FieldSpec field : this.declaredFields) {
			String pName = field.getName();
			if (names != null)
				pName = names.add(pName);
			TypeName pType = field.getType();
			ForwardingParameterSpec param = new ForwardingParameterSpec(true, pType, false, pName);
			param.target = field;
			params.add(param);
		}
	}
	
	protected void addFieldParams(NameHelper names, List<? super ForwardingParameterSpec> params) {
		if (this.resolvedParent != null)
			this.resolvedParent.addFieldParams(names, params);
		this.addLocalFieldParams(names, params);
	}
	
	public List<? extends ForwardingParameterSpec> getFieldParams(NameHelper names) {
		if (names == null)
			names = new NameHelper();
		
		List<ForwardingParameterSpec> result = new ArrayList<>();
		if (this.resolvedParent != null)
			this.resolvedParent.addFieldParams(names, result);
		// Mark parent nonnull injection & update field types
		Iterator<ForwardingParameterSpec> resultIterator = result.iterator();
		while (resultIterator.hasNext()) {
			ForwardingParameterSpec param = resultIterator.next();
			if (param.target != null) {
				FieldSpec localField = this.fields.get(param.target.name);
				
			}
		}
		
		this.addLocalFieldParams(names, result);
		
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
		
		for (MethodSpec method : this.declaredMethods) {
			method.write(writer);
			writer.println();
			writer.setEOL();
		}
	}
	
	@Override
	public String toString() {
		return String.format("TreeImplSpec{pif=%s,parent=%s,name=%s,fields=%s,methods=%s}",
				this.parentIfaces,
				this.parent,
				this.name,
				this.fields,
				this.declaredMethods);
	}
	
	/**
	 * C'tor that takes start & end and merges them into a SourceRange
	 */
	public class RangeMergeCtorSpec extends CtorSpec {
		private List<ParameterSpec> paramCache;
		public RangeMergeCtorSpec() {
			
		}
		
		@Override
		protected int getModifiers() {
			return Modifier.PUBLIC;
		}
		
		@Override
		protected void getImports(Collection<? super ClassName> result) {
			super.getImports(result);
			result.add(IRTypes.SOURCEPOSITION);
			result.add(IRTypes.SOURCERANGE);
		}

		@Override
		protected String getName() {
			return TreeImplSpec.this.getSimpleName();
		}

		@Override
		protected List<? extends ParameterSpec> getParameters() {
			if (this.paramCache == null) { 
				NameHelper names = new NameHelper();
				this.paramCache = new ArrayList<>();
				this.paramCache.add(new ParameterSpec(true, IRTypes.SOURCEPOSITION, names.add("start")));
				this.paramCache.add(new ParameterSpec(true, IRTypes.SOURCEPOSITION, names.add("end")));
				TreeImplSpec.this.addFieldParams(names, this.paramCache);
			}
			
			return this.paramCache;
		}
		
		@Override
		protected void writeBody(CodeWriter out) {
			List<? extends ParameterSpec> parameters = this.getParameters();
			final ParameterSpec paramStart = parameters.get(0), paramEnd = parameters.get(1);
			
			if (parameters.size() == 2)
				out.emit("this(new $T($N, $N));", IRTypes.SOURCERANGE, paramStart, paramEnd);
			else
				out.emit("this(new $T($N, $N), $,N);", IRTypes.SOURCERANGE, paramStart, paramEnd, parameters.subList(2, parameters.size()));
			out.setEOL();
		}
	}
	
	public class ForwardingCtorSpec extends CtorSpec {
		private List<ForwardingParameterSpec> paramCache;
		public ForwardingCtorSpec() {
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
		protected List<? extends ForwardingParameterSpec> getParameters() {
			if (this.paramCache == null) {
				List<ForwardingParameterSpec> result = this.paramCache = new ArrayList<>();
				NameHelper names = new NameHelper();
				
				ForwardingParameterSpec rangeParam = new ForwardingParameterSpec(true, IRTypes.SOURCERANGE, false, names.add("range"));
				rangeParam.injectNonNull = true;
				result.add(rangeParam);
				
				result.addAll(TreeImplSpec.this.getFieldParams(names));
			}
			
			return this.paramCache;
		}
		
		@Override
		protected void writeBody(CodeWriter out) {
			List<? extends ForwardingParameterSpec> params = this.getParameters();
			int localFieldParamCount = TreeImplSpec.this.declaredFields.size();
			
			// Write call to super
			out.print("super(");
			
			// Forward range param
			//TODO: should we allow null ranges?
			ParameterSpec rangeParam = params.get(0);
			out.emit("$T.requireNonNull($N)", Objects.class, rangeParam);
			
			// Call super, injecting nonnull checks if delta
			for (ForwardingParameterSpec param : params.subList(1, params.size() - localFieldParamCount)) {
				if (param.injectNonNull)
					out.emit(", $T.requireNonNull($N)", Objects.class, param);
				else
					out.emit(", $N", param);
			}
			out.println(");");
			
			// Generate field assignments
			for (ForwardingParameterSpec param : params.subList(params.size() - localFieldParamCount, params.size())) {
				FieldSpec field = param.target;
				if (field == null) {
					out.format("//XXX Error: Missing field for parameter %s", param.name);
				} else if (IRTypes.isPrimitive(field.type)) {
					out.emit("this.$N = $N;", field, param);
				} else if (param.nonNull) {
					out.emit("this.$N = $T.requireNonNull($N);", field, Objects.class, param);
				} else {
					out.emit("this.$N = $N;", field, param);
				}
				out.setEOL();
			}
		}
	}
	
	private static class ForwardingParameterSpec extends ParameterSpec {
		public ParameterSpec parent = null;
		public FieldSpec target = null;
		public boolean nonNull = false;
		public boolean injectNonNull = false;
		
		public ForwardingParameterSpec(boolean isFinal, TypeName type, boolean varargs, String name) {
			super(isFinal, type, varargs, name);
		}
		
		public ForwardingParameterSpec(ParameterSpec param) {
			this(param.isFinal, param.type, param.varargs, param.name);
			this.parent = param;
		}
		
		@Override
		public ForwardingParameterSpec withName(String name) {
			if (Objects.equals(this.name, name))
				return this;
			ForwardingParameterSpec result = new ForwardingParameterSpec(this);
			result.parent = this.parent;
			result.target = this.target;
			result.injectNonNull = this.injectNonNull;
			return result;
		}
		
		@Override
		public void write(CodeWriter out) {
			// TODO Auto-generated method stub
			super.write(out);
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
		public void getImports(Collection<? super ClassName> result) {
			super.getImports(result);
			if (!getMethods(AbstractTreeSpec.MF_HASH).isEmpty())
				result.add(ClassName.get(Objects.class));
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
			List<String> params = Utils.map(getMethods(AbstractTreeSpec.MF_HASH), spec -> Utils.invoke("this", spec.getName()));
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
		public void getImports(Collection<? super ClassName> result) {
			super.getImports(result);
			result.addAll(TypeName.getImportable(TreeImplSpec.this.getBaseTreeType()));
			result.add(IRTypes.TREE);
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
		public void getImports(Collection<? super ClassName> result) {
			super.getImports(result);
			result.addAll(TypeName.getImportable(TreeImplSpec.this.getBaseTreeType()));
			if (!TreeImplSpec.this.getMethods(AbstractTreeSpec.MF_GOBJECT | AbstractTreeSpec.MF_EQUIV).isEmpty())
				result.add(ClassName.get(Objects.class));
			result.add(IRTypes.TREE);
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
			for (MethodSpec spec : TreeImplSpec.this.getMethods(AbstractTreeSpec.MF_GPRIMITIVE | AbstractTreeSpec.MF_EQUIV))
				out.emit("\n&& (this.$N() == other.$N())", spec, spec);
			
			// Object props: Objects.equals(a, b)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(AbstractTreeSpec.MF_GOBJECT | AbstractTreeSpec.MF_EQUIV))
				out.emit("\n&& $T.equals(this.$N(), other.$N())", Objects.class, spec, spec);
			
			// Child: Tree.equivalentTo(Tree, Tree)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(AbstractTreeSpec.MF_GCHILD | AbstractTreeSpec.MF_EQUIV))
				out.emit("\n&& $T.equivalentTo(this.$N(), other.$N())", IRTypes.TREE, spec, spec);
			
			// Children: Tree.equivalentTo(Collection<Tree>, Collection<Tree>)
			for (MethodSpec spec : TreeImplSpec.this.getMethods(AbstractTreeSpec.MF_GCHILDREN | AbstractTreeSpec.MF_EQUIV)) {
				if (IRTypes.withoutGenerics(spec.getReturnType()).equals(IRTypes.COLLECTION_C))
					out.emit("\n&& $T.equivalentToUnordered(this.$N(), other.$N())", IRTypes.TREE, spec, spec);
				else
					out.emit("\n&& $T.equivalentTo(this.$N(), other.$N())", IRTypes.TREE, spec, spec);
			}
			
			out.println(";");
			out.popIndent(2);
		}
		
	}
}