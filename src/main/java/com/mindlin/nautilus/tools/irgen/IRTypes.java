package com.mindlin.nautilus.tools.irgen;

import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

public class IRTypes {
	public static final String RAW_PACKAGE = "com.mindlin.nautilus.tree";
	public static final String TREE_CLASS = RAW_PACKAGE + ".Tree";
	public static final String KIND_CLASS = TREE_CLASS + ".Kind";
	
	// Annotations
	public static final String ANNOTATION_PCKG = RAW_PACKAGE + ".annotations";
	public static final String TREE_IMPL = TREE_CLASS + ".Impl";
	public static final String TREE_NOIMPL = TREE_CLASS + ".NoImpl";
	public static final String TREE_ADT = TREE_CLASS + ".ADT";
	public static final String TREE_PROPERTY = TREE_CLASS + ".Property";
	public static final String TREE_CHILD = TREE_CLASS + ".Child";
	public static final String TREE_CHILDREN = TREE_CLASS + ".Children";
	public static final String ORDERING = ANNOTATION_PCKG + ".Ordering";
	public static final String ORDERING_FIRST = ORDERING + ".First";
	public static final String ORDERING_LAST = ORDERING + ".Last";
	public static final String ORDERING_BEFORE = ORDERING + ".Before";
	public static final String ORDERING_AFTER = ORDERING + ".After";
	public static final String NONNULL = ANNOTATION_PCKG + ".NonNull";
	public static final String NULLABLE = ANNOTATION_PCKG + ".Nullable";
	public static final String OPTIONAL = ANNOTATION_PCKG + ".Optional";
	
	public static final String FS_PACKAGE = "com.mindlin.nautilus.fs";
	public static final String SOURCEPOSITION = FS_PACKAGE + ".SourcePosition";
	public static final String SOURCERANGE = FS_PACKAGE + ".SourceRange";
	
	public static final String IMPL_PACKAGE = "com.mindlin.nautilus.tree.impl";
	public static final String ABSTRACT_BASE = IMPL_PACKAGE + ".AbstractTree";
	
	public static final String OVERRIDE = "java.lang.Override";
	public static final String COLLECTION = "java.util.Collection";
	public static final String LIST = "java.util.List";
	public static final String SET = "java.util.Set";
	public static final String MAP = "java.util.Map";
	public static final String COLLECTIONS = "java.util.Collections";
	public static final String OBJECTS = "java.util.Objects";
	
	public static boolean isPrimitive(TypeMirror type) {
		return type instanceof PrimitiveType;
	}
	
	public static boolean isPrimitive(String type) {
		switch (type) {
			case "char":
			case "boolean":
			case "double":
			case "float":
			case "long":
			case "int":
			case "short":
			case "byte":
				return true;
			default:
				return false;
		}
	}
	
	public static String withoutGenerics(String type) {
		int idx = type.indexOf('<');
		if (idx < 0)
			return type;
		
		return type.substring(0, idx);
	}
	
	public static boolean isCollection(TypeMirror type) {
		return isCollection(type.toString());
	}
	
	public static boolean isCollection(String type) {
		switch (type) {
			case "java.util.List":
				return true;
			default:
				return false;
		}
	}
	
	public static boolean isTree(String type) {
		if (type.startsWith(IMPL_PACKAGE)) {
			return true;
		} else if (type.startsWith(RAW_PACKAGE) && !type.endsWith("Visitor")) {
			return true;
		}
		return false;
	}
	
	private IRTypes() {
	}

}
