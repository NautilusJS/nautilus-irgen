package com.mindlin.nautilus.tools.irgen;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NameHelper {
	Set<String> names = new HashSet<>();
	
	protected boolean doAdd(String name) {
		Objects.requireNonNull(name);
		return names.add(name);
	}
	
	protected String mangle(String name) {
		int i = 0;
		StringBuffer sb = new StringBuffer(name.length() + 8);
		sb.append(name);
		sb.append("$");
		
		while (i < names.size() + 1) {
			sb.setLength(name.length() + 1);
			sb.append(i++);
			if (doAdd(sb.toString()))
				return sb.toString();
		}
		throw new IllegalStateException();
	}
	
	public String add(String name) {
		if (doAdd(name))
			return name;
		return mangle(name);
	}
	
	public String add(String name, String...candidates) {
		if (doAdd(name))
			return name;
		for (String candidate : candidates)
			if (doAdd(candidate))
				return candidate;
		return mangle(name);
	}
}
