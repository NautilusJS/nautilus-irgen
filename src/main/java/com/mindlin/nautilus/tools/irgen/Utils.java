package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.eclipse.jdt.annotation.Nullable;

import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;

public class Utils {
	public static boolean MP = false;
	private static final boolean VERBOSE = true;
	private Utils() {
	}
	
	public static Set<Modifier> modifiers(Modifier...modifiers) {
		Set<Modifier> result = new HashSet<>();
		for (Modifier modifier : modifiers)
			result.add(modifier);
		return result;
	}
	
	public static boolean isVerbose() {
		return VERBOSE;
	}
	
	public static <T> Stream<T> stream(Collection<T> src) {
		return MP ? src.parallelStream() : src.stream();
	}
	
	public static void writeModifiers(Writer out, int modifiers) throws IOException {
		out.append(Modifier.toString(modifiers));
		if (modifiers != 0)
			out.append(" ");
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
	
	public static <T> void writeList(CodeWriter out, Iterable<T> values, BiConsumer<CodeWriter, T> serializer, String separator) throws IOException {
		Iterator<T> iter = values.iterator();
		while (true) {
			T value = iter.next();
			serializer.accept(out, value);
			if (iter.hasNext())
				out.append(separator);
			else
				return;
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
		void write(CodeWriter out);
	}
	
	public static <T extends Writable> void writeAll(CodeWriter out, Iterable<T> values, String separator) throws IOException {
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
	
	public static <T extends AnnotationValue> Map<String, T> derefValuesLazy(Map<? extends ExecutableElement, T> src) {
		return new LazyAnnotationValueMap<>(src);
	}
	
	public static String immutableMethod(TypeName type) {
		switch (IRTypes.withoutGenerics(type).toString()) {
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
		return nna != null;
	}
	
	public static String invoke(String base, String mName, String...params) {
		return invoke(base, mName, Arrays.asList(params));
	}
	
	public static String invoke(String base, String mName, Iterable<String> params) {
		return String.format("%s.%s(%s)", base, mName, Utils.stringifyList(params, ", "));
	}
	
	public static <T, U> List<U> map(Collection<? extends T> data, Function<T, U> mapper) {
		List<U> result = new ArrayList<>(data.size());
		for (T value : data)
			result.add(mapper.apply(value));
		return result;
	}
	
	/*public static <T, U> List<U> map(Collection<? extends T> data, IntObjFunction<T, U> mapper) {
		List<U> result = new ArrayList<>(data.size());
		int idx = 0;
		for (T value : data)
			result.add(mapper.apply(value, idx++));
		return result;
	}*/
	
	public static <Tk, Tv, Rk, Rv> Map<Rk, Rv> map(Map<Tk, Tv> data, Function<? super Tk, ? extends Rk> keyMapper, Function<? super Tv, ? extends Rv> valueMapper) {
		return map(data, new HashMap<>(), keyMapper, valueMapper);
	}
	
	public static <Tk, Tv, Rk, Rv, R extends Map<Rk, Rv>> R map(Map<Tk, Tv> src, R dst, Function<? super Tk, ? extends Rk> keyMapper, Function<? super Tv, ? extends Rv> valueMapper) {
		Objects.requireNonNull(src);
		Objects.requireNonNull(dst);
		for (Map.Entry<Tk, Tv> entry : src.entrySet())
			dst.put(keyMapper.apply(entry.getKey()), valueMapper.apply(entry.getValue()));
		return dst;
	}
	
	public static <T, E extends Exception, U> List<U> emap(Iterable<? extends T> data, EFunction<T, E, U> mapper) throws E {
		List<U> result = new ArrayList<>();
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
	
	@FunctionalInterface
	public static interface IntObjFunction<T, R> {
		R apply(T value, int index);
	}
	
	@FunctionalInterface
	public static interface EFunction<T, E extends Exception, R> {
		R apply(T t) throws E;
	}
}
