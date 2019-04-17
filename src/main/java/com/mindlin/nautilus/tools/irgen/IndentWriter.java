package com.mindlin.nautilus.tools.irgen;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Objects;

public class IndentWriter extends PrintWriter {
	private static int nextNewline(String s, int off, int len) {
		if (s.length() - off - len > 1024) {
			// `s` is big
			for (int i = off; i < off + len; i++)
				if (s.charAt(i) == '\n')
					return i;
		} else {
			int result = s.indexOf('\n', off);
			if (result < off + len)
				return result;
		}
		return -1;
	}
	
	private static int nextNewline(char[] c, int off, int len) {
		for (int i = off; i < off + len; i++)
			if (c[i] == '\n')
				return i;
		return -1;
	}
	
	public int indent = 0;
	private boolean eol = false;
	private final String spacer;
	private transient String indentCache = null;

	public IndentWriter(Writer writer) {
		this(writer, "\t");
	}
	
	public IndentWriter(Writer writer, String spacer) {
		super(writer);
		this.spacer = Objects.requireNonNull(spacer);
	}
	
	public void indentln(String text) {
		this.pushIndent();
		this.println(text);
		this.popIndent();
	}
	
	public IndentWriter pushIndent() {
		this.indent++;
		return this;
	}
	
	public IndentWriter pushIndent(int n) {
		this.indent += n;
		return this;
	}
	
	public IndentWriter popIndent() {
		this.indent--;
		return this;
	}
	
	public IndentWriter popIndent(int n) {
		this.indent-= n;
		return this;
	}
	
	private String getIndentText() {
		if (this.indent <= 0)
			return "";
		
		int spacerLen = this.spacer.length();
		if (this.indent == 1 || spacerLen == 0)
			return this.spacer;
		
		int outLen = this.indent * spacerLen;
		if (this.indentCache != null && this.indentCache.length() >= outLen) {
//			if (this.indentCache.length() > outLen * 4)
			this.indentCache = this.indentCache.substring(0, outLen);
			return this.indentCache;
		}
		
		if (spacerLen == 1) {
			char[] buffer = new char[outLen];
			Arrays.fill(buffer, this.spacer.charAt(0));
			return this.indentCache = new String(buffer);
		}
		
		char[] buffer = new char[outLen];
		char[] schars = this.spacer.toCharArray();
		//TODO: can probably do this by doubling
		for (int i = 0; i < outLen; i+= spacerLen)
			System.arraycopy(schars, 0, buffer, i, spacerLen);
		return this.indentCache = new String(buffer);
		
	}
	
	private void printSOL() {
		if (this.eol) {
			this.eol = false;
			super.println();
			super.write(this.getIndentText());
		}
	}
	
	@Override
	public void println() {
		printSOL();
		this.eol = true;
	}
	
	public void setEOL() {
		this.eol = true;
	}
	
	@Override
	public void write(String s) {
		this.write(s, 0, s.length());
	}
	
	@Override
	public void write(final String s, int off, int len) {
		while (len > 0) {
			this.printSOL();
			int newline = nextNewline(s, off, len);
			if (newline == -1) {
				super.write(s, off, len);
				break;
			}
			if (newline > off)
				super.write(s, off, newline - off);
			len -= (newline - off) + 1;
			off = newline + 1;
			this.println();
		}
	}
	
	@Override
	public void write(char[] buf, int off, int len) {
		while (len > 0) {
			this.printSOL();
			int newline = nextNewline(buf, off, len);
			if (newline == -1) {
				super.write(buf, off, len);
				break;
			}
			if (newline > off)
				super.write(buf, off, newline - off);
			len -= (newline - off) + 1;
			off = newline + 1;
			this.println();
		}
	}
	
	@Override
	public void write(int c) {
		if (c == '\n')
			this.println();
		else {
			this.printSOL();
			super.write(c);
		}
	}

	public void space() {
		write(' ');
	}
}
