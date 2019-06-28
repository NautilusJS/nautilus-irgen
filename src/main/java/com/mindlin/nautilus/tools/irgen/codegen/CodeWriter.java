package com.mindlin.nautilus.tools.irgen.codegen;

import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import org.eclipse.jdt.annotation.NonNull;

import com.mindlin.nautilus.tools.irgen.IndentWriter;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;
import com.mindlin.nautilus.tools.irgen.ir.ClassName;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;
import com.mindlin.nautilus.tools.irgen.util.Named;

public class CodeWriter extends IndentWriter {
	Set<ClassName> imports = new HashSet<>();
	protected String packageName = null;

	public CodeWriter(@NonNull Writer writer) {
		super(writer);
	}

	public CodeWriter(@NonNull Writer writer, @NonNull String spacer) {
		super(writer, spacer);
	}
	
	public boolean isPackageImported(@NonNull String packageName) {
		return (this.imports.contains(new ClassName(packageName, null)) || Objects.equals(this.packageName, packageName));
	}
	
	public boolean isImported(ClassName clazz) {
		return this.imports.contains(clazz);
	}
	
	protected String resolveClass(ClassName clazz) {
		if (this.isImported(clazz))
			return clazz.getSimpleName();
		else if (clazz.getEnclosingClass() != null)
			return resolveClass(clazz.getEnclosingClass()) + "." + clazz.getSimpleName();
		else if (this.isPackageImported(clazz.getPackageName()))
			return clazz.getSimpleName();
		else
			return clazz.getQualifiedName();
	}
	
	public void setPackage(String packageName) {
		this.packageName = packageName;
	}
	
	public void addImport(ClassName name) {
		this.imports.add(name);
	}
	
	public void emitPackageDeclaration(String packageName) {
		this.format("package %s;\n\n", packageName);
	}
	
	public void emitImport(ClassName name) {
		if (name.getSimpleName() == null)
			this.format("import %s.*;", name.getPackageName());
		else
			this.format("import %s;", name.getQualifiedName());
		this.setEOL();
	}
	
	public void emitModifiers(int modifiers) {
		this.append(Modifier.toString(modifiers));
	}
	
	public void emitLiteral(String value) {
		this.format("\"%s\"", value);//TODO: escape
	}
	
	public void emitLiteral(char value) {
		this.format("'%c'", value);//TODO: escape
	}
	
	public void emitLiteral(int value) {
		this.format("%d", value);
	}
	
	public void emitLiteral(long value) {
		this.format("%dL", value);
	}
	
	public void emitLiteral(double value) {
		this.format("%f", value);
	}
	
	public void emitLiteral(float value) {
		this.format("%ff", value);
	}
	
	public void emitLiteral(Object value) {
		if (value == null) {
			this.print("null");
		} else if (value instanceof String) {
			this.emitLiteral((String) value);
		} else if (value instanceof Integer) {
			this.emitLiteral((int) value);
		} else if (value instanceof Long) {
			this.emitLiteral((long) value);
		} else if (value instanceof Double) {
			this.emitLiteral((double) value);
		} else if (value instanceof Character) {
			this.emitLiteral((char) value);
		} else if (value instanceof Boolean) {
			this.print(value.toString());
		}
	}
	
	public void emitType(TypeName value) {
		value.write(this);
	}
	
	public void emitType(TypeMirror value) {
		this.emitType(TypeName.wrap(value));
	}
	
	public void emitType(Class<?> value) {
		this.emitType(ClassName.get(value));
	}
	
	public void emitType(Object value) {
		if (value instanceof ClassName)
			emitType((ClassName) value);
		else if (value instanceof TypeName)
			emitType((TypeName) value);
		else if (value instanceof Class)
			emitType((Class<?>) value);
		else if (value instanceof TypeMirror)
			emitType((TypeMirror) value);
		else if (value instanceof String)
			this.print((String) value);
		else
			throw new IllegalArgumentException("Can't cast to type");
	}
	
	protected void emitArgument(@NonNull String option, Object value) {
		switch (option) {
			case "$":
				this.append("$");
				break;
			case "M":
				this.emitModifiers((int) value);
				break;
			case "L":
				this.emitLiteral(value);
				break;
			case "T":
				this.emitType(value);
				break;
			case "N":
				if (value instanceof Named)
					this.print(((Named) value).getName());
				else
					this.print((String) value);
				break;
			case "n":
				if (value instanceof Writable)
					((Writable) value).write(this);
				else
					throw new IllegalArgumentException("Can't cast to type");
				break;
			default:
				if (option.startsWith(",")) {
					String op = option.substring(1);
					if (value instanceof Iterable) {
						boolean first = true;
						for (Object elem : ((Iterable<?>) value)) {
							if (first)
								first = false;
							else
								this.print(", ");
							this.emitArgument(op, elem);
						}
					} else {
						this.emitArgument(op, value);
					}
					break;
				}
				
				this.append("Unknown option '" + option + "'");
		}
	}
	
	protected void splitPattern(String pattern, FormatPartCallback callback) {
		int index = 0;
		for (int i = 0; i < pattern.length(); ) {
			if (pattern.charAt(i) != '$') {
				int next = pattern.indexOf('$', i + 1);
				if (next == -1)
					next = pattern.length();
				callback.nextText(pattern, i, next);
				i = next;
				continue;
			}
			
			i++; // Pass '$'
			
			int start = i;
			if (pattern.charAt(i) == ',')
				i++;
			
			callback.nextArgument(index++, pattern.substring(start, ++i));
		}
	}
	
	public void emit(String pattern, Object...args) {
		this.splitPattern(pattern, new FormatPartCallback() {
			@Override
			public void nextArgument(int index, String option) {
				if (Objects.equals("$", option)) {
					CodeWriter.this.append("$");
				} else {
					CodeWriter.this.emitArgument(option, args[index % args.length]);
				}
			}
			
			@Override
			public void nextText(String value, int start, int end) {
				CodeWriter.this.append(value, start, end);
			}
		});
	}
	
	protected static interface FormatPartCallback {
		void nextText(String value, int start, int end);
		
		void nextArgument(int index, String option);
	}
}
