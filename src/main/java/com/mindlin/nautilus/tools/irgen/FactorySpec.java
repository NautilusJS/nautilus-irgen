package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.mindlin.nautilus.tools.irgen.ir.ClassSpec;
import com.mindlin.nautilus.tools.irgen.ir.MethodSpec;
import com.mindlin.nautilus.tools.irgen.ir.ParameterSpec;

public class FactorySpec extends ClassSpec {

	public FactorySpec() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected String getSimpleName() {
		return "TreeFactory";
	}
	
	@Override
	protected String getPackage() {
		return IRTypes.IMPL_PACKAGE;
	}

	@Override
	protected int getModifiers() {
		return Modifier.PUBLIC | Modifier.FINAL;
	}

	@Override
	protected Collection<MethodSpec> getMethods() {
		List<MethodSpec> result = new ArrayList<>();
		// TODO Auto-generated method stub
		return result;
	}
	
	public class FactoryMethod extends MethodSpec {
		final String type;
		public FactoryMethod(String type, String name) {
			super(name);
			this.type = type;
		}
		
		@Override
		protected int getModifiers() {
			return Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC;
		}

		@Override
		public String getReturnType() {
			return this.type;
		}

		@Override
		protected Collection<ParameterSpec> getParameters() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected void writeBody(IndentWriter out) throws IOException {
			// TODO Auto-generated method stub
			
		}
		
	}
}
