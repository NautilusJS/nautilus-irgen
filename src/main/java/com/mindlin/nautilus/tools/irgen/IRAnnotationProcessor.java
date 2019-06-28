package com.mindlin.nautilus.tools.irgen;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic.Kind;

import com.mindlin.nautilus.tools.irgen.ir.ClassSpec.OutputInfo;
import com.mindlin.nautilus.tools.irgen.util.Orderable;
import com.google.auto.service.AutoService;
import com.mindlin.nautilus.tools.irgen.ir.TreeImplSpec;
import com.mindlin.nautilus.tools.irgen.ir.TreeSpec;
import com.mindlin.nautilus.tools.irgen.ir.TypeName;

@AutoService(Processor.class)
@SupportedAnnotationTypes({IRTypes.TREE_NOIMPL, IRTypes.TREE_ADT, IRTypes.TREE_IMPL})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class IRAnnotationProcessor extends AbstractProcessor {
	protected Logger getLogger() {
		return new Logger(this.processingEnv.getMessager());
	}
	
	protected Map<String, TreeSpec> processTrees(TypeElement annotation, RoundEnvironment roundEnv) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, annotationType);
		
		Set<? extends Element> targets = roundEnv.getElementsAnnotatedWith(annotation);
		if (targets.isEmpty())
			return Collections.emptyMap();
		
		Map<String, TreeSpec> specMap = new HashMap<>();
		for (Element target : targets) {
			Logger logger = getLogger().withTarget(target);
			
			if (target.getKind() != ElementKind.INTERFACE) {
				logger.error("Illegal @Tree.%s on kind: %s", annotation.getSimpleName(), target.getKind());
				continue;
			}
			
			try {
				TreeSpec spec = processor.buildTreeSpec((TypeElement) target);
				specMap.put(Utils.getName(target), spec);
			} catch (Exception e) {
				logger.error("Error reading @Tree.%s: %s", annotation.getSimpleName(), e.getLocalizedMessage());
				throw e;
			}
		}
		return specMap;
	}
	
	protected Map<String, TreeSpec> processTreesMP(TypeElement annotation, RoundEnvironment roundEnv) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, annotationType);
		
		Set<? extends Element> targets = roundEnv.getElementsAnnotatedWith(annotation);
		if (targets.isEmpty())
			return Collections.emptyMap();
		
		return targets.parallelStream()
			.filter(target -> {
				if (target.getKind() != ElementKind.INTERFACE) {
					getLogger().withTarget(target).error("Illegal @Tree.%s on kind: %s", annotation.getSimpleName(), target.getKind());
					return false;
				}
				return true;
			})
			.map(target -> {
				try {
					return processor.buildTreeSpec((TypeElement) target);
				} catch (Exception e) {
					getLogger().withTarget(target).error("Error reading @Tree.%s: %s", annotation.getSimpleName(), e.getLocalizedMessage());
					throw e;
				}
			})
			.collect(Collectors.toConcurrentMap(target -> Utils.getName(target.source), target -> target));
	}
	
	protected Set<String> getUnprocessed(TypeElement adt, RoundEnvironment roundEnv, Map<String, TreeSpec> allSpecs) {
		Stack<String> queue = new Stack<>();
		Set<String> enqueued = new HashSet<>();
		Set<String> missing = new HashSet<>();
		
		Map<String, List<TreeSpec>> sources = new HashMap<>();
		Map<String, TreeSpec> specs = new HashMap<>(allSpecs);
		
		for (Map.Entry<String, TreeSpec> spec : allSpecs.entrySet()) {
			if (spec.getValue().kind != TreeSpec.Kind.IMPL)
				continue;
			queue.add(spec.getKey());
			enqueued.add(spec.getKey());
		}
		
		while (!queue.isEmpty()) {
			String path = queue.pop();
			TreeSpec spec = specs.get(path);
			if (spec == null) {
				getLogger().note("Missing spec for %s", path);
				missing.add(path);
				continue;
			}
			sources.put(path, null);
			for (TypeName parent : spec.parents) {
				String parentName = IRTypes.withoutGenerics(parent).toString();
				
				List<TreeSpec> psrcs = sources.computeIfAbsent(parentName, x -> new ArrayList<>());
				if (psrcs != null)
					psrcs.add(spec);
				if (!specs.containsKey(parentName))
					missing.add(parentName);
				if (enqueued.add(parentName))
					queue.add(parentName);
			}
		}
		
		Set<String> extra = new HashSet<>(specs.keySet());
		extra.removeAll(enqueued);
		if (Utils.isVerbose())
			getLogger().note("Extra specs: %s", extra);
		
		TreeBuilderProcessor processor = new TreeBuilderProcessor(this.processingEnv, adt == null ? null : (DeclaredType) adt.asType(), TreeSpec.Kind.ADT);
		if (!missing.isEmpty()) {
			String mp = missing.iterator().next();
			List<TreeSpec> mpSources = sources.getOrDefault(mp, Collections.emptyList());
			for (TreeSpec mpSource : mpSources)
				this.getLogger()
						.withTarget(mpSource.source)
						.warn("Missing type %s", mp);//TODO: warn?
			
			Types types = this.processingEnv.getTypeUtils();
			for (TreeSpec mpSource : mpSources) {
				for (TypeMirror candidate : types.directSupertypes(mpSource.source.asType())) {
					String name = Utils.getName((DeclaredType) candidate);
					getLogger().note("Candidate %s", name);
					if (!Objects.equals(name, mp))
						continue;
					try {
						TypeElement target = (TypeElement) ((DeclaredType) candidate).asElement();
						TreeSpec loaded = processor.buildTreeSpec(target);
						allSpecs.put(Utils.getName(target), loaded);
						getLogger().note("Found type %s (%s)", name, loaded);
					} catch (Exception e) {
						getLogger().error("Error processing %s (%s)", name, e.getLocalizedMessage());
						try (PrintStream ps = new PrintStream(getLogger().asOutputStream(Kind.ERROR))) {
							e.printStackTrace(ps);
						}
					}
				}
			}
		}
		
		return missing;
	}
	
	protected Map<String, TreeImplSpec> processImplOutputs(TypeElement annotation, Map<String, TreeSpec> specs) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		
		Map<String, TreeImplSpec> impls = new HashMap<>();
		
		ImplProcessor processor = new ImplProcessor(this.processingEnv, annotationType, specs, impls);
		
		// Order impl gen
		List<TreeSpec> implOrder = new ArrayList<>(specs.values());
		implOrder.removeIf(spec -> spec.kind != TreeSpec.Kind.IMPL);
		implOrder = Orderable.sorted(implOrder);
		if (Utils.isVerbose())
			getLogger().note("Impl order: %s", implOrder.stream().map(TreeSpec::getName).collect(Collectors.toList()));
		
		for (TreeSpec spec : implOrder) {
			Logger logger = getLogger().withTarget(spec.source);
			TreeImplSpec specImpl = processor.buildTreeImpl(spec.source, spec);
			impls.put(spec.getName().toString(), specImpl);
			if (Utils.isVerbose())
				logger.warn("SpecImpl: %s -> %s", spec.getName().toString(), specImpl);
		}
		
		return impls;
	}
	
	protected void writeOutputs(Iterable<TreeImplSpec> impls) {
		Filer filer = this.processingEnv.getFiler();
		
		for (TreeImplSpec impl : impls) {
			try {
				impl.write(filer);
			} catch (IOException e) {
				getLogger().error("Error writing impl %s: %s", impl.getClassName(), e.getMessage());
				e.printStackTrace();
				continue;
			} catch (Exception e) {
				getLogger().error("Error writing impl %s: %s", impl.getClassName(), e.getLocalizedMessage());
				throw e;
			}
		}
	}
	
	protected void writeOutputsMP(Collection<TreeImplSpec> impls) {
		Filer filer = this.processingEnv.getFiler();
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors() * 2 - 1);
		threads = Math.min(threads, impls.size());
		getLogger().warn("Running on %d threads", threads);
		
		Queue<TreeImplSpec> iQueue = new LinkedBlockingQueue<>(impls);
		BlockingQueue<OutputInfo> oQueue = new LinkedBlockingQueue<>();
		AtomicInteger finishCt = new AtomicInteger(threads);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		for (int i = 0; i < threads; i++)
			executor.submit(new WriterThread(iQueue, oQueue, finishCt));
		
		while (finishCt.get() > 0 || !oQueue.isEmpty()) {
			OutputInfo info;
			try {
				info = oQueue.poll(1, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				getLogger().printStackTrace(e);
				continue;
			}
			if (info == null)
				continue;
			
			JavaFileObject file;
			try {
				file = filer.createSourceFile(info.className, info.sources);
			} catch (FilerException e) {
				getLogger().error("Error creating file (Filer error) for impl %s: %s", info.className, e.getMessage());
				continue;
			} catch (IOException e) {
				getLogger().error("Error creating file (IO error) for impl %s: %s", info.className, e.getMessage());
				continue;
			} catch (Exception e) {
				// Unexpected
				getLogger().error("Error creating file (unknown) for impl %s: %s", info.className, e.getMessage());
				throw e;
			}
			
			try (Writer w = file.openWriter()) {
				w.write(info.value);
			} catch (IOException e) {
				getLogger().error("Error writing impl %s: %s", info.className, e.getMessage());
				e.printStackTrace();
			} catch (Exception e) {
				getLogger().error("Error writing impl %s: %s", info.className, e.getLocalizedMessage());
				throw e;
			}
		}
		executor.shutdown();
		
	}
	
	class WriterThread implements Runnable {
		final Queue<TreeImplSpec> inQueue;
		final Queue<OutputInfo> outQueue;
		final AtomicInteger finishCt;
		
		public WriterThread(Queue<TreeImplSpec> inQueue, Queue<OutputInfo> outQueue, AtomicInteger finishCt) {
			this.inQueue = inQueue;
			this.outQueue = outQueue;
			this.finishCt = finishCt;
		}

		@Override
		public void run() {
			try {
				while (!Thread.interrupted()) {
					TreeImplSpec impl = inQueue.poll();
					if (impl == null)
						return;
					
					OutputInfo out;
					try {
						out = impl.writeMP();
					} catch (IOException e) {
						getLogger().error("io Error writing impl %s: %s %s", impl.getClassName(), e.getClass(), e.getMessage());
						getLogger().printStackTrace(e);
						continue;
					} catch (Exception e) {
						getLogger().error("Error writing impl %s: %s %s/%s", impl.getClassName(), e.getClass(), e.getMessage(), e.getCause());
						getLogger().printStackTrace(e);
						throw e;
					}
					
					this.outQueue.add(out);
				}
			} finally {
				this.finishCt.decrementAndGet();
			}
		}
	}
	
	public static void main(String...args) {
		
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		IRAnnotationProcessor.main();
		Instant start = Instant.now();
		
		if (Utils.isVerbose())
			getLogger().note("Hello, world! %s", annotations);
		
		Map<String, TypeElement> annotationLUT = annotations.stream()
				.collect(Collectors.toMap(annotation -> annotation.getQualifiedName().toString(), x -> x));
		
		Map<String, TreeSpec> niSpecs = Collections.emptyMap(), iSpecs = Collections.emptyMap();
		
		TypeElement noImpl = annotationLUT.get(IRTypes.TREE_NOIMPL);
		if (noImpl != null)
			niSpecs = this.processTrees(noImpl, roundEnv);
		TypeElement adt = annotationLUT.get(IRTypes.TREE_ADT);
		if (adt != null)
			;
		TypeElement impl = annotationLUT.get(IRTypes.TREE_IMPL);
		if (impl != null)
			iSpecs = this.processTrees(impl, roundEnv);
		
		if (!iSpecs.isEmpty()) {
//			if (Utils.isVerbose())
//				getLogger().note("niSpecs=%s / iSpecs=%s", niSpecs, iSpecs);
			
			getLogger().note("Missing specs=%s", getUnprocessed(adt, roundEnv, niSpecs, iSpecs));
			
			if (impl != null)
				this.processImplOutputs(impl, roundEnv, niSpecs, iSpecs);
		}
		
		Duration elapsed = Duration.between(start, Instant.now());
		getLogger().note("Ran in %d.%09d", elapsed.getSeconds(), elapsed.getNano());
		
		return true;
	}
}
