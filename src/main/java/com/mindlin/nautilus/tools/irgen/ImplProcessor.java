package com.mindlin.nautilus.tools.irgen;

import static com.mindlin.nautilus.tools.irgen.Utils.invoke;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.mindlin.nautilus.tools.irgen.ir.ClassSpec;
import com.mindlin.nautilus.tools.irgen.ir.CtorSpec;
import com.mindlin.nautilus.tools.irgen.ir.FieldSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.CollectionGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.NarrowGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.OverrideMethod;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec.SimpleGetterSpec;
import com.mindlin.nautilus.tools.irgen.ir.ParameterSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;

public class ImplProcessor extends AnnotationProcessorBase {
	final Map<String, TreeSpec> niSpecs;
	final Map<String, TreeSpec> iSpecs;
	final Map<String, TreeImplSpec> impls;
	
	public ImplProcessor(ProcessingEnvironment procEnv, DeclaredType annotation, RoundEnvironment roundEnv, Map<String, TreeSpec> niSpecs, Map<String, TreeSpec> iSpecs, Map<String, TreeImplSpec> impls) {
		super(procEnv, annotation, roundEnv);
		this.niSpecs = niSpecs;
		this.iSpecs = iSpecs;
		this.impls = impls;
	}
	
	public String getResolvedSuperclass(Collection<String> parents) {
		for (String parent : parents)
			if (this.impls.containsKey(parent))
				return this.impls.get(parent).getQualifiedName();
		return IRTypes.ABSTRACT_BASE;
	}
	
	protected FieldSpec getterToField(GetterSpec getter) {
		return new FieldSpec(Utils.modifiers(Modifier.PROTECTED, Modifier.FINAL), getter.type, getter.fName);
	}
	
	protected void resolveGetters(List<GetterSpec> getters, Map<String, GetterSpec> gettersMap, TreeSpec spec) {
		//TODO: fix parent traversal order
		for (String parent : spec.parents) {
			TreeSpec parentSpec = this.niSpecs.get(parent);
			if (parentSpec == null)
				continue;
			this.resolveGetters(getters, gettersMap, parentSpec);
		}
		
		for (GetterSpec getter : spec.getters) {
			GetterSpec old = gettersMap.put(getter.name, getter);
			if (old != null) {
				getters.remove(old);
				getLogger().warn("Override getter '%s' on %s", getter.name, spec.getName());
			}
			getters.add(getter);
		}
	}
	
	protected List<GetterSpec> gettersToDeclare(List<GetterSpec> getters, TreeImplSpec parent) {
		if (parent == null)
			return new ArrayList<>(getters);
		
		for (GetterSpec getter : getters) {
			
		}
		return getters.stream()
				.filter(getter -> {
					GetterSpec parentGetter = parent.getters.get(getter.name);
					if (parentGetter == null)
						return true;// No inheritance
					if (getter == parentGetter)
						return true;// Overridden
					return false;
				})
				.collect(Collectors.toList());
	}
	
	protected void buildFields(Map<String, FieldSpec> fields, TreeSpec spec) {
		// Recurse through parents first, so they get overridden.
		for (String parent : spec.parents) {
			TreeSpec parentSpec = this.niSpecs.get(parent);
			if (parentSpec == null) {
				continue;
			}
			this.buildFields(fields, parentSpec);
		}
		
		for (GetterSpec getter : spec.getters) {
			FieldSpec field = this.getterToField(getter);
			if (fields.put(field.name, field) != null)
				getLogger().warn("Duplicate field '%s' on %s", field.name, spec.getName());
		}
	}
	
	protected MethodSpec makeGetter(TreeImplSpec impl, GetterSpec getter, TreeImplSpec parent) {
		int flags = 0;
		if (getter.hash)
			flags |= TreeImplSpec.MF_HASH;
		if (getter.compare)
			flags |= TreeImplSpec.MF_EQUIV;
		
		if (getter.optional) {
			//TODO: fix
		}
		
		if (parent != null && parent.getters.containsKey(getter.name))
			return this.makeOverrideGetter(flags, getter, parent.getters.get(getter.name));
		
		// Generate field getter
		FieldSpec field = impl.fields.get(getter.fName);
		
		String invName = Utils.getName(getter.invoker.getAnnotationType());
		if (Objects.equals(invName, IRTypes.TREE_PROPERTY)) {
			if (IRTypes.isCollection(getter.type)) {
				flags |= TreeImplSpec.MF_GOBJECT;
				return new CollectionGetterSpec(flags, getter.name, field, true);
			} else {
				if (IRTypes.isPrimitive(getter.type))
					flags |= TreeImplSpec.MF_GPRIMITIVE;
				else
					flags |= TreeImplSpec.MF_GOBJECT;
				
				return new SimpleGetterSpec(flags, getter.name, field);
			}
		} else if (Objects.equals(invName, IRTypes.TREE_CHILD)) {
			flags |= TreeImplSpec.MF_GCHILD;
			return new SimpleGetterSpec(flags, getter.name, field);
		} else if (Objects.equals(invName, IRTypes.TREE_CHILDREN)) {
			flags |= TreeImplSpec.MF_GCHILDREN;
			return new CollectionGetterSpec(flags, getter.name, field, true);
		} else {
			throw new IllegalArgumentException("Unknown invName: " + invName);
		}
	}
	
