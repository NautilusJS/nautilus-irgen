package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class Utils {
	private Utils() {
	}
	
	public static Set<Modifier> modifiers(Modifier...modifiers) {
		Set<Modifier> result = new HashSet<>();
		for (Modifier modifier : modifiers)
			result.add(modifier);
		return result;
	}
	
	public static void writeModifier(Writer out, Modifier modifier) throws IOException {
		switch (modifier) {
		case ABSTRACT:
			out.append("abstract ");
			break;
		case DEFAULT:
			out.append("default ");
			break;
		case FINAL:
			out.append("final ");
			break;
		case NATIVE:
			out.append("native ");
			break;
		case PRIVATE:
			out.append("private ");
			break;
		case PROTECTED:
			out.append("protected ");
			break;
		case PUBLIC:
			out.append("public ");
			break;
		case STATIC:
			out.append("static ");
			break;
		case STRICTFP:
			out.append("strictfp ");
			break;
		case SYNCHRONIZED:
			out.append("synchronized ");
			break;
		case TRANSIENT:
			out.append("transient ");
			break;
		case VOLATILE:
			out.append("volatile ");
			break;
		default:
			break;
		}
	}
	
	public static void writeModifiers(Writer out, Set<Modifier> modifiers) throws IOException {
		for (Modifier modifier : modifiers)
			writeModifier(out, modifier);
	}
	
	public static void writeList(Writer out, Iterable<String> values, String separator) throws IOException {
		Iterator<String> iter = values.iterator();
		while (iter.hasNext()) {
			String value = iter.next();
			out.append(value);
			if (iter.hasNext())
				out.append(separator);
		}
	}
	
	public static String stringifyList(Iterable<String> values, String separator) {
		StringBuilder sb = new StringBuilder();
		Iterator<String> iter = values.iterator();
		while (iter.hasNext()) {
			String value = iter.next();
			sb.append(value);
			if (iter.hasNext())
				sb.append(separator);
		}
		
		return sb.toString();
	}
	
	@FunctionalInterface
	public static interface Writable {
		void write(Writer out) throws IOException;
	}
	
	public static <T extends Writable> void writeAll(Writer out, Iterable<T> values, String separator) throws IOException {
		Iterator<T> iter = values.iterator();
		while (iter.hasNext()) {
			T value = iter.next();
			value.write(out);
			if (iter.hasNext())
				out.append(separator);
		}
	}
	
	public static <T extends AnnotationValue> Map<String, T> derefValues(Map<? extends ExecutableElement, T> src) {
		Map<String, T> result = new HashMap<>();
		for (Map.Entry<? extends ExecutableElement, T> entry : src.entrySet())
			result.put(entry.getKey().getSimpleName().toString(), entry.getValue());
		return result;
	}
	
	protected static String immutableMethod(String type) {
		switch (IRTypes.withoutGenerics(type)) {
		case IRTypes.COLLECTION:
			return "unmodifiableCollection";
		case IRTypes.LIST:
			return "unmodifiableList";
		case IRTypes.SET:
			return "unmodifiableSet";
		case IRTypes.MAP:
			return "unmodifiableMap";
		default:
			throw new IllegalArgumentException("Unable to make type " + type + " unmodifiable.");
		}
	}
	
	public static String unmodifiable(String cType, String var) {
		return invoke(IRTypes.COLLECTIONS, immutableMethod(cType), cast(cType, var));
	}
	
	public static AnnotationMirror getAnnotationOfType(TypeMirror type, String annotationName) {
		return getAnnotationOfType(type.getAnnotationMirrors(), annotationName);
	}
	
	public static AnnotationMirror getAnnotationOfType(Collection<? extends AnnotationMirror> annotations, String annotationName) {
		return annotations.stream()
				.filter(annotation -> Objects.equals(Utils.getName(annotation.getAnnotationType()), annotationName))
				.findFirst()
				.orElse(null);
	}
	
	public static boolean isNonNull(TypeMirror type, boolean requireCheck) {
		AnnotationMirror nna = getAnnotationOfType(type, IRTypes.NONNULL);
		if (nna == null)
			return false;
		if (!requireCheck)
			return true;
		AnnotationValue value = derefValues(nna.getElementValues()).get("check");
		if (value == null)
			return false;
		return (boolean) value.getValue();
	}
	
	public static String setField(String base, String fName, String value) {
		return String.format("(%s.%s = (%s))", base, fName, value);
	}
	
	public static String getField(String base, String fName) {
		return String.format("(%s.%s)", base, fName);
	}
	
	public static String cast(String type, String var) {
		return String.format("((%s) %s)", type, var);
	}
	
	public static String equals(String a, String b) {
		return invoke(IRTypes.OBJECTS, "equals", a, b);
	}
	
	public static String invoke(String base, String mName, String...params) {
		return invoke(base, mName, Arrays.asList(params));
	}
	
	public static String invoke(String base, String mName, Iterable<String> params) {
		return String.format("(%s.%s(%s))", base, mName, Utils.stringifyList(params, ", "));
	}
	
	public static <T, U> List<U> map(Collection<? extends T> data, Function<T, U> mapper) {
		List<U> result = new ArrayList<>(data.size());
		for (T value : data)
			result.add(mapper.apply(value));
		return result;
	}
	
	public static boolean isReserved(String raw) {
		switch (raw) {
			case "public":
			case "protected":
			case "private":
			case "class":
			case "int":
			case "byte":
			case "long":
			case "default":
			case "switch":
				return true;
			default:
				return false;
		}
	}
	
	public static String escapeIdentifier(String raw) {
		if (isReserved(raw))
			raw = "__" + raw;
		return raw;
	}
	
	public static String getName(DeclaredType type) {
		return getName(type.asElement());
	}
	
	public static String getName(Element element) {
		return ((TypeElement) element).getQualifiedName().toString();
	}
}
