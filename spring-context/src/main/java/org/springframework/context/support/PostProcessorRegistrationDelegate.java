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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {
	/**
	 注意：Ordered接口是PriorityOrdered接口的父接口
	 以下的所有操作，都利用processedBeans列表排除已经执行过的BeanDefinitionRegistryPostProcessor
	 在beanfactory是BeanDefinitionRegistry时
		 BeanDefinitionRegistryPostProcessor依次添加执行：
		 1、通过addBeanFactoryPostProcessor添加的BeanDefinitionRegistryPostProcessor，按添加顺序执行
		 2、Spring本身和通过register添加进bdMap中的（即refresh方法调用之前加入bdMap中的），并实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor，并排序/执行，此时ConfigurationClassPostProcessor会解析@configuration注解的类，
		 会把所有的BeanDefinitionRegistryPostProcessor扫描进bdmap中,
		 该BeanDefinitionRegistryPostProcessor子类中会有一个 ConfigurationClassPostProcessor 用来解析配置类，扫描所有@component添加到db，目前只有这一个类（重点）
		 3、执行以上操作后，bdMap中,未执行的，并实现了PriorityOrdered接口或实现了Ordered接口的的BeanDefinitionRegistryPostProcessor，排序，执行，此处执行0个
		 4、执行以上操作后，bdMap中,未执行的，并且PriorityOrdered和Ordered接口都没实现的BeanDefinitionRegistryPostProcessor,排序，执行，此处执行0个
		 5、调用以上所有的BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
		 6、执行通过addBeanFactoryPostProcessor添加的BeanFactoryPostProcessor的postProcessBeanFactory方法
		 调用通过addBeanFactoryPostProcessor添加并实现BeanFactoryPostProcessor接口的实现类的postProcessBeanFactory方法
	 在beanfactory不是是BeanDefinitionRegistry时
	 	 1、	调用通过addBeanFactoryPostProcessor添加并实现BeanFactoryPostProcessor和BeanDefinitionRegistryPostProcessor接口的实现类的postProcessBeanFactory方法

	 以下的所有操作，都利用processedBeans列表排除已经执行过的BeanDefinitionRegistryPostProcessor（上面执行的）和BeanFactoryPostProcessor
	 beanFactory的bdMap中的BeanFactoryPostProcessor依次添加执行：
	 1、实现了PriorityOrdered接口的BeanFactoryPostProcessor，排序/执行
	 2、 实现了Ordered接口的BeanDefinitionRegistryPostProcessor，排序，执行
	 3、PriorityOrdered和Ordered接口都没实现的BeanDefinitionRegistryPostProcessor，排序，执行
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		//存储所有处理过的BeanDefinitionRegistryPostProcessor的beanName，
		// 即在这个列表的BeanDefinitionRegistryPostProcessor在下次循环不再执行
		Set<String> processedBeans = new HashSet<>();

		if (beanFactory instanceof BeanDefinitionRegistry) {
			//beanFactory实现了BeanDefinitionRegistry接口
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			//用来存储BeanFactoryPostProcessor处理器列表
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			//用来存储BeanDefinitionRegistryPostProcessor处理器列表，
			// 在把BeanDefinitionRegistryPostProcessor添加进列表之后都会被执行
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();
			//遍历所有通过addBeanFactoryPostProcessor添加的beanFactoryPostProcessors
			//如果beanFactoryPostProcessors是BeanDefinitionRegistryPostProcessor，执行postProcessBeanDefinitionRegistry方法并放进registryProcessors集合list中
			//如果不是，则放入regularPostProcessors集合list中
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					//执行通过addBeanFactoryPostProcessor添加的BeanDefinitionRegistryPostProcessor的扩展方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.

			//未被处理过的BeanDefinitionRegistryPostProcessor，处理过以后清空
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			//getBeanNamesForType根据bd的class属性获取beanName
			//获取BeanDefinitionRegistryPostProcessor的实现类的所有名称
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			//遍历postProcessorNames，把实现BeanDefinitionRegistryPostProcessor和PriorityOrdered接口的类，
			// 实例化后添加进currentRegistryProcessors中，把它的beanName添加进processedBeans中
			//然后进行排序，添加进registryProcessors（BeanDefinitionRegistryPostProcessor处理器列表）
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			//对Spring的BeanDefinitionRegistryPostProcessor实现类进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//把currentRegistryProcessors内的BeanDefinitionRegistryPostProcessor实现类
			// 添加registryProcessors
			registryProcessors.addAll(currentRegistryProcessors);
			//调用currentRegistryProcessors中BeanDefinitionRegistryPostProcessor实现类的postProcessBeanDefinitionRegistry方法
			//此时只有一个即ConfigurationClassPostProcessor，他的作用是处理@configuration注解类
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			//清空currentRegistryProcessors
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			//遍历实现BeanDefinitionRegistryPostProcessor和Ordered接口但没实现PriorityOrdered接口的实现类beanName，实例化后添加进currentRegistryProcessors中，把beanName添加进processedBeans中
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			//遍历没有实现PriorityOrdered和Ordered接口,实现BeanDefinitionRegistryPostProcessor的实现类的beanName，实例化后添加进currentRegistryProcessors中，把beanName添加进processedBeans中
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			//最后，调用实现BeanDefinitionRegistryPostProcessor接口的实现类的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			//最后，调用实现BeanFactoryPostProcessor接口的实现类的postProcessBeanFactory方法 （此处只有通过addBeanFactoryPostProcessor添加）
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			//如果beanfactory不是BeanDefinitionRegistry，
			// 调用通过addBeanFactoryPostProcessor添加的实现BeanFactoryPostProcessor
			// 或BeanDefinitionRegistryPostProcessor接口的类的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		//便利beanFactory中的bd，已先后顺序对实现PriorityOrdered/Ordered/都没实现的类进行排序，执行postProcessBeanFactory
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		//清除缓存的合并bean定义，因为后处理程序可能有，修改原始元数据，例如替换值中的占位符…
		beanFactory.clearMetadataCache();
	}

	/**
	 * 在执行本方法注册beanPostProcessor之前，spring内部已经注册了三个postprocessor
	 * applicationContextAwareProcessor：详情请看AbstractApplicationContext的beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this))地方
	 * ApplicationListenerDetector：myh-question?
	 * configurationClassPostProcessor的内部类ImportAwarebeanPostProcessor：处理importAware接口实现类
	 *
	 * @param beanFactory
	 * @param applicationContext
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		//根据type获取所有的BeanPostProcessor实现类的名字
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		//beanFactory.getBeanPostProcessorCount()原有的有3个
		// 1代表最后面新建的new ApplicationListenerDetector(applicationContext)。
		//postProcessorNames.length从bdmap中获取的有3个
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		//判断是否有InstantiationAwareBeanPostProcessor/DestructionAwareBeanPostProcessor，有就分别把标志设为存在
		//把BeanPostProcessorChecker放到名为beanPostProcessors的list集合的尾部，如果已经存在，则remove，再存放
		//BeanPostProcessorChecker作用为了检测是否执行了所有的postProcessor，如果一个bean执行了beanProcessorTargetCount个
		//后置处理器，则说明执行完毕，否则，打印info信息
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		//定义四种BeanPostProcessor集合，实现priorityOrdered的，实现internal的，实现ordered的，都不实现的
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			//实现PriorityOrdered的，
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				//获取BeanPostProcessor实例
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				//把BeanPostProcessor添加进priorityOrderedPostProcessors
				priorityOrderedPostProcessors.add(pp);
				//如果BeanPostProcessor是MergedBeanDefinitionPostProcessor实例，
				// 表示是内部的，则BeanPostProcessor也增加进internalPostProcessors集合
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				//实现Ordered，加入orderedPostProcessorNames集合
				orderedPostProcessorNames.add(ppName);
			}
			else {
				//都不实现，加入nonOrderedPostProcessorNames集合
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		//给priorityOrderedPostProcessors集合内的postProcessor排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		//先会把前面添加进取的相同beanPostProcessors移除，再把postProcessor注册进入beanPostProcessors的list集合的尾部
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		//把 orderedPostProcessorNames 实例化，排序，先会把前面添加进取的相同beanPostProcessors移除，再注册进入beanPostProcessors的list集合的尾部
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		//把 nonOrderedPostProcessors 实例化，先会把前面添加进取的相同beanPostProcessors移除，
		// 再注册进入beanPostProcessors的list集合的尾部，不实现排序接口则不排序
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		//所有上面的beanPostProcessors都会检测时否为MergedBeanDefinitionPostProcessor实例，如果时，则加入internalPostProcessors
		//为internalPostProcessors排序，先会把前面添加进取的相同beanPostProcessors移除，
		// 再注册进入beanPostProcessors的list集合的尾部,并会把前面添加进取的移除
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		//重新注册后处理器，用于检测内部为applicationlistener的bean，
		//将它移动到处理器链的末端(用于获取代理等)。
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