	protected MethodSpec makeOverrideGetter(int flags, GetterSpec local, GetterSpec parent) {
		//TODO: add null check in getter?
		if (local == parent)
			return null;
		return new NarrowGetterSpec(flags, local.name, local.type);
	}
	
	public TreeImplSpec buildTreeImpl(TypeElement element, TreeSpec spec) {
		String implName = element.getSimpleName() + "Impl";
		TreeImplSpec impl = new TreeImplSpec(element, implName);
		
		// Resolve heritage
		impl.baseType = spec.getName();
		impl.parent = this.getResolvedSuperclass(spec.parents);
		TreeImplSpec parentSpec = this.impls.get(impl.parent);
		impl.parentIfaces.add(spec.getName());
		
		// Resolve getters
		Map<String, GetterSpec> gettersMap = new HashMap<>();
		List<GetterSpec> resolvedGetters = new LinkedList<>();// LinkedList because we might be removing values from the middle
		this.resolveGetters(resolvedGetters, gettersMap, spec);
		
		getLogger().warn("Getters for %s: %s", spec.getName(), resolvedGetters);
		
		// Determine which fields we have to declare
		// We only declare a field if it doesn't exist in our parent
		
		if (parentSpec != null)
			impl.fields.putAll(parentSpec.fields);
		this.buildFields(impl.fields, spec);
		// Drop fields that exist in superclass
		Map<String, FieldSpec> localFields;
		if (parentSpec != null) {
			localFields = new HashMap<>(impl.fields);
			for (String parentField : parentSpec.fields.keySet())
				localFields.remove(parentField);
		} else {
			localFields = impl.fields;
		}
		impl.declaredFields.addAll(localFields.values());
		
		// Generate getter methods
		for (GetterSpec getter : resolvedGetters) { 
			getLogger().warn("Add getter %s to %s", getter.name, spec.getName());
			MethodSpec method = this.makeGetter(impl, getter, parentSpec);
			if (method == null)
				continue;
			impl.declaredMethods.add(method);	
		}
		
		// Generate hash & equivalence methods
		impl.buildMethods();
		
		// Generate constructors
		List<ParameterSpec> superParams = parentSpec == null ? Collections.emptyList() : parentSpec.params;
		impl.constructors.add(impl.new ForwardingCtorSpec(superParams));
//		impl.constructors.add(new TreeImplSpec.RangeMergeCtorSpec(Utils.map(superArgs, arg -> new ParameterSpec()));
		
		return impl;
	}
	
	protected static class TreeImplSpec extends ClassSpec {
		/** Getter that returns a primitive */
		static final int MF_GPRIMITIVE = (1 << 0);
		/** Getter that returns an object */
		static final int MF_GOBJECT = (1 << 1);
		/** Getter that returns a child tree */
		static final int MF_GCHILD = (1 << 2);
		/** Getter that returns a collection of children */
		static final int MF_GCHILDREN = (1 << 3);
		/** Method that should be used for hashing */
		static final int MF_HASH = (1 << 4);
		/** Method that should be used for equivalence */
		static final int MF_EQUIV = (1 << 5);
		
		Logger logger;
		TypeElement source;
		boolean extensible = false;
		Set<String> parentIfaces = new HashSet<>();
		String parent = IRTypes.ABSTRACT_BASE;
		String baseType;
		String name;
		String kind;
		// Members
		List<FieldSpec> declaredFields = new ArrayList<>();
		List<MethodSpec> declaredMethods = new ArrayList<>();
		List<CtorSpec> constructors = new ArrayList<>();
		
		// For child resolution
		/** Contains all fields (declared & inherited) by name */
		Map<String, FieldSpec> fields = new HashMap<>();
		/** Contains all getters (declared & inherited) by name */
		Map<String, GetterSpec> getters = new HashMap<>();
		/** Contains field parameters (not including range stuff) for constructors */
		List<ParameterSpec> params = new ArrayList<>();
		
		
		public TreeImplSpec(TypeElement source, String name) {
			this.source = source;
			this.name = name;
		}
		
		@Override
		protected Element[] getSources() {
			if (this.source == null)
				return super.getSources();
			return new Element[] {this.source};
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
		
		protected void buildMethods() {
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
}
