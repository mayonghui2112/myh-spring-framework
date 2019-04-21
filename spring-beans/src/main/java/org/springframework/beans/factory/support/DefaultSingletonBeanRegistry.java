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

package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 完成singleton bean注册功能。维护了单例Map，单例工厂map，早期单例Mpa
 * 等一系列map支持singleton的创建，依赖的注入等问题
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/**单例对象容器,所有单例的创建和获取操作都会在该对象上加锁
	 * Cache of singleton objects: bean name --> bean instance */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** createbean逻辑封装称对象ObjectFactory，一个bn对应一个创建逻辑
	 * ObjectFactory是一个lamda表达式，beanName, () -> getEarlyBeanReference(beanName, mbd, bean)
	 * 其中，参数bean是刚由构造函数创建的对象
	 * Cache of singleton factories: bean name --> ObjectFactory */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**早期单例对象的容器 只调用调用了构造函数创建的对象，都放入这里，初始化后从这里移除。
	 * Cache of early singleton objects: bean name --> bean instance */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

	/** 已经被注册bn对象集合，包含被注册顺序 ，是为了保持singleton创建的顺序吗？myh-question-todo?
	 * Set of registered singletons, containing the bean names in registration order */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**当前正在创建的单例bean的名称容器，singleton一开始创建，就放入该并发容器，表示正在创建，创建完成移除
	 * Names of beans that are currently in creation */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**当前在创建检查中被排除的bean的名称容器
	 * Names of beans currently excluded from in creation checks */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** 被抑制的异常列表。
	 * List of suppressed Exceptions, available for associating related causes */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** 标志，当前单例容器正在摧毁
	 * Flag that indicates whether we're currently within destroySingletons */
	private boolean singletonsCurrentlyInDestruction = false;

	/** 禁用的bean容器
	 * Disposable bean instances: bean name --> disposable instance */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name --> Set of bean names that the bean contains
	 * 	<bn,bn包含的bn集合> 组成的关系容器 myh-quesiton?
	 * */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Set<String>依赖String，<被依赖bean，依赖该bean的集合>组成的关系容器，DependsOn注解或者xml denpend-on属性
	 * Map between dependent bean names: bean name --> Set of dependent bean names */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** String依赖Set<String>;<依赖bean，被依赖的baanName集合>组成的关系容器，DependsOn注解或者xml denpend-on属性
	 * Map between depending bean names: bean name --> Set of bean names for the bean's dependencies */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	/**
	 * 注册单例对象
	 * @param beanName the name of the bean
	 * @param singletonObject the existing singleton object
	 * @throws IllegalStateException
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		//锁定单例对象映射表
		synchronized (this.singletonObjects) {
			//singletonObjects包含此bn对应对象了吗
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			//此bn没有绑定对象，则将
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 将给定的单例对象添加到该工厂的单例缓存中。
	 * 调用此方法用来注册eager singleton。
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			//添加到单例容器
			this.singletonObjects.put(beanName, singletonObject);
			//从单例工厂容器移除，
			this.singletonFactories.remove(beanName);
			//从早期单例单例容器移除
			this.earlySingletonObjects.remove(beanName);
			//注册单例对象到registeredSingletons集合
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 为了解决循环依赖，将由构造函数创建的bean构成lamda表达式放入singletonFactories
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			//此时实例化以后还没有初始化
			if (!this.singletonObjects.containsKey(beanName)) {
				//放进单例工厂容器
				this.singletonFactories.put(beanName, singletonFactory);
				//从早期单例容器已从
				this.earlySingletonObjects.remove(beanName);
				//添加进注册单例容器，一般情况是第二次添加，但是set集合可以去重，添加不进去
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 根据bn从缓存找寻singleton对象，
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		//从singletonObjects的集合map中根据beanName获取对象
		Object singletonObject = this.singletonObjects.get(beanName);
		//对象为空，检查是否指定的单例bean是否当前处于创建状态
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			//当获得这个锁的时候，说明正在创建的实例已经实例化完成，至少已经放进singletonFactories集合里面了
			synchronized (this.singletonObjects) {
				//earlySingletonObjects集合map中获取singletonObject，
				//获取到，说明bn对应的实例已经从singletonFactories取出来了
				singletonObject = this.earlySingletonObjects.get(beanName);
				//如果没有取出来，且允许访问早期创建对象的引用
				if (singletonObject == null && allowEarlyReference) {
					//根据bn，从singletonFactories集合map中获取singletonFactory单例工程
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						//根据工厂获取对象，加入到earlySingletonObjects，并把工厂从singletonFactories移除
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return singletonObject;
	}

	/**
	 * 根据bn返回获取的raw singleton对象，如果没有被注册，创建并注册一个新的singleton bean
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary 将singleton创建逻辑延迟到子类，函数式接口，接收singleton创建逻辑
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		//断言Name不能为空
		Assert.notNull(beanName, "Bean name must not be null");
		//同步执行
		synchronized (this.singletonObjects) {
			//从singletonObjects单例对象缓存中获取bean
			Object singletonObject = this.singletonObjects.get(beanName);
			//获取不到，说明还没创建
			if (singletonObject == null) {
				//在销毁该工厂对象时不允许创建单例bean(不要在销毁方法实现中从bean工厂请求bean !)
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				//创建单例之前，将bn放入正在创建的bn集合，表明bn正在创建
				beforeSingletonCreation(beanName);
				//创建单例完成标志设置为false
				boolean newSingleton = false;
				//如果suppressedExceptions没有初始化，就初始化suppressedExceptions= new LinkedHashSet<>();
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					//调用singletonFactory，即刚刚传入的匿名类的getObject方法，获取单例
					singletonObject = singletonFactory.getObject();
					//创建单例完成标志设置为true
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					//从singletonsCurrentlyInCreation正在创建的单例容器中移除本bn
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					//把单例对象从earlybean一如singleBean
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * 注册可忽略异常
	 * Register an Exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * @param ex the Exception to register
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 移除单例对象
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	/**
	 * 该bn对应的bean是否应该被检查
	 * @param beanName
	 * @param inCreation
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 返回指定的单例bean是否当前处于创建状态
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 单例创建之前调用，将bn放入正在创建bn集合，表明正在创建该bean
	 * 默认实现将单例注册为当前正在创建的单例。
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		//如果该bn需要被检查，且不能将bn正确添加到正在创建的bean集合中，抛出异常
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * 将给定的bean添加到此注册表中的一次性bean列表中。
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * 注册两个bean之间的包含关系，例如一个内部bean和包含它的外部bean之间的关系。
	 * 还根据销毁顺序将外部的bean注册为依赖于被包含的内部的bean。
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * dependentBeanName 依赖b eanName
	 * 为给定的bean注册一个依赖bean，以便在销毁给定的bean之前销毁该bean。
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean 被依赖bn
	 * @param dependentBeanName the name of the dependent bean 依赖bn
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			//根据beanName获取依赖该bean的baanName集合，如果未null，新建立一个集合
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			//将dependentBeanName加入到依赖beanName的集合，如果加入不成功，说明已经添加过了，直接返回
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}
		//上一步添加成功，根据dependentBeanName获取该bean依赖的baanName集合，如果未null，新建立一个集合
		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			//将beanName添加入该集合
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 确定循环依赖
	 * 确定指定的依赖bean是否已注册为依赖于给定bean或依赖于其传递依赖项
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
		protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		//转换为标准名
		String canonicalName = canonicalName(beanName);
		//查询时否存在依赖该bean的bean
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		//如果没有存在依赖该bean的bean，说明不存在循环依赖，返回
		if (dependentBeans == null) {
			return false;
		}
		//如果依赖beanName的bean中有dependentBeanName，说明存在循环依赖，返回true
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		//如果依赖beanName的bean中没有dependentBeanName，
		// 遍历依赖dependentBeanName有没有依赖《依赖该beanName的bean》，即dependentBeanName间接依赖beanName
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 确定是否为beanName注册了依赖bean。
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * 返回依赖于beanName的所有bean的名称(如果有的话)。
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * 返回指定bean所依赖的所有bean的名称(如果有的话)。
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	/**
	 * 销毁所有单例对象
	 */
	public void destroySingletons() {
		if (logger.isDebugEnabled()) {
			logger.debug("Destroying singletons in " + this);
		}
		//表明单例正在销毁
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		//获取所有一次性单例
		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		//遍历销毁所有的一次性单例
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}
		//myh-question-todo？
		//清空各个map
		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		//清理所有的单例Map，并将singletonsCurrentlyInDestruction设置为true，表明单例销毁完成
		clearSingletonCache();
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 摧毁给定的一次性单例
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		//从单例map移除
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		//摧毁相应的实例
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * 销毁给定的bean实例，销毁之前摧毁所有直接间接依赖该bean的bean
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				logger.error("Destroy method on bean with name '" + beanName + "' threw an exception", ex);
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
