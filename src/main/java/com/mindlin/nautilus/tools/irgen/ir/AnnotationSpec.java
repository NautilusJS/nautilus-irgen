package com.mindlin.nautilus.tools.irgen.ir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import com.mindlin.nautilus.tools.irgen.Utils;
import com.mindlin.nautilus.tools.irgen.Utils.Writable;
import com.mindlin.nautilus.tools.irgen.codegen.CodeWriter;

public class AnnotationSpec implements Writable {
	public static AnnotationSpec get(AnnotationMirror mirror) {
		TypeName type = TypeName.wrap(mirror.getAnnotationType());
		Map<String, AnnotationValue> members = new LinkedHashMap<>();
		for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> member : mirror.getElementValues().entrySet())
			members.put(member.getKey().getSimpleName().toString(), member.getValue());
		
		return new AnnotationSpec(type, members);
	}
	public static List<? extends AnnotationSpec> from(AnnotatedConstruct source) {
		return Utils.map(source.getAnnotationMirrors(), AnnotationSpec::get);
	}
	
	public final TypeName type;
	public final Map<String, ? extends AnnotationValue> members;
	
	public AnnotationSpec(TypeName type, Map<String, ? extends AnnotationValue> members) {
		this.type = type;
		this.members = members;
	}

	@Override
	public void write(CodeWriter out) {
		this.writeInline(out);
	}
	
	protected void writeValues(CodeWriter out, AnnotationValue values) {
		values.accept(new AnnotationValueWriter(), out);
	}
	
	public void writeInline(CodeWriter out) {
		if (this.members.isEmpty()) {
			out.emit("@$T", this.type);
		} else if (this.members.size() == 1 && members.containsKey("value")) {
			out.emit("@$T(", this.type);
			this.writeValues(out, members.get("value"));
			out.emit(")");
		} else {
			out.emit("@$T(", this.type);
			out.pushIndent(2);
			boolean first = true;
			for (Map.Entry<String, ? extends AnnotationValue> entry : this.members.entrySet()) {
				if (first)
					first = false;
				else
					out.print(", ");
				
				out.emit("$N = ", entry.getKey());
				this.writeValues(out, entry.getValue());
			}
			out.popIndent(2);
			out.print(")");
		}
	}
	
	protected static class AnnotationValueWriter extends SimpleAnnotationValueVisitor8<Void, CodeWriter> {
		protected final boolean inline = true;
		
		@Override
		public Void visitBoolean(boolean b, CodeWriter out) {
			out.print(Boolean.toString(b));
			return null;
		}

		@Override
		public Void visitByte(byte b, CodeWriter out) {
			out.emit("((byte) $L)", (int) b);
			return null;
		}

		@Override
		public Void visitShort(short s, CodeWriter out) {
			out.emit("((short) $L)", (int) s);
			return null;
		}
		
		@Override
		public Void visitChar(char c, CodeWriter out) {
			out.emitLiteral(c);
			return null;
		}

		@Override
		public Void visitDouble(double d, CodeWriter out) {
			out.emitLiteral(d);
			return null;
		}

		@Override
		public Void visitFloat(float f, CodeWriter out) {
			out.emitLiteral(f);
			return null;
		}

		@Override
		public Void visitInt(int i, CodeWriter out) {
			out.emitLiteral(i);
			return null;
		}

		@Override
		public Void visitLong(long i, CodeWriter out) {
			out.emitLiteral(i);
			return null;
		}

		@Override
		public Void visitString(String s, CodeWriter out) {
			out.emitLiteral(s);
			return null;
		}

		@Override
		public Void visitArray(List<? extends AnnotationValue> vals, CodeWriter out) {
			if (vals.isEmpty()) {
				out.print("{}");
				return null;
			}
			
			out.print("{");
			if (!inline)
				out.setEOL();
			out.pushIndent(2);
			boolean first = true;
			for (AnnotationValue value : vals) {
				if (first)
					first = false;
				else if (inline)
					out.print(", ");
				else
					out.println(",");
				
				value.accept(this, out);
			}
			if (!inline)
				out.setEOL();
			out.popIndent(2);
			out.print("}");
			return null;
		}
		
		//TODO: more
	}
}
