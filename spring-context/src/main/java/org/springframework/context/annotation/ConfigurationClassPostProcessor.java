/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;

/**
 * {@link BeanFactoryPostProcessor} used for bootstrapping processing of
 * {@link Configuration @Configuration} classes.
 *
 * <p>Registered by default when using {@code <context:annotation-config/>} or
 * {@code <context:component-scan/>}. Otherwise, may be declared manually as
 * with any other BeanFactoryPostProcessor.
 *
 * <p>This post processor is priority-ordered as it is important that any
 * {@link Bean} methods declared in {@code @Configuration} classes have
 * their corresponding bean definitions registered before any other
 * {@link BeanFactoryPostProcessor} executes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,
		PriorityOrdered, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {

	private static final String IMPORT_REGISTRY_BEAN_NAME =
			ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


	private final Log logger = LogFactory.getLog(getClass());

	private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	@Nullable
	private Environment environment;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	//缓存metadataReader
	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

	private boolean setMetadataReaderFactoryCalled = false;

	private final Set<Integer> registriesPostProcessed = new HashSet<>();

	private final Set<Integer> factoriesPostProcessed = new HashSet<>();

	@Nullable
	private ConfigurationClassBeanDefinitionReader reader;

	private boolean localBeanNameGeneratorSet = false;

	/* Using short class names as default bean names */
	private BeanNameGenerator componentScanBeanNameGenerator = new AnnotationBeanNameGenerator();

	/* Using fully qualified class names as default bean names */
	private BeanNameGenerator importBeanNameGenerator = new AnnotationBeanNameGenerator() {
		@Override
		protected String buildDefaultBeanName(BeanDefinition definition) {
			String beanClassName = definition.getBeanClassName();
			Assert.state(beanClassName != null, "No bean class name set");
			return beanClassName;
		}
	};


	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
	}

	/**
	 * Set the {@link SourceExtractor} to use for generated bean definitions
	 * that correspond to {@link Bean} factory methods.
	 */
	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
	}

	/**
	 * Set the {@link ProblemReporter} to use.
	 * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
	 * declarations. For instance, an @Bean method marked as {@code final} is illegal
	 * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
	 */
	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setBeanClassLoader bean class loader}.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.setMetadataReaderFactoryCalled = true;
	}

	/**
	 * Set the {@link BeanNameGenerator} to be used when triggering component scanning
	 * from {@link Configuration} classes and when registering {@link Import}'ed
	 * configuration classes. The default is a standard {@link AnnotationBeanNameGenerator}
	 * for scanned components (compatible with the default in {@link ClassPathBeanDefinitionScanner})
	 * and a variant thereof for imported configuration classes (using unique fully-qualified
	 * class names instead of standard component overriding).
	 * <p>Note that this strategy does <em>not</em> apply to {@link Bean} methods.
	 * <p>This setter is typically only appropriate when configuring the post-processor as
	 * a standalone bean definition in XML, e.g. not using the dedicated
	 * {@code AnnotationConfig*} application contexts or the {@code
	 * <context:annotation-config>} element. Any bean name generator specified against
	 * the application context will take precedence over any value set here.
	 * @since 3.1.1
	 * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
	 * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
		this.localBeanNameGeneratorSet = true;
		this.componentScanBeanNameGenerator = beanNameGenerator;
		this.importBeanNameGenerator = beanNameGenerator;
	}

	@Override
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		}
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
		}
	}


	/**
	 * Derive further bean definitions from the configuration classes in the registry.
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		int registryId = System.identityHashCode(registry);
		if (this.registriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}
		if (this.factoriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}
		this.registriesPostProcessed.add(registryId);

		processConfigBeanDefinitions(registry);
	}

	/**
	 * Prepare the Configuration classes for servicing bean requests at runtime
	 * by replacing them with CGLIB-enhanced subclasses.
	 * 通过使用cglib增强的子类替换配置类，
	 * 来准备在运行时服务bean实例。
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		int factoryId = System.identityHashCode(beanFactory);
		if (this.factoriesPostProcessed.contains(factoryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + beanFactory);
		}
		this.factoriesPostProcessed.add(factoryId);
		if (!this.registriesPostProcessed.contains(factoryId)) {
			// BeanDefinitionRegistryPostProcessor hook apparently not supported...
			// Simply call processConfigurationClasses lazily at this point then.
			processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
		}
		//增强beanFactory中的全配置类
		enhanceConfigurationClasses(beanFactory);
		//加入BeanPostProcessor--ImportAwareBeanPostProcessor
		beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
	}

	/**
	 * pring有两种对象，配置类和普通类，
	 * 配置类是有@configuration的全配置类和有/@Bean/@Component/@ComponentScan/@Import/@ImportResourced其中一钟的部分配置类
	 * 对于@componet部分配置类，不做任何处理
	 * 对于@ComponentScan会扫描旗下的包
	 * 对于@Import导入定义的类
	 * 对于@ImportResourced
	 * 目前理解：所有有注解，且需要spring维护的类都是配置类
	 * 对于@configuration的全配置类 myh-todo
	 * 最后通过his.reader.loadBeanDefinitions(configClasses);把所有配置类放入bd中
	 * 获取所有的bd，然后判断bd是不是包含configuration
	 * Build and validate a configuration model based on the registry of
	 * {@link Configuration} classes.
	 */
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
		//用来存储没有被处理过，且注解为@configuration/@Bean/@Component/@ComponentScan/@Import/@ImportResourced其中一种的bd，此bd作为配置类，
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		//获取bdMap中所有的bdName
		String[] candidateNames = registry.getBeanDefinitionNames();
		//遍历bdName
		for (String beanName : candidateNames) {
			//根据bdName获取bd
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			//判断bd是否处理过，是则打印日志
			if (ConfigurationClassUtils.isFullConfigurationClass(beanDef) ||
					ConfigurationClassUtils.isLiteConfigurationClass(beanDef)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			//如果没有被处理过，判断是否加了@configuration/@Bean/@Component/@ComponentScan/@Import/@ImportResource注解，
			// 是则加入configCandidates，否则继续循环，判断下一个bd
			//beanDef如果未配置类，checkConfigurationClassCandidate方法解析beanDef的order属性，设置bd
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}
		//configCandidates为空说明，没有配置类需要处理了，直接返回
		// Return immediately if no @Configuration classes were found
		if (configCandidates.isEmpty()) {
			return;
		}
		//有需要处理的配置类则继续
		// Sort by previously determined @Order value, if applicable
		//按先前确定的@Order值排序(如果适用)
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

		// Detect any custom bean name generation strategy supplied through the enclosing application context
		//获取name生成策略，如果有自定以的则使用自定义的，如果没有，使用默认的
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			if (!this.localBeanNameGeneratorSet) {
				//可以获取早期bean
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator;
					this.importBeanNameGenerator = generator;
				}
			}
		}
		//environment为空，配置一个默认的StandardEnvironment
		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

		// Parse each @Configuration class
		//生成一个解析类，用来解析配置类
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);
		//根据所有配置类List列表生成待解析的配置类set集合，去重，原因如下
		// BeanDefinitionHolder的hashcode和bd/beanName/aliases有关,其中bd的hashcode与baenclass，scope等有关，
		// 多以同一个类的BeanDefinitionHolder的hashcode相同，
		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
		//已经解析过的配置类列表
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
			parser.parse(candidates);
			parser.validate();
			//获取已经解析过的配置类set集合
			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			//清空已经解析过的所有配置类
			configClasses.removeAll(alreadyParsed);

			//创建一个读取器，
			// 维护registry，
			// 把解析器的importStack对象作为importRegistry维护，用去操作import导入的对象
			// Read the model and create bean definitions based on its content
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
			//此方法只是为了处理处理configClasses中imports注解导入的类，处理@bean，@importResource
			//为什么不处理@componentScan类和配置类本身，因为在此方法执行之前，要么在注册配置类时，要么在scan的时候，就进行了注册操作
			this.reader.loadBeanDefinitions(configClasses);
			//把所有刚解析的配置类加入alreadyParsed集合
			alreadyParsed.addAll(configClasses);
			//处理完配置类，清空配置类set集合candidates
			candidates.clear();
			//如果现在注册表中的bd数量大于处理配置类之前的bd数量
			//registry.getBeanDefinitionCount现在注册表中的bd数量
			// candidateNames处理配置类之前的bd数量
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				//获取现在注册表中所有的bdName数组
				String[] newCandidateNames = registry.getBeanDefinitionNames();
				//对处理配置类之前的bdName进行去重
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
				//存储已经解析过的bd
				Set<String> alreadyParsedClasses = new HashSet<>();
				//把已经解析过的配置类集合alreadyParsed的beanName加入configurationClass中
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				//遍历现在注册表中所有的bdName数组,如果在解析配置类是新增的类中有没有处理过的配置类，则加入candidates在下次循环解析
				for (String candidateName : newCandidateNames) {
					//如果解析之前注册表中所有的bdName数组不包含新加入的bdName
					if (!oldCandidateNames.contains(candidateName)) {
						//则根据bdName获取bd
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						//判断该bd是否是配置且是否被解析过，如果是没有解析过的配置类，加入candidates，在下次循环解析
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				candidateNames = newCandidateNames;
			}
		}
		while (!candidates.isEmpty());

		// 将ImportRegistry注册为bean，以便支持ImportAware @Configuration类
		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			//清除外部提供的MetadataReaderFactory中的缓存;这对于共享缓存是一个no-op，因为它将被ApplicationContext清除。
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

	/**
	 * Post-processes a BeanFactory in search of Configuration class BeanDefinitions;
	 * any candidates are then enhanced by a {@link ConfigurationClassEnhancer}.
	 * Candidate status is determined by BeanDefinition attribute metadata.
	 * 对bean工厂进行后处理，以搜索配置类bean定义;
	 * 然后使用{@link ConfigurationClassEnhancer}增强所有bean。
	 * 候选状态由BeanDefinition属性元数据决定。
	 * @see ConfigurationClassEnhancer
	 */
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		//AbstractBeanDefinition实例的全配置类集合
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		//遍历beanfactory中的所有的bdName
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			//根据bdName获取bd
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			//判断beanDef是不是@configuration注解类，即是不是全注解类（bd有full标记）
			if (ConfigurationClassUtils.isFullConfigurationClass(beanDef)) {
				//不是AbstractBeanDefinition实例报错
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				}
				//警告信息，不用管
				else if (logger.isWarnEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.warn("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				//把类放入集合configBeanDefs中,
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
		//没有全注解类，则返回
		if (configBeanDefs.isEmpty()) {
			// nothing to enhance -> return immediately
			return;
		}
		//创建一个ConfigurationClassEnhancer增强配置类
		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		//遍历全配置类集合
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			//获取bd
			AbstractBeanDefinition beanDef = entry.getValue();
			// If a @Configuration class gets proxied, always proxy the target class
			//是否实现cglib代理
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			try {
				// Set enhanced subclass of the user-specified bean class
				//设置用户指定的bean类的增强子类

				//获取bd的beanClass
				Class<?> configClass = beanDef.resolveBeanClass(this.beanClassLoader);

				if (configClass != null) {
					//根据beanClass创建confingClass的cglib代理类
					//可以通过下面这个工具找到内存中的类
					//java -classpath "C:\Program Files\Java\jdk1.8.0_60\lib\sa-jdi.jar" sun.jvm.hotspot.HSDB
					Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
					if (configClass != enhancedClass) {
						if (logger.isDebugEnabled()) {
							logger.debug(String.format("Replacing bean definition '%s' existing class '%s' with " +
									"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
						}
						beanDef.setBeanClass(enhancedClass);
					}
				}
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
			}
		}
	}


	private static class ImportAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

		private final BeanFactory beanFactory;

		public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public PropertyValues postProcessPropertyValues(
				PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) {

			// Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
			// postProcessPropertyValues method attempts to autowire other configuration beans.
			if (bean instanceof EnhancedConfiguration) {
				((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
			}
			return pvs;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName)  {
			if (bean instanceof ImportAware) {
				ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
				AnnotationMetadata importingClass = ir.getImportingClassFor(bean.getClass().getSuperclass().getName());
				if (importingClass != null) {
					((ImportAware) bean).setImportMetadata(importingClass);
				}
			}
			return bean;
		}
	}

}
