package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.Elements;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec.GetterSpec;

public class GetterSpecFactory implements Function<ExecutableElement, GetterSpec> {
	protected Elements elements;
	protected Logger baseLogger;
	
	public GetterSpecFactory(Elements elements, Logger baseLogger) {
		this.elements = elements;
		this.baseLogger = baseLogger;
	}

	protected Logger getLogger() {
		return this.baseLogger;
	}
	
	protected Map<String, ? extends AnnotationValue> derefValues(AnnotationMirror mirror) {
		//TODO: lazy?
		return Utils.derefValues(mirror.getElementValues());
	}
	
	protected void applyOverrideAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		result.override = true;
	}
	
	protected void applyOptionalAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		//TODO: get default value
		result.optional = true;
	}
	
	protected void applyOrderingFirstAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		result.first = true;
	}
	
	protected void applyOrderingLastAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		result.last = true;
	}
	
	protected void applyOrderingBeforeAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		Map<String, ? extends AnnotationValue> values = derefValues(mirror);
		AnnotationValue valueVal = values.get("value");
		if (valueVal != null) {
			//TODO: multiple values?
			String value = valueVal.accept(new ValueToString(), null);
			if (value != null) {
				result.before.add(value);
				return;
			}
		}
		logger.error("Missing value");
	}
	
	/**
	 * Apply @Ordering.After
	 * @param logger
	 * @param result
	 * @param mirror
	 */
	protected void applyOrderingAfterAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		Map<String, ? extends AnnotationValue> values = derefValues(mirror);
		AnnotationValue valueVal = values.get("value");
		if (valueVal != null) {
			//TODO: multiple values?
			String value = valueVal.accept(new ValueToString(), null);
			if (value != null) {
				result.after.add(value);
				return;
			}
		}
		logger.error("Missing value");
	}
	
	protected void applyOrderingAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		Map<String, ? extends AnnotationValue> values = derefValues(mirror);
		
		AnnotationValue valueVal = values.get("value");
		if (valueVal != null) {
			int value = valueVal.accept(new ValueToInt(), null);
			if (value > -1) {
				//TODO fix
				logger.warn("Absolute positioning not yet supported");
			}
		}
		AnnotationValue firstVal = values.get("first");
		if (firstVal != null) {
			boolean first = firstVal.accept(new ValueToBoolean(), null);
			if (first) {
				if (result.first)
					logger.withValue(firstVal).warn("Duplicate 'first' ordering");
				result.first = true;
			}
		}
		
		// Apply before/after values
		AnnotationValue beforeVal = values.get("before");
		if (beforeVal != null) {
			List<String> beforeVals = beforeVal.accept(new ValueToStream<>(new ValueToString()), null)
					.collect(Collectors.toList());
			if (!beforeVals.isEmpty()) {
				result.before.addAll(beforeVals);
			}
		}
		
		AnnotationValue afterVal = values.get("after");
		if (afterVal != null) {
			List<String> afterVals = afterVal.accept(new ValueToStream<>(new ValueToString()), null)
					.collect(Collectors.toList());
			if (!afterVals.isEmpty()) {
				result.before.addAll(afterVals);
			}
		}
	}
	
	protected boolean applyInvokerAnnotation(Logger logger, GetterSpec result, AnnotationMirror mirror) {
		if (result.invoker != null) {
			String message = "Duplicate invocation";
			logger.withSite(result.invoker).error(message);
			logger.error(message);
			return false;
		}
		result.invoker = mirror;
		
		Map<String, ? extends AnnotationValue> values = derefValues(mirror);
		
		AnnotationValue nameVal = values.get("name");
		if (nameVal != null) {
			String fName = nameVal.accept(new ValueToString(), null);
			if (fName != null && !Objects.equals("__infer__", fName))
				result.fName = fName;
		}
		
		AnnotationValue hashVal = values.get("hash");
		if (hashVal != null) {
			boolean hash = hashVal.accept(new ValueToBoolean(), null);
			result.hash = hash;
		}
		
		AnnotationValue compareVal = values.get("hash");
		if (compareVal != null) {
			boolean compare = compareVal.accept(new ValueToBoolean(), null);
			result.compare = compare;
		}
		
		return true;
	}
	
	@Override
	public GetterSpec apply(ExecutableElement method) {
		GetterSpec result = new GetterSpec();
		result.target = method;
		result.name = method.getSimpleName().toString();
		
		
		for (AnnotationMirror mirror : elements.getAllAnnotationMirrors(method)) {
			Logger logger = getLogger().withTarget(method, mirror);
			String name = Utils.getName(mirror.getAnnotationType());
			switch (name) {
				case IRTypes.OVERRIDE:
					this.applyOverrideAnnotation(logger, result, mirror);
					continue;
				case IRTypes.ORDERING:
					this.applyOrderingAnnotation(logger, result, mirror);
					break;
				case IRTypes.ORDERING_BEFORE:
					this.applyOrderingBeforeAnnotation(logger, result, mirror);
					continue;
				case IRTypes.ORDERING_AFTER:
					this.applyOrderingAfterAnnotation(logger, result, mirror);
					continue;
				case IRTypes.ORDERING_FIRST:
					this.applyOrderingFirstAnnotation(logger, result, mirror);
					continue;
				case IRTypes.ORDERING_LAST:
					this.applyOrderingLastAnnotation(logger, result, mirror);
					continue;
				case IRTypes.OPTIONAL:
					this.applyOptionalAnnotation(logger, result, mirror);
					continue;
				case IRTypes.TREE_PROPERTY:
				case IRTypes.TREE_CHILD:
				case IRTypes.TREE_CHILDREN:
					if (!this.applyInvokerAnnotation(logger, result, mirror))
						return null;
					
					continue;
				default:
					logger.error("Annotation %s", name);
					break;
			}
			//getLogger().note("Got mirror %s on %s", name, method);
		}
		
		if (result.invoker == null)
			return null;
		
		result.type = method.getReturnType();
		
		try (StringWriter out = new StringWriter(); CodeWriter cw = new CodeWriter(out)) {
			TypeName type = TypeName.wrap(result.type);
			cw.emit("$T", type);
//			getLogger().withTarget(method).warn("Return type: %s (%s)", type, out.toString());
		} catch (IOException e) {
			// Should be impossible
			e.printStackTrace();
			throw new AssertionError(e);
		}
		
		if (!method.getParameters().isEmpty()) {
			getLogger().withTarget(method).error("Not a getter (has %d arguments)", method.getParameters().size());
			return null;
		}
		
		if (result.fName == null) {
			String fName = result.name;
			// Drop get/is
			if (fName.startsWith("get") && fName.length() > 3)
				fName = Character.toLowerCase(fName.charAt(3)) + fName.substring(4);
			else if (fName.startsWith("is") && fName.length() > 2)
				fName = Character.toLowerCase(fName.charAt(2)) + fName.substring(3);
			result.fName = Utils.escapeIdentifier(fName);
		}
		
		return result;
	}
	
	@NonNullByDefault
	private static class ValueToStream<T, P> extends AbstractAnnotationValueVisitor8<Stream<? extends T>, P> {
		protected final AnnotationValueVisitor<T, P> mapper;
		
		public ValueToStream(AnnotationValueVisitor<T, P> mapper) {
			this.mapper = mapper;
		}
		
		@Override
		public Stream<? extends T> visitBoolean(boolean b, P p) {
			return Stream.of(this.mapper.visitBoolean(b, p));
		}
		
		@Override
		public Stream<? extends T> visitByte(byte b, P p) {
			return Stream.of(this.mapper.visitByte(b, p));
		}
		
		@Override
		public Stream<? extends T> visitChar(char c, P p) {
			return Stream.of(this.mapper.visitChar(c, p));
		}
		
		@Override
		public Stream<? extends T> visitDouble(double d, P p) {
			return Stream.of(this.mapper.visitDouble(d, p));
		}
		
		@Override
		public Stream<? extends T> visitFloat(float f, P p) {
			return Stream.of(this.mapper.visitFloat(f, p));
		}
		
		@Override
		public Stream<? extends T> visitInt(int i, P p) {
			return Stream.of(this.mapper.visitInt(i, p));
		}
		
		@Override
		public Stream<? extends T> visitLong(long i, P p) {
			return Stream.of(this.mapper.visitLong(i, p));
		}
		
		@Override
		public Stream<? extends T> visitShort(short s, P p) {
			return Stream.of(this.mapper.visitShort(s, p));
		}
		
		@Override
		public Stream<? extends T> visitString(String s, P p) {
			return Stream.of(this.mapper.visitString(s, p));
		}
		
		@Override
		public Stream<? extends T> visitType(TypeMirror t, P p) {
			return Stream.of(this.mapper.visitType(t, p));
		}
		
		@Override
		public Stream<? extends T> visitEnumConstant(VariableElement c, P p) {
			return Stream.of(this.mapper.visitEnumConstant(c, p));
		}
		
		@Override
		public Stream<? extends T> visitAnnotation(AnnotationMirror a, P p) {
			return Stream.of(this.mapper.visitAnnotation(a, p));
		}
		
		@Override
		public Stream<? extends T> visitArray(List<? extends AnnotationValue> vals, P p) {
			return vals.stream().map(av -> this.mapper.visit(av, p));
		}
	}
	
	private static class ValueToString extends AbstractAnnotationValueVisitor8<String, Void> {
		@Override
		public String visitBoolean(boolean b, Void p) {
			return Boolean.toString(b);
		}

		@Override
		public String visitByte(byte b, Void p) {
			return Byte.toString(b);
		}

		@Override
		public String visitChar(char c, Void p) {
			return Character.toString(c);
		}

		@Override
		public String visitDouble(double d, Void p) {
			return Double.toString(d);
		}

		@Override
		public String visitFloat(float f, Void p) {
			return Float.toString(f);
		}

		@Override
		public String visitInt(int i, Void p) {
			return Integer.toString(i);
		}

		@Override
		public String visitLong(long i, Void p) {
			return Long.toString(i);
		}

		@Override
		public String visitShort(short s, Void p) {
			return Short.toString(s);
		}

		@Override
		public String visitString(String s, Void p) {
			return s;
		}

		@Override
		public String visitType(TypeMirror t, Void p) {
			return t.toString();
		}

		@Override
		public String visitEnumConstant(VariableElement c, Void p) {
			return c.toString();
		}

		@Override
		public String visitAnnotation(AnnotationMirror a, Void p) {
			return a.toString();
		}

		@Override
		public String visitArray(List<? extends AnnotationValue> vals, Void p) {
			return vals.toString();
		}
		
		@Override
		public String visitUnknown(AnnotationValue av, Void p) {
			return String.valueOf(av.getValue());
		}
	}
	
	private static class ValueToInt extends AbstractAnnotationValueVisitor8<Integer, Void> {
		protected final boolean strict;
		
		public ValueToInt() {
			this(true);
		}
		
		public ValueToInt(boolean strict) {
			this.strict = strict;
		}
		
		protected Integer visitDefault(Void p) {
			return null;
		}

		@Override
		public Integer visitBoolean(boolean b, Void p) {
			if (this.strict)
				return this.visitDefault(p);
			return b ? 1 : 0;
		}

		@Override
		public Integer visitChar(char c, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Integer visitFloat(float f, Void p) {
			return this.visitDouble(f, p);
		}

		@Override
		public Integer visitDouble(double d, Void p) {
			if (this.strict)
				return this.visitDefault(p);
			return (int) d;
		}

		@Override
		public Integer visitByte(byte b, Void p) {
			return this.visitLong(b, p);
		}

		@Override
		public Integer visitShort(short s, Void p) {
			return this.visitLong(s, p);
		}

		@Override
		public Integer visitInt(int i, Void p) {
			return i;
		}

		@Override
		public Integer visitLong(long i, Void p) {
			try {
				if (!this.strict)
					return Math.toIntExact(i);
			} catch (ArithmeticException e) {
			}
			return this.visitDefault(p);
		}

		@Override
		public Integer visitString(String s, Void p) {
			try {
				if (!this.strict)
					return Integer.parseInt(s);
			} catch (NumberFormatException e) {
			}
			return this.visitDefault(p);
		}

		@Override
		public Integer visitType(TypeMirror t, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Integer visitEnumConstant(VariableElement c, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Integer visitAnnotation(AnnotationMirror a, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Integer visitArray(List<? extends AnnotationValue> vals, Void p) {
			return this.visitDefault(p);
		}
		
		@Override
		public Integer visitUnknown(AnnotationValue av, Void p) {
			return this.visitDefault(p);
		}
	}
	
	private static class ValueToBoolean extends AbstractAnnotationValueVisitor8<Boolean, Void> {
		protected final boolean strict;
		
		public ValueToBoolean() {
			this(true);
		}
		
		public ValueToBoolean(boolean strict) {
			this.strict = strict;
		}
		
		protected Boolean visitDefault(Void p) {
			return null;
		}

		@Override
		public Boolean visitBoolean(boolean b, Void p) {
			return b;
		}

		@Override
		public Boolean visitChar(char c, Void p) {
			return this.visitLong(c, p);
		}

		@Override
		public Boolean visitFloat(float f, Void p) {
			return this.visitDouble(f, p);
		}

		@Override
		public Boolean visitDouble(double d, Void p) {
			if (this.strict)
				return this.visitDefault(p);
			return d != 0.0;
		}

		@Override
		public Boolean visitByte(byte b, Void p) {
			return this.visitLong(b, p);
		}

		@Override
		public Boolean visitShort(short s, Void p) {
			return this.visitLong(s, p);
		}

		@Override
		public Boolean visitInt(int i, Void p) {
			return this.visitLong(i, p);
		}

		@Override
		public Boolean visitLong(long i, Void p) {
			if (this.strict)
				return this.visitDefault(p);
			return i != 0;
		}

		@Override
		public Boolean visitString(String s, Void p) {
			if (!this.strict) {
				if ("true".equalsIgnoreCase(s))
					return true;
				if ("false".equalsIgnoreCase(s))
					return false;
			}
			return this.visitDefault(p);
		}

		@Override
		public Boolean visitType(TypeMirror t, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Boolean visitEnumConstant(VariableElement c, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Boolean visitAnnotation(AnnotationMirror a, Void p) {
			return this.visitDefault(p);
		}

		@Override
		public Boolean visitArray(List<? extends AnnotationValue> vals, Void p) {
			return this.visitDefault(p);
		}
		
		@Override
		public Boolean visitUnknown(AnnotationValue av, Void p) {
			return this.visitDefault(p);
		}
	}
}
